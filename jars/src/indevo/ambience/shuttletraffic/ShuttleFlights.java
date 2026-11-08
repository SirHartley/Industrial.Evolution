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
        PLANET_LAP,
        LOOPS,
        LOOPING,
        FIGURE_EIGHT
    }

    private static final Set<String> DIPLOMAT_HULLS = Set.of("hermes", "kite");
    private static final Set<String> HUNT_HULLS = Set.of("shuttlepod", "kite", "mudskipper");
    private static final Set<String> MINING_HULLS = Set.of("shepherd", "mudskipper", "ox");
    private static final Set<String> SALVAGE_HULLS = Set.of("ox", "mudskipper", "shepherd");
    private static final Set<String> TRAINING_HULLS = Set.of("kite", "shuttlepod");
    private static final String PICKET_FALLBACK = "talon";
    private static final float LANE_OFFSET = 40f;
    private static final float LANE_JITTER = 10f;
    private static final float HANDLE_EXTRA = 80f;
    private static final float PLANET_LAP_RANGE = 2000f;
    private static final float POD_SPEED = 35f;
    private static final float PANIC_RADIUS = 60f;
    private static final float SCREEN_RADIUS = 120f;
    private static final float MAINTENANCE_RADIUS = 100f;
    private static final float JOIN_HANDLE = 150f;

    private final ShuttleTrafficRenderer renderer;
    private final ShuttleNetwork network;
    private final Random random;

    public ShuttleFlights(ShuttleTrafficRenderer renderer, ShuttleNetwork network, Random random) {
        this.renderer = renderer;
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
        float toAngle = Misc.getAngleInDegrees(p0, p3);
        float chord = Misc.getDistance(p0, p3);
        float launchAngle = (farSide ? toAngle + 180f : toAngle) + spread(LANE_OFFSET);
        float landAngle = (farSide ? toAngle : toAngle + 180f) + spread(LANE_OFFSET);
        float bend = network.getBend(p0, p3, bendFrac * chord, from.entity, to.entity);

        return new ShuttlePath().add(new ShuttlePath.BezierSegment(
                new ShuttlePath.PadWaypoint(from, launchAngle, false),
                new ShuttlePath.PadWaypoint(to, landAngle, true),
                getHandle(from, chord), getHandle(to, chord), bend));
    }

    public ShuttlePath redirectPath(Vector2f pos, float heading, ShuttleAnchor to) {
        float distance = Misc.getDistance(pos, to.getLocation());
        ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(pos, Misc.getUnitVectorAtDegreeAngle(heading));
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(to, Misc.getAngleInDegrees(to.getLocation(), pos), true);
        return new ShuttlePath().add(new ShuttlePath.BezierSegment(start, end, Math.min(JOIN_HANDLE, distance * 0.4f), getHandle(to, distance), 0f));
    }

    public ShuttlePath fleePath(Vector2f pos, float heading, ShuttleAnchor refuge, boolean panic) {
        ShuttlePath path = new ShuttlePath();
        ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(pos, Misc.getUnitVectorAtDegreeAngle(heading));

        if (panic) {
            ShuttlePath.CircleSegment circle = circleAt(null, pos, heading, PANIC_RADIUS + random.nextFloat() * 30f, randomSign(), 0f);
            path.add(circle);
            start = new ShuttlePath.SegmentWaypoint(circle, 1f);
        }

        float distance = Misc.getDistance(pos, refuge.getLocation());
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(refuge, Misc.getAngleInDegrees(refuge.getLocation(), pos), true);
        return path.add(new ShuttlePath.BezierSegment(start, end, Math.min(150f, distance * 0.35f), getHandle(refuge, distance), 0f));
    }

    public ShuttlePath awayPath(Vector2f pos, float heading, float threatX, float threatY, boolean panic) {
        ShuttlePath path = new ShuttlePath();
        ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(pos, Misc.getUnitVectorAtDegreeAngle(heading));

        if (panic) {
            ShuttlePath.CircleSegment circle = circleAt(null, pos, heading, PANIC_RADIUS + random.nextFloat() * 30f, randomSign(), 0f);
            path.add(circle);
            start = new ShuttlePath.SegmentWaypoint(circle, 1f);
        }

        float away = Misc.getAngleInDegrees(new Vector2f(threatX, threatY), pos) + spread(20f);
        Vector2f end = Misc.getUnitVectorAtDegreeAngle(away);
        end.scale(800f + random.nextFloat() * 700f);
        Vector2f.add(end, pos, end);

        return path.add(new ShuttlePath.BezierSegment(start, new ShuttlePath.FixedWaypoint(end, Misc.getUnitVectorAtDegreeAngle(away)), 150f, 150f, 0f));
    }

    public ShuttlePath holdPath(Vector2f pos, float heading, float radius) {
        return new ShuttlePath().add(circleAt(null, pos, heading, radius, 1f, 0f));
    }

    public ShuttlePath screenPath(Vector2f pos, float heading, ShuttleAnchor home, float threatX, float threatY) {
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
                .add(new ShuttlePath.BezierSegment(new ShuttlePath.FixedWaypoint(pos, Misc.getUnitVectorAtDegreeAngle(heading)),
                        new ShuttlePath.SegmentWaypoint(circle, 0f), handle, handle, 0f))
                .add(circle);
    }

    public List<Shuttle> launchPods(Vector2f point, PlanetAPI planet, int count) {
        List<Shuttle> pods = new ArrayList<>(count);
        ShuttleAnchor target = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);

        for (int i = 0; i < count; i++) {
            ShuttleHull hull = ShuttleHullPool.createPod(random);
            if (hull == null) break;

            float angle = random.nextFloat() * 360f;
            float distance = Misc.getDistance(point, planet.getLocation());
            ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(
                    new ShuttlePath.FixedWaypoint(point, Misc.getUnitVectorAtDegreeAngle(angle)),
                    new ShuttlePath.PadWaypoint(target, random.nextFloat() * 360f, true),
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

        Shuttle shuttle = create(hull, route, faction, from, to, lanePath(from, to, route.bendFrac * (smuggler ? 2f : 1f), smuggler), 1f);
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
        boolean showOff = remnant || ShuttleNetwork.isPirate(home.faction);
        ShuttleHull hull = remnant ? ShuttleHullPool.rollAny(home.faction, random, 0f, false) : ShuttleHullPool.rollAny(home.faction, random, 0.2f, true);
        if (hull == null) return Collections.emptyList();

        Shuttle leader = create(hull, route, home.faction, home, home, joyridePath(home, pickPattern(showOff)), 1.15f);
        leader.turnRate = 180f;
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

        Shuttle leader = create(hull, route, home.faction, home, home, joyridePath(home, random.nextBoolean() ? Pattern.LOOPS : Pattern.FIGURE_EIGHT), 1f);
        leader.turnRate = 63f;
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

    private ShuttlePath joyridePath(ShuttleAnchor home, Pattern pattern) {
        if (pattern == Pattern.PLANET_LAP) {
            PlanetAPI planet = network.findNearestPlanet(home.entity, PLANET_LAP_RANGE);
            if (planet == null) return joyridePath(home, Pattern.LOOPS);

            float startAngle = Misc.getAngleInDegrees(planet.getLocation(), home.getLocation());
            float sweep = 360f * (random.nextBoolean() ? 1f : 2f) * randomSign();
            return loopPath(home, List.of(new ShuttlePath.CircleSegment(planet, 0f, 0f, planet.getRadius() * 1.6f, startAngle, sweep, 0f)));
        }

        Vector2f loc = home.getLocation();
        float dirAngle = random.nextFloat() * 360f;
        Vector2f point = Misc.getUnitVectorAtDegreeAngle(dirAngle);
        point.scale(home.getRadius() + 150f + random.nextFloat() * 100f);
        Vector2f.add(point, loc, point);

        float heading = dirAngle + 90f * randomSign();
        float side = randomSign();
        int circles = switch (pattern) {
            case LOOPS -> 2 + random.nextInt(2);
            case FIGURE_EIGHT -> 2;
            default -> 1;
        };
        float sharedRadius = 50f + random.nextFloat() * 30f;

        List<ShuttlePath.Segment> segments = new ArrayList<>(circles);
        for (int i = 0; i < circles; i++) {
            float radius = switch (pattern) {
                case LOOPS -> 60f + random.nextFloat() * 60f;
                case LOOPING -> 25f + random.nextFloat() * 15f;
                default -> sharedRadius;
            };

            segments.add(circleAt(home.entity, point, heading, radius, i % 2 == 0 ? side : -side, pattern == Pattern.LOOPING ? 0.3f : 0f));
        }

        return loopPath(home, segments);
    }

    private ShuttlePath lingerPath(ShuttleAnchor home, ShuttleTarget target, float days, float speed) {
        float length = days * Global.getSector().getClock().getSecondsPerDay() * speed;
        int count = Math.max(2, Math.min(24, Math.round(length / (target.wander * 1.5f))));
        float bandAngle = (float) Math.toDegrees(Math.atan2(target.offsetY, target.offsetX));
        float drift = target.bandRadius > 0f ? (float) Math.toDegrees(40f / target.bandRadius) * randomSign() : 0f;
        float theta = random.nextFloat() * 360f;
        float turn = 80f * randomSign();

        List<ShuttlePath.Waypoint> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            float baseX = target.offsetX;
            float baseY = target.offsetY;
            if (target.bandRadius > 0f) {
                double angle = Math.toRadians(bandAngle + drift * i);
                baseX = (float) Math.cos(angle) * target.bandRadius;
                baseY = (float) Math.sin(angle) * target.bandRadius;
            }

            float radius = target.wander * (0.66f + random.nextFloat() * 0.67f);
            theta += turn + spread(20f);
            double rad = Math.toRadians(theta);
            points.add(new ShuttlePath.RelativeWaypoint(target.center, baseX + (float) Math.cos(rad) * radius, baseY + (float) Math.sin(rad) * radius,
                    theta + Math.signum(turn) * 90f));
        }

        float handle = target.wander;
        List<ShuttlePath.Segment> segments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) segments.add(new ShuttlePath.BezierSegment(points.get(i), points.get(i + 1), handle, handle, 0f));

        Vector2f first = new Vector2f();
        points.get(0).getPosition(first);
        return connect(home, points.get(0), points.get(count), first, segments, handle);
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
        float angle = Misc.getAngleInDegrees(loc, first);
        float chord = Misc.getDistance(loc, first);
        float outHandle = Math.max(minHandle, chord * 0.35f);

        ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(new ShuttlePath.PadWaypoint(home, angle + spread(20f), false), entry,
                getHandle(home, chord), outHandle, network.getBend(loc, first, 0f, home.entity, null)));
        for (ShuttlePath.Segment segment : segments) path.add(segment);
        return path.add(new ShuttlePath.BezierSegment(exit, new ShuttlePath.PadWaypoint(home, angle + spread(20f), true), outHandle,
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
            follower.turnRate = leader.turnRate;
            follower.flees = leader.flees;
            follower.picket = leader.picket;
            follower.queues = false;
            leader.followers.add(follower);
            craft.add(follower);
        }

        return craft;
    }

    private Shuttle create(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path, float speedMult) {
        return new Shuttle(hull, route, faction, from, to, path, ShuttleTrafficRenderer.getEnd(from), ShuttleTrafficRenderer.getEnd(to),
                speedMult * (0.85f + 0.3f * random.nextFloat()), spread(LANE_JITTER), random.nextFloat());
    }

    private ShuttlePath.CircleSegment circleAt(SectorEntityToken entity, Vector2f point, float heading, float radius, float side, float scaleBump) {
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

        return new ShuttlePath.CircleSegment(entity, offsetX, offsetY, radius, centerAngle + 180f, 360f * side, scaleBump);
    }

    private boolean canFly(ShuttleAnchor from, ShuttleAnchor to) {
        float days = renderer.getDays();
        return from.isUsable(renderer.getLocation()) && to.isUsable(renderer.getLocation()) && !from.threatened && !to.threatened
                && (from.role == ShuttleAnchor.Role.REMNANT || from.isSafe(days));
    }

    private Pattern pickPattern(boolean showOff) {
        WeightedRandomPicker<Pattern> picker = new WeightedRandomPicker<>(random);
        picker.add(Pattern.PLANET_LAP, 3f);
        picker.add(Pattern.LOOPS, showOff ? 6f : 3f);
        picker.add(Pattern.LOOPING, showOff ? 4f : 2f);
        picker.add(Pattern.FIGURE_EIGHT, 2f);
        return picker.pick();
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
