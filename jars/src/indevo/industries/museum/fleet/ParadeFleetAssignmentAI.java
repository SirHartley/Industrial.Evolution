package indevo.industries.museum.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumConstants;

import static indevo.industries.museum.data.MuseumConstants.FLEET_RETURNING_TAG;

public class ParadeFleetAssignmentAI extends BaseAssignmentAI implements FleetEventListener {

    public String sourceMarketId;
    public String targetMarketId;
    public float paradeOrbitTimeDays;

    public ParadeFleetAssignmentAI(CampaignFleetAPI fleet, String sourceMarketId, String targetMarketId, float paradeOrbitTimeDays){
        this.fleet = fleet;

        this.sourceMarketId = sourceMarketId;
        this.targetMarketId = targetMarketId;
        this.paradeOrbitTimeDays = paradeOrbitTimeDays;

        fleet.addEventListener(this);

        giveInitialAssignments();
    }

    public static ParadeFleetAssignmentAI get(CampaignFleetAPI fleet) {
        for (EveryFrameScript s : fleet.getScripts()) {
            if (s instanceof ParadeFleetAssignmentAI) return (ParadeFleetAssignmentAI) s;
        }

        return null;
    }

    @Override
    protected void giveInitialAssignments() {
        MarketAPI sourceMarket = Global.getSector().getEconomy().getMarket(sourceMarketId);
        MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(targetMarketId);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE,
                sourceMarket.getPrimaryEntity(),
                7f,
                "Preparing for parade",
                false,
                () -> addParadeConditionToMarket(sourceMarket),
                () -> removeParadeConditionFromMarket(sourceMarket));

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION,
                targetMarket.getPrimaryEntity(),
                31f,
                "Travelling to " + targetMarket.getName());

        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE,
                targetMarket.getPrimaryEntity(),
                31f,
                "Parading",
                false,
                () -> addParadeConditionToMarket(targetMarket),
                () -> removeParadeConditionFromMarket(targetMarket));

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
                sourceMarket.getPrimaryEntity(),
                31f,
                "Returning to " + sourceMarket.getName());
    }

    @Override
    protected void pickNext() {
        MarketAPI sourceMarket = Global.getSector().getEconomy().getMarket(sourceMarketId);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, sourceMarket.getPrimaryEntity(), 31f, "Returning to " + sourceMarket.getName());
    }

    public void forceReturnToSource(){
        fleet.clearAssignments();
        MarketAPI sourceMarket = Global.getSector().getEconomy().getMarket(sourceMarketId);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, sourceMarket.getPrimaryEntity(), 31f, "Returning to " + sourceMarket.getName());
        fleet.addTag(FLEET_RETURNING_TAG);
    }

    public String getTargetMarketId() {
        return targetMarketId;
    }

    public void addParadeConditionToMarket(MarketAPI market){
        market.addCondition(Ids.COND_PARADE);
    }

    public void removeParadeConditionFromMarket(MarketAPI market){
        market.removeCondition(Ids.COND_PARADE);
        market.removeTag(MuseumConstants.ON_PARADE_TAG);
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (fleet != null) {
            fleet.removeEventListener(this);

            MarketAPI sourceMarket = Global.getSector().getEconomy().getMarket(sourceMarketId);
            MarketAPI targetMarket = Global.getSector().getEconomy().getMarket(targetMarketId);

            if (sourceMarket != null) removeParadeConditionFromMarket(sourceMarket);
            if (targetMarket != null) removeParadeConditionFromMarket(targetMarket);
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }
}
