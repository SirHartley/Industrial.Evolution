package indevo.ambience.shuttletraffic.movement;

import org.lwjgl.util.vector.Vector2f;

// free steering toward a moving point: the velocity eases toward the point's velocity plus an arrival speed, limited by acceleration
public class SteerMover {

    public static final float DASH_EXTRA = 60f;
    public static final float DASH_MAX = 320f;
    public static final float ACCEL = 150f;
    // arrival speed per su of distance, keeps the last few su from ringing at low frame rates
    private static final float ARRIVE_GAIN = 3f;
    private static final float ARRIVE_SHARE = 0.8f;

    public final Vector2f pos = new Vector2f();
    public final Vector2f vel = new Vector2f();

    public SteerMover(Vector2f start, Vector2f startVel) {
        pos.set(start);
        vel.set(startVel);
    }

    // up to the target's speed + 60 su/s, never above 320 su/s
    public static float getDashSpeed(float targetSpeed) {
        return Math.min(DASH_MAX, targetSpeed + DASH_EXTRA);
    }

    public void steer(float amount, float tx, float ty, float tvx, float tvy, float maxSpeed) {
        float rx = tx - pos.x;
        float ry = ty - pos.y;
        float dist = (float) Math.sqrt(rx * rx + ry * ry);
        float targetSpeed = (float) Math.sqrt(tvx * tvx + tvy * tvy);
        float closing = Math.max(DASH_EXTRA, maxSpeed - targetSpeed);
        float arrive = Math.min(closing, Math.min((float) Math.sqrt(2f * ACCEL * ARRIVE_SHARE * dist), dist * ARRIVE_GAIN));

        float dvx = tvx - vel.x;
        float dvy = tvy - vel.y;
        if (dist > 0.01f) {
            dvx += rx / dist * arrive;
            dvy += ry / dist * arrive;
        }

        float dv = (float) Math.sqrt(dvx * dvx + dvy * dvy);
        float step = ACCEL * amount;
        if (dv > step) {
            dvx *= step / dv;
            dvy *= step / dv;
        }

        vel.x += dvx;
        vel.y += dvy;
        float speed = vel.length();
        float top = Math.max(maxSpeed, targetSpeed);
        if (speed > top) vel.scale(top / speed);

        pos.x += vel.x * amount;
        pos.y += vel.y * amount;
    }

    // seconds until a craft at dash speed meets a target moving in a straight line, NaN when it never does
    public static float interceptTime(Vector2f from, Vector2f target, Vector2f targetVel, float speed) {
        float rx = target.x - from.x;
        float ry = target.y - from.y;
        float a = targetVel.lengthSquared() - speed * speed;
        float b = 2f * (rx * targetVel.x + ry * targetVel.y);
        float c = rx * rx + ry * ry;
        if (Math.abs(a) < 0.001f) return b < 0f ? -c / b : Float.NaN;

        float disc = b * b - 4f * a * c;
        if (disc < 0f) return Float.NaN;

        float root = (float) Math.sqrt(disc);
        float t1 = (-b - root) / (2f * a);
        float t2 = (-b + root) / (2f * a);
        float t = t1 > 0f && t2 > 0f ? Math.min(t1, t2) : Math.max(t1, t2);
        return t > 0f ? t : Float.NaN;
    }
}
