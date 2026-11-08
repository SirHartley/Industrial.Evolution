package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ShuttleNetwork {

    public static class Body {
        public final SectorEntityToken entity;
        public final float radius;

        public Body(SectorEntityToken entity, float radius) {
            this.entity = entity;
            this.radius = radius;
        }
    }

    public static final float SMUGGLER_CHANCE = 0.01f;
    // market lanes keep about one craft per this many su of lane for two size 5 markets
    private static final float LANE_SPACING = 500f;
    public static final float DIPLOMAT_CHANCE = 0.05f;
    public static final float HUNT_CHANCE = 0.03f;
    public static final float MAINTENANCE_CHANCE = 0.02f;

    public final Map<SectorEntityToken, ShuttleAnchor> anchors = new LinkedHashMap<>();
    public final Map<String, ShuttleRoute> routes = new LinkedHashMap<>();
    public final List<ShuttleAnchor> marketAnchors = new ArrayList<>();
    public final List<ShuttleAnchor> decivAnchors = new ArrayList<>();
    public final List<Body> bodies = new ArrayList<>();

    public void clear() {
        anchors.clear();
        routes.clear();
        marketAnchors.clear();
        decivAnchors.clear();
        bodies.clear();
    }

    public void rebuild(LocationAPI location, Random random) {
        for (ShuttleAnchor anchor : anchors.values()) anchor.seen = false;
        for (ShuttleRoute route : routes.values()) route.seen = false;
        marketAnchors.clear();
        decivAnchors.clear();

        Map<ShuttleAnchor, Float> density = new HashMap<>();
        addMarkets(location, random, density);
        addDecivs(location);
        addMarketLanes(random, density);
        addPirateHops(random);
        addHunts(random);
        addMaintenance(location, random);
        addRemnants(location, random);

        routes.values().removeIf(route -> !route.seen);
        Iterator<ShuttleAnchor> iterator = anchors.values().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().seen) iterator.remove();
        }

        rebuildBodies(location);
    }

    public ShuttleAnchor findRefuge(ShuttleAnchor exclude, FactionAPI faction, Vector2f from, LocationAPI location) {
        boolean pirate = isPirate(faction);
        ShuttleAnchor best = null;
        float bestScore = Float.MAX_VALUE;

        for (ShuttleAnchor anchor : anchors.values()) {
            if (anchor == exclude || anchor.threatened || !anchor.isUsable(location)) continue;
            if (anchor.role == ShuttleAnchor.Role.REMNANT) continue;
            if (pirate != (anchor.role == ShuttleAnchor.Role.DECIV || isPirate(anchor.faction))) continue;
            if (faction != null && anchor.faction != null && anchor.faction.isHostileTo(faction)) continue;

            float score = Misc.getDistance(from, anchor.getLocation());
            if (faction != null && anchor.faction != faction) score *= 1.5f;
            if (score < bestScore) {
                bestScore = score;
                best = anchor;
            }
        }

        return best;
    }

    public float getBend(Vector2f p0, Vector2f p3, float bend, SectorEntityToken fromEntity, SectorEntityToken toEntity) {
        float cx = p3.x - p0.x;
        float cy = p3.y - p0.y;
        float chord2 = cx * cx + cy * cy;
        if (chord2 < 1f) return bend;

        float chord = (float) Math.sqrt(chord2);
        float nx = -cy / chord;
        float ny = cx / chord;

        for (Body body : bodies) {
            if (body.entity == fromEntity || body.entity == toEntity) continue;

            Vector2f loc = body.entity.getLocation();
            float ox = loc.x - p0.x;
            float oy = loc.y - p0.y;
            float t = (ox * cx + oy * cy) / chord2;
            if (t < 0.1f || t > 0.9f) continue;

            // the bezier midpoint sits at 0.75 of the control point shift
            float across = ox * nx + oy * ny;
            float mid = bend * 0.75f;
            if (Math.abs(mid - across) >= body.radius) continue;

            float side = across != 0f ? Math.signum(across) : (bend != 0f ? -Math.signum(bend) : 1f);
            bend = (across - side * body.radius * 1.1f) / 0.75f;
        }

        return Math.max(-chord * 0.6f, Math.min(chord * 0.6f, bend));
    }

    public PlanetAPI pickHuntTarget(SectorEntityToken from, Random random) {
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>(random);
        for (Body body : bodies) {
            if (!(body.entity instanceof PlanetAPI planet) || planet.isStar() || planet == from) continue;
            picker.add(planet, hasRuins(planet.getMarket()) ? 3f : 1f);
        }

        return picker.isEmpty() ? null : picker.pick();
    }

    private void addMarkets(LocationAPI location, Random random, Map<ShuttleAnchor, Float> density) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(location)) {
            if (!hasTraffic(market, location)) continue;

            SectorEntityToken primary = market.getPrimaryEntity();
            boolean stationMarket = primary.hasTag(Tags.STATION);
            ShuttleAnchor anchor = getAnchor(primary, stationMarket ? ShuttleAnchor.Type.STATION : ShuttleAnchor.Type.PLANET,
                    ShuttleAnchor.Role.MARKET, market, market.getFaction());
            anchor.closed = isPortDisrupted(market);
            marketAnchors.add(anchor);

            float mult = getDensity(market);
            density.put(anchor, mult);

            getRoute(ShuttleRoute.Kind.JOYRIDE, anchor, null, random).max = isPirate(anchor.faction) ? 2 : 1;

            int wings = market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND) ? 2
                    : market.hasIndustry(Industries.PATROLHQ) ? 1 : 0;
            if (wings > 0) getRoute(ShuttleRoute.Kind.PICKET, anchor, null, random).max = wings;

            if (market.hasIndustry(Ids.ACADEMY)) getRoute(ShuttleRoute.Kind.TRAINING, anchor, null, random);

            if (market.hasIndustry(Ids.EMBASSY)) getRoute(ShuttleRoute.Kind.DIPLOMAT, anchor, null, random).chancePerDay = DIPLOMAT_CHANCE;

            if (market.hasIndustry(Industries.MINING) || market.hasIndustry(Ids.SWITCHABLE_MINING)) {
                ShuttleTarget target = findMiningTarget(location, primary);
                if (target != null) {
                    ShuttleRoute route = getRoute(ShuttleRoute.Kind.MINING, anchor, null, random);
                    route.target = target;
                    route.max = Math.max(1, Math.round((1f + market.getSize() / 4f) * mult));
                }
            }

            if (market.hasIndustry(Ids.SCRAPYARD)) {
                ShuttleTarget target = findSalvageTarget(location, primary);
                if (target != null) getRoute(ShuttleRoute.Kind.SALVAGE, anchor, null, random).target = target;
            }

            if (stationMarket) continue;

            for (SectorEntityToken entity : market.getConnectedEntities()) {
                if (entity == primary || !entity.hasTag(Tags.STATION) || entity.getContainingLocation() != location) continue;

                ShuttleAnchor station = getAnchor(entity, ShuttleAnchor.Type.STATION, ShuttleAnchor.Role.MARKET, market, market.getFaction());
                station.closed = anchor.closed;

                ShuttleRoute route = getRoute(ShuttleRoute.Kind.SUPPLY, anchor, station, random);
                route.max = Math.max(1, Math.round((1f + market.getSize() / 3f) * mult));
            }
        }
    }

    private void addDecivs(LocationAPI location) {
        for (PlanetAPI planet : location.getPlanets()) {
            MarketAPI market = planet.getMarket();
            if (planet.isStar() || market == null || market.isInEconomy() || !market.hasCondition(Conditions.DECIVILIZED_SUBPOP)) continue;

            decivAnchors.add(getAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.DECIV, market, market.getFaction()));
        }
    }

    private void addMarketLanes(Random random, Map<ShuttleAnchor, Float> density) {
        for (int i = 0; i < marketAnchors.size(); i++) {
            for (int j = i + 1; j < marketAnchors.size(); j++) {
                ShuttleAnchor a = marketAnchors.get(i);
                ShuttleAnchor b = marketAnchors.get(j);
                if (isPirate(a.faction) || isPirate(b.faction)) continue;

                if (a.faction.isHostileTo(b.faction)) {
                    getRoute(ShuttleRoute.Kind.SMUGGLER, a, b, random).chancePerDay = SMUGGLER_CHANCE;
                    continue;
                }

                float mult = (density.get(a) + density.get(b)) / 2f * (a.market.getSize() + b.market.getSize()) / 10f;
                float chord = Misc.getDistance(a.getLocation(), b.getLocation());
                ShuttleRoute route = getRoute(ShuttleRoute.Kind.MARKET, a, b, random);
                route.max = Math.max(2, Math.round(chord / LANE_SPACING * mult));
            }
        }
    }

    private void addPirateHops(Random random) {
        for (ShuttleAnchor base : marketAnchors) {
            if (!isPirate(base.faction)) continue;
            for (ShuttleAnchor deciv : decivAnchors) getRoute(ShuttleRoute.Kind.PIRATE_CARGO, base, deciv, random);
        }
    }

    private void addHunts(Random random) {
        for (ShuttleAnchor deciv : decivAnchors) getRoute(ShuttleRoute.Kind.HUNT, deciv, null, random).chancePerDay = HUNT_CHANCE;
    }

    private void addMaintenance(LocationAPI location, Random random) {
        for (SectorEntityToken objective : location.getEntitiesWithTag(Tags.OBJECTIVE)) {
            if (!objective.hasTag(Tags.COMM_RELAY) && !objective.hasTag(Tags.NAV_BUOY) && !objective.hasTag(Tags.SENSOR_ARRAY)) continue;
            if (objective.getFaction() == null) continue;

            String owner = objective.getFaction().getId();
            ShuttleAnchor nearest = null;
            float bestDist = Float.MAX_VALUE;
            for (ShuttleAnchor anchor : marketAnchors) {
                if (!owner.equals(anchor.faction.getId())) continue;

                float dist = Misc.getDistance(anchor.getLocation(), objective.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = anchor;
                }
            }

            if (nearest == null) continue;

            ShuttleRoute route = getRoute(ShuttleRoute.Kind.MAINTENANCE, nearest, null, objective.getId(), random);
            route.target = new ShuttleTarget(objective, 0f, 0f, 0f, 0f);
            route.chancePerDay = MAINTENANCE_CHANCE;
        }
    }

    private void addRemnants(LocationAPI location, Random random) {
        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (!fleet.isStationMode() || !Factions.REMNANTS.equals(fleet.getFaction().getId())) continue;
            if (!FleetTypes.BATTLESTATION.equals(fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE))) continue;

            ShuttleAnchor anchor = getAnchor(fleet, ShuttleAnchor.Type.STATION, ShuttleAnchor.Role.REMNANT, null, fleet.getFaction());
            getRoute(ShuttleRoute.Kind.JOYRIDE, anchor, null, random).max = 2;
        }
    }

    private ShuttleTarget findMiningTarget(LocationAPI location, SectorEntityToken from) {
        ShuttleTarget best = null;
        float bestDist = Float.MAX_VALUE;

        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            String type = terrain.getType();
            if (!Terrain.ASTEROID_BELT.equals(type) && !Terrain.RING.equals(type) && !Terrain.ASTEROID_FIELD.equals(type)) continue;
            if (!(terrain.getPlugin() instanceof BaseRingTerrain ring)) continue;

            Vector2f center = terrain.getLocation();
            float angle = Misc.getAngleInDegrees(center, from.getLocation());
            float toCenter = Misc.getDistance(center, from.getLocation());
            float bandRadius;
            float dist;

            if (ring instanceof AsteroidFieldTerrainPlugin field && field.params != null) {
                bandRadius = field.params.maxRadius * 0.5f;
                dist = Math.max(0f, toCenter - bandRadius);
            } else if (ring.params != null) {
                bandRadius = ring.params.middleRadius;
                dist = Math.abs(toCenter - bandRadius);
            } else continue;

            if (dist >= bestDist) continue;

            Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);
            bestDist = dist;
            best = new ShuttleTarget(terrain, dir.x * bandRadius, dir.y * bandRadius, bandRadius, 80f);
        }

        return best;
    }

    private ShuttleTarget findSalvageTarget(LocationAPI location, SectorEntityToken from) {
        ShuttleTarget best = null;
        float bestDist = Float.MAX_VALUE;

        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            if (!Terrain.DEBRIS_FIELD.equals(terrain.getType()) || !(terrain.getPlugin() instanceof BaseRingTerrain ring) || ring.params == null) continue;

            float dist = Misc.getDistance(terrain.getLocation(), from.getLocation());
            if (dist >= bestDist) continue;

            bestDist = dist;
            best = new ShuttleTarget(terrain, 0f, 0f, 0f, Math.max(60f, Math.min(120f, ring.params.bandWidthInEngine * 0.5f)));
        }

        for (SectorEntityToken entity : location.getCustomEntities()) {
            if (!Entities.WRECK.equals(entity.getCustomEntityType())) continue;

            float dist = Misc.getDistance(entity.getLocation(), from.getLocation());
            if (dist >= bestDist) continue;

            bestDist = dist;
            best = new ShuttleTarget(entity, 0f, 0f, 0f, 70f);
        }

        return best;
    }

    private void rebuildBodies(LocationAPI location) {
        bodies.clear();

        Map<SectorEntityToken, Float> coronas = new HashMap<>();
        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            String type = terrain.getType();
            if (!Terrain.CORONA.equals(type) && !Terrain.EVENT_HORIZON.equals(type)) continue;
            if (!(terrain.getPlugin() instanceof BaseRingTerrain ring) || ring.params == null || ring.params.relatedEntity == null) continue;

            float outer = ring.params.middleRadius + ring.params.bandWidthInEngine / 2f;
            coronas.merge(ring.params.relatedEntity, outer, Math::max);
        }

        for (PlanetAPI planet : location.getPlanets()) {
            float radius;
            if (planet.isStar()) radius = Math.max(planet.getRadius() * 1.5f, coronas.getOrDefault(planet, 0f));
            else radius = planet.getRadius() * 1.3f + 30f;

            bodies.add(new Body(planet, radius));
        }
    }

    private ShuttleAnchor getAnchor(SectorEntityToken entity, ShuttleAnchor.Type type, ShuttleAnchor.Role role, MarketAPI market, FactionAPI faction) {
        ShuttleAnchor anchor = anchors.get(entity);
        if (anchor == null || anchor.type != type || anchor.role != role) {
            anchor = new ShuttleAnchor(entity, type, role, market, faction);
            anchors.put(entity, anchor);
        }

        anchor.market = market;
        anchor.faction = faction;
        anchor.seen = true;
        return anchor;
    }

    private ShuttleRoute getRoute(ShuttleRoute.Kind kind, ShuttleAnchor a, ShuttleAnchor b, Random random) {
        return getRoute(kind, a, b, b != null ? b.entity.getId() : "", random);
    }

    private ShuttleRoute getRoute(ShuttleRoute.Kind kind, ShuttleAnchor a, ShuttleAnchor b, String targetId, Random random) {
        String key = kind.name() + "|" + a.entity.getId() + "|" + targetId;
        ShuttleRoute route = routes.get(key);
        if (route == null || route.a != a || route.b != b) {
            route = new ShuttleRoute(key, kind, a, b, (random.nextFloat() - 0.5f) * 0.5f);
            route.timer = random.nextFloat() * 5f;
            routes.put(key, route);
        }

        route.seen = true;
        return route;
    }

    private static boolean hasTraffic(MarketAPI market, LocationAPI location) {
        SectorEntityToken primary = market.getPrimaryEntity();
        return primary != null && primary.getContainingLocation() == location && market.isInEconomy() && !market.isHidden()
                && market.hasSpaceport() && !primary.isDiscoverable();
    }

    private static boolean isPortDisrupted(MarketAPI market) {
        Industry port = market.getIndustry(Industries.SPACEPORT);
        if (port == null) port = market.getIndustry(Industries.MEGAPORT);
        return port != null && port.isDisrupted();
    }

    private static float getDensity(MarketAPI market) {
        float mult = market.hasIndustry(Industries.MEGAPORT) ? 1.5f : 1f;
        if (market.hasIndustry(Industries.COMMERCE)) mult *= 1.25f;
        mult *= Math.max(0.5f, Math.min(1f, 0.5f + market.getStabilityValue() / 20f));

        boolean raided = market.getMemoryWithoutUpdate().getBoolean(MemFlags.RECENTLY_RAIDED)
                || market.getMemoryWithoutUpdate().getBoolean(MemFlags.RECENTLY_BOMBARDED);
        if (raided) mult *= 0.5f;

        return mult;
    }

    private static boolean hasRuins(MarketAPI market) {
        return market != null && (market.hasCondition(Conditions.RUINS_SCATTERED) || market.hasCondition(Conditions.RUINS_WIDESPREAD)
                || market.hasCondition(Conditions.RUINS_EXTENSIVE) || market.hasCondition(Conditions.RUINS_VAST));
    }

    public static boolean isPirate(FactionAPI faction) {
        return faction != null && Factions.PIRATES.equals(faction.getId());
    }
}
