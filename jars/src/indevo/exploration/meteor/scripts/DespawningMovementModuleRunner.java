package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;

public class DespawningMovementModuleRunner extends MovementModuleRunner{

    public SectorEntityToken entity;

    public DespawningMovementModuleRunner(MeteorMovementModuleAPI module, SectorEntityToken entity) {
        super(module, entity);

        this.entity = entity;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (module.isMovementFinished()) Misc.fadeAndExpire(entity, 0.1f);
    }
}
