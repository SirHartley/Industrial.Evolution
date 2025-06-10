package indevo.exploration.meteor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.crucible.ability.YeetScript;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Circle;
import indevo.utils.helper.CircularArc;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

//roll when player enters system
//never when inhabited
//roll again once a week
//chance 0.05 on system visit, 0.1 every 7 days means 2 months = guaranteed, then at least 5 months cd on system, 1 month overall

//pick centerpoint
//angle face away from sun
//create angles beween 10 and 30 drg
//add pre-swarm asteroid spawner

public class MeteorSwarmManager implements EconomyTickListener {

    public static final String MEM_RANDOM = "$IndEvo_MeteorRandom";
    public static final String MEM_TIMEOUT = "$IndEvo_MeteorTimeout";

    //parameter todo these should be in the settings file
    public static final float BASE_CHANCE_PER_ECONOMY_TICK = 0.02f; //18% per month 92% per year

    public static final float MIN_INTENSITY = 1;
    public static final float MAX_INTENSITY = 4;
    public static final float BASE_SHOWER_WIDTH = 3000f;
    public static final float INTENSITY_WIDTH_MODIFIER = 1000f;
    public static final float MAX_DENSITY = 2.5f;
    public static final float MAX_RUNTIME_MULT = 2.5f;
    public static final float BASE_RUNTIME = 50f;
    public static final float LOCATION_TIMEOUT_AFTER_SPAWN_DAYS = 31 * 6;
    public static final float GENERAL_TIMEOUT_AFTER_SPAWN_DAYS = 31 * 1;

    //position
    private static final float GRID_LIMIT = 30000f;
    public static final float MAX_DISTANCE_FROM_SUN = 10000F;
    public static final float MIN_DISTANCE_FROM_SUN = 3000f;
    public static final float MAX_ANGLE = 80f;
    public static final float MIN_ANGLE = 50f;

    public static class MeteorShowerData {
        public float chance;
        public float treasureModifier;

        public MeteorShowerData(float chance, float treasureModifier) {
            this.treasureModifier = treasureModifier;
            this.chance = chance;
        }
    }

    public enum MeteroidShowerType {
        ASTEROID,
        MAGMAROID,
        ICEROID,
        IRRADIOID,
        METHEROID
    }

    public static Map<MeteroidShowerType, MeteorShowerData> METEOR_TYPE_DATA = new HashMap<>() {{
        put(MeteroidShowerType.ASTEROID, new MeteorShowerData(150f, 1f));
        put(MeteroidShowerType.MAGMAROID, new MeteorShowerData(10f, 1f));
        put(MeteroidShowerType.ICEROID, new MeteorShowerData(10f, 1f));
        put(MeteroidShowerType.IRRADIOID, new MeteorShowerData(10f, 1f));
        put(MeteroidShowerType.METHEROID, new MeteorShowerData(1f, 1f));
    }};

