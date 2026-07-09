package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;

public class PayloadOnFinishedModuleRunner extends MovementModuleRunner {

    public SectorEntityToken entity;
    public Script script;

    public PayloadOnFinishedModuleRunner(MeteorMovementModuleAPI module, SectorEntityToken entity, Script script) {
        super(module, entity);

        this.entity = entity;
        this.script = script;
    }

    @Override
    public void advance(float amount) {
        if (module.isMovementFinished()) script.run();
        super.advance(amount);
    }
}
