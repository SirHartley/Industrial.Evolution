package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningPathRenderer;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

public class ArcingMovementModule extends BaseMeteorMovementModule {

    protected final CircularArc arc;
    private final float velocity;
    protected float currentAngle;

    public ArcingMovementModule(CircularArc arc, float velocity) {
        this.arc = arc;
        this.velocity = velocity;
        currentAngle = arc.startAngle;
    }

    @Override
    public boolean isMovementFinished() {
        return arc.getTraversalProgress(currentAngle) >= 1f && !entity.hasTag(Tags.FADING_OUT_AND_EXPIRING);
    }

    @Override
    public void advance(float amount) {
        float arcLengthTraveled = velocity * amount;
        float angleDelta = (arcLengthTraveled / arc.radius) * (float) (180f / Math.PI);  // Convert radians to degrees
        int dir = arc.getAngleTravelDir();
        currentAngle += angleDelta * dir;
        currentAngle = Misc.normalizeAngle(currentAngle);

        Vector2f pos = arc.getPointForAngle(currentAngle);
        entity.getLocation().set(pos.x, pos.y);

        float tangentAngle = Misc.normalizeAngle(currentAngle + 90f * arc.getAngleTravelDir());
        Vector2f tangent = Misc.getUnitVectorAtDegreeAngle(tangentAngle);
        tangent.scale(velocity);
        entity.getVelocity().set(tangent);

        reportPosition();
    }

    public void reportPosition(){
        MeteorSwarmWarningPathRenderer.reportAngle(entity.getContainingLocation(), currentAngle);
    }

    @Override
    public Vector2f getCurrentLoc() {
        return arc.getPointForAngle(currentAngle);
    }

    @Override
    public CircularArc getArc() {
        return arc;
    }

    @Override
    public float getVelocity() {
        return velocity;
    }
}
