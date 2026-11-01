package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;

public class PayloadOnFinishedModuleRunner extends MovementModuleRunner {

    public SectorEntityToken entity;
    public Script script;
    public boolean done = false;

    public PayloadOnFinishedModuleRunner(MeteorMovementModuleAPI module, SectorEntityToken entity, Script script) {
        super(module, entity);

        this.entity = entity;
        this.script = script;
    }

    // the engine drops a script as soon as isDone() is true, so the payload has to run in the same advance
    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        super.advance(amount);

        if (module.isMovementFinished()) {
            done = true;
            if (script == null) throw new IllegalStateException("IndEvo: PayloadOnFinishedModuleRunner on " + entity.getId() + " has no payload script");
            script.run();
        }
    }
}
