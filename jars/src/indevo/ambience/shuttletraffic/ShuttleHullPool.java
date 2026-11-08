package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.EngineSlotAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.helper.ReflectionUtils;
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
    private static final String POD_CATEGORY = "misc";
    private static final String POD_KEY = "cargoPods";
    private static final String POD_STYLE = "LOW_TECH";
    private static final float POD_CELL = 32f;
    private static final float POD_SIZE = 13f;
    private static final Color DEFAULT_GLOW = new Color(255, 145, 75, 255);
    private static final Logger log = Global.getLogger(ShuttleHullPool.class);

    private static final Map<String, ShuttleHull> hulls = new HashMap<>();
    private static final Map<String, List<ShuttleHull>> factionPools = new HashMap<>();
    private static final Map<ShuttleHull, Float> baseWeights = new HashMap<>();
    private static List<ShuttleHull> basePool;
    private static Set<String> knownHullIds;
    private static JSONObject engineStyles;
    private static boolean reflectionWarned = false;

    private ShuttleHullPool() {
    }

    public static void reset() {
        hulls.clear();
        factionPools.clear();
        baseWeights.clear();
        basePool = null;
        knownHullIds = null;
        engineStyles = null;
    }

    public static ShuttleHull rollCargo(FactionAPI faction, Random random, float baseShare, boolean smallOnly) {
        ShuttleHull hull = null;
        if (faction != null && random.nextFloat() >= baseShare) hull = pick(getFactionPool(faction, true), random, h -> h.civilian && (!smallOnly || h.small));
        if (hull == null) hull = pick(getBasePool(), random, h -> !smallOnly || h.small);
        return hull;
    }

    public static ShuttleHull rollSmall(FactionAPI faction, Random random) {
        ShuttleHull hull = faction != null ? pick(getFactionPool(faction, true), random, h -> h.small) : null;
        if (hull == null) hull = pick(getBasePool(), random, h -> h.small);
        return hull;
    }

    public static ShuttleHull rollAny(FactionAPI faction, Random random, float baseShare, boolean crewed) {
        ShuttleHull hull = null;
        if (faction != null && random.nextFloat() >= baseShare) hull = pick(getFactionPool(faction, crewed), random, h -> true);
        if (hull == null) hull = pick(getBasePool(), random, h -> true);
        return hull;
    }

    public static ShuttleHull rollBase(Random random, Set<String> hullIds) {
        return pick(getBasePool(), random, h -> hullIds.contains(h.hullId));
    }

    public static ShuttleHull getHull(String hullId, boolean fighter, int wingSize) {
        if (!hullExists(hullId)) return null;

        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
        return createHull(spec, fighter, wingSize, false, fighter);
    }

    public static ShuttleHull createPod(Random random) {
        SpriteAPI sprite = Global.getSettings().getSprite(POD_CATEGORY, POD_KEY);
        if (sprite == null || sprite.getWidth() < POD_CELL) return null;

        int cols = Math.max(1, (int) (sprite.getWidth() / POD_CELL));
        int rows = Math.max(1, (int) (sprite.getHeight() / POD_CELL));
        float cellWidth = sprite.getTextureWidth() / cols;
        float cellHeight = sprite.getTextureHeight() / rows;
        sprite.setTexX(random.nextInt(cols) * cellWidth);
        sprite.setTexY(random.nextInt(rows) * cellHeight);
        sprite.setTexWidth(cellWidth);
        sprite.setTexHeight(cellHeight);

        JSONObject style = getEngineStyles().optJSONObject(POD_STYLE);
        Color glow = style != null ? readColor(style, "engineCampaignColor", DEFAULT_GLOW) : DEFAULT_GLOW;
        Color trail = style != null ? readColor(style, "contrailCampaignColor", Misc.setAlpha(glow, 75)) : Misc.setAlpha(glow, 75);
        return new ShuttleHull(POD_KEY, sprite, true, false, true, false, false, 1, glow, trail, false, POD_SIZE, POD_SIZE);
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

            ShuttleHull hull = createHull(spec, spec.getHullSize() == HullSize.FIGHTER, 1, true, row.optBoolean("small", false));
            if (hull == null) continue;

            basePool.add(hull);
            baseWeights.put(hull, (float) row.optDouble("weight", 1d));
        }

        return basePool;
    }

    public static List<ShuttleHull> getFactionPool(FactionAPI faction, boolean crewed) {
        String key = faction.getId() + (crewed ? "" : "|any");
        List<ShuttleHull> pool = factionPools.get(key);
        if (pool != null) return pool;

        // base rows first, shared hulls keep their csv flags
        getBasePool();
        pool = new ArrayList<>();

        for (String wingId : faction.getKnownFighters()) {
            FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(wingId);
            if (wing == null || (crewed && wing.hasTag(Tags.AUTOMATED_FIGHTER))) continue;

            ShipVariantAPI variant = wing.getVariant();
            if (variant == null || variant.getHullSpec() == null || (crewed && !isCrewed(variant.getHullSpec()))) continue;

            ShuttleHull hull = createHull(variant.getHullSpec(), true, Math.max(1, Math.min(3, wing.getNumFighters())), false, true);
            if (hull != null && !pool.contains(hull)) pool.add(hull);
        }

        for (String hullId : faction.getKnownShips()) {
            if (!hullExists(hullId)) continue;

            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec.getHullSize() != HullSize.FRIGATE || (crewed && !isCrewed(spec)) || spec.hasTag(Tags.RESTRICTED)) continue;

            EnumSet<ShipTypeHints> hints = spec.getHints();
            if (hints.contains(ShipTypeHints.STATION) || hints.contains(ShipTypeHints.MODULE)) continue;

            ShuttleHull hull = createHull(spec, false, 1, isCivilian(hints), SMALL_FRIGATE.equals(spec.getBaseHullId()));
            if (hull != null && !pool.contains(hull)) pool.add(hull);
        }

        factionPools.put(key, pool);
        return pool;
    }

    private static float getWeight(ShuttleHull hull) {
        Float base = baseWeights.get(hull);
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

    private static ShuttleHull createHull(ShipHullSpecAPI spec, boolean fighter, int wingSize, boolean civilian, boolean small) {
        String key = spec.getHullId();
        if (hulls.containsKey(key)) return hulls.get(key);

        SpriteAPI sprite = loadSprite(spec.getSpriteName());
        if (sprite == null) {
            log.warn("Shuttle traffic: no sprite for hull " + key);
            hulls.put(key, null);
            return null;
        }

        EnumSet<ShipTypeHints> hints = spec.getHints();
        EngineLook look = getEngineLook(spec);
        ShuttleHull hull = new ShuttleHull(key, sprite, fighter, civilian, small, hints.contains(ShipTypeHints.LINER),
                hints.contains(ShipTypeHints.FREIGHTER), wingSize, look.glow, look.trail, look.additive);

        hulls.put(key, hull);
        return hull;
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

        // ShipHullSpecAPI has no engine slots, the game's hull spec class does; names are not obfuscated
        try {
            Object slots = ReflectionUtils.invoke("getEngineSlots", spec, new Object[0], false);
            if (slots instanceof List<?> list && !list.isEmpty()) {
                Object slot = list.get(0);
                Object id = ReflectionUtils.invoke("getStyleId", slot, new Object[0], false);
                if (id instanceof String s) styleId = s;
                if (slot instanceof EngineSlotAPI api) slotColor = api.getColor();
            }
        } catch (Throwable t) {
            if (!reflectionWarned) log.warn("Shuttle traffic: engine slot lookup failed, using manufacturer styles", t);
            reflectionWarned = true;
            styleId = null;
        }

        if (styleId == null) styleId = getStyleForManufacturer(spec.getManufacturer());

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
        if ("High Tech".equals(manufacturer)) return "HIGH_TECH";
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

    private record EngineLook(Color glow, Color trail, boolean additive) {
    }
}
