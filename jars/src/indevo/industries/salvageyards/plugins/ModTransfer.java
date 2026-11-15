package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.RestorationDocks;
import indevo.utils.helper.MiscIE;

import java.util.*;

// D-mod and S-mod transfer rules shared by the panel tabs
public final class ModTransfer {

    public enum Kind {
        DMOD, SMOD
    }

    public static final float RELICS_PER_DP = 5f;
    public static final String LORE = "The modification is woven through the ship's frame. Domain micro-assemblers can separate it at the joints and rebuild it inside a compatible hull.";

    private ModTransfer() {
    }

    public static List<FleetMemberAPI> getMembers(MarketAPI market) {
        Set<FleetMemberAPI> members = new LinkedHashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

        CargoAPI storageCargo = MiscIE.getStorageCargo(market);
        if (storageCargo != null && storageCargo.getMothballedShips() != null)
            members.addAll(storageCargo.getMothballedShips().getMembersListCopy());

        return new ArrayList<>(members);
    }

    public static boolean isInFleet(FleetMemberAPI member) {
        return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(member);
    }

    public static float getCostMult(MarketAPI market) {
        float costMod = 1.5f - market.getFaction().getRelationship(Global.getSector().getPlayerFaction().getId());
        return Math.max(0.8f, Math.min(1.5f, costMod));
    }

    public static boolean isRestorable(FleetMemberAPI member) {
        return !member.getVariant().hasTag(Tags.VARIANT_UNRESTORABLE) && !member.getHullSpec().hasTag(Tags.HULL_UNRESTORABLE);
    }

    public static boolean isSameSizeAndTechtype(FleetMemberAPI a, FleetMemberAPI b) {
        boolean equalSize = a.getHullSpec().getHullSize().equals(b.getHullSpec().getHullSize());
        boolean equalType = String.valueOf(a.getHullSpec().getManufacturer()).equals(String.valueOf(b.getHullSpec().getManufacturer()));

        return equalSize && equalType;
    }

    public static List<String> getDModList(FleetMemberAPI member) {
        List<String> dmodList = new ArrayList<>();
        for (String s : member.getVariant().getHullMods()) {
            if (Global.getSettings().getHullModSpec(s).getTags().contains(Tags.HULLMOD_DMOD)) dmodList.add(s);
        }

        return dmodList;
    }

    // built in d-mods can not be removed from the source hull
    public static List<String> getRemovableDModList(FleetMemberAPI member) {
        List<String> list = getDModList(member);
        list.removeAll(member.getHullSpec().getBuiltInMods());
        return list;
    }

    public static List<String> getTransferableDMods(FleetMemberAPI from, FleetMemberAPI to) {
        List<String> list = getRemovableDModList(from);
        list.removeAll(getDModList(to));
        return list;
    }

    public static List<String> getSModList(FleetMemberAPI member) {
        List<String> list = new ArrayList<>();

        for (HullModSpecAPI spec : Misc.getCurrSpecialModsList(member.getVariant())) {
            if (!spec.hasTag(Tags.HULLMOD_NO_BUILD_IN)) list.add(spec.getId());
        }

        return list;
    }

    public static List<String> getTransferableSMods(FleetMemberAPI from, FleetMemberAPI to) {
        List<String> list = new ArrayList<>();
        for (String id : getSModList(from)) {
            if (isInstalledAsNormalMod(to.getVariant(), id)) list.add(id);
        }

        return list;
    }

    // the refit already validated the mod on this hull, campaign has no ShipAPI for isApplicableToShip
    public static boolean isInstalledAsNormalMod(ShipVariantAPI variant, String id) {
        return variant.getHullMods().contains(id)
                && !variant.getPermaMods().contains(id)
                && !variant.getHullSpec().getBuiltInMods().contains(id);
    }

    public static List<String> getNormalMods(FleetMemberAPI member) {
        List<String> list = new ArrayList<>();
        for (String id : member.getVariant().getHullMods()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec.isHidden() || spec.hasTag(Tags.HULLMOD_DMOD)) continue;
            if (isInstalledAsNormalMod(member.getVariant(), id)) list.add(id);
        }

