package indevo.industries.engineeringhub;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;

import java.util.Map;

// reverse engineering rules shared by the Engineering Hub and the rat Engineering Annex; progress per base hull id is in sector memory
public final class ReverseEngineering {

    public static final String PROGRESS_KEY = "$IndEvo_researchProgress";
    public static final int RELICS_PER_DP = 10;
    private static final float RED_PER_DMOD = 0.05f;

    private ReverseEngineering() {
    }

    public static Map<String, Float> getProgressMap() {
        return MiscIE.getMapFromMemory(PROGRESS_KEY);
    }

    public static void storeProgressMap(Map<String, Float> map) {
        MiscIE.storeMapInMemory(MiscIE.getClampedMap(map, 1f), PROGRESS_KEY);
    }

    public static float getProgress(String baseHullId) {
        Float progress = getProgressMap().get(baseHullId);
        return progress == null ? 0f : Math.min(progress, 1f);
    }

    public static void addProgress(String baseHullId, float value) {
        Map<String, Float> map = getProgressMap();
        map.merge(baseHullId, value, Float::sum);
        storeProgressMap(map);
    }

    public static void removeProgress(String baseHullId) {
        Map<String, Float> map = getProgressMap();
        map.remove(baseHullId);
        storeProgressMap(map);
    }

    public static ShipHullSpecAPI getBaseHullSpec(ShipHullSpecAPI spec) {
        if (spec.isRestoreToBase()) return spec.getBaseHull();
        if (spec.isDefaultDHull()) return spec.getDParentHull();
        return spec;
    }

    public static String getBaseHullId(ShipHullSpecAPI spec) {
        return getBaseHullSpec(spec).getHullId();
    }

    // what one ship without D-mods adds
    public static float getCleanValue(HullSize size) {
        return switch (size) {
            case FRIGATE, DESTROYER, CRUISER -> 0.35f;
            case CAPITAL_SHIP -> 0.45f;
            default -> 0f;
        };
    }

    // 5% less per D-mod with a growing penalty; ignoredDMods is the hub's alpha core
    public static float getValue(ShipVariantAPI variant, int ignoredDMods) {
        int dMods = getNumNonBuiltInDMods(variant);
        if (dMods > 0) dMods = Math.max(0, dMods - ignoredDMods);

        float cut = RED_PER_DMOD * dMods;
        float value = getCleanValue(variant.getHullSize()) - cut * (1f + cut * dMods);
        // rounded so 25% does not end up 24%
        return Math.max(Math.round(value * 100f) / 100f, 0f);
    }

    public static int getNumNonBuiltInDMods(ShipVariantAPI variant) {
        int count = 0;
        for (String id : variant.getHullMods()) {
            if (!Global.getSettings().getHullModSpec(id).hasTag(Tags.HULLMOD_DMOD)) continue;
            if (variant.getHullSpec().getBuiltInMods().contains(id)) continue;
            count++;
        }

        return count;
    }

    // 15 days up to cruisers, 21 for capitals, before AI cores
    public static int getBaseDays(HullSize size) {
        return Math.round(getCleanValue(size) * 57) - 5;
    }

    public static int getRelicCost(String hullId) {
        return Math.round(Global.getSettings().getHullSpec(hullId).getSuppliesToRecover() * RELICS_PER_DP);
    }

    public static String getPaymentItemId(String hullId) {
        if (isTiandong(hullId)) return "tiandong_retrofit_bp";
        if (isRoider(hullId)) return "roider_retrofit_bp";
        return Items.SHIP_BP;
    }

    // a ship blueprint, or the retrofit item for tiandong and roider hulls, of the same hull size
    public static boolean paysFor(SpecialItemData item, String hullId) {
        ShipHullSpecAPI hull = getHullSpecOrNull(hullId);
        if (hull == null || item == null || item.getData() == null || !getPaymentItemId(hullId).equals(item.getId())) return false;

        ShipHullSpecAPI written = getHullSpecOrNull(item.getData());
        return written != null && hull.getHullSize() == written.getHullSize();
    }

    // getHullSpec throws on an id whose mod is gone
    public static ShipHullSpecAPI getHullSpecOrNull(String id) {
        if (id == null) return null;
        try {
            return Global.getSettings().getHullSpec(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean isRoider(String id) {
        ShipHullSpecAPI spec = findHullSpec(id);
        return spec != null ? spec.getTags().contains("roider_retrofit") : id.equals("roider_retrofit_bp");
    }

    public static boolean isTiandong(String id) {
        ShipHullSpecAPI spec = findHullSpec(id);
        return spec != null ? spec.getManufacturer().equals("Tiandong") : id.equals("tiandong_retrofit_bp");
    }

    private static ShipHullSpecAPI findHullSpec(String id) {
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.getHullId().equals(id)) return spec;
        }

        return null;
    }

    public static void deliver(String hullId, MarketAPI target, CargoAPI cargo) {
        ShipHullSpecAPI ship = Global.getSettings().getHullSpec(hullId);
        boolean dong = isTiandong(hullId);
        boolean roider = isRoider(hullId);
        String itemId = dong ? "tiandong_retrofit_bp" : roider ? "roider_retrofit_bp" : Items.SHIP_BP;
        String what = dong || roider ? "A retrofit template for " : "A blueprint for ";

        cargo.addSpecial(new SpecialItemData(itemId, hullId), 1);
        Global.getSector().getCampaignUI().addMessage(
                what + Misc.getAOrAnFor(ship.getHullName()) + " %s has been reverse engineered and delivered to %s",
                Global.getSettings().getColor("standardTextColor"),
                ship.getNameWithDesignationWithDashClass(), target.getName(),
                Misc.getHighlightColor(), target.getFaction().getBrightUIColor()
        );
    }
}
