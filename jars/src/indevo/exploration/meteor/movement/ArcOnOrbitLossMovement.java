package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

public class ArcOnOrbitLossMovement extends ArcingMovementModule{

    private boolean hasReset = false;

    public ArcOnOrbitLossMovement(CircularArc arc, float velocity) {
        super(arc, velocity);
    }

    @Override
    public void advance(float amount) {
        if (entity.getOrbit() != null) return;

        if (!hasReset) {
            CircularArc newArc = arc.getModifiedRadiusArc(Misc.getDistance(entity.getLocation(), arc.center));
            float angle = arc.getAngleForPoint(entity.getLocation());

            arc = newArc;
            currentAngle = angle;

            hasReset = true;
        }

        super.advance(amount);
    }

}
