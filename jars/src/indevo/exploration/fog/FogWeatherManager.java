package indevo.exploration.fog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.config.Setting;
import indevo.exploration.fog.fleet.FogLurkerAssignmentAI;
import indevo.exploration.fog.terrain.FogTerrainPlugin;
import indevo.exploration.fog.terrain.FogTerrainPlugin.Kind;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FogWeatherManager implements EconomyTickListener {

    public static final String DATA_KEY = "IndEvo_fogTerrains";

    public static final float SPELL_CHANCE = 0.15f;
    public static final float BANK_CHANCE = 0.10f;
    public static final int MAX_SPELLS = 3;
    public static final int MAX_BANKS = 6;
    public static final float MAX_DISTANCE_LY = 20f;

    public static final float ROLL_IN_DAYS = 2f;
    public static final float FADE_DAYS = 8f;
    public static final float SPELL_MIN_HOLD_DAYS = 25f;
    public static final float SPELL_MAX_HOLD_DAYS = 35f;
    public static final float BANK_MIN_HOLD_DAYS = 90f;
    public static final float BANK_MAX_HOLD_DAYS = 180f;
    public static final float BANK_MIN_SIZE = 5000f;
    public static final float BANK_MAX_SIZE = 9000f;
    public static final float GAS_GIANT_CHANCE = 0.6f;
    public static final float SPELL_MIN_RADIUS = 8000f;
    public static final float SPELL_EDGE_EXTRA = 2500f;
    public static final float BANK_ORBIT_DAYS_PER_UNIT = 0.1f;

    public static final float SPELL_AMBUSH_CHANCE = 0.35f;
    public static final float BANK_AMBUSH_CHANCE = 0.20f;
    public static final float PIRATE_CHANCE = 0.7f;
    public static final float LURKER_MIN_POINTS = 30f;
    public static final float LURKER_MAX_POINTS = 70f;
    public static final float LURKER_POST_MIN_MASK = 0.5f;

    private final Random random = new Random();

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (!listeners.hasListenerOfClass(FogWeatherManager.class)) listeners.addListener(new FogWeatherManager(), true);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        List<SectorEntityToken> active = getActiveFog();
        if (!Setting.FOG_ENABLED.enabled()) return;

        int spells = 0, banks = 0;
        for (SectorEntityToken fog : active) {
            if (getPlugin(fog).getKind() == Kind.SPELL) spells++;
            else banks++;
        }

        if (spells < MAX_SPELLS && random.nextFloat() < SPELL_CHANCE) spawnNearPlayer(Kind.SPELL);
        if (banks < MAX_BANKS && random.nextFloat() < BANK_CHANCE) spawnNearPlayer(Kind.BANK);
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    @SuppressWarnings("unchecked")
    public static List<SectorEntityToken> getActiveFog() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        List<SectorEntityToken> list = (List<SectorEntityToken>) data.get(DATA_KEY);
        if (list == null) {
            list = new ArrayList<>();
            data.put(DATA_KEY, list);
        }
        list.removeIf(fog -> !fog.isAlive());
        return list;
    }

    public static boolean hasFog(LocationAPI loc) {
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            if (FogTerrainPlugin.TERRAIN_ID.equals(terrain.getType())) return true;
        }
        return false;
    }

    public static boolean isValidSystem(StarSystemAPI system, Kind kind) {
        if (kind == Kind.SPELL && system.hasTag(Tags.THEME_CORE)) return false;
        if (system.hasTag(Tags.THEME_HIDDEN)
                || system.hasTag(Tags.THEME_SPECIAL)
                || system.hasTag(Tags.SYSTEM_ABYSSAL)
                || system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                || system.getType() == StarSystemGenerator.StarSystemType.NEBULA) return false;

        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            String type = terrain.getType();
            if (Terrain.NEBULA.equals(type) || FogTerrainPlugin.TERRAIN_ID.equals(type)) return false;
        }
        return true;
    }

    public static SectorEntityToken spawn(StarSystemAPI system, Kind kind, Random random, boolean forceAmbush) {
        if (hasFog(system)) throw new IllegalStateException("IndEvo fog spawned twice in " + system.getName());

        SectorEntityToken fog;
        float radius;
        int lurkers;
        if (kind == Kind.SPELL) {
            radius = getSystemRadius(system);
            float hold = MiscIE.getRandomInRange(SPELL_MIN_HOLD_DAYS, SPELL_MAX_HOLD_DAYS, random);
            fog = system.addTerrain(FogTerrainPlugin.TERRAIN_ID, new FogTerrainPlugin.FogParams(kind, radius, random.nextInt(), ROLL_IN_DAYS, hold, FADE_DAYS));
            SectorEntityToken center = system.getCenter();
            fog.setLocation(center.getLocation().x, center.getLocation().y);
            fog.setCircularOrbit(center, 0f, 0f, 100f);
            lurkers = forceAmbush || random.nextFloat() < SPELL_AMBUSH_CHANCE ? 1 + random.nextInt(2) : 0;
        } else {
            radius = MiscIE.getRandomInRange(BANK_MIN_SIZE, BANK_MAX_SIZE, random) / 2f;
            float hold = MiscIE.getRandomInRange(BANK_MIN_HOLD_DAYS, BANK_MAX_HOLD_DAYS, random);
            fog = system.addTerrain(FogTerrainPlugin.TERRAIN_ID, new FogTerrainPlugin.FogParams(kind, radius, random.nextInt(), ROLL_IN_DAYS, hold, FADE_DAYS));
            placeBank(system, fog, radius, random);
            lurkers = forceAmbush || random.nextFloat() < BANK_AMBUSH_CHANCE ? 1 : 0;
        }

        getActiveFog().add(fog);
        for (int i = 0; i < lurkers; i++) spawnLurker(system, fog, radius, random);
        return fog;
    }

    private void spawnNearPlayer(Kind kind) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>(random);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (Misc.getDistanceLY(player.getLocationInHyperspace(), system.getLocation()) > MAX_DISTANCE_LY) continue;
            if (isValidSystem(system, kind)) picker.add(system);
        }
        if (picker.isEmpty()) return;
        spawn(picker.pick(), kind, random, false);
    }

    private static float getSystemRadius(StarSystemAPI system) {
        float max = 0f;
        for (PlanetAPI planet : system.getPlanets()) max = Math.max(max, Misc.getDistance(planet, system.getCenter()) + planet.getRadius());
        for (SectorEntityToken jp : system.getJumpPoints()) max = Math.max(max, Misc.getDistance(jp, system.getCenter()));
        return Math.max(SPELL_MIN_RADIUS, max + SPELL_EDGE_EXTRA);
    }

    private static void placeBank(StarSystemAPI system, SectorEntityToken fog, float radius, Random random) {
        SectorEntityToken center = system.getCenter();
        List<PlanetAPI> giants = new ArrayList<>();
        List<Float> orbits = new ArrayList<>();
        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.isStar()) continue;
            if (planet.isGasGiant()) giants.add(planet);
            if (planet.getOrbitFocus() == center || (planet.getOrbitFocus() instanceof PlanetAPI focus && focus.isStar())) {
                orbits.add(Misc.getDistance(planet, center));
            }
        }

        if (!giants.isEmpty() && random.nextFloat() < GAS_GIANT_CHANCE) {
            PlanetAPI giant = giants.get(random.nextInt(giants.size()));
            fog.setLocation(giant.getLocation().x, giant.getLocation().y);
            fog.setCircularOrbit(giant, 0f, 0f, 100f);
            return;
        }

        orbits.sort(null);
        float starRadius = system.getStar() != null ? system.getStar().getRadius() : 0f;
        WeightedRandomPicker<Float> gaps = new WeightedRandomPicker<>(random);
        for (int i = 0; i + 1 < orbits.size(); i++) gaps.add((orbits.get(i) + orbits.get(i + 1)) / 2f);
        if (!orbits.isEmpty()) gaps.add(orbits.get(orbits.size() - 1) + radius);
        float orbitRadius = gaps.isEmpty() ? MiscIE.getRandomInRange(3000f, 8000f, random) : gaps.pick();
        orbitRadius = Math.max(orbitRadius, starRadius + radius + 500f);

        float angle = random.nextFloat() * 360f;
        double rad = Math.toRadians(angle);
        fog.setLocation(center.getLocation().x + (float) Math.cos(rad) * orbitRadius, center.getLocation().y + (float) Math.sin(rad) * orbitRadius);
        fog.setCircularOrbit(center, angle, orbitRadius, orbitRadius * BANK_ORBIT_DAYS_PER_UNIT);
    }

    private static void spawnLurker(StarSystemAPI system, SectorEntityToken fog, float radius, Random random) {
        String faction = random.nextFloat() < PIRATE_CHANCE ? Factions.PIRATES : Factions.LUDDIC_PATH;
        float points = MiscIE.getRandomInRange(LURKER_MIN_POINTS, LURKER_MAX_POINTS, random);
        FleetParamsV3 params = new FleetParamsV3(system.getLocation(), faction, null, FleetTypes.PATROL_MEDIUM, points, 0f, 0f, 0f, 0f, 0f, 0f);
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            Global.getLogger(FogWeatherManager.class).warn("IndEvo fog: no " + faction + " lurker fleet could be built for " + system.getName());
            return;
        }

        FogTerrainPlugin plugin = getPlugin(fog);
        float postX = 0f, postY = 0f, best = -1f;
        for (int i = 0; i < 30 && best < LURKER_POST_MIN_MASK; i++) {
            float dist = radius * MiscIE.getRandomInRange(0.2f, 0.7f, random);
            double angle = random.nextDouble() * Math.PI * 2;
            float x = (float) Math.cos(angle) * dist, y = (float) Math.sin(angle) * dist;
            float mask = plugin.getRestMaskAt(x, y);
            if (mask > best) {
                best = mask;
                postX = x;
                postY = y;
            }
        }

        fleet.setTransponderOn(false);
        fleet.getMemoryWithoutUpdate().set(FogLurkerAssignmentAI.MEM_LURKER, true);
        system.spawnFleet(fog, postX, postY, fleet);
        FogLurkerAssignmentAI ai = new FogLurkerAssignmentAI(fleet, fog, postX, postY);
        fleet.addScript(ai);
        ai.holdPost();
    }

    private static FogTerrainPlugin getPlugin(SectorEntityToken fog) {
        if (!(fog instanceof CampaignTerrainAPI terrain) || !(terrain.getPlugin() instanceof FogTerrainPlugin plugin)) {
            throw new IllegalStateException("IndEvo fog list holds " + fog + ", not an IndEvo_fog terrain");
        }
        return plugin;
    }
}
