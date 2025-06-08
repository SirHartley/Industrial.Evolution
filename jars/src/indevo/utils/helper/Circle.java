package indevo.utils.helper;

import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class Circle {
    public Vector2f center;
    public float radius;

    public Circle(Vector2f center, float radius) {
        this.center = center;
        this.radius = radius;
    }

    public Vector2f getPointForAngle(float angle){
        return MathUtils.getPointOnCircumference(center, radius, angle);
    }

    public float getAngleForPoint(Vector2f point){
        return Misc.getAngleInDegrees(center, point);
    }
}
