package indevo.utils.animation.data;

import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.TrigHelper;
import org.lwjgl.util.vector.Vector2f;

public class CirclePathData {
    public Vector2f center;
    public float radius;
    public float startAngle;
    public float endAngle;

    public CirclePathData(Vector2f center, float radius, float startAngle, float endAngle) {
        this.center = center;
        this.radius = radius;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
    }

    public CirclePathData(Vector2f start, Vector2f end, float radius) {
        this.center = TrigHelper.findTwoPointCircle(start, end, radius).one;
        this.radius = radius;
        this.startAngle = Misc.getAngleInDegrees(center, start);
        this.endAngle = Misc.getAngleInDegrees(center, end);
    }
}