    public static void register(){
        Global.getSector().getListenerManager().addListener(new MeteorSwarmManager(), true);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        LocationAPI loc = Global.getSector().getCurrentLocation();
        MemoryAPI locMem = loc.getMemoryWithoutUpdate();
        MemoryAPI globalMem = Global.getSector().getMemoryWithoutUpdate();

        if (globalMem.getBoolean(MEM_TIMEOUT)
                || locMem.getBoolean(MEM_TIMEOUT)
                || loc.isHyperspace()
                || !Misc.getMarketsInLocation(loc).isEmpty()
                || loc.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                || loc.hasTag(Tags.THEME_HIDDEN)
                || loc.hasTag(Tags.THEME_SPECIAL)
                || loc.hasTag(Tags.SYSTEM_ABYSSAL)) return;

        Random random = getRandom();
        boolean spawn = random.nextFloat() < BASE_CHANCE_PER_ECONOMY_TICK;
        boolean devmode = Global.getSettings().isDevMode();

        if (spawn || devmode) {

            WeightedRandomPicker<MeteroidShowerType> picker = new WeightedRandomPicker<>(random);
            for (Map.Entry<MeteroidShowerType, MeteorShowerData> e : METEOR_TYPE_DATA.entrySet()) picker.add(e.getKey(), e.getValue().chance);
            MeteroidShowerType type = picker.pick();

            //parameters
            float intensity = random.nextFloat() * (MAX_INTENSITY - MIN_INTENSITY) + MIN_INTENSITY;
            float width = BASE_SHOWER_WIDTH + INTENSITY_WIDTH_MODIFIER * intensity;
            int lootAmt = Math.max(1, Math.round(intensity * METEOR_TYPE_DATA.get(type).treasureModifier));
            float density = Math.min(MAX_DENSITY, intensity);
            float runtime = BASE_RUNTIME * Math.min(MAX_RUNTIME_MULT, intensity);

            //location
            float radius = random.nextFloat() * (MAX_DISTANCE_FROM_SUN - MIN_DISTANCE_FROM_SUN) + MIN_DISTANCE_FROM_SUN + width / 2;
            float centerAngle = 360f * random.nextFloat();
            Vector2f arcCenterLoc = MathUtils.getPointOnCircumference(null, radius, centerAngle);

            float angleToCenter = Misc.getAngleInDegrees(arcCenterLoc, new Vector2f(0,0));
            float adjustment = random.nextFloat() * (MAX_ANGLE - MIN_ANGLE) + MIN_ANGLE;
            float startAngle = MathUtils.clampAngle(angleToCenter + adjustment);
            float endAngle = MathUtils.clampAngle(angleToCenter - adjustment);

            Vector2f startLoc = intersectWithGridBoundary(arcCenterLoc, startAngle);
            Vector2f endloc = intersectWithGridBoundary(arcCenterLoc, endAngle);

            Circle circle = TrigHelper.findThreePointCircle(startLoc,arcCenterLoc, endloc);
            CircularArc arc = new CircularArc(circle, circle.getAngleForPoint(startLoc), circle.getAngleForPoint(endloc));

            ModPlugin.log("Spawning asteroid swarm: \nintensity " + intensity + " \nlootAmt " + lootAmt + " \ndensity " + density + " \nruntime " + runtime + " \nwidth " + width + " \nstartAngle " + startAngle + " \nendAngle " + endAngle);
            if (devmode) Global.getSector().getPlayerFleet().addScript(new YeetScript(Global.getSector().getPlayerFleet(), arcCenterLoc));

            switch (type){
                default -> loc.addScript(new MeteorSwarmSpawner((StarSystemAPI) loc, intensity, lootAmt, density, runtime, arc, width, random.nextLong()));
                //todo cases for everything else
            }

            globalMem.set(MEM_TIMEOUT, true, GENERAL_TIMEOUT_AFTER_SPAWN_DAYS);
            locMem.set(MEM_TIMEOUT, true, LOCATION_TIMEOUT_AFTER_SPAWN_DAYS);
        }
    }

    @Override
    public void reportEconomyMonthEnd() {

    }

    public Random getRandom() {
        Random random;
        Map<String, Object> mem = Global.getSector().getPersistentData();

        if (mem.containsKey(MEM_RANDOM)) random = (Random) mem.get(MEM_RANDOM);
        else {
            random = new Random();
            mem.put(MEM_RANDOM, random);
        }

        return random;
    }

    public static Vector2f intersectWithGridBoundary(Vector2f loc, float angleToCenter) {
        angleToCenter = (float) Math.toRadians(angleToCenter);
        float dx = (float) Math.cos(angleToCenter);
        float dy = (float) Math.sin(angleToCenter);

        float tMax = Float.POSITIVE_INFINITY;

        if (dx != 0) {
            float tx1 = (-GRID_LIMIT - loc.x) / dx;
            float tx2 = (GRID_LIMIT - loc.x) / dx;
            tMax = Math.min(tMax, Math.max(tx1, tx2));
        }

        if (dy != 0) {
            float ty1 = (-GRID_LIMIT - loc.y) / dy;
            float ty2 = (GRID_LIMIT - loc.y) / dy;
            tMax = Math.min(tMax, Math.max(ty1, ty2));
        }

        return new Vector2f(loc.x + dx * tMax, loc.y + dy * tMax);
    }
}
