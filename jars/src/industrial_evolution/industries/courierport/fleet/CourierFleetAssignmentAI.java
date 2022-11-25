package industrial_evolution.industries.courierport.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import industrial_evolution.industries.courierport.intel.ShippingIntel;
import industrial_evolution.industries.courierport.listeners.Shipment;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;

public class CourierFleetAssignmentAI extends BaseAssignmentAI implements FleetEventListener {
    public Shipment container;

    public CourierFleetAssignmentAI(CampaignFleetAPI fleet, Shipment container){
        this.fleet = fleet;
        this.container = container;

        fleet.addEventListener(this);

        giveInitialAssignments();
    }

    public static CourierFleetAssignmentAI get(CampaignFleetAPI fleet){
        for (EveryFrameScript s : fleet.getScripts()){
            if (s instanceof CourierFleetAssignmentAI) return (CourierFleetAssignmentAI) s;
        }

        return null;
    }

    public void updateAssignments(){
        fleet.clearAssignments();

        container.intel.setStatus(ShippingIntel.ShippingStatus.TRANSFER);

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION,
                container.contract.getToMarket().getPrimaryEntity(),
                container.maxDaysBeforeFailsafe,
                "Transporting cargo for contract [" + container.contract.name + "]",
                new Script() {
                    @Override
                    public void run() {
                        container.intel.setStatus(ShippingIntel.ShippingStatus.UNLOADING);
                    }
                });

        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, container.contract.getToMarket().getPrimaryEntity(), 2f,"Unloading cargo");
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, container.contract.getToMarket().getPrimaryEntity(), 1f, "Disbanding", new Script() {
            @Override
            public void run() {
                container.finalizeAndRemove();
            }
        });
    }

    @Override
    protected void giveInitialAssignments() {
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE,
                container.contract.getFromMarket().getPrimaryEntity(),
                2f,
                "Loading cargo",
                false,
                new Script() {
                    @Override
                    public void run() {
                        container.intel.setStatus(ShippingIntel.ShippingStatus.ASSEMBLING);
                    }
                },
                new Script() {
                    @Override
                    public void run() {
                        container.intel.setStatus(ShippingIntel.ShippingStatus.TRANSFER);
                    }
                });

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION,
                container.contract.getToMarket().getPrimaryEntity(),
                container.maxDaysBeforeFailsafe,
                "Transporting cargo for contract [" + container.contract.name + "]",
                new Script() {
                    @Override
                    public void run() {
                        container.intel.setStatus(ShippingIntel.ShippingStatus.UNLOADING);
                    }
                });

        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, container.contract.getToMarket().getPrimaryEntity(), 2f,"Unloading cargo");
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, container.contract.getToMarket().getPrimaryEntity(), 1f, "Disbanding", new Script() {
            @Override
            public void run() {
                container.finalizeAndRemove();
            }
        });
    }

    @Override
    protected void pickNext() {
        fleet.addAssignment(
            FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
            container.contract.getToMarket().getPrimaryEntity(),
            container.maxDaysBeforeFailsafe,
            "Emergency direct routing for [" + container.contract.name + "] due to fleet AI error",
            new Script() {
                @Override
                public void run() {
                    container.finalizeAndRemove();
                }
            }
        );
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if(fleet != null) fleet.removeEventListener(this);
        //IndEvo_modPlugin.log("Courier fleet despawned, reason " + reason.toString() + " current assignment: " + fleet.getCurrentAssignment().getActionText());
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }
}
