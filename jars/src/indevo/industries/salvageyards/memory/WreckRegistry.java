package indevo.industries.salvageyards.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.SkillsChangeRemoveVentsCapsEffect;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.RestorationDocks;
import indevo.industries.salvageyards.plugins.ModTransfer;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.submarkets.impl.ReverseEngineeringSubmarketPlugin;

import java.util.*;

public class WreckRegistry {

    public static final int MAX_HULLS_PER_ID = 5;
    public static final int MAX_WEAPONS_PER_ID = 20;
    public static final int MAX_WINGS_PER_ID = 10;

    private final Map<String, List<WreckFit>> hulls = new HashMap<>();
    private final Map<String, Integer> weapons = new HashMap<>();
    private final Map<String, Integer> wings = new HashMap<>();

    public static class WreckFit {
        private final String hullId;
        private final Map<String, String> weapons = new LinkedHashMap<>();
        private final List<String> wings = new ArrayList<>();
        private final List<String> hullmods = new ArrayList<>();
        private final int vents;
        private final int caps;

        public WreckFit(String hullId, ShipVariantAPI variant) {
            this.hullId = hullId;

            for (String slotId : variant.getNonBuiltInWeaponSlots()) {
                String id = variant.getWeaponId(slotId);
                if (isRecoverableWeapon(id)) weapons.put(slotId, id);
            }

            for (String id : variant.getNonBuiltInWings()) {
                if (isRecoverableWing(id)) wings.add(id);
            }

            for (String id : variant.getNonBuiltInHullmods()) {
                HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
                if (spec == null || spec.isHidden() || spec.hasTag(Tags.HULLMOD_DMOD) || spec.hasTag(Tags.HULLMOD_NO_DROP)) continue;
                if (variant.getPermaMods().contains(id)) continue;

                hullmods.add(id);
            }

            vents = variant.getNumFluxVents();
            caps = variant.getNumFluxCapacitors();
        }

        public String getHullId() {
            return hullId;
        }

        public Collection<String> getWeapons() {
            return Collections.unmodifiableCollection(weapons.values());
        }

        public List<String> getWings() {
            return Collections.unmodifiableList(wings);
        }

        public List<String> getHullmods() {
            return Collections.unmodifiableList(hullmods);
        }

        public float getValue() {
            float value = 0f;
            for (String id : weapons.values()) value += Global.getSettings().getWeaponSpec(id).getBaseValue();
            for (String id : wings) value += Global.getSettings().getFighterWingSpec(id).getBaseValue();
            return value;
        }

        public FleetMemberAPI createMember(MutableCharacterStatsAPI stats) {
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, ForgeTemplateItemPlugin.getValidVariantIdForHullId(hullId));
            ModTransfer.cycleToCustomVariant(member);

            ShipVariantAPI variant = member.getVariant();
            variant.setSource(VariantSource.REFIT);
            variant.clear();

            for (Map.Entry<String, String> e : weapons.entrySet()) {
                WeaponSlotAPI slot = variant.getSlot(e.getKey());
                if (slot != null && !slot.isBuiltIn()) variant.addWeapon(e.getKey(), e.getValue());
            }

            int firstFreeBay = variant.getHullSpec().getBuiltInWings().size();
            for (int i = 0; i < wings.size(); i++) variant.setWingId(firstFreeBay + i, wings.get(i));

            for (String id : hullmods) variant.addMod(id);

            variant.setNumFluxVents(Math.min(vents, SkillsChangeRemoveVentsCapsEffect.getMaxVents(variant.getHullSpec().getHullSize(), stats)));
            variant.setNumFluxCapacitors(Math.min(caps, SkillsChangeRemoveVentsCapsEffect.getMaxCaps(variant.getHullSpec().getHullSize(), stats)));

            trimToOrdnancePoints(variant, stats, firstFreeBay);
            variant.autoGenerateWeaponGroups();
            member.updateStats();

            return member;
        }

