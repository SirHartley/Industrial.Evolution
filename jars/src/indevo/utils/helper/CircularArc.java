package indevo.utils.helper;

import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class CircularArc extends Circle{

    public float startAngle;
    public float endAngle;

    public CircularArc(Vector2f center, float radius, float startAngle, float endAngle) {
        super(center, radius);
        this.startAngle = Misc.normalizeAngle(startAngle);
        this.endAngle = Misc.normalizeAngle(endAngle);
    }

    public CircularArc(Circle circle, float startAngle, float endAngle){
        super(circle.center, circle.radius);
        this.startAngle = Misc.normalizeAngle(startAngle);
        this.endAngle = Misc.normalizeAngle(endAngle);
    }

    public CircularArc getModifiedRadiusArc(float newRadius){
        return new CircularArc(center, newRadius, startAngle, endAngle);
    }

    public boolean isPointOnArc(Vector2f point) {
        float angle = Misc.getAngleInDegrees(center, point);
        angle = Misc.normalizeAngle(angle);
        int dir = getAngleTravelDir();

        if (dir == 1) {
            return Misc.isBetween(angle, startAngle, endAngle);
        } else {
            return Misc.isBetween(angle, endAngle, startAngle);
        }
    }

    public float getTraversalProgress(float angle) {
        angle = Misc.normalizeAngle(angle);
        float totalArc = getArcLength();

        float traveled;
        if (getAngleTravelDir() == 1) {
            traveled = Misc.normalizeAngle(angle - startAngle);
        } else {
            traveled = Misc.normalizeAngle(startAngle - angle);
        }

        return Math.min(traveled / totalArc, 1f);
    }

    public Pair<Vector2f, Vector2f> getPerpendicularLineAtAngle(float angle, float lineLength) {
        angle = Misc.normalizeAngle(angle);
        Vector2f pointOnCircumference = getPointForAngle(angle);

        float perpAngle1 = Misc.normalizeAngle(angle + 90f);
        float perpAngle2 = Misc.normalizeAngle(angle - 90f);

        Vector2f end1 = MathUtils.getPointOnCircumference(pointOnCircumference, lineLength / 2f, perpAngle1);
        Vector2f end2 = MathUtils.getPointOnCircumference(pointOnCircumference, lineLength / 2f, perpAngle2);

        return new Pair<>(end1, end2);
    }

    public float getDistanceFromCenterLine(Vector2f point) {
        float dist = Misc.getDistance(center, point);
        float angle = Misc.normalizeAngle(Misc.getAngleInDegrees(center, point));
        int dir = getAngleTravelDir();
        boolean onArc = (dir == 1) ? Misc.isBetween(angle, startAngle, endAngle) : Misc.isBetween(angle, endAngle, startAngle);

        if (onArc) return Math.abs(dist - radius);

        //if not on arc, get dist to endpoints (not sure if smart)
        Vector2f p1 = getPointForAngle(startAngle);
        Vector2f p2 = getPointForAngle(endAngle);
        return Math.min(Misc.getDistance(point, p1), Misc.getDistance(point, p2));
    }

    public int getAngleTravelDir() {
        float diff = Misc.normalizeAngle(endAngle - startAngle);
        return (diff == 0) ? 1 : (diff <= 180 ? 1 : -1);
    }

    private float getArcLength() {
        float diff = Misc.normalizeAngle(endAngle - startAngle);
        return (getAngleTravelDir() == 1) ? diff : 360f - diff;
    }
}

