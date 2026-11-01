package indevo.industries.museum.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class ParadeConditionScript implements Script {

    public final String marketId;
    public final boolean add;

    public ParadeConditionScript(String marketId, boolean add) {
        this.marketId = marketId;
        this.add = add;
    }

    @Override
    public void run() {
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;

        if (add) ParadeFleetAssignmentAI.addParadeConditionToMarket(market);
        else ParadeFleetAssignmentAI.removeParadeConditionFromMarket(market);
    }
}
