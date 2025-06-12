package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.campaign.SectorEntityToken;

public interface MeteorMovementModuleAPI {
    boolean isMovementFinished();
    void advance(float amount);
    void init(SectorEntityToken entity);
}
