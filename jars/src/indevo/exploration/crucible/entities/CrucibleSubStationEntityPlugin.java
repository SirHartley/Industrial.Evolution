package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;

public class CrucibleSubStationEntityPlugin extends BaseCrucibleEntityPlugin {

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        this.data = new CrucibleData(0.15f, 0.24f, 12f, 1.8f, 300f, 0.3f, 0.3f, new IntervalUtil(4, 15));
        super.init(entity, pluginParams);
    }
}
