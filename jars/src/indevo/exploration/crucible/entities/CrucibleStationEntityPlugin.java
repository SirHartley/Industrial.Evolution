package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;

public class CrucibleStationEntityPlugin extends BaseCrucibleEntityPlugin {

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        this.data = new CrucibleData(-0.13f, 0.22f, 10f, 2.5f, 600f, 0.4f, 0.6f, new IntervalUtil(4, 15));
        super.init(entity, pluginParams);
    }
}
