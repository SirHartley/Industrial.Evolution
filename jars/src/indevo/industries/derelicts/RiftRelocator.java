package indevo.industries.derelicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin.auroraColors;
import static com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin.baseColors;

public class RiftRelocator {

    public static final Logger log = Global.getLogger(RiftRelocator.class);

    public static final String RIFT_ID_PREFIX = "IndEvo_planet_replacer";
    public static final String RIFT_TAG = "IndEvo_artificialRift";
    public static final String RIFT_NAME = "Artificial Hyperspace Tear";
    public static final String RIFT_DESC_ID = "IndEvo_JumpRift";
    public static final String ORIGINAL_ORBIT_KEY = "$IndEvo_original_planet_orbit";

    private static final float RIFT_RADIUS = 50f;
    private static final float RIFT_FIELD_WIDTH = 250f;
    private static final float MIN_STAR_DISTANCE = 700f;
    private static final float ORBIT_MARGIN = 200f;
    private static final float MIN_ORBIT = 100f;
    private static final float MAX_PERIOD = 100000f;
    private static final float ORBIT_UNITS_PER_DAY = 20f;
    private static final int MAX_DEPTH = 8;

    private RiftRelocator() {
    }

    public static void relocate(MarketAPI market, StarSystemAPI target) {
        PlanetAPI planet = market.getPlanetEntity();
        StarSystemAPI origin = market.getStarSystem();
        if (planet == null || origin == null || target == null || origin == target)
            throw new IllegalStateException("can not move " + market.getId() + " from " + origin + " to " + target);

        List<SectorEntityToken> movers = new ArrayList<>();
        List<SectorEntityToken> stayers = new ArrayList<>();
        collect(planet, market, getChildren(origin), movers, stayers, 0);

        JumpPointAPI rift = createRift(origin, planet);
        redirectHyperspaceJumpPoints(planet, rift);
        for (SectorEntityToken e : stayers) {
            if (e.isPlayerFleet()) e.setOrbit(null);
            else refocus(e, rift);
        }

        float oldRadius = Math.max(planet.getCircularOrbitRadius(), MIN_ORBIT);
        float wanted = getOriginalOrbit(planet);
        float newRadius = findFreeOrbit(target, getExtent(planet, getChildren(origin), 0), wanted);
        float period = getPeriod(planet.getCircularOrbitPeriod(), oldRadius, newRadius);
        float angle = planet.getCircularOrbitAngle();

        EconomyAPI economy = Global.getSector().getEconomy();
        economy.removeMarket(market);

        origin.removeEntity(planet);
        target.addEntity(planet);
        for (SectorEntityToken e : movers) {
            origin.removeEntity(e);
            target.addEntity(e);
        }

        planet.setCircularOrbit(target.getCenter(), angle, newRadius, period);
        economy.addMarket(market, false);

        rift.addDestination(new JumpPointAPI.JumpDestination(planet, "the " + target.getName()));
        log.info("moved " + market.getId() + " from " + origin.getId() + " to " + target.getId() + " with " + movers.size() + " orbiters, orbit " + newRadius);
    }

    public static int countRifts(StarSystemAPI system) {
        int count = 0;
        for (SectorEntityToken e : system.getJumpPoints()) if (isRift(e)) count++;
        return count;
    }

    public static boolean isRift(SectorEntityToken entity) {
        return entity.hasTag(RIFT_TAG) || entity.getId().startsWith(RIFT_ID_PREFIX);
    }

    public static List<SectorEntityToken> getOrbiters(SectorEntityToken focus) {
        List<SectorEntityToken> result = new ArrayList<>();
        if (focus.getContainingLocation() == null) return result;

        for (SectorEntityToken e : focus.getContainingLocation().getAllEntities()) {
            if (e != focus && getFocus(e) == focus) result.add(e);
        }

        return result;
    }

    private static SectorEntityToken getFocus(SectorEntityToken entity) {
        if (entity instanceof RingBandAPI band && band.getFocus() != null) return band.getFocus();
        if (entity instanceof CampaignTerrainAPI terrain && terrain.getPlugin() instanceof BaseRingTerrain ring && ring.getRelatedEntity() != null)
            return ring.getRelatedEntity();
        return entity.getOrbitFocus();
    }

