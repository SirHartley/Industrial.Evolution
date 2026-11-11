package indevo.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttlePath;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class ShuttleTrafficChecks {

    private static final float[] RATES = {30f, 60f, 144f};
    private static final float SPEED = 24f;

    private final List<String> problems = new ArrayList<>();
    private int checks;

    private ShuttleTrafficChecks() {
    }

    public static void main(String[] args) {
        if (Global.getSector() != null || Global.getSettings() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }

        ShuttleTrafficChecks run = new ShuttleTrafficChecks();
        try {
            // Misc reads settings in its static initialiser
            Global.setSettings(proxy(SettingsAPI.class));
            run.checkConstantSpeed();
            run.checkRefresh();
            run.checkMoveAcross();
            run.checkStarPass();
        } finally {
            Global.setSettings(null);
        }

        if (!run.problems.isEmpty()) {
            for (String problem : run.problems) System.out.println("FAIL " + problem);
            System.out.println("Shuttle traffic: " + run.problems.size() + " of " + run.checks + " checks failed");
            System.exit(1);
        }

        System.out.println("Shuttle traffic: " + run.checks + " checks passed");
    }

    private void check(boolean passed, String problem) {
        checks++;
        if (!passed) problems.add(problem);
    }

    // a cursor moves at the same speed on beziers and circles, and arrives at the same time at any frame rate
    // 16 sample tables, linear inside a sample: in the first sample after a short bezier handle a step strays by about 20%
    private void checkConstantSpeed() {
        for (float rate : RATES) {
            Body center = new Body(0f, 0f, 100f);
            ShuttlePath path = testPath(center);
            path.rebuild();
            float length = path.getLength();
            float dt = 1f / rate;
            float step = SPEED * dt;

            ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
            Vector2f last = new Vector2f();
            Vector2f point = new Vector2f();
            path.getPoint(cursor, last);
            float worst = 0f;
            float worstAt = 0f;
            int frames = 0;
            boolean done = false;
            while (!done && frames < 100000) {
                path.refresh(cursor);
                done = path.move(cursor, step);
                path.getPoint(cursor, point);
                frames++;
                float error = Math.abs(Vector2f.sub(point, last, null).length() - step) / step;
                if (!done && error > worst) {
                    worst = error;
                    worstAt = path.getTraveled(cursor);
                }
                last.set(point);
            }

            float time = frames * dt;
            check(done, rate + " Hz: cursor never reached the end");
            check(Math.abs(time - length / SPEED) <= dt + length / SPEED * 0.0001f, rate + " Hz: arrived after " + time + " s, expected " + length / SPEED);
            check(worst < 0.25f, rate + " Hz: step length off by " + worst * 100f + "% at " + worstAt + " of " + length);
        }
    }

    // a rebuilt table never moves the craft; a lane that moves as a whole carries the craft with it
    private void checkRefresh() {
        Body a = new Body(-1500f, 200f, 120f);
        Body b = new Body(1800f, -400f, 90f);
        ShuttlePath path = lane(a, b);
        path.rebuild();

        Vector2f before = new Vector2f();
        Vector2f after = new Vector2f();
        ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
        for (int i = 1; i < 10; i++) {
            path.place(cursor, path.getLength() * i / 10f);
            path.getPoint(cursor, before);
            path.refresh(cursor);
            path.rebuild();
            path.getPoint(cursor, after);
            check(before.equals(after), "refresh moved a still craft at " + i + "/10 from " + before + " to " + after);
        }

        for (int i = 1; i < 10; i++) {
            path.place(cursor, path.getLength() * i / 10f);
            path.getPoint(cursor, before);
            a.loc.translate(37f, -21f);
            b.loc.translate(37f, -21f);
            path.refresh(cursor);
            path.getPoint(cursor, after);
            float dx = after.x - before.x - 37f;
            float dy = after.y - before.y + 21f;
            check(Math.abs(dx) + Math.abs(dy) < 0.05f, "refresh did not carry the craft with its lane at " + i + "/10, off by " + dx + ", " + dy);
        }
    }

    // moving over segment ends keeps the whole distance
    private void checkMoveAcross() {
        Body center = new Body(0f, 0f, 100f);
        ShuttlePath path = testPath(center);
        path.rebuild();
        float length = path.getLength();
        ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();

        for (float start = 5f; start < length - 400f; start += length / 23f) {
            for (float distance : new float[]{1f, 50f, 333f}) {
                path.place(cursor, start);
                float before = path.getTraveled(cursor);
                boolean done = path.move(cursor, distance);
                float after = path.getTraveled(cursor);
                check(!done && Math.abs(after - before - distance) < 0.01f, "move " + distance + " from " + before + " went " + (after - before));
            }
        }

        path.place(cursor, length - 10f);
        check(path.move(cursor, 20f), "move past the end did not report the end");
        check(Math.abs(path.getTraveled(cursor) - length) < 0.01f, "move past the end stopped at " + path.getTraveled(cursor) + " of " + length);
    }

    // a lane across a star stays clear of it while both anchors orbit during the flight
    private void checkStarPass() {
        Body star = new Body(0f, 0f, 400f);
        float clearance = star.radius * 1.1f;

        for (float rate : RATES) {
            for (float offset : new float[]{-30f, 30f}) {
                for (float bend : new float[]{-300f, 0f, 300f}) {
                    Body a = new Body(0f, 0f, 150f);
                    Body b = new Body(0f, 0f, 100f);
                    orbit(a, 2200f, 10f, 0f);
                    orbit(b, 2900f, 185f, 0f);

                    ShuttlePath.Waypoint start = new ShuttlePath.PadWaypoint(anchor(a), new ShuttlePath.EntityWaypoint(b.token), 8f, 0f, false);
                    ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(anchor(b), new ShuttlePath.EntityWaypoint(a.token), -8f, 0f, true);
                    ShuttlePath.Waypoint pass = new ShuttlePath.StarPassWaypoint(a.token, b.token, star.token, clearance, bend, offset, false);
                    ShuttlePath path = new ShuttlePath()
                            .add(new ShuttlePath.BezierSegment(start, pass, 230f, 700f, 0f))
                            .add(new ShuttlePath.BezierSegment(pass, end, 700f, 180f, 0f));
                    path.rebuild();

                    float dt = 1f / rate;
                    float time = 0f;
                    float closest = Float.MAX_VALUE;
                    ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
                    Vector2f point = new Vector2f();
                    boolean done = false;
                    while (!done && time < 1000f) {
                        time += dt;
                        orbit(a, 2200f, 10f, time);
                        orbit(b, 2900f, 185f, time);
                        path.refresh(cursor);
                        done = path.move(cursor, SPEED * 4f * dt);
                        path.getPoint(cursor, point);
                        closest = Math.min(closest, point.length());
                    }

                    String name = rate + " Hz, offset " + offset + ", bend " + bend;
                    check(done, name + ": star pass flight never ended");
                    check(closest >= star.radius, name + ": star pass came within " + closest + " of a " + star.radius + " su star");
                }
            }
        }
    }

    // one lap per 400 s, far less than real orbits, so the lane turns a lot during one flight
    private static void orbit(Body body, float radius, float startAngle, float time) {
        double angle = Math.toRadians(startAngle + time * 0.9f);
        body.loc.set((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius);
    }

    private static ShuttlePath testPath(Body center) {
        ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(new Vector2f(-900f, -300f), new Vector2f(1f, 0.2f));
        ShuttlePath.CircleSegment circle = new ShuttlePath.CircleSegment(center.token, 0f, 0f, 260f, 190f, 25f, 200f, 400f, 0f);
        ShuttlePath.Waypoint end = new ShuttlePath.FixedWaypoint(new Vector2f(800f, 700f), new Vector2f(0.3f, 1f));
        return new ShuttlePath()
                .add(new ShuttlePath.BezierSegment(start, new ShuttlePath.SegmentWaypoint(circle, 0f), 150f, 150f, 0f))
                .add(circle)
                .add(new ShuttlePath.BezierSegment(new ShuttlePath.SegmentWaypoint(circle, 1f), end, 150f, 200f, 0f))
                .add(new ShuttlePath.BezierSegment(end, new ShuttlePath.FixedWaypoint(new Vector2f(3800f, -200f), new Vector2f(1f, -0.4f)), 400f, 300f, 700f));
    }

    private static ShuttlePath lane(Body a, Body b) {
        ShuttlePath.Waypoint start = new ShuttlePath.PadWaypoint(anchor(a), new ShuttlePath.EntityWaypoint(b.token), -8f, 0f, false);
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(anchor(b), new ShuttlePath.EntityWaypoint(a.token), 8f, 0f, true);
        return new ShuttlePath().add(new ShuttlePath.BezierSegment(start, end, 200f, 170f, 250f));
    }

    private static ShuttleAnchor anchor(Body body) {
        return new ShuttleAnchor(body.token, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.MARKET, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private static final class Body {
        private final Vector2f loc;
        private final float radius;
        private final SectorEntityToken token;

        private Body(float x, float y, float radius) {
            this.loc = new Vector2f(x, y);
            this.radius = radius;
            this.token = (SectorEntityToken) Proxy.newProxyInstance(SectorEntityToken.class.getClassLoader(), new Class<?>[]{SectorEntityToken.class},
                    (self, method, args) -> switch (method.getName()) {
                        case "getLocation" -> loc;
                        case "getRadius" -> this.radius;
                        case "isAlive" -> true;
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }
}