        return list;
    }

    public static int getMaxSMods(FleetMemberAPI member) {
        return Misc.getMaxPermanentMods(member, Global.getSector().getPlayerStats());
    }

    public static int getFreeSlots(FleetMemberAPI member) {
        return getMaxSMods(member) - Misc.getCurrSpecialMods(member.getVariant());
    }

    public static List<String> getMovable(Kind kind, FleetMemberAPI member) {
        return kind == Kind.DMOD ? getRemovableDModList(member) : getSModList(member);
    }

    public static List<String> getTransferable(Kind kind, FleetMemberAPI from, FleetMemberAPI to) {
        return kind == Kind.DMOD ? getTransferableDMods(from, to) : getTransferableSMods(from, to);
    }

    // null when the ship can give mods to at least one other ship here
    public static String getOriginReason(Kind kind, FleetMemberAPI origin, List<FleetMemberAPI> members) {
        if (kind == Kind.DMOD && !isRestorable(origin)) return "It can not be restored.";

        boolean hasPartner = false;
        boolean hasFreeSlot = false;
        for (FleetMemberAPI other : members) {
            if (other == origin || !isSameSizeAndTechtype(origin, other)) continue;
            if (kind == Kind.DMOD && !isRestorable(other)) continue;
            hasPartner = true;

            if (kind == Kind.SMOD && getFreeSlots(other) <= 0) continue;
            hasFreeSlot = true;
            if (getTargetReason(kind, origin, other) == null) return null;
        }

        if (!hasPartner) return "None of your ships here match it in size and manufacturer.";
        if (kind == Kind.DMOD) return "Every matching ship already has the same D-mods.";
        if (!hasFreeSlot) return "No matching ship has a free S-mod slot.";
        return "No matching ship with a free S-mod slot has the hullmod installed.";
    }

    // null when the target can take at least one mod from the origin
    public static String getTargetReason(Kind kind, FleetMemberAPI origin, FleetMemberAPI target) {
        if (kind == Kind.DMOD) {
            if (!isRestorable(target)) return "It can not be restored.";
            if (getTransferableDMods(origin, target).isEmpty()) return "It has these D-mods already.";
            return null;
        }

        if (getFreeSlots(target) <= 0) return target.getShipName() + " has no free S-mod slot left.";
        if (getTransferableSMods(origin, target).isEmpty()) return "None of the S-mods are installed on it as hullmods. Install them in the refit screen first.";
        return null;
    }

    public static String getUnavailableReason(Kind kind, MarketAPI market) {
        return kind == Kind.DMOD ? getDModReason(market) : getSModReason(market);
    }

    private static String getDModReason(MarketAPI market) {
        List<FleetMemberAPI> members = getMembers(market);
        boolean hasOrigin = false;
        boolean hasPartner = false;

        for (FleetMemberAPI m : members) {
            if (getRemovableDModList(m).isEmpty() || !isRestorable(m)) continue;
            hasOrigin = true;

            for (FleetMemberAPI other : members) {
                if (other == m || !isRestorable(other) || !isSameSizeAndTechtype(m, other)) continue;
                hasPartner = true;
                if (!getTransferableDMods(m, other).isEmpty()) return null;
            }
        }

        if (!hasOrigin) return "None of your ships here have D-mods that can be moved.";
        if (!hasPartner) return "None of your ships here match a ship with D-mods in size and manufacturer.";
        return "Every matching ship already has the same D-mods.";
    }

    private static String getSModReason(MarketAPI market) {
        List<FleetMemberAPI> members = getMembers(market);
        boolean hasOrigin = false;
        boolean hasPartner = false;
        boolean hasFreeSlot = false;

        for (FleetMemberAPI m : members) {
            if (getSModList(m).isEmpty()) continue;
            hasOrigin = true;

            for (FleetMemberAPI other : members) {
                if (other == m || !isSameSizeAndTechtype(m, other)) continue;
                hasPartner = true;
                if (getFreeSlots(other) <= 0) continue;
                hasFreeSlot = true;
                if (!getTransferableSMods(m, other).isEmpty()) return null;
            }
        }

        if (!hasOrigin) return "None of your ships here have S-mods.";
        if (!hasPartner) return "None of your ships here match a ship with S-mods in size and manufacturer.";
        if (!hasFreeSlot) return "No matching ship has a free S-mod slot.";
        return "No matching ship with a free S-mod slot has the hullmod installed. Install it in the refit screen first.";
    }

    public static float getCreditCostPerMod(FleetMemberAPI origin, int numMods, float costMult) {
        if (numMods <= 0) return 0f;
        float baseVal = RestorationDocks.getBaseShipHullSpec(origin.getVariant(), true).getBaseValue();
        float singleRepairCost = (float) (((baseVal / 2) * (Math.pow(1.2, numMods) + 1)) / numMods);

        return singleRepairCost * Setting.SY_DMOD_MOVE_BASE_COST_MULT.value() * costMult;
    }

    public static float getCreditsPerMod(Kind kind, FleetMemberAPI origin, float costMult) {
        int n = kind == Kind.DMOD ? DModManager.getNumDMods(origin.getVariant()) : getSModList(origin).size();
        return getCreditCostPerMod(origin, n, costMult);
    }

    public static int getRelicsPerMod(FleetMemberAPI target) {
        return Math.round(RELICS_PER_DP * target.getUnmodifiedDeploymentPointsCost());
    }

    public static void cycleToCustomVariant(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());
        member.setVariant(variant, false, true);
    }

    public static void moveDMods(FleetMemberAPI origin, FleetMemberAPI target, Collection<String> mods) {
        cycleToCustomVariant(origin);
        cycleToCustomVariant(target);

        for (String s : mods) {
            origin.getVariant().removePermaMod(s);
            target.getVariant().addPermaMod(s);
        }

        if (getDModList(origin).isEmpty()) restoreToBase(origin);
        if (!getDModList(target).isEmpty()) setDHull(target);

        origin.updateStats();
        target.updateStats();
    }

    public static void moveSMods(FleetMemberAPI origin, FleetMemberAPI target, Collection<String> mods) {
        cycleToCustomVariant(origin);
        cycleToCustomVariant(target);
        origin.getVariant().setSource(VariantSource.REFIT);
        target.getVariant().setSource(VariantSource.REFIT);

        for (String id : mods) {
            origin.getVariant().removePermaMod(id);
            origin.getVariant().removeMod(id);
            target.getVariant().addPermaMod(id, true);
        }

        origin.updateStats();
        target.updateStats();
    }

    private static void restoreToBase(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());
        variant.setHullSpecAPI(RestorationDocks.getBaseShipHullSpec(member.getVariant(), false));
        member.setVariant(variant, false, true);
    }

    private static void setDHull(FleetMemberAPI member) {
        ShipVariantAPI variant = member.getVariant();
        variant.setSource(VariantSource.REFIT);

        if (!variant.isDHull()) {
            ShipHullSpecAPI dHull = Global.getSettings().getHullSpec(Misc.getDHullId(variant.getHullSpec()));
            variant.setHullSpecAPI(dHull);
            member.setVariant(variant, false, true);
        }
    }
}
