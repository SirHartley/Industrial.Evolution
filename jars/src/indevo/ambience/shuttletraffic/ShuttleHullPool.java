package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.EngineSlotAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.helper.ReflectionUtils;
import indevo.utils.helper.StarReflectionUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public final class ShuttleHullPool {

    public static final String CSV_PATH = "data/config/indEvo/shuttle_traffic.csv";
    private static final String ENGINE_STYLES_PATH = "data/config/engine_styles.json";
    private static final String SMALL_FRIGATE = "kite";
    private static final String POD_HULL = "mining_drone";
    private static final String REMNANT_STYLE = "HIGH_TECH";
    private static final float POD_LENGTH = 3f;
    private static final Color DEFAULT_GLOW = new Color(255, 145, 75, 255);
    private static final Logger log = Global.getLogger(ShuttleHullPool.class);

    private static final Map<String, ShuttleHull.Look> looks = new HashMap<>();
    // Remnant entries get their own looks: another faction may fly the same hull in its own colours
    private static final Map<String, ShuttleHull.Look> remnantLooks = new HashMap<>();
    private static final Map<String, FactionPool> factionPools = new HashMap<>();
    private static final Map<String, ShuttleHull> baseEntries = new HashMap<>();
    private static final Map<String, Float> baseWeights = new HashMap<>();
    private static List<ShuttleHull> basePool;
    private static ShuttleHull podHull;
    private static boolean podChecked = false;
    private static Set<String> knownHullIds;
    private static JSONObject engineStyles;
    private static boolean reflectionWarned = false;

    private record FactionPool(FactionAPI faction, int ships, int fighters, List<ShuttleHull> hulls) {
    }

    private record EngineLook(Color glow, Color trail, boolean additive) {
    }

    private ShuttleHullPool() {
    }

    public static void reset() {
        looks.clear();
        remnantLooks.clear();
        factionPools.clear();
        baseEntries.clear();
        baseWeights.clear();
        basePool = null;
        podHull = null;
        podChecked = false;
        knownHullIds = null;
        engineStyles = null;
    }

    public static ShuttleHull rollCargo(FactionAPI faction, Random random, float baseShare, boolean smallOnly) {
        ShuttleHull hull = null;
        if (faction != null && (isRemnant(faction) || random.nextFloat() >= baseShare)) hull = pick(getFactionPool(faction, true), random, h -> h.civilian && (!smallOnly || h.small));
        if (hull == null && !isRemnant(faction)) hull = pick(getBasePool(), random, h -> !smallOnly || h.small);
        return hull;
    }

    public static ShuttleHull rollSmall(FactionAPI faction, Random random) {
        ShuttleHull hull = faction != null ? pick(getFactionPool(faction, true), random, h -> h.small) : null;
        if (hull == null && !isRemnant(faction)) hull = pick(getBasePool(), random, h -> h.small);
        return hull;
    }

    public static ShuttleHull rollAny(FactionAPI faction, Random random, float baseShare, boolean crewed) {
        ShuttleHull hull = null;
        if (faction != null && (isRemnant(faction) || random.nextFloat() >= baseShare)) hull = pick(getFactionPool(faction, crewed), random, h -> true);
        if (hull == null && !isRemnant(faction)) hull = pick(getBasePool(), random, h -> true);
        return hull;
    }

    // every Remnant craft is blue and never falls back to a base pool hull
    public static boolean isRemnant(FactionAPI faction) {
        return faction != null && Factions.REMNANTS.equals(faction.getId());
    }

    public static ShuttleHull rollBase(Random random, Set<String> hullIds) {
        return pick(getBasePool(), random, h -> hullIds.contains(h.hullId));
    }

    public static ShuttleHull getHull(String hullId, boolean fighter, int wingSize) {
        if (!hullExists(hullId)) return null;

        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
        return createHull(spec, fighter, wingSize, false, fighter);
    }

    // a fitted wing as a craft in its fleet's look, Remnant wings in the Remnant look; null when the wing has no usable hull
    public static ShuttleHull getWingHull(String wingId, FactionAPI faction) {
        FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(wingId);
        ShipVariantAPI variant = wing != null ? wing.getVariant() : null;
        if (variant == null || variant.getHullSpec() == null) return null;

        getBasePool();
        return forFaction(faction, createHull(variant.getHullSpec(), true, Math.max(1, Math.min(3, wing.getNumFighters())), false, true));
    }

    // a faction that learned a hull or wing since its pool was built gets a new pool
    public static void dropStaleFactionPools() {
        factionPools.values().removeIf(pool -> pool.faction().getKnownShips().size() != pool.ships()
                || pool.faction().getKnownFighters().size() != pool.fighters());
    }

    // the vanilla mining drone sprite at a fixed length, null when a mod removed the hull
    public static ShuttleHull getPod() {
        if (podChecked) return podHull;

        podChecked = true;
        ShipHullSpecAPI spec = hullExists(POD_HULL) ? Global.getSettings().getHullSpec(POD_HULL) : null;
        SpriteAPI sprite = spec != null ? loadSprite(spec.getSpriteName()) : null;
        if (sprite == null) {
            log.warn("Shuttle traffic: no " + POD_HULL + " sprite, escape pods off");
            return null;
        }

        EngineLook engine = getEngineLook(spec);
        float width = POD_LENGTH * sprite.getWidth() / Math.max(1f, sprite.getHeight());
        ShuttleHull.Look look = new ShuttleHull.Look(POD_HULL, sprite, engine.glow, engine.trail, engine.additive);
        podHull = new ShuttleHull(look, true, false, true, false, false, 1, width, POD_LENGTH);
        return podHull;
    }

    public static ShuttleHull pick(List<ShuttleHull> pool, Random random, Predicate<ShuttleHull> filter) {
        WeightedRandomPicker<ShuttleHull> picker = new WeightedRandomPicker<>(random);
        for (ShuttleHull hull : pool) {
            if (filter.test(hull)) picker.add(hull, getWeight(hull));
        }
        return picker.isEmpty() ? null : picker.pick();
    }

    public static List<ShuttleHull> getBasePool() {
        if (basePool != null) return basePool;

        basePool = new ArrayList<>();
        JSONArray rows;
        try {
            rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", CSV_PATH, "IndEvo");
        } catch (IOException | JSONException e) {
            log.warn("Could not read " + CSV_PATH, e);
            return basePool;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            String id = row.optString("id", "");
            if (id.isEmpty() || id.startsWith("#")) continue;

            String hullId = row.optString("hullId", "");
            if (!hullExists(hullId)) {
                log.warn("Shuttle traffic: unknown hull " + hullId + " in " + CSV_PATH);
                continue;
            }

            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (!isCrewed(spec)) {
                log.warn("Shuttle traffic: hull " + hullId + " has no crew, skipped");
                continue;
            }

            ShuttleHull.Look look = getLook(spec);
            if (look == null) continue;

            EnumSet<ShipTypeHints> hints = spec.getHints();
            ShuttleHull hull = new ShuttleHull(look, spec.getHullSize() == HullSize.FIGHTER, true, row.optBoolean("small", false),
                    hints.contains(ShipTypeHints.LINER), hints.contains(ShipTypeHints.FREIGHTER), 1);
            basePool.add(hull);
            baseEntries.putIfAbsent(hullId, hull);
            baseWeights.put(hullId, (float) row.optDouble("weight", 1d));
        }

        return basePool;
    }

    public static List<ShuttleHull> getFactionPool(FactionAPI faction, boolean crewed) {
        String key = faction.getId() + (crewed ? "" : "|any");
        FactionPool cached = factionPools.get(key);
        if (cached != null) return cached.hulls();

        // base rows first, shared hulls keep their csv flags
        getBasePool();
        List<ShuttleHull> pool = new ArrayList<>();

        for (String wingId : faction.getKnownFighters()) {
            FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(wingId);
            if (wing == null || (crewed && wing.hasTag(Tags.AUTOMATED_FIGHTER))) continue;

            ShipVariantAPI variant = wing.getVariant();
            if (variant == null || variant.getHullSpec() == null || (crewed && !isCrewed(variant.getHullSpec()))) continue;

            ShuttleHull hull = forFaction(faction, createHull(variant.getHullSpec(), true, Math.max(1, Math.min(3, wing.getNumFighters())), false, true));
            if (hull != null && !contains(pool, hull)) pool.add(hull);
        }

        for (String hullId : faction.getKnownShips()) {
            if (!hullExists(hullId)) continue;

            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec.getHullSize() != HullSize.FRIGATE || (crewed && !isCrewed(spec)) || spec.hasTag(Tags.RESTRICTED)) continue;

            EnumSet<ShipTypeHints> hints = spec.getHints();
            if (hints.contains(ShipTypeHints.STATION) || hints.contains(ShipTypeHints.MODULE)) continue;

            ShuttleHull hull = forFaction(faction, createHull(spec, false, 1, isCivilian(hints), SMALL_FRIGATE.equals(spec.getBaseHullId())));
            if (hull != null && !contains(pool, hull)) pool.add(hull);
        }

        factionPools.put(key, new FactionPool(faction, faction.getKnownShips().size(), faction.getKnownFighters().size(), pool));
        return pool;
    }

    private static ShuttleHull forFaction(FactionAPI faction, ShuttleHull hull) {
        if (hull == null || !isRemnant(faction)) return hull;

        ShuttleHull.Look look = remnantLooks.computeIfAbsent(hull.hullId, id -> {
            EngineLook engine = getStyleLook(REMNANT_STYLE, null);
            return new ShuttleHull.Look(id, hull.sprite, engine.glow, engine.trail, engine.additive);
        });
        return hull.withLook(look);
    }

    private static boolean contains(List<ShuttleHull> pool, ShuttleHull hull) {
        for (ShuttleHull entry : pool) if (entry.hullId.equals(hull.hullId) && entry.wingSize == hull.wingSize) return true;
        return false;
    }

    private static float getWeight(ShuttleHull hull) {
        Float base = baseWeights.get(hull.hullId);
        if (base != null) return base;
        if (hull.civilian) return 3f;
        if (hull.fighter) return 2f;
        return 1f;
    }

    private static boolean isCrewed(ShipHullSpecAPI spec) {
        return spec.getMinCrew() > 0f && !spec.isBuiltInMod(HullMods.AUTOMATED) && !spec.hasTag(Tags.AUTOMATED);
    }

    private static boolean isCivilian(EnumSet<ShipTypeHints> hints) {
        return hints.contains(ShipTypeHints.CIVILIAN) || hints.contains(ShipTypeHints.FREIGHTER) || hints.contains(ShipTypeHints.TANKER)
                || hints.contains(ShipTypeHints.LINER) || hints.contains(ShipTypeHints.TRANSPORT);
    }

    private static boolean hullExists(String hullId) {
        if (knownHullIds == null) {
            knownHullIds = new HashSet<>();
            for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) knownHullIds.add(spec.getHullId());
        }

        return hullId != null && knownHullIds.contains(hullId);
    }

    // a hull with a csv row keeps the csv flags in every pool, the wing size is per entry
    private static ShuttleHull createHull(ShipHullSpecAPI spec, boolean fighter, int wingSize, boolean civilian, boolean small) {
        ShuttleHull base = baseEntries.get(spec.getHullId());
        if (base != null) return base.withWingSize(wingSize);

        ShuttleHull.Look look = getLook(spec);
        if (look == null) return null;

        EnumSet<ShipTypeHints> hints = spec.getHints();
        return new ShuttleHull(look, fighter, civilian, small, hints.contains(ShipTypeHints.LINER), hints.contains(ShipTypeHints.FREIGHTER), wingSize);
    }

    private static ShuttleHull.Look getLook(ShipHullSpecAPI spec) {
        String key = spec.getHullId();
        if (looks.containsKey(key)) return looks.get(key);

        SpriteAPI sprite = loadSprite(spec.getSpriteName());
        if (sprite == null) {
            log.warn("Shuttle traffic: no sprite for hull " + key);
            looks.put(key, null);
            return null;
        }

        EngineLook engine = getEngineLook(spec);
        ShuttleHull.Look look = new ShuttleHull.Look(key, sprite, engine.glow, engine.trail, engine.additive);
        looks.put(key, look);
        return look;
    }

    private static SpriteAPI loadSprite(String path) {
        if (path == null || path.isEmpty()) return null;

        SpriteAPI sprite = Global.getSettings().getSprite(path);
        if (sprite != null && sprite.getTextureId() > 0 && sprite.getWidth() > 0) return sprite;

        try {
            Global.getSettings().loadTexture(path);
        } catch (IOException e) {
            return null;
        }

        sprite = Global.getSettings().getSprite(path);
        return sprite != null && sprite.getWidth() > 0 ? sprite : null;
    }

    private static EngineLook getEngineLook(ShipHullSpecAPI spec) {
        String styleId = null;
        Color slotColor = null;

        try {
            EngineSlotAPI slot = getFirstEngineSlot(spec);
            if (slot != null) {
                Object id = ReflectionUtils.invoke("getStyleId", slot, new Object[0], false);
                if (id instanceof String s) styleId = s;
                slotColor = slot.getColor();
            }
        } catch (Throwable t) {
            if (!reflectionWarned) log.warn("Shuttle traffic: engine slot lookup failed, using manufacturer styles", t);
            reflectionWarned = true;
            styleId = null;
        }

        if (styleId == null) styleId = getStyleForManufacturer(spec.getManufacturer());
        return getStyleLook(styleId, slotColor);
    }

    // ShipHullSpecAPI has no engine slots, the game's hull spec class does (getEngineSlots, not obfuscated)
    // its list holds obfuscated slot specs, not EngineSlot: their one no arg method returning EngineSlotAPI builds the slot
    private static EngineSlotAPI getFirstEngineSlot(ShipHullSpecAPI spec) {
        Object slots = ReflectionUtils.invoke("getEngineSlots", spec, new Object[0], false);
        if (!(slots instanceof List<?> list) || list.isEmpty()) return null;

        Object slot = list.get(0);
        if (slot instanceof EngineSlotAPI api) return api;

        for (StarReflectionUtils.ReflectedMethod method : StarReflectionUtils.getMethodsMatching(slot, null, EngineSlotAPI.class, 0)) {
            if (method.invoke(slot) instanceof EngineSlotAPI api) return api;
        }

        throw new IllegalStateException("no EngineSlotAPI method on " + slot.getClass().getName());
    }

    private static EngineLook getStyleLook(String styleId, Color slotColor) {
        JSONObject style = getEngineStyles().optJSONObject(styleId);
        if (style == null) {
            Color base = slotColor != null ? slotColor : DEFAULT_GLOW;
            return new EngineLook(Misc.setAlpha(base, 255), Misc.setAlpha(base, 75), true);
        }

        Color glow = readColor(style, "engineCampaignColor", readColor(style, "engineColor", DEFAULT_GLOW));
        Color trail = readColor(style, "contrailCampaignColor", null);
        if (trail == null) trail = Misc.setAlpha(readColor(style, "contrailColor", glow), 75);
        boolean additive = !"SMOKE".equals(style.optString("type", "GLOW"));

        return new EngineLook(Misc.setAlpha(glow, 255), trail, additive);
    }

    private static String getStyleForManufacturer(String manufacturer) {
        if ("Low Tech".equals(manufacturer)) return "LOW_TECH";
        if ("High Tech".equals(manufacturer) || "Remnant".equals(manufacturer)) return "HIGH_TECH";
        return "MIDLINE";
    }

    private static JSONObject getEngineStyles() {
        if (engineStyles != null) return engineStyles;

        try {
            engineStyles = Global.getSettings().getMergedJSON(ENGINE_STYLES_PATH);
        } catch (IOException | JSONException e) {
            log.warn("Shuttle traffic: could not read " + ENGINE_STYLES_PATH, e);
            engineStyles = new JSONObject();
        }

        return engineStyles;
    }

    private static Color readColor(JSONObject style, String key, Color fallback) {
        JSONArray array = style.optJSONArray(key);
        if (array == null || array.length() < 3) return fallback;

        return new Color(clamp(array.optInt(0)), clamp(array.optInt(1)), clamp(array.optInt(2)), array.length() > 3 ? clamp(array.optInt(3)) : 255);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
