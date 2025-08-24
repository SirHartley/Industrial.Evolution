package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.Circle;
import indevo.utils.helper.Settings;
import indevo.utils.helper.TrigHelper;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

import static indevo.industries.artillery.projectiles.MissileCarrierEntityPlugin.AVERAGE_PROJ_IMPACT_TIME;

/**
 * time spent making basic trigonometry cooperate with me: 25 hours (includes missile carrier, submunitions and frustrated youtube watching)
 */
public class MissileShotScript implements EveryFrameScript {
    //fire missile at projected target + some fudge
    //missile has soft tracking, is medium fast
    //missile dumbfire flies to loc, deploys tracking submunitions
    //the submunitions are slow but track, if the player enters their radius, they intercept and make a slow field

    public static final int DEFAULT_MISSILE_AMT = Settings.getInt(Settings.ARTILLERY_MISSILE_PROJECTILES_PER_SHOT); //MUST BE DIVISIBLE BY 2
    public static final float MISSILE_TRAJECTORY_BENDY_FACTOR = 0.4f; //the higher, the more circular the trajectory, MAXIMUM 1f
    public static final float AVG_IMPACT_SECONDS = Settings.getFloat(Settings.ARTILLERY_MISSILE_PROJECTILES_IMPACT_TIME);

    public static final float FUZZ_FACTOR = Settings.getFloat(Settings.ARTILLERY_MISSILE_FUZZ); //the higher, the more fuzz
    public static final float MAX_FUZZ_DIST = Settings.getFloat(Settings.ARTILLERY_MISSILE_MAX_COVERED_AREA);
    public static final float MIN_FUZZ_DIST = 400f;

    public boolean done = false;
    public int missileAmt;
    public SectorEntityToken target;
    public SectorEntityToken origin;
    public float radiusIncrement;

    public MissileShotScript(SectorEntityToken origin, SectorEntityToken target, int missileAmt) {
        this.origin = origin;
        this.target = target;
        this.missileAmt = missileAmt % 2 == 0 ? missileAmt : missileAmt + 1; //players are dumb so we make sure

        //max distance is r so we divide r by half the missile amt to get increment to either side
        //since we don't want to have missiles coming in perpendicular, we mult it by bendy factor
        this.radiusIncrement = ((Misc.getDistance(target, origin) / 2) / (missileAmt / 2f)) * Math.min(1, MISSILE_TRAJECTORY_BENDY_FACTOR);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (isDone()) return;

        Vector2f anticipatedTargetLoc = getAnticipatedTargetLoc(target);
        Vector2f originLocation = origin.getLocation();

        //RenderTemporaryIndicator.spawn(origin.getContainingLocation(), anticipatedTargetLoc, Color.YELLOW);

        Random random = new Random();
        float vel = target.getVelocity().length();

        for (int i = 0; i < missileAmt; i++) {
            boolean firstHalf = i <= (missileAmt / 2 - 1);

            Vector2f fuzzedTarget = MathUtils.getPointOnCircumference(anticipatedTargetLoc, Math.min(Math.max(vel, MIN_FUZZ_DIST) * random.nextFloat() * FUZZ_FACTOR, MAX_FUZZ_DIST), MathUtils.getRandomNumberInRange(0, 360));
            //RenderTemporaryIndicator.spawn(origin.getContainingLocation(), fuzzedTarget, Color.RED);

            //need 3 points for curved trajectory = a circle section in practice
            //get a halfway point, get a point at 90° angle from it with the required distance for the third point needed
            float halfDist = Misc.getDistance(originLocation, fuzzedTarget) / 2;
            float angle = Misc.getAngleInDegrees(originLocation, fuzzedTarget);

            Vector2f halfwayPoint = MathUtils.getPointOnCircumference(originLocation, halfDist, angle);

            float angleThirdPoint = Misc.getAngleInDegrees(halfwayPoint, originLocation) + 90f * (firstHalf ? -1 : 1);
            float distThirdPoint = firstHalf ? radiusIncrement * (i + 1) : radiusIncrement * (i - (missileAmt / 2f - 1));
            Vector2f thirdPoint = MathUtils.getPointOnCircumference(halfwayPoint, distThirdPoint, angleThirdPoint);

            Circle trajectoryCircleData = TrigHelper.findThreePointCircle(originLocation, fuzzedTarget, thirdPoint);

            //now we need a target loc for the missile to split, which will be on a circle intersecting with the trajectory
            float splitDistance = Misc.getDistance(originLocation, fuzzedTarget) * 0.3f;
            Vector2f splitLocation = TrigHelper.findClosestCircleIntersectToPoint(fuzzedTarget, splitDistance, trajectoryCircleData.center, trajectoryCircleData.radius, originLocation);

            if (splitLocation == null) {
                i--;
                continue;
            }

            float splitAngleOnTrajectory = Misc.getAngleInDegrees(trajectoryCircleData.center, splitLocation);

            MissileCarrierEntityPlugin.MissileParams params = new MissileCarrierEntityPlugin.MissileParams(
                    origin,
                    fuzzedTarget,
                    MathUtils.getRandomNumberInRange(AVG_IMPACT_SECONDS * 0.8f, AVG_IMPACT_SECONDS * 1.2f),
                    trajectoryCircleData.center,
                    trajectoryCircleData.radius,
                    splitAngleOnTrajectory);

            MissileCarrierEntityPlugin.spawn(params);
        }

        done = true;
    }

    public static Vector2f getAnticipatedTargetLoc(SectorEntityToken entity) {
        Vector2f vel = entity.getVelocity();
        float dist = vel.length() * (AVERAGE_PROJ_IMPACT_TIME + 1f);
        float currentNavigationAngle = Misc.getAngleInDegrees(vel);
        Vector2f location = Misc.getUnitVectorAtDegreeAngle(currentNavigationAngle);
        location.scale(dist);
        return Vector2f.add(location, entity.getLocation(), location);
    }
}
