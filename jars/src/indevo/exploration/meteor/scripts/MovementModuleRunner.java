package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;

public class MovementModuleRunner implements EveryFrameScript {

    public MeteorMovementModuleAPI module;

    public MovementModuleRunner(MeteorMovementModuleAPI module, SectorEntityToken entity) {
        this.module = module;
        module.init(entity);
    }

    @Override
    public boolean isDone() {
        return module.isMovementFinished();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        module.advance(amount);
    }
}
