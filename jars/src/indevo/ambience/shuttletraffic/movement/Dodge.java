package indevo.ambience.shuttletraffic.movement;

// keeps a craft clear of fleets: a world space shift off each fleet's track, eased at a rate set by the closing speed, and a pace change for crossing fleets
public class Dodge {

    public static final float LOOKAHEAD = 3f;
    public static final float MARGIN = 40f;
    public static final float BASE_RATE = 20f;
    public static final float MAX_RATE = 120f;
    public static final float NEAR_MISS_SPEED = 100f;
    private static final float CLOSING_RATE_MULT = 0.5f;
    private static final float BRAKE = 0.5f;
    private static final float SURGE = 1.3f;
    private static final float PACE_RATE = 1.5f;
    // a craft deep inside a fleet shifts at most this share of the reach, it does not fly off to the edge of the screen
    private static final float MAX_SHIFT = 1.5f;

    private float shiftX = 0f;
    private float shiftY = 0f;
    private float shiftRateX = 0f;
    private float shiftRateY = 0f;
    private float pace = 1f;

    private float targetX;
    private float targetY;
    private float targetLength;
    private float rate;
    private float targetPace;
    private boolean nearMiss;

    public void clear() {
        shiftX = 0f;
        shiftY = 0f;
        shiftRateX = 0f;
        shiftRateY = 0f;
        pace = 1f;
        begin();
    }

    public void begin() {
        targetX = 0f;
        targetY = 0f;
        targetLength = 0f;
        rate = BASE_RATE;
        targetPace = 1f;
        nearMiss = false;
    }

    // b: the path point without the shift, v: the craft's path velocity, f: the fleet
    public void add(float bx, float by, float vx, float vy, float fx, float fy, float fvx, float fvy, float radius) {
        float rx = bx - fx;
        float ry = by - fy;
        float px = rx + shiftX;
        float py = ry + shiftY;
        if (px * px + py * py < radius * radius && fvx * fvx + fvy * fvy > NEAR_MISS_SPEED * NEAR_MISS_SPEED) nearMiss = true;

        float wx = vx - fvx;
        float wy = vy - fvy;
        float w2 = wx * wx + wy * wy;
        if (w2 < 1f) {
            // moving together: treat the fleet as standing still so the shift still goes sideways
            wx = vx;
            wy = vy;
            w2 = wx * wx + wy * wy;
            if (w2 < 1f) {
                wx = 1f;
                wy = 0f;
                w2 = 1f;
            }
        }

        // the unshifted spot enters the reach circle within the lookahead; a big slow fleet is seen before its closest approach
        float reach = radius + MARGIN;
        float rw = rx * wx + ry * wy;
        float r2 = rx * rx + ry * ry;
        float enter = 0f;
        if (r2 > reach * reach) {
            float disc = rw * rw - w2 * (r2 - reach * reach);
            if (rw >= 0f || disc < 0f) return;
            enter = (-rw - (float) Math.sqrt(disc)) / w2;
            if (enter > LOOKAHEAD) return;
        }

        float t = Math.max(0f, -rw / w2);
        float w = (float) Math.sqrt(w2);
        float mx = rx + wx * t;
        float my = ry + wy * t;
        float nx = -wy / w;
        float ny = wx / w;
        float across = mx * nx + my * ny;
        float along = (mx * wx + my * wy) / w;
        if (along * along >= reach * reach) return;

        float side = Math.signum(across + shiftX * nx + shiftY * ny);
        if (side == 0f) side = Math.signum(nx * vx + ny * vy);
        if (side == 0f) side = 1f;

        float need = (float) Math.sqrt(reach * reach - along * along);
        float k = side * need - across;
        if (side * k <= 0f) return;

        k = Math.max(-reach * MAX_SHIFT, Math.min(reach * MAX_SHIFT, k));
        if (Math.abs(k) > targetLength) {
            targetX = nx * k;
            targetY = ny * k;
            targetLength = Math.abs(k);
        }

        // fast enough to be clear before the spot enters the reach circle
        float dist = (float) Math.sqrt(px * px + py * py);
        float closing = dist > 0.01f ? Math.max(0f, -(px * wx + py * wy) / dist) : w;
        float gapX = nx * k - shiftX;
        float gapY = ny * k - shiftY;
        float inTime = (float) Math.sqrt(gapX * gapX + gapY * gapY) / Math.max(0.25f, enter * 0.8f);
        rate = Math.max(rate, Math.min(MAX_RATE, Math.max(BASE_RATE + CLOSING_RATE_MULT * closing, inTime)));

        float craftSpeed = (float) Math.sqrt(vx * vx + vy * vy);
        if (craftSpeed < 0.01f) return;

        float crossSpeed = Math.abs(fvx * vy - fvy * vx) / craftSpeed;
        if (crossSpeed <= craftSpeed * 0.5f) return;

        // the fleet crosses ahead: let it pass; behind: get through first; braking wins over two fleets
        float ahead = (mx * vx + my * vy) / craftSpeed;
        if (ahead < 0f) targetPace = BRAKE;
        else if (targetPace != BRAKE) targetPace = SURGE;
    }

    // envelope 0 over launch and landing, so a craft never dodges off its pad
    public void step(float amount, float envelope) {
        float tx = targetX * envelope;
        float ty = targetY * envelope;
        float dx = tx - shiftX;
        float dy = ty - shiftY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float step = rate * amount;
        float oldX = shiftX;
        float oldY = shiftY;

        if (dist <= step) {
            shiftX = tx;
            shiftY = ty;
        } else {
            shiftX += dx / dist * step;
            shiftY += dy / dist * step;
        }

        shiftRateX = amount > 0f ? (shiftX - oldX) / amount : 0f;
        shiftRateY = amount > 0f ? (shiftY - oldY) / amount : 0f;

        float paceTarget = 1f + (targetPace - 1f) * envelope;
        float paceStep = PACE_RATE * amount;
        pace = pace < paceTarget ? Math.min(paceTarget, pace + paceStep) : Math.max(paceTarget, pace - paceStep);
    }

    public boolean isActive() {
        return targetLength > 0f;
    }

    public boolean isShifted() {
        return targetLength > 0f || shiftX * shiftX + shiftY * shiftY > 1f;
    }

    public boolean isNearMiss() {
        return nearMiss;
    }

    public float getShiftX() {
        return shiftX;
    }

    public float getShiftY() {
        return shiftY;
    }

    public float getShiftRateX() {
        return shiftRateX;
    }

    public float getShiftRateY() {
        return shiftRateY;
    }

    public float getPace() {
        return pace;
    }
}
