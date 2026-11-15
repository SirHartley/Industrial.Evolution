package indevo.ambience.shuttletraffic.listeners;

import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

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

    // npc autoresolve rounds and ends run in the campaign loop, never in a dialog
    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (battle != null && !battle.isPlayerInvolved()) traffic.getDogfights().onRound(battle);
    }

    // finish clears both sides before this call, the round snapshot still has the fleets
    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (battle == null) return;
        if (!battle.isPlayerInvolved()) traffic.getDogfights().onFinished(primaryWinner, battle);

        List<CampaignFleetAPI> fleets = battle.getSnapshotBothSides();
        if (fleets == null || fleets.isEmpty() || traffic.getLocation() == null) return;

        Vector2f center = new Vector2f();
        float weight = 0f;
        boolean seen = false;
        for (CampaignFleetAPI fleet : fleets) {
            if (fleet.getContainingLocation() != traffic.getLocation()) continue;
            center.x += fleet.getLocation().x * fleet.getRadius();
            center.y += fleet.getLocation().y * fleet.getRadius();
            weight += fleet.getRadius();
            seen |= fleet.isPlayerFleet() || fleet.getVisibilityLevelToPlayerFleet() != SectorEntityToken.VisibilityLevel.NONE;
        }
        if (!seen || weight <= 0f) return;

        center.scale(1f / weight);
        traffic.getFlights().getEvents().queueRecovery(traffic.getLocation(), center, primaryWinner != null ? primaryWinner.getFaction() : null);
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        traffic.getFlights().getFleets().queueTrade(transaction);
    }
}
