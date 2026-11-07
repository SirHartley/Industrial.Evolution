package indevo.industries.orbitalworks.data;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class OrbitalWorksSlotJob {

    public final MarketAPI market;
    public final FleetMemberAPI member;
    public int daysDone = 0;

    public OrbitalWorksSlotJob(MarketAPI market, FleetMemberAPI member) {
        this.market = market;
        this.member = member;
    }
}
