package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;

// one day is 10 s, so cruise speed is also reach per day; one burn level is 20 su/s
public enum SpeedClass {
    CIVIL(1f, 0f, 0.35f),
    SERVICE(1.8f, 0f, 0.15f),
    MILITARY(0f, 60f, 0.35f);

    // no craft moves faster than this in the world, frame carry and fleeing included
    public static final float WORLD_CAP = 75f;
    // path speed changes per second at most, up and down
    public static final float SLEW_UP = 12f;
    public static final float SLEW_DOWN = 20f;
    // fleet tied craft only fly to a fleet slower than this
    public static final float FLEET_TIED_MAX_SPEED = 40f;

    // mean of the fighter and frigate civil speeds in ShuttleHull
    private static final float MEAN_CIVIL_SPEED = 27f;

    private final float hullMult;
    private final float fixedSpeed;
    // share of cruise speed at the very end of a launch or landing
    public final float minEase;

    SpeedClass(float hullMult, float fixedSpeed, float minEase) {
        this.hullMult = hullMult;
        this.fixedSpeed = fixedSpeed;
        this.minEase = minEase;
    }

    public float getCruiseSpeed(ShuttleHull hull) {
        return fixedSpeed > 0f ? fixedSpeed : hull.getCruiseSpeed() * hullMult;
    }

    public float getMeanSpeed() {
        return fixedSpeed > 0f ? fixedSpeed : MEAN_CIVIL_SPEED * hullMult;
    }

    public static float slew(float current, float target, float amount) {
        if (target > current) return Math.min(target, current + SLEW_UP * amount);
        return Math.max(target, current - SLEW_DOWN * amount);
    }

    // a flight to a target that expires only launches when it gets there in time
    public static boolean canReach(float distance, float speed, float daysLeft) {
        return speed > 0f && distance / speed <= daysLeft * Global.getSector().getClock().getSecondsPerDay();
    }
}
