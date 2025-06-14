package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

public interface MeteorMovementModuleAPI {
    boolean isMovementFinished();
    void advance(float amount);
    void init(SectorEntityToken entity);
    Vector2f getCurrentLoc();
    CircularArc getArc();
    float getVelocity();
}
