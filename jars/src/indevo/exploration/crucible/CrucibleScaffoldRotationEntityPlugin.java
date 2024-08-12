package indevo.exploration.crucible;

import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;

public class CrucibleScaffoldRotationEntityPlugin extends BaseCustomEntityPlugin {

    @Override
    public void advance(float amount) {
        super.advance(amount);

        entity.setFacing(entity.getFacing() + 0.2f);
    }
}
