package indevo.exploration.meteor.movement;

import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

public class RelativeArcingMovement extends ArcingMovementModule {
    public Vector2f relativePoint;

    public RelativeArcingMovement(CircularArc arc, float velocity, Vector2f relativePoint) {
        super(arc, velocity);
        this.relativePoint = relativePoint;
    }

    @Override
    public void advance(float amount) {
        arc.center = Vector2f.add(arc.center, relativePoint, null);
        super.advance(amount);
    }
}
