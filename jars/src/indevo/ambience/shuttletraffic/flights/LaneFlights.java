package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleFactionRepo;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LaneFlights extends BaseFlights {

    private static final Set<String> DIPLOMAT_HULLS = Set.of("hermes", "kite");
    private static final Set<String> HUNT_HULLS = Set.of("shuttlepod", "kite", "mudskipper");
    private static final Set<String> COURIER_HULLS = Set.of("kite", "hermes");
    private static final float TRAFFIC_LANE_JITTER = 3f;
    private static final float PILGRIM_SPEED = 0.7f;
    public static final int CONVOY_MARKET_SIZE = 5;
    public static final float CONVOY_GAP_MIN = 3f;
    public static final float CONVOY_GAP_MAX = 6f;
    private static final int CONVOY_MIN = 4;
    private static final int CONVOY_MAX = 6;
    private static final int CONVOY_ESCORT = 2;
    private static final float CONVOY_SPACING = 16f;
    private static final float CONVOY_ESCORT_SIDE = 25f;
    private static final String ESCORT_FALLBACK = "talon";

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
            return create(hull, route, faction, from, to, paths.trafficLanePath(route, from == route.b, ShuttleFactionRepo.get(faction).laneDiscipline()), SpeedClass.CIVIL, 1f, TRAFFIC_LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
        }

        return create(hull, route, faction, from, to, paths.lanePath(from, to, route.bendFrac * (smuggler ? 2f : 1f), smuggler), SpeedClass.CIVIL, 1f, LANE_JITTER,
                smuggler ? ShuttleLook.SMUGGLER : ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    // a due convoy replaces the single craft of that launch
    public List<Shuttle> launchMarket(ShuttleRoute route, int room) {
        if (isConvoyDue(route)) {
            List<Shuttle> convoy = launchConvoy(route, room);
            if (!convoy.isEmpty()) {
                route.nextConvoyDay = traffic.getDays() + getConvoyGap(route);
                return convoy;
            }
        }

        return single(launchLane(route));
    }

    // a convoy waits for its first gap after entering the system, so the warm start never places one
    private boolean isConvoyDue(ShuttleRoute route) {
        if (route.a.market == null || route.b.market == null) return false;
        if (route.a.market.getSize() < CONVOY_MARKET_SIZE || route.b.market.getSize() < CONVOY_MARKET_SIZE) return false;

        float gap = getConvoyGap(route);
        if (gap == Float.MAX_VALUE) return false;
        if (route.nextConvoyDay < 0f) {
            route.nextConvoyDay = traffic.getDays() + random.nextFloat() * gap;
            return false;
        }

        return traffic.getDays() >= route.nextConvoyDay;
    }

    private float getConvoyGap(ShuttleRoute route) {
        float mult = (ShuttleFactionRepo.get(route.a.faction).convoys() + ShuttleFactionRepo.get(route.b.faction).convoys()) / 2f;
        if (mult <= 0f) return Float.MAX_VALUE;

        return (CONVOY_GAP_MIN + random.nextFloat() * (CONVOY_GAP_MAX - CONVOY_GAP_MIN)) / mult;
    }

    // freighters in line astern, one fighter wing on one flank
    private List<Shuttle> launchConvoy(ShuttleRoute route, int room) {
        int count = Math.min(CONVOY_MIN + random.nextInt(CONVOY_MAX - CONVOY_MIN + 1), room - CONVOY_ESCORT);
        if (count < CONVOY_MIN) return List.of();

        boolean reverse = random.nextBoolean();
        ShuttleAnchor from = reverse ? route.b : route.a;
        ShuttleAnchor to = reverse ? route.a : route.b;
        if (!canFly(from, to)) return List.of();

        FactionAPI faction = from.faction;
        ShuttleHull lead = ShuttleHullPool.rollCargo(faction, random, 0.6f, false);
        if (lead == null) return List.of();

        Shuttle leader = create(lead, route, faction, from, to, paths.trafficLanePath(route, reverse, ShuttleFactionRepo.get(faction).laneDiscipline()), SpeedClass.CIVIL, 1f,
                TRAFFIC_LANE_JITTER, ShuttleLook.CRAFT, leaderTraits(Shuttle.CIVIL, count));
        List<Shuttle> convoy = new ArrayList<>(count + CONVOY_ESCORT);
        convoy.add(leader);
        for (int i = 1; i < count; i++) {
            ShuttleHull hull = ShuttleHullPool.rollCargo(faction, random, 0.6f, false);
            convoy.add(follower(leader, hull != null ? hull : lead, ShuttleLook.CRAFT, CONVOY_SPACING * i, 0f, 1f, false));
        }

        ShuttleHull escort = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(faction, true), random, h -> h.fighter);
        if (escort == null) escort = ShuttleHullPool.getHull(ESCORT_FALLBACK, true, 3);
        if (escort == null) return convoy;

        float side = CONVOY_ESCORT_SIDE * paths.randomSign();
        float mid = CONVOY_SPACING * (count - 1) * 0.5f;
        for (int i = 0; i < CONVOY_ESCORT; i++) convoy.add(follower(leader, escort, ShuttleLook.CRAFT, mid + (i - 0.5f) * CONVOY_SPACING, side, 2f, false));
        return convoy;
    }

    public Shuttle launchCourier(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        ShuttleAnchor to = route.b;
        if (random.nextBoolean()) {
            from = route.b;
            to = route.a;
        }

        if (!canFly(from, to)) return null;

        ShuttleHull hull = ShuttleHullPool.rollBase(random, COURIER_HULLS);
        if (hull == null) return null;

        return create(hull, route, from.faction, from, to, paths.lanePath(from, to, route.bendFrac, false), SpeedClass.MILITARY, 1f, LANE_JITTER, ShuttleLook.CRAFT,
                Shuttle.CIVIL);
    }

    // one way, to the shrine
    public Shuttle launchPilgrim(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        ShuttleAnchor to = route.b;
        if (!canFly(from, to)) return null;

        ShuttleHull hull = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(from.faction, true), random, h -> h.civilian && h.liner);
        if (hull == null) hull = ShuttleHullPool.rollCargo(from.faction, random, 0.6f, false);
        if (hull == null) return null;

        return create(hull, route, from.faction, from, to, paths.lanePath(from, to, route.bendFrac, false), SpeedClass.CIVIL, PILGRIM_SPEED, LANE_JITTER,
                ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    // a hidden base and its partner either way, or a Pather cell craft to a random other planet; small craft, smuggler look
    public Shuttle launchDark(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        ShuttleAnchor to = route.b;
        if (to == null) {
            PlanetAPI planet = network.pickPlanet(from.entity, random);
            if (planet == null) return null;
            to = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);
        } else if (random.nextBoolean()) {
            to = route.a;
            from = route.b;
        }

        if (!canFly(from, to)) return null;

        FactionAPI faction = route.a.faction;
        ShuttleHull hull = ShuttleHullPool.rollSmall(faction, random);
        if (hull == null) return null;

        return create(hull, route, faction, from, to, paths.lanePath(from, to, route.bendFrac * 2f, true), SpeedClass.CIVIL, 1f, LANE_JITTER,
                ShuttleLook.SMUGGLER, Shuttle.CIVIL);
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
        return create(hull, route, from.faction, from, to, paths.lanePath(from, to, route.bendFrac, false), SpeedClass.CIVIL, 1f, LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    public Shuttle launchHunt(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        PlanetAPI planet = network.pickHuntTarget(from.entity, random);
        if (planet == null) return null;

        ShuttleAnchor to = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);
        if (!canFly(from, to)) return null;

        ShuttleHull hull = ShuttleHullPool.rollBase(random, HUNT_HULLS);
        if (hull == null) return null;

        Shuttle shuttle = create(hull, route, from.faction, from, to, paths.lanePath(from, to, route.bendFrac, false), SpeedClass.CIVIL, 0.8f, LANE_JITTER, ShuttleLook.HUNT, Shuttle.CIVIL);
        shuttle.stay(2f + random.nextFloat() * 3f);
        return shuttle;
    }
}
