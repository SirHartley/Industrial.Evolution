package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;

import static indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin.MEM_ACTIVITY_LEVEL;

public class CrucibleScaffoldRotationEntityPlugin extends BaseCustomEntityPlugin {

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (entity.hasTag(BaseCrucibleEntityPlugin.TAG_ENABLED)) entity.setFacing(entity.getFacing() + (0.2f * getActivityLevel()));
    }

    public float getActivityLevel() {
        return entity.getMemoryWithoutUpdate().getFloat(MEM_ACTIVITY_LEVEL);
    }
}
