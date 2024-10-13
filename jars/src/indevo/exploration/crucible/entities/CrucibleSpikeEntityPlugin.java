package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;

public class CrucibleSpikeEntityPlugin extends BaseCustomEntityPlugin {

    public SectorEntityToken crucible;
    public boolean orbitSet = false;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        entity.setFacing(Misc.getAngleInDegrees(crucible.getLocation(), entity.getLocation()));

        if (entity.hasTag(BaseCrucibleEntityPlugin.TAG_ENABLED) && !orbitSet){
            //set new orbit with a tenth of the orbit time
            entity.setCircularOrbit(entity.getOrbitFocus(), entity.getCircularOrbitAngle(), entity.getCircularOrbitRadius(), entity.getCircularOrbitPeriod() / 100f);
            orbitSet = true;
        }
    }
}