        // npc fits can exceed the player's ordnance points
        private static void trimToOrdnancePoints(ShipVariantAPI variant, MutableCharacterStatsAPI stats, int firstFreeBay) {
            int max = variant.getHullSpec().getOrdnancePoints(stats);

            while (variant.computeOPCost(stats) > max) {
                if (variant.getNumFluxCapacitors() > 0) {
                    variant.setNumFluxCapacitors(variant.getNumFluxCapacitors() - 1);
                } else if (variant.getNumFluxVents() > 0) {
                    variant.setNumFluxVents(variant.getNumFluxVents() - 1);
                } else if (!variant.getNonBuiltInWings().isEmpty()) {
                    variant.setWingId(firstFreeBay + variant.getNonBuiltInWings().size() - 1, null);
                } else if (!variant.getNonBuiltInWeaponSlots().isEmpty()) {
                    List<String> slots = variant.getNonBuiltInWeaponSlots();
                    variant.clearSlot(slots.get(slots.size() - 1));
                } else if (!variant.getNonBuiltInHullmods().isEmpty()) {
                    List<String> mods = new ArrayList<>(variant.getNonBuiltInHullmods());
                    variant.removeMod(mods.get(mods.size() - 1));
                } else break;
            }
        }
    }

    public static class Order {
        public final Map<String, Integer> hulls = new LinkedHashMap<>();
        public final List<WreckFit> fits = new ArrayList<>();
        public final Map<String, Integer> weapons = new LinkedHashMap<>();
        public final Map<String, Integer> wings = new LinkedHashMap<>();
        public int salvageUnitCost = 0;
        public float fitCost = 0f;

        public boolean isEmpty() {
            return hulls.isEmpty() && weapons.isEmpty() && wings.isEmpty();
        }
    }

    public void recordLosses(BattleAPI battle) {
        Set<String> allowed = ReverseEngineeringSubmarketPlugin.getAllowedHulls();
        Set<String> restricted = ReverseEngineeringSubmarketPlugin.getRestrictedHulls();

        List<CampaignFleetAPI> fleets = new ArrayList<>(battle.getSnapshotSideOne());
        fleets.addAll(battle.getSnapshotSideTwo());

        for (CampaignFleetAPI fleet : fleets) {
            boolean hullsAllowed = !fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY);

            for (FleetMemberAPI member : Misc.getSnapshotMembersLost(fleet)) {
                ShipVariantAPI variant = member.getVariant();

                for (String slotId : variant.getNonBuiltInWeaponSlots()) {
                    String id = variant.getWeaponId(slotId);
                    if (isRecoverableWeapon(id)) add(weapons, id, MAX_WEAPONS_PER_ID);
                }

                for (String id : variant.getNonBuiltInWings()) {
                    if (isRecoverableWing(id)) add(wings, id, MAX_WINGS_PER_ID);
                }

                if (!hullsAllowed || !isRebuildable(member, allowed, restricted)) continue;

                String hullId = getRestoredHull(variant).getHullId();
                List<WreckFit> list = hulls.computeIfAbsent(hullId, k -> new ArrayList<>());
                list.add(new WreckFit(hullId, variant));
                while (list.size() > MAX_HULLS_PER_ID) list.remove(0);
            }
        }
    }

    public Set<String> getHullIds() {
        return new HashSet<>(hulls.keySet());
    }

    public Set<String> getWeaponIds() {
        return new HashSet<>(weapons.keySet());
    }

    public Set<String> getWingIds() {
        return new HashSet<>(wings.keySet());
    }

    public int getHullCount(String hullId) {
        List<WreckFit> list = hulls.get(hullId);
        return list == null ? 0 : list.size();
    }

    public int getWeaponCount(String weaponId) {
        return weapons.getOrDefault(weaponId, 0);
    }

    public int getWingCount(String wingId) {
        return wings.getOrDefault(wingId, 0);
    }

    public int getTotalHulls() {
        int total = 0;
        for (List<WreckFit> list : hulls.values()) total += list.size();
        return total;
    }

    public int getTotalWeapons() {
        int total = 0;
        for (int i : weapons.values()) total += i;
        return total;
    }

    public int getTotalWings() {
        int total = 0;
        for (int i : wings.values()) total += i;
        return total;
    }

    public List<WreckFit> getOldestFits(String hullId, int count) {
        List<WreckFit> list = hulls.get(hullId);
        if (list == null) return new ArrayList<>();
        return new ArrayList<>(list.subList(0, Math.min(count, list.size())));
    }

    // the order holds the oldest fits per hull, so taking from the front removes exactly those
    public void take(Order order) {
        for (Map.Entry<String, Integer> e : order.hulls.entrySet()) {
            List<WreckFit> list = hulls.get(e.getKey());
            if (list == null || list.size() < e.getValue()) throw new IllegalStateException("Wreck Registry has fewer " + e.getKey() + " hulls than the order takes");

            list.subList(0, e.getValue()).clear();
            if (list.isEmpty()) hulls.remove(e.getKey());
        }

        for (Map.Entry<String, Integer> e : order.weapons.entrySet()) remove(weapons, e.getKey(), e.getValue());
        for (Map.Entry<String, Integer> e : order.wings.entrySet()) remove(wings, e.getKey(), e.getValue());
    }

    public static ShipHullSpecAPI getRestoredHull(ShipVariantAPI variant) {
        return RestorationDocks.getBaseShipHullSpec(variant, false);
    }

    private static boolean isRebuildable(FleetMemberAPI member, Set<String> allowed, Set<String> restricted) {
        if (member.isStation() || member.isFighterWing() || Misc.isUnboardable(member)) return false;
        return ReverseEngineeringSubmarketPlugin.isWhitelisted(getRestoredHull(member.getVariant()), allowed, restricted);
    }

    private static boolean isRecoverableWeapon(String weaponId) {
        return weaponId != null && !Global.getSettings().getWeaponSpec(weaponId).hasTag(Tags.NO_DROP);
    }

    private static boolean isRecoverableWing(String wingId) {
        return wingId != null && !Global.getSettings().getFighterWingSpec(wingId).hasTag(Tags.WING_NO_DROP);
    }

    private static void add(Map<String, Integer> map, String id, int max) {
        map.put(id, Math.min(max, map.getOrDefault(id, 0) + 1));
    }

    private static void remove(Map<String, Integer> map, String id, int amount) {
        int left = map.getOrDefault(id, 0) - amount;
        if (left < 0) throw new IllegalStateException("Wreck Registry has fewer " + id + " than the order takes");

        if (left == 0) map.remove(id);
        else map.put(id, left);
    }
}
