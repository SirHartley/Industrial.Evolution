package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningPathRenderer;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

public class ExternalOrbitMovement extends BaseMeteorMovementModule{
    private final CircularArc arc;
    private float currentAngle;

    public ExternalOrbitMovement(CircularArc arc) {
        this.arc = arc;
        currentAngle = arc.startAngle;
    }

    @Override
    public boolean isMovementFinished() {
        return arc.getTraversalProgress(entity.getLocation()) >= 1f && !entity.hasTag(Tags.FADING_OUT_AND_EXPIRING);
    }

    @Override
    public void advance(float amount) {
        currentAngle = arc.getAngleForPoint(entity.getLocation());
        reportPosition();
    }

    public void reportPosition(){
        MeteorSwarmWarningPathRenderer.reportAngle(entity.getContainingLocation(), currentAngle);
    }

    @Override
    public Vector2f getCurrentLoc() {
        if (entity != null) return entity.getLocation();
        else return arc.getPointForAngle(currentAngle);
    }

    @Override
    public CircularArc getArc() {
        return arc;
    }

    @Override
    public float getVelocity() {
        return 0f;
    }
}
