package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleNetwork;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTarget;
import indevo.ambience.shuttletraffic.movement.ShuttlePath;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShuttlePaths {

    // one cruise circuit, rolled once so a race flies it twice
    public record Cruise(float bendFrac, float side, float entryAngle, float launchOffset, float landOffset) {
    }

    private static final float LANE_OFFSET = 40f;
    // market lanes: each direction keeps right of the line between the anchors, so a pair shows two steady streams
    private static final float TRAFFIC_LANE_ANGLE = 8f;
    private static final float TRAFFIC_LANE_SPREAD = 1.5f;
    private static final float TRAFFIC_LANE_PASS = 30f;
    private static final float HANDLE_EXTRA = 80f;
    private static final float PANIC_RADIUS = 60f;
    private static final float SCREEN_RADIUS = 120f;
    private static final float JOIN_HANDLE = 150f;
    private static final float CRUISE_RADIUS_MULT = 1.5f;
    private static final float CRUISE_RADIUS_EXTRA = 60f;
    private static final float CRUISE_LAP_SPEED = 0.9f;

    private final ShuttleNetwork network;
    private final Random random;

    public ShuttlePaths(ShuttleNetwork network, Random random) {
        this.network = network;
        this.random = random;
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
        return addLane(new ShuttlePath(), a, b, from, to, start, end, bend, offset);
    }

    private ShuttlePath addLane(ShuttlePath path, ShuttleAnchor a, ShuttleAnchor b, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath.Waypoint start,
                                ShuttlePath.Waypoint end, float bend, float offset) {
        float chord = Misc.getDistance(a.getLocation(), b.getLocation());
        boolean reverse = from != a;
        ShuttleNetwork.Body star = network.getStarAcross(a.getLocation(), b.getLocation());
        if (star == null) {
            return path.add(new ShuttlePath.BezierSegment(start, end, getHandle(from, chord), getHandle(to, chord), reverse ? -bend : bend));
        }

        ShuttlePath.Waypoint pass = new ShuttlePath.StarPassWaypoint(a.entity, b.entity, star.entity, star.radius * 1.1f, bend, offset, reverse);
        Vector2f passLoc = new Vector2f();
        pass.getPosition(passLoc);
        float first = Misc.getDistance(from.getLocation(), passLoc);
        float second = Misc.getDistance(passLoc, to.getLocation());
        return path
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

    // lane out to the sight, one slow lap around it, lane home on the other side
    public ShuttlePath cruisePath(ShuttleAnchor home, SectorEntityToken sight, Cruise cruise) {
        ShuttleAnchor target = new ShuttleAnchor(sight, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, null, null);
        Vector2f p0 = home.getLocation();
        Vector2f p3 = sight.getLocation();
        float chord = Misc.getDistance(p0, p3);
        float out = network.getBend(p0, p3, cruise.bendFrac() * chord, home.entity, sight);
        float back = network.getBend(p0, p3, -cruise.bendFrac() * chord, home.entity, sight);

        float radius = sight.getRadius() * CRUISE_RADIUS_MULT + CRUISE_RADIUS_EXTRA;
        ShuttlePath.CircleSegment lap = new ShuttlePath.CircleSegment(sight, 0f, 0f, radius, Misc.getAngleInDegrees(p3, p0) + cruise.entryAngle(),
                360f * cruise.side(), 0f);
        lap.withSpeedMult(CRUISE_LAP_SPEED);
        ShuttlePath.Waypoint entry = new ShuttlePath.SegmentWaypoint(lap, 0f);
        ShuttlePath.Waypoint exit = new ShuttlePath.SegmentWaypoint(lap, 1f);

        ShuttlePath path = new ShuttlePath();
        addLane(path, home, target, home, target, new ShuttlePath.PadWaypoint(home, entry, cruise.launchOffset(), 0f, false), entry, out, 0f);
        path.add(lap);
        return addLane(path, home, target, target, home, exit, new ShuttlePath.PadWaypoint(home, exit, cruise.landOffset(), 0f, true), back, 0f);
    }

    // the lap turns the way the bent lane arrives, so the craft swings onto it without an s bend
    public Cruise rollCruise(float bendFrac) {
        float side = bendFrac != 0f ? Math.signum(bendFrac) : randomSign();
        return new Cruise(bendFrac, side, spread(30f), spread(20f), spread(20f));
    }

    public ShuttlePath lingerPath(ShuttleAnchor home, ShuttleTarget target, float days, float speed) {
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

    public ShuttlePath loopPath(ShuttleAnchor home, List<ShuttlePath.Segment> segments) {
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

    public float getHandle(ShuttleAnchor anchor, float chord) {
        return Math.min(anchor.getRadius() + HANDLE_EXTRA, chord * 0.45f);
    }

    public float spread(float range) {
        return (random.nextFloat() - 0.5f) * 2f * range;
    }

    public float randomSign() {
        return random.nextBoolean() ? 1f : -1f;
    }
}
