package indevo.utils.helper;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class TrigHelper {

    public static Vector2f findClosestCircleIntersectToPoint(Vector2f center0, double r0,
                                                             Vector2f center1, double r1,
                                                             Vector2f targetPoint) {
        //https://stackoverflow.com/questions/29596319/find-the-intersection-points-of-two-circles

        float a, dx, dy, d, h, rx, ry;
        float x2, y2;

        dx = center1.x - center0.x;
        dy = center1.y - center0.y;

        // Determine the straight-line distance between the centers
        d = (float) Math.sqrt((dy * dy) + (dx * dx));

        if (d > (r0 + r1)) return null;

        a = (float) (((r0 * r0) - (r1 * r1) + (d * d)) / (2.0 * d));
        x2 = center0.x + (dx * a / d);
        y2 = center0.y + (dy * a / d);

        h = (float) Math.sqrt((r0 * r0) - (a * a));
        rx = -dy * (h / d);
        ry = dx * (h / d);

        // Determine the absolute intersection points
        Vector2f pos1 = new Vector2f(x2 + rx, y2 + ry);
        Vector2f pos2 = new Vector2f(x2 - rx, y2 - ry);

        return Misc.getDistance(pos1, targetPoint) < Misc.getDistance(pos2, targetPoint) ? pos1 : pos2;
    }

    public static Circle findThreePointCircle(Vector2f p1, Vector2f p2, Vector2f p3) {
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

        return new Circle(center, r);
    }

    public static Pair<Vector2f, Float> findTwoPointCircle(Vector2f p1, Vector2f p2, float radius) {
        float b = Misc.getDistance(p1, p2);
        float hb = (float) Math.sqrt((Math.pow(radius, 2f) - ((Math.pow(b, 2f) * 1/4f))));

        Vector2f halfwayPoint = MathUtils.getPointOnCircumference(p1, b / 2f, Misc.getAngleInDegrees(p1, p2));
        float angleThirdPoint = Misc.getAngleInDegrees(halfwayPoint, p1) + 90f;
        Vector2f thirdPoint = MathUtils.getPointOnCircumference(halfwayPoint, hb, angleThirdPoint);

        return new Pair<>(thirdPoint, radius);
    }

    //for roughly 1 @ 1, s= 0.4f, m = 1
    //more even distribution at s=0.7, m = 2
    public static float getNormalDistributionCurve(float x, float s, float m) {
        if (s <= 0) {
            s = 0;
            ModPlugin.log("s = 0 normal dist error");
        }

        double coefficient = 1.0 / (Math.sqrt(2 * Math.PI) * s);
        double exponent = -Math.pow(x - m, 2) / (2 * s * s);

        return (float) (coefficient * Math.exp(exponent));
    }
}
