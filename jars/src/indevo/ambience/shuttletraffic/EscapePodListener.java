package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public class EscapePodListener extends BaseCampaignEventListener {

    private final ShuttleTrafficRenderer renderer;

    public EscapePodListener(ShuttleTrafficRenderer renderer) {
        super(false);
        this.renderer = renderer;
    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE || fleet == null || fleet.isPlayerFleet()) return;
        renderer.queuePods(fleet);
    }
}
