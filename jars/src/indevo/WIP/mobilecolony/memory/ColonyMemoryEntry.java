package indevo.WIP.mobilecolony.memory;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class ColonyMemoryEntry {
    private MarketAPI market;
    private StarSystemAPI system;
    private CampaignFleetAPI fleet;

    public ColonyMemoryEntry(MarketAPI market, StarSystemAPI system, CampaignFleetAPI fleet) {
        this.market = market;
        this.system = system;
        this.fleet = fleet;
    }

    public void joinFleetAndMarket(){
        market.getConnectedEntities().add(fleet);
        fleet.setMarket(market);
        fleet.getMemoryWithoutUpdate().set(MemFlags.STATION_MARKET, market);
    }

    public MarketAPI getMarket() {
        return market;
    }

    public StarSystemAPI getSystem() {
        return system;
    }

    public CampaignFleetAPI getFleet() {
        return fleet;
    }
}
