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
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.fog.fleet.FogLurkerAssignmentAI;
import indevo.exploration.fog.terrain.FogTerrainPlugin;
import indevo.exploration.fog.terrain.FogTerrainPlugin.Kind;
import indevo.utils.helper.MiscIE;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FogWeatherManager implements EconomyTickListener {

    public static final String DATA_KEY = "IndEvo_fogTerrains";
    public static final String SEEDED_KEY = "IndEvo_fogSeeded";
    public static final String SPELL_START_KEY = "$IndEvo_fogSpellStart";
    public static final String SPELL_WAIT_KEY = "$IndEvo_fogSpellWait";

    // rolled for every fog capable system on each economy tick, so the amount of fog scales with the sector
    public static final float SPELL_CHANCE = 0.004f;
    public static final float BANK_CHANCE = 0.01f;
    public static final float SPELL_COOLDOWN_DAYS = 240f;
    public static final int MAX_BANKS_PER_SYSTEM = 2;
    // safety net only, fog outside the player's system costs nothing
    public static final int MAX_BANKS = 300;
    // no fog where nebula tiles cover more than this share of the disc a spell would fill
    public static final float MAX_NEBULA_COVER = 0.1f;

    public static final float ROLL_IN_DAYS = 2f;
    public static final float FADE_DAYS = 8f;
    public static final float SPELL_MIN_HOLD_DAYS = 25f;
    public static final float SPELL_MAX_HOLD_DAYS = 35f;
    public static final float BANK_MIN_HOLD_DAYS = 90f;
    public static final float BANK_MAX_HOLD_DAYS = 180f;
    public static final float BANK_MIN_SIZE = 8000f;
    public static final float BANK_MAX_SIZE = 14000f;
    public static final float GAS_GIANT_CHANCE = 0.6f;
    public static final int BANK_SPOT_TRIES = 10;
    public static final float SYSTEM_MIN_RADIUS = 8000f;
    public static final float SYSTEM_EDGE_EXTRA = 2500f;
    // a spell is an oversized bank on the star: its lobes reach about the system radius, the body covers less
    public static final float SPELL_MIN_RADIUS = 9000f;
    public static final float SPELL_MAX_RADIUS = 20000f;
    public static final float BANK_ORBIT_DAYS_PER_UNIT = 0.1f;

    public static final float SPELL_AMBUSH_CHANCE = 0.35f;
    public static final float BANK_AMBUSH_CHANCE = 0.10f;
    public static final float PIRATE_CHANCE = 0.7f;
    public static final float LURKER_MIN_POINTS = 30f;
    public static final float LURKER_MAX_POINTS = 70f;
    public static final float LURKER_POST_MIN_MASK = 0.5f;

    public static final float BANK_CACHE_CHANCE = 0.15f;
    public static final String[] CACHE_TYPES = {Entities.SUPPLY_CACHE_SMALL, Entities.EQUIPMENT_CACHE_SMALL, Entities.WEAPONS_CACHE_SMALL};
    public static final float CACHE_ORBIT_DAYS = 300f;

    private final Random random = new Random();
    private final Map<StarSystemAPI, Boolean> capable = new IdentityHashMap<>();

    private record BankSpot(SectorEntityToken focus, float angle, float orbitRadius, float days, float band) {
    }

    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        if (listeners.hasListenerOfClass(FogWeatherManager.class)) return;
        FogWeatherManager manager = new FogWeatherManager();
        listeners.addListener(manager, true);
        manager.seedIfNeeded();
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (FogMode.current() == FogMode.OFF) return;
        seedIfNeeded();

        int banks = 0;
        for (SectorEntityToken fog : getActiveFog()) if (getPlugin(fog).getKind() == Kind.BANK) banks++;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCapable(system)) continue;
            if (isSpellReady(system) && hasRoom(system, Kind.SPELL) && random.nextFloat() < SPELL_CHANCE) {
                spawn(system, Kind.SPELL, random, false, false, false);
            } else if (banks < MAX_BANKS && hasRoom(system, Kind.BANK) && random.nextFloat() < BANK_CHANCE
                    && spawn(system, Kind.BANK, random, false, false, false) != null) {
                banks++;
            }
        }
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    // new games and saves from before the seed get the fog an old campaign would have, each at a random age
    private void seedIfNeeded() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        if (data.containsKey(SEEDED_KEY) || FogMode.current() == FogMode.OFF) return;
        data.put(SEEDED_KEY, true);

        float tickDays = 365f / 12f / Global.getSettings().getFloat("economyIterPerMonth");
        float spellLife = ROLL_IN_DAYS + (SPELL_MIN_HOLD_DAYS + SPELL_MAX_HOLD_DAYS) / 2f + FADE_DAYS;
        float bankLife = ROLL_IN_DAYS + (BANK_MIN_HOLD_DAYS + BANK_MAX_HOLD_DAYS) / 2f + FADE_DAYS;
        float spellSeed = Math.min(1f, SPELL_CHANCE * spellLife / tickDays);
        float bankSeed = Math.min(1f, BANK_CHANCE * bankLife / tickDays / MAX_BANKS_PER_SYSTEM);

        int banks = 0;
        for (SectorEntityToken fog : getActiveFog()) if (getPlugin(fog).getKind() == Kind.BANK) banks++;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCapable(system)) continue;
            if (isSpellReady(system) && hasRoom(system, Kind.SPELL) && random.nextFloat() < spellSeed) {
                spawn(system, Kind.SPELL, random, false, false, true);
                continue;
            }
            for (int i = 0; i < MAX_BANKS_PER_SYSTEM; i++) {
                if (banks < MAX_BANKS && hasRoom(system, Kind.BANK) && random.nextFloat() < bankSeed
                        && spawn(system, Kind.BANK, random, false, false, true) != null) {
                    banks++;
                }
            }
        }
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
        return !getFogs(loc).isEmpty();
    }

    public static List<FogTerrainPlugin> getFogs(LocationAPI loc) {
        List<FogTerrainPlugin> fogs = new ArrayList<>();
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            if (FogTerrainPlugin.TERRAIN_ID.equals(terrain.getType())) fogs.add(getPlugin(terrain));
        }
        return fogs;
    }

    // a spell fills the system alone, banks share it up to MAX_BANKS_PER_SYSTEM
    public static boolean hasRoom(LocationAPI loc, Kind kind) {
        List<FogTerrainPlugin> fogs = getFogs(loc);
        if (kind == Kind.SPELL) return fogs.isEmpty();
        if (fogs.size() >= MAX_BANKS_PER_SYSTEM) return false;
        for (FogTerrainPlugin fog : fogs) if (fog.getKind() == Kind.SPELL) return false;
        return true;
    }

    public static boolean isSpellReady(StarSystemAPI system) {
        MemoryAPI mem = system.getMemoryWithoutUpdate();
        if (!mem.contains(SPELL_START_KEY)) return true;
        return Global.getSector().getClock().getElapsedDaysSince(mem.getLong(SPELL_START_KEY)) >= mem.getFloat(SPELL_WAIT_KEY);
    }

    public static boolean isFogCapable(StarSystemAPI system) {
        if (system.hasTag(Tags.THEME_HIDDEN)
                || system.hasTag(Tags.THEME_SPECIAL)
                || system.hasTag(Tags.SYSTEM_ABYSSAL)
                || system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        return getNebulaCover(system) <= MAX_NEBULA_COVER;
    }

    // tags and nebula do not change in play, the tile count is too slow to run for every system on every tick
    private boolean isCapable(StarSystemAPI system) {
        return capable.computeIfAbsent(system, FogWeatherManager::isFogCapable);
    }

    public static float getNebulaCover(StarSystemAPI system) {
        Vector2f centre = system.getCenter().getLocation();
        float radius = getSystemRadius(system);
        float covered = 0f;
        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (!Terrain.NEBULA.equals(terrain.getType()) || !(terrain.getPlugin() instanceof BaseTiledTerrain nebula)) continue;
            int[][] tiles = nebula.getTiles();
            float size = nebula.getTileSize();
            float x0 = terrain.getLocation().x - centre.x - tiles.length * size / 2f + size / 2f;
            float y0 = terrain.getLocation().y - centre.y - tiles[0].length * size / 2f + size / 2f;
            int inside = 0;
            for (int i = 0; i < tiles.length; i++) {
                for (int j = 0; j < tiles[i].length; j++) {
                    if (tiles[i][j] >= 0 && Math.hypot(x0 + i * size, y0 + j * size) <= radius) inside++;
                }
            }
            covered += inside * size * size;
        }
        return covered / (float) (Math.PI * radius * radius);
    }

    public static SectorEntityToken spawn(StarSystemAPI system, Kind kind, Random random, boolean forceAmbush, boolean forceCache) {
        return spawn(system, kind, random, forceAmbush, forceCache, false);
    }

    // null when a bank finds no spot clear of the banks already there; a seeded fog starts in its hold at a random age
    private static SectorEntityToken spawn(StarSystemAPI system, Kind kind, Random random, boolean forceAmbush, boolean forceCache, boolean seeded) {
        if (!hasRoom(system, kind)) throw new IllegalStateException("IndEvo fog " + kind + " has no room in " + system.getName());

        float rollIn = seeded ? 0f : ROLL_IN_DAYS;
        float age = seeded ? random.nextFloat() : 0f;
        SectorEntityToken fog;
        float radius;
        int lurkers;
        if (kind == Kind.SPELL) {
            radius = Math.max(SPELL_MIN_RADIUS, Math.min(SPELL_MAX_RADIUS, getSystemRadius(system)));
            float hold = MiscIE.getRandomInRange(SPELL_MIN_HOLD_DAYS, SPELL_MAX_HOLD_DAYS, random) * (1f - age);
            fog = system.addTerrain(FogTerrainPlugin.TERRAIN_ID, new FogTerrainPlugin.FogParams(kind, radius, random.nextInt(), rollIn, hold, FADE_DAYS));
            SectorEntityToken center = system.getStar() != null ? system.getStar() : system.getCenter();
            fog.setLocation(center.getLocation().x, center.getLocation().y);
            fog.setCircularOrbit(center, 0f, 0f, 100f);
            lurkers = forceAmbush || random.nextFloat() < SPELL_AMBUSH_CHANCE ? 1 + random.nextInt(2) : 0;

            MemoryAPI mem = system.getMemoryWithoutUpdate();
            mem.set(SPELL_START_KEY, Global.getSector().getClock().getTimestamp());
            mem.set(SPELL_WAIT_KEY, rollIn + hold + FADE_DAYS + SPELL_COOLDOWN_DAYS);
        } else {
            radius = MiscIE.getRandomInRange(BANK_MIN_SIZE, BANK_MAX_SIZE, random) / 2f;
            BankSpot spot = pickBankSpot(system, radius, random);
            if (spot == null) return null;
            float hold = MiscIE.getRandomInRange(BANK_MIN_HOLD_DAYS, BANK_MAX_HOLD_DAYS, random) * (1f - age);
            fog = system.addTerrain(FogTerrainPlugin.TERRAIN_ID, new FogTerrainPlugin.FogParams(kind, radius, random.nextInt(), rollIn, hold, FADE_DAYS));
            double rad = Math.toRadians(spot.angle());
            Vector2f focus = spot.focus().getLocation();
            fog.setLocation(focus.x + (float) Math.cos(rad) * spot.orbitRadius(), focus.y + (float) Math.sin(rad) * spot.orbitRadius());
            fog.setCircularOrbit(spot.focus(), spot.angle(), spot.orbitRadius(), spot.days());
            lurkers = forceAmbush || random.nextFloat() < BANK_AMBUSH_CHANCE ? 1 : 0;
            if (forceCache || random.nextFloat() < BANK_CACHE_CHANCE) spawnCache(system, fog, radius, random);
        }

        getActiveFog().add(fog);
        for (int i = 0; i < lurkers; i++) spawnLurker(system, fog, radius, random);
        return fog;
    }

    private static float getSystemRadius(StarSystemAPI system) {
        float max = 0f;
        for (PlanetAPI planet : system.getPlanets()) max = Math.max(max, Misc.getDistance(planet, system.getCenter()) + planet.getRadius());
        for (SectorEntityToken jp : system.getJumpPoints()) max = Math.max(max, Misc.getDistance(jp, system.getCenter()));
        return Math.max(SYSTEM_MIN_RADIUS, max + SYSTEM_EDGE_EXTRA);
    }

    // a gas giant, else a star orbit between planet orbits; banks on bands this far apart never touch while they orbit
    private static BankSpot pickBankSpot(StarSystemAPI system, float radius, Random random) {
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

        orbits.sort(null);
        float starRadius = system.getStar() != null ? system.getStar().getRadius() : 0f;
        WeightedRandomPicker<Float> gaps = new WeightedRandomPicker<>(random);
        for (int i = 0; i + 1 < orbits.size(); i++) gaps.add((orbits.get(i) + orbits.get(i + 1)) / 2f);
        if (!orbits.isEmpty()) gaps.add(orbits.get(orbits.size() - 1) + radius);

        List<SectorEntityToken> others = new ArrayList<>();
        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (FogTerrainPlugin.TERRAIN_ID.equals(terrain.getType())) others.add(terrain);
        }

        for (int i = 0; i < BANK_SPOT_TRIES; i++) {
            BankSpot spot;
            if (!giants.isEmpty() && random.nextFloat() < GAS_GIANT_CHANCE) {
                PlanetAPI giant = giants.get(random.nextInt(giants.size()));
                spot = new BankSpot(giant, 0f, 0f, 100f, Misc.getDistance(giant, center));
            } else {
                float orbitRadius = gaps.isEmpty() ? MiscIE.getRandomInRange(3000f, 8000f, random) : gaps.pick();
                orbitRadius = Math.max(orbitRadius, starRadius + radius + 500f);
                spot = new BankSpot(center, random.nextFloat() * 360f, orbitRadius, orbitRadius * BANK_ORBIT_DAYS_PER_UNIT, orbitRadius);
            }
            if (isClear(spot.band(), radius, others, center)) return spot;
        }
        return null;
    }

    private static boolean isClear(float band, float radius, List<SectorEntityToken> others, SectorEntityToken center) {
        for (SectorEntityToken other : others) {
            if (Math.abs(band - Misc.getDistance(other, center)) < radius + getPlugin(other).getRadius()) return false;
        }
        return true;
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

        Vector2f post = pickThickSpot(getPlugin(fog), radius * 0.2f, radius * 0.7f, random);
        fleet.setTransponderOn(false);
        fleet.getMemoryWithoutUpdate().set(FogLurkerAssignmentAI.MEM_LURKER, true);
        system.spawnFleet(fog, post.x, post.y, fleet);
        FogLurkerAssignmentAI ai = new FogLurkerAssignmentAI(fleet, fog, post.x, post.y);
        fleet.addScript(ai);
        ai.holdPost();
    }

    private static void spawnCache(StarSystemAPI system, SectorEntityToken fog, float radius, Random random) {
        FogTerrainPlugin plugin = getPlugin(fog);
        // well inside the ragged edge, which starts at 0.6 radius
        Vector2f spot = pickThickSpot(plugin, radius * 0.1f, radius * 0.35f, random);
        SectorEntityToken cache = BaseThemeGenerator.addSalvageEntity(random, system, CACHE_TYPES[random.nextInt(CACHE_TYPES.length)], Factions.NEUTRAL);
        cache.setLocation(fog.getLocation().x + spot.x, fog.getLocation().y + spot.y);
        cache.setCircularOrbit(fog, Misc.getAngleInDegrees(spot), spot.length(), CACHE_ORBIT_DAYS);
        plugin.setCache(cache);
    }

    // offset from the fog centre with the thickest rest mask of up to 30 tries
    private static Vector2f pickThickSpot(FogTerrainPlugin plugin, float minDist, float maxDist, Random random) {
        Vector2f spot = new Vector2f();
        float best = -1f;
        for (int i = 0; i < 30 && best < LURKER_POST_MIN_MASK; i++) {
            float dist = MiscIE.getRandomInRange(minDist, maxDist, random);
            double angle = random.nextDouble() * Math.PI * 2;
            float x = (float) Math.cos(angle) * dist, y = (float) Math.sin(angle) * dist;
            float mask = plugin.getFullMaskAt(x, y);
            if (mask > best) {
                best = mask;
                spot.set(x, y);
            }
        }
        return spot;
    }

    private static FogTerrainPlugin getPlugin(SectorEntityToken fog) {
        if (!(fog instanceof CampaignTerrainAPI terrain) || !(terrain.getPlugin() instanceof FogTerrainPlugin plugin)) {
            throw new IllegalStateException("IndEvo fog list holds " + fog + ", not an IndEvo_fog terrain");
        }
        return plugin;
    }
}
