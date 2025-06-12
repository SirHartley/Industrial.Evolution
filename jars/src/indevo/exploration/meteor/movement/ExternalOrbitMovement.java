package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningRenderer;
import indevo.utils.helper.CircularArc;

public class ExternalOrbitMovement extends BaseMeteorMovementModule{
    private final CircularArc arc;
    private float currentAngle;

    public ExternalOrbitMovement(CircularArc arc) {
        this.arc = arc;
        currentAngle = arc.startAngle;
    }

    @Override
    public boolean isMovementFinished() {
        return arc.getTraversalProgress(currentAngle) >= 1f && !entity.hasTag(Tags.FADING_OUT_AND_EXPIRING);
    }

    @Override
    public void advance(float amount) {
        currentAngle = arc.getAngleForPoint(entity.getLocation());
        reportPosition();
    }

    public void reportPosition(){
        MeteorSwarmWarningRenderer.reportAngle(entity.getContainingLocation(), currentAngle);
    }

}