    private static Map<SectorEntityToken, List<SectorEntityToken>> getChildren(LocationAPI location) {
        Map<SectorEntityToken, List<SectorEntityToken>> children = new HashMap<>();
        for (SectorEntityToken e : location.getAllEntities()) {
            SectorEntityToken focus = getFocus(e);
            if (focus != null && focus != e) children.computeIfAbsent(focus, k -> new ArrayList<>()).add(e);
        }

        return children;
    }

    public static List<SectorEntityToken> getMovers(MarketAPI market) {
        List<SectorEntityToken> movers = new ArrayList<>();
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null || planet.getContainingLocation() == null) return movers;

        collect(planet, market, getChildren(planet.getContainingLocation()), movers, new ArrayList<>(), 0);
        return movers;
    }

    // planets, jump points, other markets and the player stay in the old system
    private static void collect(SectorEntityToken focus, MarketAPI market, Map<SectorEntityToken, List<SectorEntityToken>> children, List<SectorEntityToken> movers, List<SectorEntityToken> stayers, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalStateException("orbit chain deeper than " + MAX_DEPTH + " at " + focus.getId());

        for (SectorEntityToken e : children.getOrDefault(focus, Collections.emptyList())) {
            if (staysBehind(e, market)) {
                stayers.add(e);
                continue;
            }

            movers.add(e);
            collect(e, market, children, movers, stayers, depth + 1);
        }
    }

    private static boolean staysBehind(SectorEntityToken entity, MarketAPI market) {
        if (entity.isPlayerFleet() || entity instanceof JumpPointAPI || entity instanceof PlanetAPI) return true;

        MarketAPI other = entity.getMarket();
        return other != null && other != market && !other.isPlanetConditionMarketOnly();
    }

    private static void refocus(SectorEntityToken entity, SectorEntityToken rift) {
        float angle = Misc.getAngleInDegrees(rift.getLocation(), entity.getLocation());
        float period = entity.getCircularOrbitPeriod() > 0 ? entity.getCircularOrbitPeriod() : 31f;
        entity.setCircularOrbit(rift, angle, entity.getCircularOrbitRadius(), period);
    }

    private static JumpPointAPI createRift(StarSystemAPI system, PlanetAPI planet) {
        JumpPointAPI rift = Global.getFactory().createJumpPoint(RIFT_ID_PREFIX + "_" + Misc.genUID(), RIFT_NAME);
        rift.addTag(RIFT_TAG);
        rift.setStandardWormholeToHyperspaceVisual();
        rift.setRadius(RIFT_RADIUS);
        rift.setAutoCreateEntranceFromHyperspace(false);
        rift.setAutogenJumpPointNameInHyper(RIFT_NAME);
        rift.setCustomDescriptionId(RIFT_DESC_ID);

        if (planet.getOrbitFocus() != null) {
            rift.setCircularOrbit(planet.getOrbitFocus(), planet.getCircularOrbitAngle(), planet.getCircularOrbitRadius(), planet.getCircularOrbitPeriod());
        } else rift.setFixedLocation(planet.getLocation().x, planet.getLocation().y);

        // the orbit only places the rift on its next advance, refocus reads the location before that
        rift.getLocation().set(planet.getLocation());

        system.addEntity(rift);
        addMagneticField(system, rift);
        return rift;
    }

    private static void addMagneticField(StarSystemAPI system, SectorEntityToken rift) {
        Random random = new Random();
        float r = rift.getRadius();

        SectorEntityToken field = system.addTerrain(Terrain.MAGNETIC_FIELD, new MagneticFieldTerrainPlugin.MagneticFieldParams(
                r + RIFT_FIELD_WIDTH,
                r / 2f,
                rift,
                r,
                r + RIFT_FIELD_WIDTH + 50f,
                baseColors[random.nextInt(baseColors.length)],
                1f,
                auroraColors[random.nextInt(auroraColors.length)]));
        field.setCircularOrbit(rift, 0, 0, 100);
    }

    private static void redirectHyperspaceJumpPoints(PlanetAPI planet, JumpPointAPI rift) {
        for (SectorEntityToken e : Global.getSector().getHyperspace().getEntitiesWithTag(Tags.JUMP_POINT)) {
            if (!(e instanceof JumpPointAPI jumpPoint)) continue;

            boolean targetsPlanet = false;
            for (JumpPointAPI.JumpDestination dest : jumpPoint.getDestinations()) {
                if (dest.getDestination() == planet) targetsPlanet = true;
            }

            if (!targetsPlanet) continue;

            jumpPoint.removeDestination(planet);
            jumpPoint.addDestination(new JumpPointAPI.JumpDestination(rift, RIFT_NAME));
            jumpPoint.setName(RIFT_NAME);
            jumpPoint.setStandardWormholeToNothingVisual();
        }
    }

    // the first jump stores the planet's own orbit so later jumps do not drift outwards
    private static float getOriginalOrbit(PlanetAPI planet) {
        MemoryAPI mem = planet.getMemoryWithoutUpdate();
        if (!mem.contains(ORIGINAL_ORBIT_KEY)) mem.set(ORIGINAL_ORBIT_KEY, Math.max(planet.getCircularOrbitRadius(), MIN_ORBIT));
        return mem.getFloat(ORIGINAL_ORBIT_KEY);
    }

    private static float getPeriod(float oldPeriod, float oldRadius, float newRadius) {
        float period = oldPeriod * newRadius / oldRadius;
        if (period > 0f && period < MAX_PERIOD) return period;
        return newRadius / ORBIT_UNITS_PER_DAY;
    }

    private static float findFreeOrbit(StarSystemAPI system, float planetExtent, float wanted) {
        SectorEntityToken center = system.getCenter();
        Map<SectorEntityToken, List<SectorEntityToken>> children = getChildren(system);

        List<float[]> bands = new ArrayList<>();
        for (SectorEntityToken e : children.getOrDefault(center, Collections.emptyList())) {
            float[] band = getBand(e, children);
            if (band != null) bands.add(band);
        }

        bands.sort(Comparator.comparingDouble(b -> b[0]));

        float half = planetExtent + ORBIT_MARGIN;
        float starRadius = system.getStar() != null && !system.isNebula() ? system.getStar().getRadius() : 0f;
        float orbit = Math.max(wanted, starRadius * 3f + MIN_STAR_DISTANCE);

        for (float[] band : bands) {
            if (band[1] <= orbit - half) continue;
            if (band[0] >= orbit + half) break;
            orbit = band[1] + half;
        }

        return orbit;
    }

    private static float[] getBand(SectorEntityToken entity, Map<SectorEntityToken, List<SectorEntityToken>> children) {
        if (entity instanceof RingBandAPI band) {
            float half = band.getBandWidthInEngine() / 2f;
            return new float[]{band.getMiddleRadius() - half, band.getMiddleRadius() + half};
        }

        if (entity instanceof CampaignTerrainAPI terrain) {
            if (!(terrain.getPlugin() instanceof BaseRingTerrain ring) || ring.getRingParams() == null) return null;
            float half = ring.getRingParams().bandWidthInEngine / 2f;
            float middle = ring.getRingParams().middleRadius + entity.getCircularOrbitRadius();
            return new float[]{middle - half, middle + half};
        }

        float r = entity.getCircularOrbitRadius();
        float extent = getExtent(entity, children, 0);
        return new float[]{r - extent, r + extent};
    }

    private static float getExtent(SectorEntityToken entity, Map<SectorEntityToken, List<SectorEntityToken>> children, int depth) {
        float extent = entity.getRadius();
        if (depth > MAX_DEPTH) return extent;

        for (SectorEntityToken child : children.getOrDefault(entity, Collections.emptyList())) {
            if (child instanceof RingBandAPI band) {
                extent = Math.max(extent, band.getMiddleRadius() + band.getBandWidthInEngine() / 2f);
            } else if (child instanceof CampaignTerrainAPI terrain) {
                if (terrain.getPlugin() instanceof BaseRingTerrain ring && ring.getRingParams() != null)
                    extent = Math.max(extent, ring.getRingParams().middleRadius + ring.getRingParams().bandWidthInEngine / 2f);
            } else {
                extent = Math.max(extent, child.getCircularOrbitRadius() + getExtent(child, children, depth + 1));
            }
        }

        return extent;
    }
}
