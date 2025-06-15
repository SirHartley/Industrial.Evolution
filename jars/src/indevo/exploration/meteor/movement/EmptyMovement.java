package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

public class EmptyMovement implements MeteorMovementModuleAPI{

    @Override
    public boolean isMovementFinished() {
        return false;
    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void init(SectorEntityToken entity) {

    }

    @Override
    public Vector2f getCurrentLoc() {
        return new Vector2f(0,0);
    }

    @Override
    public CircularArc getArc() {
        return null;
    }

    @Override
    public float getVelocity() {
        return 0;
    }
}
