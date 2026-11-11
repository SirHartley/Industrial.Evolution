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
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.ids.Ids;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    public static final float FREE_PORT_SMUGGLER_MULT = 3f;
    public static final float PIRATE_ACTIVITY_LANES = 2f / 3f;
    public static final float CELL_CHANCE = 0.02f;
    public static final float HIDDEN_BASE_CHANCE = 0.01f;
    public static final float PRIVATEER_CHANCE = 0.03f;
    // market lanes keep about one craft per this many su of lane for two size 5 markets
    private static final float LANE_SPACING = 500f;
    public static final float DIPLOMAT_CHANCE = 0.05f;
    public static final float HUNT_CHANCE = 0.03f;
    public static final float MAINTENANCE_CHANCE = 0.02f;
    private static final float SIGHT_RANGE = 3000f;
    private static final float SIGHT_GAP = 400f;
    public static final float SWARM_RANGE = 600f;

    public final Map<SectorEntityToken, ShuttleAnchor> anchors = new LinkedHashMap<>();
    public final Map<String, ShuttleRoute> routes = new LinkedHashMap<>();
    public final List<ShuttleAnchor> marketAnchors = new ArrayList<>();
    public final List<ShuttleAnchor> decivAnchors = new ArrayList<>();
    public final List<Body> bodies = new ArrayList<>();
    // IE meteors in the system, refreshed with the fleet scan; meteors fly, so their positions are read live
    private final List<SectorEntityToken> meteors = new ArrayList<>();

    public void clear() {
        anchors.clear();
        routes.clear();
        marketAnchors.clear();
        decivAnchors.clear();
        bodies.clear();
        meteors.clear();
    }

    // meteor swarms have no callback for their movement, so this runs with the fleet scan
    public void updateSwarms(LocationAPI location) {
        boolean had = !meteors.isEmpty();
        meteors.clear();
        for (SectorEntityToken meteor : location.getEntitiesWithTag(MeteorSwarmManager.METEOR_TAG)) {
            if (meteor.isAlive()) meteors.add(meteor);
        }

        if (meteors.isEmpty() && !had) return;
        for (ShuttleAnchor anchor : anchors.values()) anchor.swarmed = isSwarmed(anchor.getLocation(), anchor.getLocation(), anchor.getRadius());
    }

    // any meteor within SWARM_RANGE of the segment, plus the margin
    public boolean isSwarmed(Vector2f p0, Vector2f p1, float margin) {
        float range = SWARM_RANGE + margin;
        for (SectorEntityToken meteor : meteors) {
            if (getSegmentDistance(meteor.getLocation(), p0, p1) <= range) return true;
        }
        return false;
    }

    public static float getSegmentDistance(Vector2f point, Vector2f p0, Vector2f p1) {
        float dx = p1.x - p0.x;
        float dy = p1.y - p0.y;
        float len2 = dx * dx + dy * dy;
        float t = len2 > 0f ? Math.max(0f, Math.min(1f, ((point.x - p0.x) * dx + (point.y - p0.y) * dy) / len2)) : 0f;
        float cx = point.x - p0.x - dx * t;
        float cy = point.y - p0.y - dy * t;
        return (float) Math.sqrt(cx * cx + cy * cy);
    }

    public void rebuild(LocationAPI location, Random random) {
        for (ShuttleAnchor anchor : anchors.values()) anchor.seen = false;
        for (ShuttleRoute route : routes.values()) route.seen = false;
        marketAnchors.clear();
        decivAnchors.clear();

        addMarkets(location, random);
        addDecivs(location);
        addMarketLanes(random);
        addPilgrims(location, random);
        addPirateHops(random);
        addHunts(random);
        addMaintenance(location, random);
        addRemnants(location, random);
        addHiddenBases(location, random);

        routes.values().removeIf(route -> !route.seen);
        Iterator<ShuttleAnchor> iterator = anchors.values().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().seen) iterator.remove();
        }

        rebuildBodies(location);
    }

    public ShuttleAnchor findRefuge(ShuttleAnchor exclude, FactionAPI faction, Vector2f from, LocationAPI location) {
        boolean pirate = ShuttleFactionRepo.isPirate(faction);
        ShuttleAnchor best = null;
        float bestScore = Float.MAX_VALUE;

        for (ShuttleAnchor anchor : anchors.values()) {
            if (anchor == exclude || anchor.threatened || !anchor.isUsable(location)) continue;
            if (anchor.role == ShuttleAnchor.Role.REMNANT || anchor.role == ShuttleAnchor.Role.TARGET) continue;
            if (pirate != (anchor.role == ShuttleAnchor.Role.DECIV || ShuttleFactionRepo.isPirate(anchor.faction))) continue;
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

    // nearest star between the two points that the lane could reach while its anchors orbit during the flight
    public Body getStarAcross(Vector2f p0, Vector2f p3) {
        float cx = p3.x - p0.x;
        float cy = p3.y - p0.y;
        float chord2 = cx * cx + cy * cy;
        if (chord2 < 1f) return null;

        float chord = (float) Math.sqrt(chord2);
        Body best = null;
        float bestAcross = Float.MAX_VALUE;
        for (Body body : bodies) {
            if (!(body.entity instanceof PlanetAPI planet) || !planet.isStar()) continue;

            Vector2f loc = body.entity.getLocation();
            float t = ((loc.x - p0.x) * cx + (loc.y - p0.y) * cy) / chord2;
            if (t < 0.05f || t > 0.95f) continue;

            float across = Math.abs((loc.x - p0.x) * -cy + (loc.y - p0.y) * cx) / chord;
            if (across > body.radius + chord * 0.5f || across >= bestAcross) continue;

            best = body;
            bestAcross = across;
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
            picker.add(planet, getHuntWeight(planet.getMarket()));
        }

        return picker.isEmpty() ? null : picker.pick();
    }

    private void addMarkets(LocationAPI location, Random random) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(location)) {
            if (!hasTraffic(market, location)) continue;

            SectorEntityToken primary = market.getPrimaryEntity();
            boolean stationMarket = primary.hasTag(Tags.STATION);
            ShuttleAnchor anchor = getAnchor(primary, stationMarket ? ShuttleAnchor.Type.STATION : ShuttleAnchor.Type.PLANET,
                    ShuttleAnchor.Role.MARKET, market, market.getFaction());
            anchor.closed = isPortDisrupted(market);
            marketAnchors.add(anchor);

            float mult = getDensity(market);
            anchor.density = mult;
            ShuttleProfile profile = ShuttleFactionRepo.get(market.getFaction());

            SectorEntityToken sight = findSight(anchor, location, random);
            int cruises = Math.round(profile.cruises());
            if (sight != null && cruises > 0) {
                ShuttleRoute cruise = getRoute(ShuttleRoute.Kind.CRUISE, anchor, null, random);
                cruise.target = new ShuttleTarget(sight, 0f, 0f, 0f, 0f);
                cruise.demand = cruises;
            }

            int wings = market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND) ? 2
                    : market.hasIndustry(Industries.PATROLHQ) ? 1 : 0;
            wings = wings > 0 ? Math.max(1, Math.round(wings * profile.pickets())) : 0;
            if (market.hasCondition(Conditions.PIRATE_ACTIVITY)) wings++;
            if (wings > 0) getRoute(ShuttleRoute.Kind.PICKET, anchor, null, random).demand = wings;

            if (market.hasIndustry(Ids.ACADEMY) && sight != null) {
                getRoute(ShuttleRoute.Kind.TRAINING, anchor, null, random).target = new ShuttleTarget(sight, 0f, 0f, 0f, 0f);
            }

            float dark = market.hasCondition(Conditions.PATHER_CELLS) ? CELL_CHANCE : 0f;
            if (market.hasIndustry(Ids.PIRATEHAVEN)) dark = Math.max(dark, PRIVATEER_CHANCE);
            if (dark > 0f) getRoute(ShuttleRoute.Kind.DARK, anchor, null, random).chancePerDay = dark;

            if (market.hasCondition(Ids.COND_PARADE)) getRoute(ShuttleRoute.Kind.PARADE, anchor, null, random);

            if (market.hasIndustry(Ids.EMBASSY)) getRoute(ShuttleRoute.Kind.DIPLOMAT, anchor, null, random).chancePerDay = DIPLOMAT_CHANCE;

            if (market.hasIndustry(Industries.MINING) || market.hasIndustry(Ids.SWITCHABLE_MINING)) {
                ShuttleTarget target = findMiningTarget(location, primary);
                if (target != null) {
                    ShuttleRoute route = getRoute(ShuttleRoute.Kind.MINING, anchor, null, random);
                    route.target = target;
                    route.demand = Math.max(1, Math.round((1f + market.getSize() / 4f) * mult));
                }
            }

            if (market.hasIndustry(Ids.SCRAPYARD)) {
                ShuttleTarget target = findSalvageTarget(location, primary);
                if (target != null) getRoute(ShuttleRoute.Kind.SALVAGE, anchor, null, random).target = target;
            }

            if (stationMarket) continue;

            boolean towing = market.hasIndustry(Ids.REPAIRDOCKS);
            for (SectorEntityToken entity : market.getConnectedEntities()) {
                if (entity == primary || !entity.hasTag(Tags.STATION) || entity.getContainingLocation() != location) continue;

                ShuttleAnchor station = getAnchor(entity, ShuttleAnchor.Type.STATION, ShuttleAnchor.Role.MARKET, market, market.getFaction());
                station.closed = anchor.closed;

                ShuttleRoute route = getRoute(ShuttleRoute.Kind.SUPPLY, anchor, station, random);
                route.demand = Math.max(1, Math.round((1f + market.getSize() / 3f) * mult));
                if (towing) getRoute(ShuttleRoute.Kind.TOW, anchor, station, random);
                towing = false;
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

    private void addMarketLanes(Random random) {
        for (int i = 0; i < marketAnchors.size(); i++) {
            for (int j = i + 1; j < marketAnchors.size(); j++) {
                ShuttleAnchor a = marketAnchors.get(i);
                ShuttleAnchor b = marketAnchors.get(j);
                if (ShuttleFactionRepo.isPirate(a.faction) || ShuttleFactionRepo.isPirate(b.faction)) continue;

                if (a.faction.isHostileTo(b.faction)) {
                    boolean freePort = a.market.isFreePort() || b.market.isFreePort();
                    getRoute(ShuttleRoute.Kind.SMUGGLER, a, b, random).chancePerDay = SMUGGLER_CHANCE * (freePort ? FREE_PORT_SMUGGLER_MULT : 1f);
                    continue;
                }

                float lanes = (ShuttleFactionRepo.get(a.faction).lanes() + ShuttleFactionRepo.get(b.faction).lanes()) / 2f;
                float mult = (a.density + b.density) / 2f * (a.market.getSize() + b.market.getSize()) / 10f * lanes;
                if (a.market.hasCondition(Conditions.PIRATE_ACTIVITY)) mult *= PIRATE_ACTIVITY_LANES;
                if (b.market.hasCondition(Conditions.PIRATE_ACTIVITY)) mult *= PIRATE_ACTIVITY_LANES;
                float chord = Misc.getDistance(a.getLocation(), b.getLocation());
                ShuttleRoute route = getRoute(ShuttleRoute.Kind.MARKET, a, b, random);
                route.demand = Math.max(2, Math.round(chord / LANE_SPACING * mult));

                if (a.market.hasIndustry(Ids.PORT)) getRoute(ShuttleRoute.Kind.COURIER, a, b, random);
                else if (b.market.hasIndustry(Ids.PORT)) getRoute(ShuttleRoute.Kind.COURIER, b, a, random);
            }
        }
    }

    // Cathedral markets and vanilla shrines; a shrine without a market of its own gets a target anchor
    private void addPilgrims(LocationAPI location, Random random) {
        List<ShuttleAnchor> shrines = new ArrayList<>();
        for (ShuttleAnchor anchor : marketAnchors) {
            if (anchor.market.hasIndustry(Ids.CHURCH) || anchor.market.hasTag(Tags.LUDDIC_SHRINE) || anchor.entity.hasTag(Tags.LUDDIC_SHRINE)) shrines.add(anchor);
        }

        for (SectorEntityToken entity : location.getEntitiesWithTag(Tags.LUDDIC_SHRINE)) {
            ShuttleAnchor known = anchors.get(entity);
            if (known != null && known.seen) continue;
            if (!entity.isAlive() || entity.isDiscoverable() || entity.getFaction() == null) continue;

            ShuttleAnchor.Type type = entity instanceof PlanetAPI ? ShuttleAnchor.Type.PLANET : ShuttleAnchor.Type.STATION;
            shrines.add(getAnchor(entity, type, ShuttleAnchor.Role.TARGET, entity.getMarket(), entity.getFaction()));
        }

        for (ShuttleAnchor shrine : shrines) {
            for (ShuttleAnchor source : marketAnchors) {
                if (source == shrine || ShuttleFactionRepo.isPirate(source.faction) || source.faction.isHostileTo(shrine.faction)) continue;
                getRoute(ShuttleRoute.Kind.PILGRIM, source, shrine, random);
            }
        }
    }

    private void addPirateHops(Random random) {
        for (ShuttleAnchor base : marketAnchors) {
            if (!ShuttleFactionRepo.isPirate(base.faction)) continue;
            for (ShuttleAnchor deciv : decivAnchors) getRoute(ShuttleRoute.Kind.PIRATE_CARGO, base, deciv, random);
        }
    }

    private void addHunts(Random random) {
        for (ShuttleAnchor deciv : decivAnchors) getRoute(ShuttleRoute.Kind.HUNT, deciv, null, random).chancePerDay = HUNT_CHANCE;
    }

    // comm relays, nav buoys, sensor arrays, IE watchtowers and artillery stations; the owner is the entity's faction
    private void addMaintenance(LocationAPI location, Random random) {
        List<SectorEntityToken> objectives = new ArrayList<>(location.getEntitiesWithTag(Tags.OBJECTIVE));
        objectives.addAll(location.getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION));
        for (SectorEntityToken objective : objectives) {
            if (!objective.hasTag(Tags.COMM_RELAY) && !objective.hasTag(Tags.NAV_BUOY) && !objective.hasTag(Tags.SENSOR_ARRAY)
                    && !objective.hasTag(Ids.TAG_WATCHTOWER) && !objective.hasTag(Ids.TAG_ARTILLERY_STATION)) continue;
            if (objective.getFaction() == null || !objective.isAlive()) continue;

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
            SectorEntityToken sight = findSight(anchor, location, random);
            if (sight != null) getRoute(ShuttleRoute.Kind.CRUISE, anchor, null, random).target = new ShuttleTarget(sight, 0f, 0f, 0f, 0f);
        }
    }

    // a pirate base the player has not found yet: no anchor of its own for other traffic, one dark craft to a decivilized planet, else the nearest market
    private void addHiddenBases(LocationAPI location, Random random) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(location)) {
            SectorEntityToken primary = market.getPrimaryEntity();
            if (primary == null || primary.getContainingLocation() != location || !market.isHidden() || !primary.isDiscoverable()) continue;
            if (!ShuttleFactionRepo.isPirate(market.getFaction())) continue;

            ShuttleAnchor partner = nearest(decivAnchors, primary);
            if (partner == null) partner = nearest(marketAnchors, primary);
            if (partner == null) continue;

            ShuttleAnchor.Type type = primary instanceof PlanetAPI ? ShuttleAnchor.Type.PLANET : ShuttleAnchor.Type.STATION;
            ShuttleAnchor base = getAnchor(primary, type, ShuttleAnchor.Role.TARGET, market, market.getFaction());
            getRoute(ShuttleRoute.Kind.DARK, base, partner, random).chancePerDay = HIDDEN_BASE_CHANCE;
        }
    }

    private static ShuttleAnchor nearest(List<ShuttleAnchor> anchors, SectorEntityToken to) {
        ShuttleAnchor best = null;
        float bestDist = Float.MAX_VALUE;
        for (ShuttleAnchor anchor : anchors) {
            float dist = Misc.getDistance(anchor.getLocation(), to.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = anchor;
            }
        }
        return best;
    }

    // any planet but a star and the one the craft leaves
    public PlanetAPI pickPlanet(SectorEntityToken from, Random random) {
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>(random);
        for (Body body : bodies) {
            if (body.entity instanceof PlanetAPI planet && !planet.isStar() && planet != from) picker.add(planet);
        }
        return picker.isEmpty() ? null : picker.pick();
    }

    // another planet, moon or station within range; gas giants and ringed planets weigh 3, nearer ones more
    private SectorEntityToken findSight(ShuttleAnchor anchor, LocationAPI location, Random random) {
        if (isSight(anchor, anchor.sight, location)) return anchor.sight;

        Set<SectorEntityToken> ringed = new HashSet<>();
        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            if (Terrain.RING.equals(terrain.getType()) && terrain.getPlugin() instanceof BaseRingTerrain ring && ring.params != null
                    && ring.params.relatedEntity != null) {
                ringed.add(ring.params.relatedEntity);
            }
        }

        WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
        List<SectorEntityToken> candidates = new ArrayList<>(location.getPlanets());
        candidates.addAll(location.getEntitiesWithTag(Tags.STATION));
        for (SectorEntityToken entity : candidates) {
            if (!isSight(anchor, entity, location)) continue;

            float weight = entity instanceof PlanetAPI planet && (planet.isGasGiant() || ringed.contains(planet)) ? 3f : 1f;
            float dist = Misc.getDistance(anchor.getLocation(), entity.getLocation());
            picker.add(entity, weight * (1.25f - dist / SIGHT_RANGE));
        }

        anchor.sight = picker.isEmpty() ? null : picker.pick();
        return anchor.sight;
    }

    private static boolean isSight(ShuttleAnchor anchor, SectorEntityToken entity, LocationAPI location) {
        if (entity == null || entity == anchor.entity || !entity.isAlive() || entity.getContainingLocation() != location) return false;
        if (entity instanceof PlanetAPI planet && planet.isStar()) return false;

        float dist = Misc.getDistance(anchor.getLocation(), entity.getLocation());
        return dist <= SIGHT_RANGE && dist - anchor.getRadius() - entity.getRadius() >= SIGHT_GAP;
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
            route = new ShuttleRoute(key, kind, a, b, ShuttleRoute.rollBend(random));
            route.timer = random.nextFloat() * 5f;
            routes.put(key, route);
        }

        route.seen = true;
        return route;
    }

    // raider and Luddic Path bases stay hidden for life; a pirate profile's base counts once the player has found it
    private static boolean hasTraffic(MarketAPI market, LocationAPI location) {
        SectorEntityToken primary = market.getPrimaryEntity();
        if (primary == null || primary.getContainingLocation() != location || !market.isInEconomy() || !market.hasSpaceport() || primary.isDiscoverable()) {
            return false;
        }

        return !market.isHidden() || ShuttleFactionRepo.isPirate(market.getFaction());
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

    private static float getHuntWeight(MarketAPI market) {
        if (market == null) return 1f;
        if (market.hasCondition(Ids.COND_RUINS)) return 5f;

        boolean ruins = market.hasCondition(Conditions.RUINS_SCATTERED) || market.hasCondition(Conditions.RUINS_WIDESPREAD)
                || market.hasCondition(Conditions.RUINS_EXTENSIVE) || market.hasCondition(Conditions.RUINS_VAST);
        return ruins ? 3f : 1f;
    }
}
