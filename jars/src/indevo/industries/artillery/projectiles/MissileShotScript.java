package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
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

    public static final int DEFAULT_MISSILE_AMT = Global.getSettings().getInt("IndEvo_Artillery_missile_projectilesPerShot"); //MUST BE DIVISIBLE BY 2
    public static final float MISSILE_TRAJECTORY_BENDY_FACTOR = 0.4f; //the higher, the more circular the trajectory, MAXIMUM 1f
    public static final float AVG_IMPACT_SECONDS = Global.getSettings().getFloat("IndEvo_Artillery_missile_projectilesImpactTime");

    public static final float FUZZ_FACTOR = Global.getSettings().getFloat("IndEvo_Artillery_missile_fuzz"); //the higher, the more fuzz
    public static final float MAX_FUZZ_DIST = Global.getSettings().getFloat("IndEvo_Artillery_missile_maxCoveredArea");
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

            Vector2f fuzzedTarget = MathUtils.getPointOnCircumference(anticipatedTargetLoc,  Math.min(Math.max(vel, MIN_FUZZ_DIST) * random.nextFloat() * FUZZ_FACTOR, MAX_FUZZ_DIST), MathUtils.getRandomNumberInRange(0, 360));
            //RenderTemporaryIndicator.spawn(origin.getContainingLocation(), fuzzedTarget, Color.RED);

            //need 3 points for curved trajectory = a circle section in practice
            //get a halfway point, get a point at 90° angle from it with the required distance for the third point needed
            float halfDist = Misc.getDistance(originLocation, fuzzedTarget) / 2;
            float angle = Misc.getAngleInDegrees(originLocation, fuzzedTarget);

            Vector2f halfwayPoint = MathUtils.getPointOnCircumference(originLocation, halfDist, angle);

            float angleThirdPoint = Misc.getAngleInDegrees(halfwayPoint, originLocation) + 90f * (firstHalf ? -1 : 1);
            float distThirdPoint = firstHalf ? radiusIncrement * (i + 1) : radiusIncrement * (i - (missileAmt / 2f - 1));
            Vector2f thirdPoint = MathUtils.getPointOnCircumference(halfwayPoint, distThirdPoint, angleThirdPoint);

            Pair<Vector2f, Float> trajectoryCircleData = findCircle(originLocation, thirdPoint, fuzzedTarget);

            //now we need a target loc for the missile to split, which will be on a circle intersecting with the trajectory
            float splitDistance = Misc.getDistance(originLocation, fuzzedTarget) * 0.3f;
            Vector2f splitLocation = findClosestCircleIntersectToPoint(fuzzedTarget, splitDistance, trajectoryCircleData.one, trajectoryCircleData.two, originLocation);

            if (splitLocation == null) {
                i--;
                continue;
            }

            float splitAngleOnTrajectory = Misc.getAngleInDegrees(trajectoryCircleData.one, splitLocation);

            MissileCarrierEntityPlugin.MissileParams params = new MissileCarrierEntityPlugin.MissileParams(
                    origin,
                    fuzzedTarget,
                    MathUtils.getRandomNumberInRange(AVG_IMPACT_SECONDS * 0.8f, AVG_IMPACT_SECONDS * 1.2f),
                    trajectoryCircleData.one,
                    trajectoryCircleData.two,
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

    public static Pair<Vector2f, Float> findCircle(Vector2f p1, Vector2f p2, Vector2f p3) {
        //https://stackoverflow.com/questions/62488827/solving-equation-to-find-center-point-of-circle-from-3-points/71045382#71045382
        double x1 = p1.x;
        double y1 = p1.y;
        double x2 = p2.x;
        double y2 = p2.y;
        double x3 = p3.x;
        double y3 = p3.y;

        double x12 = x1 - x2;
        double x13 = x1 - x3;
        double y12 = y1 - y2;
        double y13 = y1 - y3;
        double y31 = y3 - y1;
        double y21 = y2 - y1;
        double x31 = x3 - x1;
        double x21 = x2 - x1;

        double sx13 = (double) (Math.pow(x1, 2) - Math.pow(x3, 2));
        double sy13 = (double) (Math.pow(y1, 2) - Math.pow(y3, 2));
        double sx21 = (double) (Math.pow(x2, 2) - Math.pow(x1, 2));
        double sy21 = (double) (Math.pow(y2, 2) - Math.pow(y1, 2));

        double f = ((sx13) * (x12)
                + (sy13) * (x12)
                + (sx21) * (x13)
                + (sy21) * (x13))
                / (2 * ((y31) * (x12) - (y21) * (x13)));

        double g = ((sx13) * (y12)
                + (sy13) * (y12)
                + (sx21) * (y13)
                + (sy21) * (y13))
                / (2 * ((x31) * (y12) - (x21) * (y13)));

        double c = -(double) Math.pow(x1, 2) - (double) Math.pow(y1, 2) -
                2 * g * x1 - 2 * f * y1;
        double h = -g;
        double k = -f;
        double sqr_of_r = h * h + k * k - c;

        // r is the radius
        float r = (float) Math.sqrt(sqr_of_r);
        Vector2f center = new Vector2f();
        center.x = (float) h;
        center.y = (float) k;

        return new Pair<>(center, r);
    }

    private Vector2f findClosestCircleIntersectToPoint(Vector2f center0, double r0,
                                                       Vector2f center1, double r1,
                                                       Vector2f targetPoint) {
        //https://stackoverflow.com/questions/29596319/find-the-intersection-points-of-two-circles

        float a, dx, dy, d, h, rx, ry;
        float x2, y2;

        dx = center1.x - center0.x;
        dy = center1.y - center0.y;

        // Determine the straight-line distance between the centers
        d = (float) Math.sqrt((dy * dy) + (dx * dx));

        // Check for solvability because I can't trust myself
        if (d > (r0 + r1)) return null;

        a = (float) (((r0 * r0) - (r1 * r1) + (d * d)) / (2.0 * d));
        x2 = center0.x + (dx * a / d);
        y2 = center0.y + (dy * a / d);

        h = (float) Math.sqrt((r0 * r0) - (a * a));
        rx = -dy * (h / d);
        ry = dx * (h / d);

        // Determine the absolute intersection points please kill me
        Vector2f pos1 = new Vector2f(x2 + rx, y2 + ry);
        Vector2f pos2 = new Vector2f(x2 - rx, y2 - ry);

        return Misc.getDistance(pos1, targetPoint) < Misc.getDistance(pos2, targetPoint) ? pos1 : pos2;
    }
}
