package indevo.exploration.meteor.movement;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

/**
 * has to be initialized with init() before advance can be called
 */

public abstract class BaseMeteorMovementModule implements MeteorMovementModuleAPI{
    public SectorEntityToken entity;

    @Override
    public void init(SectorEntityToken entity) {
        this.entity = entity;
    }
}
