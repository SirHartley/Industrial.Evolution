package industrial_evolution.splinterFleet.fleetAssignmentAIs;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import industrial_evolution.splinterFleet.OrbitFocus;
import industrial_evolution.splinterFleet.fleetManagement.Behaviour;
import com.fs.starfarer.api.util.Misc;

public class HideAssignmentAI extends BaseSplinterFleetAssignmentAIV2 {

    SectorEntityToken orbitFocus;

    public HideAssignmentAI(CampaignFleetAPI fleet) {
        super();
        this.fleet = fleet;
        this.orbitFocus = OrbitFocus.getHideOrbitFocus(fleet);

        giveInitialAssignments();
    }

    @Override
    protected void pickNext() {
        if (fleet.getAI() == null) return;

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, orbitFocus, ASSIGNMENT_DURATION_FOREVER, new Script() {
            @Override
            public void run() {
                if (orbitFocus.getOrbit() != null) fleet.setOrbit(orbitFocus.getOrbit().makeCopy());
                lobotomize("Hiding");
            }
        });

        fleet.addFloatingText("Heading to hiding place", fleet.getFaction().getBaseUIColor(), 1f);
        useAbility(Abilities.GO_DARK, true);

        //fleet.addAssignment(FleetAssignment.HOLD, orbitFocus, ASSIGNMENT_DURATION_FOREVER, "Hiding");
    }

    @Override
    public void setFlags() {
        MemoryAPI splinterFleetMemory = fleet.getMemoryWithoutUpdate();
        splinterFleetMemory.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);

    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!Behaviour.isBehaviourOverridden(fleet) && fleet.getAI() == null && Misc.getDistance(fleet.getLocation(), orbitFocus.getLocation()) > 100f) {
            //fleet was moved while hiding, no idea why - head back to hiding spot

            fleet.setOrbit(null);
            restoreAI();
            pickNext();
        }
    }
}
