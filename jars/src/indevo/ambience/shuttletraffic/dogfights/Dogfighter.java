package indevo.ambience.shuttletraffic.dogfights;

import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleTrail;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// one dogfighter: no path, it turns toward a wanted heading at its turn rate and eases to a throttle every frame
public class Dogfighter {

    public enum State {
        LAUNCH,
        FORM,
        PURSUE,
        EVADE,
        EXTEND,
        STRAFE,
        SUPPORT,
        LOOP,
        RETURN,
        REFUGE,
        DYING
    }

    public enum Strafe {
        SETUP,
        RUN,
        BREAK
    }

    public static final float SPEED_MULT = 2.2f;
    public static final float TURN_RATE = 90f * 2.5f;
    public static final float MIN_THROTTLE = 0.6f;
    public static final float MAX_THROTTLE = 1.2f;
    public static final float TRAIL_SECONDS = 1f;
    private static final float ACCEL = 80f;
    private static final float APPEAR_TIME = 0.6f;
    private static final float DYING_TIME = 0.5f;
    private static final float FOLD_LENGTH = 30f;

    public final WingType wing;
    public final ShuttleHull hull;
    public final int side;
    public final DogfightGroup group;
    public final int rank;
    public final DogfightFleet home;
    public final float maxSpeed;
    public final int toughness;
    public final float phase;
    public final Vector2f pos = new Vector2f();
    public final Vector2f vel = new Vector2f();
    public final ShuttleTrail trail = new ShuttleTrail(TRAIL_SECONDS);

    float facing;
    float speed;
    float desired;
    float throttle = 1f;
    int hits = 0;
    State state = State.LAUNCH;
    float stateTime = 0f;
    float age = 0f;
    Dogfighter target;
    DogfightFleet strafeTarget;
    Strafe strafe = Strafe.SETUP;
    float approachAngle;
    final Vector2f runPoint = new Vector2f();
    float breakTurn;
    float extendTime;
    float weaveDir = 1f;
    float weaveTimer = 0f;
    float weaveThrottle = 1f;
    float loopDir = 1f;
    float dieDelay = -1f;
    boolean torpedoDropped = false;
    DogfightHost.Refuge refuge;

    int burstLeft = 0;
    float burstTimer = 0f;
    float cooldown = 0f;
    boolean burstLaser;
    boolean burstAtFleet;

    float alpha = 1f;
    float scale = 1f;
    boolean done = false;

    public Dogfighter(WingType wing, int side, DogfightGroup group, int rank, DogfightFleet home, int toughness, float phase) {
        this.wing = wing;
        this.hull = wing.hull();
        this.side = side;
        this.group = group;
        this.rank = rank;
        this.home = home;
        this.maxSpeed = hull.getCruiseSpeed() * SPEED_MULT;
        this.toughness = toughness;
        this.phase = phase;
    }

    public void place(Vector2f at, float heading) {
        pos.set(at);
        facing = heading;
        speed = maxSpeed * 0.5f;
        desired = heading;
        vel.set(dirX() * speed, dirY() * speed);
    }

    public void setState(State next) {
        if (state == next) return;
        state = next;
        stateTime = 0f;
    }

    // turn at most TURN_RATE toward the wanted heading, ease speed toward the throttle, move, then the look
    void fly(float amount) {
        age += amount;
        stateTime += amount;
        trail.advance(amount);

        if (state == State.DYING) {
            pos.x += vel.x * amount;
            pos.y += vel.y * amount;
            vel.scale(Math.max(0f, 1f - amount));
            alpha = Math.max(0f, 1f - stateTime / DYING_TIME);
            if (stateTime >= DYING_TIME) done = true;
            trail.shift(0f, 0f);
            return;
        }

        float diff = wrap(desired - facing);
        float turn = TURN_RATE * amount;
        facing = wrap(facing + Math.max(-turn, Math.min(turn, diff)));

        float wanted = maxSpeed * Math.max(MIN_THROTTLE, Math.min(MAX_THROTTLE, throttle));
        float step = ACCEL * amount;
        speed = speed < wanted ? Math.min(wanted, speed + step) : Math.max(wanted, speed - step);
        vel.set(dirX() * speed, dirY() * speed);
        pos.x += vel.x * amount;
        pos.y += vel.y * amount;

        float appear = Math.min(1f, age / APPEAR_TIME);
        alpha = appear;
        scale = 0.5f + 0.5f * appear;
        if (state == State.RETURN && home != null) foldInto(home.loc, FOLD_LENGTH);
        if (state == State.REFUGE && refuge != null) foldInto(refuge.loc(), refuge.radius());

        trail.shift(0f, 0f);
        float back = hull.length * scale * 0.5f;
        trail.pushTrailIfDue(pos.x - dirX() * back, pos.y - dirY() * back);
    }

    // the fleet or station end look: shrink to 0.7 and fade over the last stretch
    private void foldInto(Vector2f point, float length) {
        float dist = Vector2f.sub(point, pos, null).length();
        float t = Math.min(1f, dist / Math.max(1f, length));
        alpha *= t;
        scale *= 1f - 0.3f * (1f - t);
        if (dist < 5f || t < 0.05f) done = true;
    }

    public void explode() {
        if (state == State.DYING) return;
        setState(State.DYING);
        burstLeft = 0;
    }

    public void render(float alphaMult, SpriteAPI glow) {
        float a = alpha * alphaMult;
        if (a <= 0.01f || hull.sprite == null) return;

        SpriteAPI sprite = hull.sprite;
        sprite.setSize(hull.width * scale, hull.length * scale);
        sprite.setAngle(facing - 90f);
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(a);
        sprite.setNormalBlend();
        sprite.renderAtCenter(pos.x, pos.y);
        if (state == State.DYING) return;

        float glowSize = hull.width * scale * 0.7f;
        float back = hull.length * scale * 0.5f;
        glow.setSize(glowSize, glowSize);
        glow.setAngle(0f);
        glow.setColor(hull.glowColor);
        glow.setAdditiveBlend();
        glow.setAlphaMult(a * (0.85f + 0.15f * (float) Math.random()));
        glow.renderAtCenter(pos.x - dirX() * back, pos.y - dirY() * back);
    }

    public void renderTrail(float alphaMult) {
        float a = alpha * alphaMult;
        if (a <= 0.01f) return;

        float back = hull.length * scale * 0.5f;
        float width = Math.min(3f, 0.5f * hull.width * scale);
        trail.render(pos.x - dirX() * back, pos.y - dirY() * back, width, hull.trailColor, hull.additiveTrail, a);
    }

    public boolean isFighting() {
        return !done && state != State.DYING && state != State.RETURN && state != State.REFUGE && state != State.LOOP;
    }

    public boolean canBeHit() {
        return !done && state != State.DYING;
    }

    public boolean isDone() {
        return done;
    }

    public State getState() {
        return state;
    }

    public float getFacing() {
        return facing;
    }

    public float getSpeed() {
        return speed;
    }

    public float getHitRadius() {
        return hull.length * 0.5f + 2f;
    }

    float dirX() {
        return (float) Math.cos(Math.toRadians(facing));
    }

    float dirY() {
        return (float) Math.sin(Math.toRadians(facing));
    }

    static float wrap(float angle) {
        angle %= 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    static float angleTo(Vector2f from, float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - from.y, x - from.x));
    }
}
