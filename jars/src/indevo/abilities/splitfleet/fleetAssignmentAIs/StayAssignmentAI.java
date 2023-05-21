package indevo.abilities.splitfleet.fleetAssignmentAIs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.OrbitFocus;

public class StayAssignmentAI extends BaseSplinterFleetAssignmentAIV2 {

    SectorEntityToken orbitFocus;

    public StayAssignmentAI(CampaignFleetAPI fleet) {
        super();
        this.fleet = fleet;
        this.orbitFocus = OrbitFocus.getOrbitFocusAtTokenPosition(fleet);

        giveInitialAssignments();
    }

    @Override
    protected void pickNext() {
        if (fleet.getAI() == null) return;

        fleet.addAssignment(FleetAssignment.HOLD, orbitFocus, ASSIGNMENT_DURATION_FOREVER, "Holding Position");
    }

    @Override
    public void setFlags() {
        MemoryAPI splinterFleetMemory = fleet.getMemoryWithoutUpdate();
        splinterFleetMemory.set(MemFlags.CAN_ONLY_BE_ENGAGED_WHEN_VISIBLE_TO_PLAYER, true);
        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Misc.getDistance(fleet, Global.getSector().getPlayerFleet()) < 1000f) headToPlayerIfTargettedAssignment();
    }
}
