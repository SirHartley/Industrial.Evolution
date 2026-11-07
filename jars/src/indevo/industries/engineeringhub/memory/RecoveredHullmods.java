package indevo.industries.engineeringhub.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.utils.helper.MiscIE;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecoveredHullmods {

    public static final String MEM_KEY = "$IndEvo_engHubHullmodData";
    public static final int MIN_COST = 25;
    public static final float BASE_VALUE_PER_RELIC = 100f;

    public static void record(MarketAPI market, ShipVariantAPI variant) {
        Map<String, String> data = getStored(market);
        recordVariant(data, variant);
        market.getMemoryWithoutUpdate().set(MEM_KEY, data);
    }

    private static void recordVariant(Map<String, String> data, ShipVariantAPI variant) {
        if (variant == null) return;

        ShipHullSpecAPI hull = variant.getHullSpec();
        for (String id : variant.getHullMods()) {
            if (data.containsKey(id) || hull.isBuiltInMod(id)) continue;
            if (isLearnable(Global.getSettings().getHullModSpec(id))) data.put(id, hull.getHullId());
        }

        for (String slotId : variant.getModuleSlots()) {
            if (variant.getSlot(slotId).isStationModule()) recordVariant(data, variant.getModuleVariant(slotId));
        }
    }

    // FleetEncounterContext.lootHullMods filter plus d-mods
    public static boolean isLearnable(HullModSpecAPI spec) {
        return spec != null
                && !Global.getSector().getPlayerFaction().knowsHullMod(spec.getId())
                && !spec.isHidden()
                && !spec.isHiddenEverywhere()
                && !spec.hasTag(Tags.HULLMOD_NO_DROP)
                && !spec.hasTag(Tags.HULLMOD_DMOD);
    }

    public static Map<String, String> getLearnable(MarketAPI market) {
        Map<String, String> data = getStored(market);
        data.keySet().removeIf(id -> !isLearnable(Global.getSettings().getHullModSpec(id)));
        market.getMemoryWithoutUpdate().set(MEM_KEY, data);
        return new LinkedHashMap<>(data);
    }

    public static int getCost(HullModSpecAPI spec) {
        return Math.max(MIN_COST, Math.round(spec.getBaseValue() / BASE_VALUE_PER_RELIC));
    }

    public static int getAvailableRelics(MarketAPI market) {
        CargoAPI storage = Misc.getStorageCargo(market);
        CargoAPI fleet = MiscIE.getPlayerCargo();

        float amount = 0f;
        if (storage != null) amount += storage.getCommodityQuantity(ItemIds.RARE_PARTS);
        if (fleet != null) amount += fleet.getCommodityQuantity(ItemIds.RARE_PARTS);
        return (int) amount;
    }

    public static void learn(MarketAPI market, String id) {
        HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
        if (!isLearnable(spec)) throw new IllegalStateException("IndEvo: hullmod " + id + " is not learnable at " + market.getName());

        int cost = getCost(spec);
        if (getAvailableRelics(market) < cost) throw new IllegalStateException("IndEvo: not enough relic components for " + id + " at " + market.getName() + ", need " + cost);

        float remaining = cost;
        CargoAPI storage = Misc.getStorageCargo(market);
        if (storage != null) {
            float take = Math.min(remaining, storage.getCommodityQuantity(ItemIds.RARE_PARTS));
            storage.removeCommodity(ItemIds.RARE_PARTS, take);
            remaining -= take;
        }

        if (remaining > 0) MiscIE.getPlayerCargo().removeCommodity(ItemIds.RARE_PARTS, remaining);

        Global.getSector().getPlayerFaction().addKnownHullMod(id);

        Map<String, String> data = getStored(market);
        data.remove(id);
        market.getMemoryWithoutUpdate().set(MEM_KEY, data);

        Global.getSector().getCampaignUI().addMessage("Learned %s, used %s.", Misc.getTextColor(),
                spec.getDisplayName(), cost + " " + Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName(),
                Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getStored(MarketAPI market) {
        MemoryAPI mem = market.getMemoryWithoutUpdate();
        if (mem.contains(MEM_KEY)) return (Map<String, String>) mem.get(MEM_KEY);
        return new LinkedHashMap<>();
    }
}
