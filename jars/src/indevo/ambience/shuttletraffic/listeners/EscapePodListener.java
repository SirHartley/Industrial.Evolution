package indevo.ambience.shuttletraffic.listeners;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import indevo.ambience.shuttletraffic.ShuttleTraffic;

public class EscapePodListener extends BaseCampaignEventListener {

    private final ShuttleTraffic traffic;

    public EscapePodListener(ShuttleTraffic traffic) {
        super(false);
        this.traffic = traffic;
    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE || fleet == null || fleet.isPlayerFleet()) return;
        // FleetEncounterContext passes the battle, the fleet still sits on its side
        traffic.queuePods(fleet, param instanceof BattleAPI battle ? battle : null);
    }
}
