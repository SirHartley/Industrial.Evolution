package indevo.ambience.shuttletraffic.listeners;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import indevo.ambience.shuttletraffic.ShuttleTraffic;

// transient, added once by ShuttleTrafficRenderer.register; callbacks arrive during dialogs, so everything is queued
public class ShuttleCampaignListener extends BaseCampaignEventListener {

    private final ShuttleTraffic traffic;

    public ShuttleCampaignListener(ShuttleTraffic traffic) {
        super(false);
        this.traffic = traffic;
    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE || fleet == null || fleet.isPlayerFleet()) return;
        // FleetEncounterContext passes the battle, the fleet still sits on its side
        traffic.queuePods(fleet, param instanceof BattleAPI battle ? battle : null);
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        traffic.getFlights().getFleets().queueTrade(transaction);
    }
}
