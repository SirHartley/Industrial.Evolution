package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ShuttleFlights {

    private enum Pattern {
        ORBIT,
        ARC
    }

    private static final Set<String> DIPLOMAT_HULLS = Set.of("hermes", "kite");
    private static final Set<String> HUNT_HULLS = Set.of("shuttlepod", "kite", "mudskipper");
    private static final Set<String> MINING_HULLS = Set.of("shepherd", "mudskipper", "ox");
    private static final Set<String> SALVAGE_HULLS = Set.of("ox", "mudskipper", "shepherd");
    private static final Set<String> TRAINING_HULLS = Set.of("kite", "shuttlepod");
    private static final String PICKET_FALLBACK = "talon";
    private static final float LANE_OFFSET = 40f;
    private static final float LANE_JITTER = 10f;
    // market lanes: each direction keeps right of the line between the anchors, so a pair shows two steady streams
    private static final float TRAFFIC_LANE_ANGLE = 8f;
    private static final float TRAFFIC_LANE_SPREAD = 1.5f;
    private static final float TRAFFIC_LANE_JITTER = 3f;
    private static final float TRAFFIC_LANE_PASS = 30f;
    private static final float HANDLE_EXTRA = 80f;
    private static final float POD_SPEED = 35f;
    private static final float PANIC_RADIUS = 60f;
    private static final float SCREEN_RADIUS = 120f;
    private static final float MAINTENANCE_RADIUS = 100f;
    private static final float JOIN_HANDLE = 150f;

    private final ShuttleTraffic traffic;
    private final ShuttleNetwork network;
    private final Random random;

    public ShuttleFlights(ShuttleTraffic traffic, ShuttleNetwork network, Random random) {
        this.traffic = traffic;
        this.network = network;
        this.random = random;
    }

    public List<Shuttle> launch(ShuttleRoute route, int room) {
        if (room <= 0) return Collections.emptyList();

        return switch (route.kind) {
            case SUPPLY, MARKET, PIRATE_CARGO, SMUGGLER -> single(launchLane(route));
            case DIPLOMAT -> single(launchDiplomat(route));
            case HUNT -> single(launchHunt(route));
            case JOYRIDE -> launchJoyride(route, room);
            case PICKET -> launchPicket(route, room);
            case TRAINING -> launchTraining(route, room);
            case MINING, SALVAGE -> single(launchLinger(route));
            case MAINTENANCE -> single(launchMaintenance(route));
        };
    }

    public ShuttlePath lanePath(ShuttleAnchor from, ShuttleAnchor to, float bendFrac, boolean farSide) {
        Vector2f p0 = from.getLocation();
        Vector2f p3 = to.getLocation();
        float chord = Misc.getDistance(p0, p3);
        float side = farSide ? 180f : 0f;
        float offset = LANE_OFFSET * Math.max(0.3f, Math.min(1f, chord / 800f));
        float bend = network.getBend(p0, p3, bendFrac * chord, from.entity, to.entity);

        return lane(from, to, from, to, new ShuttlePath.PadWaypoint(from, new ShuttlePath.EntityWaypoint(to.entity), side + spread(offset), 0f, false),
                new ShuttlePath.PadWaypoint(to, new ShuttlePath.EntityWaypoint(from.entity), side + spread(offset), 0f, true), bend, 0f);
    }

    // one curve per anchor pair, bent the same way in both directions
    public ShuttlePath trafficLanePath(ShuttleRoute route, boolean reverse) {
        Vector2f p0 = route.a.getLocation();
        Vector2f p3 = route.b.getLocation();
        float chord = Misc.getDistance(p0, p3);
        float bend = network.getBend(p0, p3, route.bendFrac * chord, route.a.entity, route.b.entity);
        ShuttleAnchor from = reverse ? route.b : route.a;
        ShuttleAnchor to = reverse ? route.a : route.b;
        float angle = TRAFFIC_LANE_ANGLE + spread(TRAFFIC_LANE_SPREAD);

        return lane(route.a, route.b, from, to, new ShuttlePath.PadWaypoint(from, new ShuttlePath.EntityWaypoint(to.entity), -angle, 0f, false),
                new ShuttlePath.PadWaypoint(to, new ShuttlePath.EntityWaypoint(from.entity), angle, 0f, true), bend, reverse ? TRAFFIC_LANE_PASS : -TRAFFIC_LANE_PASS);
    }

    // bend and offset are along the left normal of a to b; a lane with a star between its anchors goes through a live pass point beside it
    private ShuttlePath lane(ShuttleAnchor a, ShuttleAnchor b, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath.Waypoint start, ShuttlePath.Waypoint end,
                             float bend, float offset) {
        float chord = Misc.getDistance(a.getLocation(), b.getLocation());
        boolean reverse = from != a;
        ShuttleNetwork.Body star = network.getStarAcross(a.getLocation(), b.getLocation());
        if (star == null) {
            return new ShuttlePath().add(new ShuttlePath.BezierSegment(start, end, getHandle(from, chord), getHandle(to, chord), reverse ? -bend : bend));
        }

        ShuttlePath.Waypoint pass = new ShuttlePath.StarPassWaypoint(a.entity, b.entity, star.entity, star.radius * 1.1f, bend, offset, reverse);
        Vector2f passLoc = new Vector2f();
        pass.getPosition(passLoc);
        float first = Misc.getDistance(from.getLocation(), passLoc);
        float second = Misc.getDistance(passLoc, to.getLocation());
        return new ShuttlePath()
                .add(new ShuttlePath.BezierSegment(start, pass, getHandle(from, first), first * 0.35f, 0f))
                .add(new ShuttlePath.BezierSegment(pass, end, second * 0.35f, getHandle(to, second), 0f));
    }

    public ShuttlePath redirectPath(SectorEntityToken frame, Vector2f pos, float heading, ShuttleAnchor to) {
        float distance = Misc.getDistance(pos, to.getLocation());
        ShuttlePath.Waypoint start = startAt(frame, pos, heading);
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(to, start, 0f, 0f, true);
        return new ShuttlePath().add(new ShuttlePath.BezierSegment(start, end, Math.min(JOIN_HANDLE, distance * 0.4f), getHandle(to, distance), 0f));
    }

    public ShuttlePath fleePath(SectorEntityToken frame, Vector2f pos, float heading, ShuttleAnchor refuge, boolean panic) {
        ShuttlePath path = new ShuttlePath();
        ShuttlePath.Waypoint start = startAt(frame, pos, heading);

        if (panic) {
            ShuttlePath.CircleSegment circle = circleAt(frame, pos, heading, PANIC_RADIUS + random.nextFloat() * 30f, randomSign());
            path.add(circle);
            start = new ShuttlePath.SegmentWaypoint(circle, 1f);
        }

        float distance = Misc.getDistance(pos, refuge.getLocation());
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(refuge, start, 0f, 0f, true);
        return path.add(new ShuttlePath.BezierSegment(start, end, Math.min(150f, distance * 0.35f), getHandle(refuge, distance), 0f));
    }

    public ShuttlePath awayPath(SectorEntityToken frame, Vector2f pos, float heading, float threatX, float threatY, boolean panic) {
        ShuttlePath path = new ShuttlePath();
        ShuttlePath.Waypoint start = startAt(frame, pos, heading);

        if (panic) {
            ShuttlePath.CircleSegment circle = circleAt(frame, pos, heading, PANIC_RADIUS + random.nextFloat() * 30f, randomSign());
            path.add(circle);
            start = new ShuttlePath.SegmentWaypoint(circle, 1f);
        }

        float away = Misc.getAngleInDegrees(new Vector2f(threatX, threatY), pos) + spread(20f);
        Vector2f end = Misc.getUnitVectorAtDegreeAngle(away);
        end.scale(800f + random.nextFloat() * 700f);
        Vector2f.add(end, pos, end);

        return path.add(new ShuttlePath.BezierSegment(start, new ShuttlePath.FixedWaypoint(end, Misc.getUnitVectorAtDegreeAngle(away)), 150f, 150f, 0f));
    }

    public ShuttlePath holdPath(SectorEntityToken center, Vector2f pos, float heading, float radius) {
        return new ShuttlePath().add(circleAt(center, pos, heading, radius, 1f));
    }

    public ShuttlePath screenPath(SectorEntityToken frame, Vector2f pos, float heading, ShuttleAnchor home, float threatX, float threatY) {
        Vector2f loc = home.getLocation();
        Vector2f center = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(loc, new Vector2f(threatX, threatY)));
        center.scale(home.getRadius() + SCREEN_RADIUS + 130f);
        float offsetX = center.x;
        float offsetY = center.y;
        Vector2f.add(center, loc, center);

        ShuttlePath.CircleSegment circle = new ShuttlePath.CircleSegment(home.entity, offsetX, offsetY, SCREEN_RADIUS,
                Misc.getAngleInDegrees(center, pos), 360f, 0f);

        Vector2f entry = new Vector2f();
        circle.evaluate(0f, entry);
        float distance = Misc.getDistance(pos, entry);
        if (distance < 5f) return new ShuttlePath().add(circle);

        float handle = Math.min(JOIN_HANDLE, distance * 0.4f);
        return new ShuttlePath()
                .add(new ShuttlePath.BezierSegment(startAt(frame, pos, heading), new ShuttlePath.SegmentWaypoint(circle, 0f), handle, handle, 0f))
                .add(circle);
    }

    public List<Shuttle> launchPods(Vector2f point, PlanetAPI planet, int count) {
        List<Shuttle> pods = new ArrayList<>(count);
        ShuttleAnchor target = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);

        for (int i = 0; i < count; i++) {
            ShuttleHull hull = ShuttleHullPool.getPod();
            if (hull == null) break;

            float angle = random.nextFloat() * 360f;
            float distance = Misc.getDistance(point, planet.getLocation());
            ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(point, Misc.getUnitVectorAtDegreeAngle(angle));
            ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(start, new ShuttlePath.PadWaypoint(target, start, spread(60f), 0f, true),
                    120f + random.nextFloat() * 80f, getHandle(target, distance), 0f));

            Shuttle pod = new Shuttle(hull, null, null, target, target, path, Shuttle.End.NONE, Shuttle.End.PLANET,
                    POD_SPEED / hull.getCruiseSpeed(), 0f, random.nextFloat());
            pod.home = null;
            pod.boost = 45f + random.nextFloat() * 40f;
            pod.boostTime = 2f;
            pod.delay = random.nextFloat() * 1.5f;
            pod.spinRate = (20f + random.nextFloat() * 40f) * randomSign();
            pod.engineGlow = false;
            pod.avoids = false;
            pod.flees = false;
            pod.queues = false;
            pod.trailWidth = 1.5f;
            pod.trailMax = 8;
            pods.add(pod);
        }

        return pods;
    }

    private Shuttle launchLane(ShuttleRoute route) {
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

        Shuttle shuttle = route.kind == ShuttleRoute.Kind.MARKET
                ? create(hull, route, faction, from, to, trafficLanePath(route, from == route.b), 1f, TRAFFIC_LANE_JITTER)
                : create(hull, route, faction, from, to, lanePath(from, to, route.bendFrac * (smuggler ? 2f : 1f), smuggler), 1f);
        if (smuggler) {
            shuttle.engineGlow = false;
            shuttle.trailAlpha = 0.3f;
        }

        return shuttle;
    }

    private Shuttle launchDiplomat(ShuttleRoute route) {
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
        return create(hull, route, from.faction, from, to, lanePath(from, to, route.bendFrac, false), 1f);
    }

    private Shuttle launchHunt(ShuttleRoute route) {
        ShuttleAnchor from = route.a;
        PlanetAPI planet = network.pickHuntTarget(from.entity, random);
        if (planet == null) return null;

        ShuttleAnchor to = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);
        if (!canFly(from, to)) return null;

        ShuttleHull hull = ShuttleHullPool.rollBase(random, HUNT_HULLS);
        if (hull == null) return null;

        Shuttle shuttle = create(hull, route, from.faction, from, to, lanePath(from, to, route.bendFrac, false), 0.8f);
        shuttle.stayDays = 2f + random.nextFloat() * 3f;
        shuttle.trailAlpha = 0.5f;
        shuttle.flicker = 0.6f;
        return shuttle;
    }

    private List<Shuttle> launchJoyride(ShuttleRoute route, int room) {
        ShuttleAnchor home = route.a;
        if (!canFly(home, home)) return Collections.emptyList();

        boolean remnant = home.role == ShuttleAnchor.Role.REMNANT;
        ShuttleHull hull = remnant ? ShuttleHullPool.rollAny(home.faction, random, 0f, false) : ShuttleHullPool.rollAny(home.faction, random, 0.2f, true);
        if (hull == null) return Collections.emptyList();

        Pattern pattern = home.type == ShuttleAnchor.Type.PLANET && random.nextFloat() < 0.6f ? Pattern.ARC : Pattern.ORBIT;
        Shuttle leader = create(hull, route, home.faction, home, home, joyridePath(home, pattern), 1.1f);
        leader.flees = !remnant;
        return formation(leader, hull.fighter ? hull.wingSize : 1, room, false);
    }

    private List<Shuttle> launchPicket(ShuttleRoute route, int room) {
        ShuttleAnchor home = route.a;
        if (!canFly(home, home)) return Collections.emptyList();

        ShuttleHull hull = ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(home.faction, true), random, h -> h.fighter);
        if (hull == null) hull = ShuttleHullPool.getHull(PICKET_FALLBACK, true, 3);
        if (hull == null) return Collections.emptyList();

        float radiusX = home.getRadius() + 150f + random.nextFloat() * 150f;
        ShuttlePath.CircleSegment ellipse = new ShuttlePath.CircleSegment(home.entity, 0f, 0f, radiusX, radiusX * 0.7f, random.nextFloat() * 360f,
                random.nextFloat() * 360f, 360f * (random.nextBoolean() ? 1f : 2f) * randomSign(), 0f);

        Shuttle leader = create(hull, route, home.faction, home, home, loopPath(home, List.of(ellipse)), 0.8f);
        leader.picket = true;
        return formation(leader, hull.wingSize, room, false);
    }

    private List<Shuttle> launchTraining(ShuttleRoute route, int room) {
        ShuttleAnchor home = route.a;
        if (!canFly(home, home)) return Collections.emptyList();

        ShuttleHull hull = random.nextBoolean() ? ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(home.faction, true), random, h -> h.fighter) : null;
        if (hull == null) hull = ShuttleHullPool.rollBase(random, TRAINING_HULLS);
        if (hull == null) return Collections.emptyList();

        Shuttle leader = create(hull, route, home.faction, home, home, joyridePath(home, Pattern.ORBIT), 1f);
        return formation(leader, 2 + random.nextInt(2), room, true);
    }

    private Shuttle launchLinger(ShuttleRoute route) {
        ShuttleAnchor home = route.a;
        if (route.target == null || !canFly(home, home)) return null;

        boolean mining = route.kind == ShuttleRoute.Kind.MINING;
        ShuttleHull hull = mining && random.nextBoolean() ? ShuttleHullPool.pick(ShuttleHullPool.getFactionPool(home.faction, true), random, h -> h.freighter) : null;
        if (hull == null) hull = ShuttleHullPool.rollBase(random, mining ? MINING_HULLS : SALVAGE_HULLS);
        if (hull == null) return null;

        float speedMult = 0.8f;
        float days = mining ? 1f + random.nextFloat() * 2f : 2f + random.nextFloat() * 2f;
        return create(hull, route, home.faction, home, home, lingerPath(home, route.target, days, hull.getCruiseSpeed() * speedMult), speedMult);
    }

    private Shuttle launchMaintenance(ShuttleRoute route) {
        ShuttleAnchor home = route.a;
        if (route.target == null || !canFly(home, home)) return null;

        ShuttleHull hull = ShuttleHullPool.rollCargo(home.faction, random, 0.6f, false);
        if (hull == null) return null;

        SectorEntityToken objective = route.target.center;
        float startAngle = Misc.getAngleInDegrees(objective.getLocation(), home.getLocation());
        ShuttlePath.CircleSegment lap = new ShuttlePath.CircleSegment(objective, 0f, 0f, MAINTENANCE_RADIUS, startAngle, 360f * randomSign(), 0f);
        return create(hull, route, home.faction, home, home, loopPath(home, List.of(lap)), 1f);
    }

    // orbit: one lap well above the anchor; arc: a low hop to another spot on the same planet
    private ShuttlePath joyridePath(ShuttleAnchor home, Pattern pattern) {
        boolean arc = pattern == Pattern.ARC;
        float altitude = arc ? home.getRadius() * 0.25f + 40f + random.nextFloat() * 40f : 100f + random.nextFloat() * 120f;
        float sweep = (arc ? 60f + random.nextFloat() * 80f : 360f) * randomSign();
        float side = Math.signum(sweep);
        float radius = home.getRadius() + altitude;

        ShuttlePath.CircleSegment circle = new ShuttlePath.CircleSegment(home.entity, 0f, 0f, radius, random.nextFloat() * 360f, sweep, 0f);
        ShuttlePath.Waypoint entry = new ShuttlePath.SegmentWaypoint(circle, 0f);
        ShuttlePath.Waypoint exit = new ShuttlePath.SegmentWaypoint(circle, 1f);

        // pads sit 60 degrees behind the entry and past the exit, so the craft climbs out along the entry radial and spirals in
        ShuttlePath.Waypoint launch = new ShuttlePath.PadWaypoint(home, entry, -60f * side, 60f * side, false);
        ShuttlePath.Waypoint land = new ShuttlePath.PadWaypoint(home, exit, 60f * side, -60f * side, true);
        float padHandle = Math.max(40f, radius * 0.5f);
        float circleHandle = Math.max(40f, radius * 0.4f);

        return new ShuttlePath()
                .add(new ShuttlePath.BezierSegment(launch, entry, padHandle, circleHandle, 0f))
                .add(circle)
                .add(new ShuttlePath.BezierSegment(exit, land, circleHandle, padHandle, 0f));
    }

    private ShuttlePath lingerPath(ShuttleAnchor home, ShuttleTarget target, float days, float speed) {
        float length = days * Global.getSector().getClock().getSecondsPerDay() * speed;
        int count = Math.max(2, Math.min(24, Math.round(length / (target.wander * 1.5f))));
        float bandAngle = (float) Math.toDegrees(Math.atan2(target.offsetY, target.offsetX));
        float drift = target.bandRadius > 0f ? (float) Math.toDegrees(40f / target.bandRadius) * randomSign() : 0f;
        float theta = random.nextFloat() * 360f;
        float turn = 80f * randomSign();

        int spare = 2;
        List<ShuttlePath.Waypoint> points = new ArrayList<>(count + 1 + spare * 2);
        for (int i = 0; i <= count + spare * 2; i++) {
            float baseX = target.offsetX;
            float baseY = target.offsetY;
            if (target.bandRadius > 0f) {
                double angle = Math.toRadians(bandAngle + drift * i);
                baseX = (float) Math.cos(angle) * target.bandRadius;
                baseY = (float) Math.sin(angle) * target.bandRadius;
            }

            float radius = target.wander * (0.8f + random.nextFloat() * 0.4f);
            theta += turn + spread(20f);
            double rad = Math.toRadians(theta);
            points.add(new ShuttlePath.RelativeWaypoint(target.center, baseX + (float) Math.cos(rad) * radius, baseY + (float) Math.sin(rad) * radius,
                    theta + Math.signum(turn) * 90f));
        }

        // enter and leave where the loop already heads away from and back to home, so the transits need no u turn
        Vector2f loc = home.getLocation();
        int entry = 0;
        for (int i = 1; i <= spare * 2; i++) if (getAlignment(points.get(i), loc, false) > getAlignment(points.get(entry), loc, false)) entry = i;
        int exit = entry + count;
        for (int i = exit + 1; i < points.size(); i++) if (getAlignment(points.get(i), loc, true) > getAlignment(points.get(exit), loc, true)) exit = i;

        // about the handle of an 80 degree circular arc, longer ones overshoot into cusps
        float handle = target.wander * 0.5f;
        List<ShuttlePath.Segment> segments = new ArrayList<>(exit - entry);
        for (int i = entry; i < exit; i++) segments.add(new ShuttlePath.BezierSegment(points.get(i), points.get(i + 1), handle, handle, 0f));

        Vector2f first = new Vector2f();
        points.get(entry).getPosition(first);
        return connect(home, points.get(entry), points.get(exit), first, segments, target.wander);
    }

    private float getAlignment(ShuttlePath.Waypoint point, Vector2f home, boolean toHome) {
        Vector2f pos = new Vector2f();
        Vector2f dir = new Vector2f();
        point.getPosition(pos);
        point.getDirection(dir);
        Vector2f away = Vector2f.sub(pos, home, null);
        if (away.lengthSquared() < 1f) return 0f;

        away.normalise();
        float dot = Vector2f.dot(dir, away);
        return toHome ? -dot : dot;
    }

    private ShuttlePath loopPath(ShuttleAnchor home, List<ShuttlePath.Segment> segments) {
        ShuttlePath.Waypoint entry = new ShuttlePath.SegmentWaypoint(segments.get(0), 0f);
        ShuttlePath.Waypoint exit = new ShuttlePath.SegmentWaypoint(segments.get(segments.size() - 1), 1f);
        Vector2f first = new Vector2f();
        entry.getPosition(first);
        return connect(home, entry, exit, first, segments, 0f);
    }

    private ShuttlePath connect(ShuttleAnchor home, ShuttlePath.Waypoint entry, ShuttlePath.Waypoint exit, Vector2f first,
                                List<ShuttlePath.Segment> segments, float minHandle) {
        Vector2f loc = home.getLocation();
        float chord = Misc.getDistance(loc, first);
        float outHandle = Math.max(minHandle, chord * 0.35f);

        ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(new ShuttlePath.PadWaypoint(home, entry, spread(20f), 0f, false), entry,
                getHandle(home, chord), outHandle, network.getBend(loc, first, 0f, home.entity, null)));
        for (ShuttlePath.Segment segment : segments) path.add(segment);
        return path.add(new ShuttlePath.BezierSegment(exit, new ShuttlePath.PadWaypoint(home, exit, spread(20f), 0f, true), outHandle,
                getHandle(home, chord), network.getBend(first, loc, 0f, null, home.entity)));
    }

    private List<Shuttle> formation(Shuttle leader, int size, int room, boolean loose) {
        List<Shuttle> craft = new ArrayList<>(size);
        craft.add(leader);

        int count = Math.min(size, room);
        if (count > 1) leader.queues = false;

        int straggler = loose && count > 1 && random.nextFloat() < 0.25f ? 1 + random.nextInt(count - 1) : -1;
        for (int i = 1; i < count; i++) {
            int rank = (i + 1) / 2;
            Shuttle follower = new Shuttle(leader.hull, null, leader.faction, leader.from, leader.to, null, leader.launchEnd, leader.landEnd,
                    leader.speedMult, 0f, random.nextFloat());
            follower.leader = leader;
            follower.home = leader.home;
            follower.slotBack = 8f * rank;
            follower.slotSide = 6f * rank * (i % 2 == 1 ? 1f : -1f);
            follower.noise = loose ? 4f : 0f;
            follower.straggler = i == straggler;
            follower.flees = leader.flees;
            follower.picket = leader.picket;
            follower.queues = false;
            leader.followers.add(follower);
            craft.add(follower);
        }

        return craft;
    }

    private Shuttle create(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path, float speedMult) {
        return create(hull, route, faction, from, to, path, speedMult, LANE_JITTER);
    }

    private Shuttle create(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path, float speedMult,
                           float jitter) {
        return new Shuttle(hull, route, faction, from, to, path, from.getEnd(), to.getEnd(),
                speedMult * (0.85f + 0.3f * random.nextFloat()), spread(jitter), random.nextFloat());
    }

    private ShuttlePath.CircleSegment circleAt(SectorEntityToken entity, Vector2f point, float heading, float radius, float side) {
        float centerAngle = heading + 90f * side;
        Vector2f center = Misc.getUnitVectorAtDegreeAngle(centerAngle);
        center.scale(radius);
        Vector2f.add(center, point, center);

        float offsetX = center.x;
        float offsetY = center.y;
        if (entity != null) {
            offsetX -= entity.getLocation().x;
            offsetY -= entity.getLocation().y;
        }

        return new ShuttlePath.CircleSegment(entity, offsetX, offsetY, radius, centerAngle + 180f, 360f * side, 0f);
    }

    // a join path starts where the craft is, moving with the frame it flies in
    private static ShuttlePath.Waypoint startAt(SectorEntityToken frame, Vector2f pos, float heading) {
        if (frame == null) return new ShuttlePath.FixedWaypoint(pos, Misc.getUnitVectorAtDegreeAngle(heading));

        Vector2f loc = frame.getLocation();
        return new ShuttlePath.RelativeWaypoint(frame, pos.x - loc.x, pos.y - loc.y, heading);
    }

    private boolean canFly(ShuttleAnchor from, ShuttleAnchor to) {
        float days = traffic.getDays();
        return from.isUsable(traffic.getLocation()) && to.isUsable(traffic.getLocation()) && !from.threatened && !to.threatened
                && (from.role == ShuttleAnchor.Role.REMNANT || from.isSafe(days));
    }

    private float getHandle(ShuttleAnchor anchor, float chord) {
        return Math.min(anchor.getRadius() + HANDLE_EXTRA, chord * 0.45f);
    }

    private float spread(float range) {
        return (random.nextFloat() - 0.5f) * 2f * range;
    }

    private float randomSign() {
        return random.nextBoolean() ? 1f : -1f;
    }

    private static List<Shuttle> single(Shuttle shuttle) {
        return shuttle == null ? Collections.emptyList() : List.of(shuttle);
    }
}
