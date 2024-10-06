package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;

public class CrucibleArmEntityPlugin extends BaseCustomEntityPlugin {

    public static class CrucibleArmEntityPluginParams{
        SectorEntityToken catapult;
        SectorEntityToken crucible;

        public CrucibleArmEntityPluginParams(SectorEntityToken catapult, SectorEntityToken crucible) {
            this.catapult = catapult;
            this.crucible = crucible;
        }
    }

    public SectorEntityToken catapult;
    public SectorEntityToken crucible;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        catapult = ((CrucibleArmEntityPluginParams) pluginParams).catapult;
        crucible = ((CrucibleArmEntityPluginParams) pluginParams).crucible;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        entity.setLocation(crucible.getLocation().x, crucible.getLocation().y);
        entity.setFacing(Misc.getAngleInDegrees(crucible.getLocation(), catapult.getLocation()));
    }
}
