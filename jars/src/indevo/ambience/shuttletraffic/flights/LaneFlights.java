package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;

import java.util.Set;

public class LaneFlights extends BaseFlights {

    private static final Set<String> DIPLOMAT_HULLS = Set.of("hermes", "kite");
    private static final Set<String> HUNT_HULLS = Set.of("shuttlepod", "kite", "mudskipper");
    private static final float TRAFFIC_LANE_JITTER = 3f;

    public LaneFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public Shuttle launchLane(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        ShuttleAnchor to = route.b;
        if (random.nextBoolean()) {
            from = route.b;
            to = route.a;
        }

        if (!canFly(from, to)) return null;

        boolean smuggler = route.kind == ShuttleRoute.Kind.SMUGGLER;
        FactionAPI faction = route.kind == ShuttleRoute.Kind.PIRATE_CARGO ? route.a.faction : from.faction;
        ShuttleHull hull = switch (route.kind) {
            case SUPPLY -> ShuttleHullPool.rollCargo(faction, random, 0.8f, true);
            case PIRATE_CARGO -> ShuttleHullPool.rollSmall(faction, random);
            default -> ShuttleHullPool.rollCargo(faction, random, 0.6f, false);
        };
        if (hull == null) return null;

        if (route.kind == ShuttleRoute.Kind.MARKET) {
            return create(hull, route, faction, from, to, paths.trafficLanePath(route, from == route.b), 1f, TRAFFIC_LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
        }

        return create(hull, route, faction, from, to, paths.lanePath(from, to, route.bendFrac * (smuggler ? 2f : 1f), smuggler), 1f, LANE_JITTER,
                smuggler ? ShuttleLook.SMUGGLER : ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    public Shuttle launchDiplomat(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        WeightedRandomPicker<ShuttleAnchor> picker = new WeightedRandomPicker<>(random);
        for (ShuttleAnchor anchor : network.marketAnchors) {
            if (anchor != from && !anchor.faction.getId().equals(from.faction.getId()) && canFly(from, anchor)) picker.add(anchor);
        }

        if (picker.isEmpty()) return null;

        ShuttleHull hull = random.nextBoolean() ? ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(from.faction, true), random, h -> h.liner) : null;
        if (hull == null) hull = ShuttleHullPool.rollBase(random, DIPLOMAT_HULLS);
        if (hull == null) return null;

        ShuttleAnchor to = picker.pick();
        return create(hull, route, from.faction, from, to, paths.lanePath(from, to, route.bendFrac, false), 1f, LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    public Shuttle launchHunt(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        PlanetAPI planet = network.pickHuntTarget(from.entity, random);
        if (planet == null) return null;

        ShuttleAnchor to = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);
        if (!canFly(from, to)) return null;

        ShuttleHull hull = ShuttleHullPool.rollBase(random, HUNT_HULLS);
        if (hull == null) return null;

        Shuttle shuttle = create(hull, route, from.faction, from, to, paths.lanePath(from, to, route.bendFrac, false), 0.8f, LANE_JITTER, ShuttleLook.HUNT, Shuttle.CIVIL);
        shuttle.stay(2f + random.nextFloat() * 3f);
        return shuttle;
    }
}
