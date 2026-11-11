package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;

// one day is 10 s, so cruise speed is also reach per day
public enum SpeedClass {
    CIVIL(1f, 0f),
    SERVICE(1.8f, 0f),
    MILITARY(0f, 75f);

    // mean of the fighter and frigate civil speeds in ShuttleHull
    private static final float MEAN_CIVIL_SPEED = 27f;

    private final float hullMult;
    private final float fixedSpeed;

    SpeedClass(float hullMult, float fixedSpeed) {
        this.hullMult = hullMult;
        this.fixedSpeed = fixedSpeed;
    }

    public float getCruiseSpeed(ShuttleHull hull) {
        return fixedSpeed > 0f ? fixedSpeed : hull.getCruiseSpeed() * hullMult;
    }

    public float getMeanSpeed() {
        return fixedSpeed > 0f ? fixedSpeed : MEAN_CIVIL_SPEED * hullMult;
    }

    // a flight to a target that expires only launches when it gets there in time
    public static boolean canReach(float distance, float speed, float daysLeft) {
        return speed > 0f && distance / speed <= daysLeft * Global.getSector().getClock().getSecondsPerDay();
    }
}
