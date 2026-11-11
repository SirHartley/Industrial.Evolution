package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.movement.ShuttlePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class LocalFlights extends BaseFlights {

    private static final Set<String> MINING_HULLS = Set.of("shepherd", "mudskipper", "ox");
    private static final Set<String> SALVAGE_HULLS = Set.of("ox", "mudskipper", "shepherd");
    private static final Set<String> TRAINING_HULLS = Set.of("kite", "shuttlepod");
    private static final String PICKET_FALLBACK = "talon";
    private static final float MAINTENANCE_RADIUS = 100f;

    public LocalFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public List<Shuttle> launchJoyride(ShuttleRoute route, int room) {
        ShuttleAnchor home = route.a;
        if (!canFly(home, home)) return Collections.emptyList();

        boolean remnant = home.role == ShuttleAnchor.Role.REMNANT;
        ShuttleHull hull = remnant ? ShuttleHullPool.rollAny(home.faction, random, 0f, false) : ShuttleHullPool.rollAny(home.faction, random, 0.2f, true);
        if (hull == null) return Collections.emptyList();

        ShuttlePaths.Pattern pattern = home.type == ShuttleAnchor.Type.PLANET && random.nextFloat() < 0.6f ? ShuttlePaths.Pattern.ARC : ShuttlePaths.Pattern.ORBIT;
        int count = Math.min(hull.fighter ? hull.wingSize : 1, room);
        Set<Shuttle.Trait> traits = remnant ? EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.QUEUES) : Shuttle.CIVIL;
        Shuttle leader = create(hull, route, home.faction, home, home, paths.joyridePath(home, pattern), 1.1f, LANE_JITTER, ShuttleLook.CRAFT, leaderTraits(traits, count));
        return formation(leader, count, false);
    }

    public List<Shuttle> launchPicket(ShuttleRoute route, int room) {
        ShuttleAnchor home = route.a;
        if (!canFly(home, home)) return Collections.emptyList();

        ShuttleHull hull = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(home.faction, true), random, h -> h.fighter);
        if (hull == null) hull = ShuttleHullPool.getHull(PICKET_FALLBACK, true, 3);
        if (hull == null) return Collections.emptyList();

        float radiusX = home.getRadius() + 150f + random.nextFloat() * 150f;
        ShuttlePath.CircleSegment ellipse = new ShuttlePath.CircleSegment(home.entity, 0f, 0f, radiusX, radiusX * 0.7f, random.nextFloat() * 360f,
                random.nextFloat() * 360f, 360f * (random.nextBoolean() ? 1f : 2f) * paths.randomSign(), 0f);

        int count = Math.min(hull.wingSize, room);
        Set<Shuttle.Trait> traits = EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.QUEUES, Shuttle.Trait.FLEES, Shuttle.Trait.PICKET);
        Shuttle leader = create(hull, route, home.faction, home, home, paths.loopPath(home, List.of(ellipse)), 0.8f, LANE_JITTER, ShuttleLook.CRAFT, leaderTraits(traits, count));
        return formation(leader, count, false);
    }

    public List<Shuttle> launchTraining(ShuttleRoute route, int room) {
        ShuttleAnchor home = route.a;
        if (!canFly(home, home)) return Collections.emptyList();

        ShuttleHull hull = random.nextBoolean() ? ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(home.faction, true), random, h -> h.fighter) : null;
        if (hull == null) hull = ShuttleHullPool.rollBase(random, TRAINING_HULLS);
        if (hull == null) return Collections.emptyList();

        ShuttlePath path = paths.joyridePath(home, ShuttlePaths.Pattern.ORBIT);
        int count = Math.min(2 + random.nextInt(2), room);
        Shuttle leader = create(hull, route, home.faction, home, home, path, 1f, LANE_JITTER, ShuttleLook.CRAFT, leaderTraits(Shuttle.CIVIL, count));
        return formation(leader, count, true);
    }

    public Shuttle launchLinger(ShuttleRoute route) {
        ShuttleAnchor home = route.a;
        if (route.target == null || !canFly(home, home)) return null;

        boolean mining = route.kind == ShuttleRoute.Kind.MINING;
        ShuttleHull hull = mining && random.nextBoolean() ? ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(home.faction, true), random, h -> h.freighter) : null;
        if (hull == null) hull = ShuttleHullPool.rollBase(random, mining ? MINING_HULLS : SALVAGE_HULLS);
        if (hull == null) return null;

        float speedMult = 0.8f;
        float days = mining ? 1f + random.nextFloat() * 2f : 2f + random.nextFloat() * 2f;
        return create(hull, route, home.faction, home, home, paths.lingerPath(home, route.target, days, hull.getCruiseSpeed() * speedMult), speedMult,
                LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    public Shuttle launchMaintenance(ShuttleRoute route) {
        ShuttleAnchor home = route.a;
        if (route.target == null || !canFly(home, home)) return null;

        ShuttleHull hull = ShuttleHullPool.rollCargo(home.faction, random, 0.6f, false);
        if (hull == null) return null;

        SectorEntityToken objective = route.target.center;
        float startAngle = Misc.getAngleInDegrees(objective.getLocation(), home.getLocation());
        ShuttlePath.CircleSegment lap = new ShuttlePath.CircleSegment(objective, 0f, 0f, MAINTENANCE_RADIUS, startAngle, 360f * paths.randomSign(), 0f);
        return create(hull, route, home.faction, home, home, paths.loopPath(home, List.of(lap)), 1f, LANE_JITTER, ShuttleLook.CRAFT, Shuttle.CIVIL);
    }

    // formations skip the docking queue
    private static Set<Shuttle.Trait> leaderTraits(Set<Shuttle.Trait> traits, int count) {
        if (count <= 1) return traits;

        Set<Shuttle.Trait> set = EnumSet.copyOf(traits);
        set.remove(Shuttle.Trait.QUEUES);
        return set;
    }

    private List<Shuttle> formation(Shuttle leader, int count, boolean loose) {
        List<Shuttle> craft = new ArrayList<>(count);
        craft.add(leader);

        int straggler = loose && count > 1 && random.nextFloat() < 0.25f ? 1 + random.nextInt(count - 1) : -1;
        for (int i = 1; i < count; i++) {
            int rank = (i + 1) / 2;
            Shuttle follower = new Shuttle(leader.hull, null, leader.faction, leader.getFrom(), leader.getTo(), null, leader.getLaunchEnd(), leader.getLandEnd(),
                    leader.getSpeedMult(), 0f, random.nextFloat(), leader.getLook(), leaderTraits(leader.getTraits(), 2));
            follower.follow(leader, 8f * rank, 6f * rank * (i % 2 == 1 ? 1f : -1f), loose ? 4f : 0f, i == straggler);
            craft.add(follower);
        }

        return craft;
    }
}
