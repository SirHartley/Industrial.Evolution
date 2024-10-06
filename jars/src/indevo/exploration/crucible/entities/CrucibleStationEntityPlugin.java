package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.*;

public class CrucibleStationEntityPlugin extends BaseCrucibleEntityPlugin {

    public void init(SectorEntityToken entity, Object pluginParams) {
        this.data = new CrucibleData(-0.13f, 0.22f, 10f, 3f, 600f, 0.5f, 0.67f, new IntervalUtil(4, 15));
        super.init(entity, pluginParams);
    }
}
