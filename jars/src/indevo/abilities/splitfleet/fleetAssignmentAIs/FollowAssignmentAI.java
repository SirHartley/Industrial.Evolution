package indevo.abilities.splitfleet.fleetAssignmentAIs;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class FollowAssignmentAI extends BaseSplinterFleetAssignmentAIV2 {

    public FollowAssignmentAI(CampaignFleetAPI fleet) {
        super();
        this.fleet = fleet;
        giveInitialAssignments();
    }

    public void setFlags() {
        MemoryAPI splinterFleetMemory = fleet.getMemoryWithoutUpdate();
        splinterFleetMemory.set(MemFlags.CAN_ONLY_BE_ENGAGED_WHEN_VISIBLE_TO_PLAYER, true);
        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
    }

    @Override
    protected void pickNext() {
        if (fleet.getAI() == null) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, playerFleet, ASSIGNMENT_DURATION_FOREVER, "Following");
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        headToPlayerIfTargettedAssignment();
        checkForJumpActions();
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        super.reportFleetJumped(fleet, from, to);
        if (this.fleet.getAI() == null) return;
        if (!fleet.isPlayerFleet()) return;

        if (!this.fleet.isInCurrentLocation()) {
            addToJumpList(from, to);
        }
    }

    @Override
    public void reportFleetTransitingGate(CampaignFleetAPI fleet, SectorEntityToken gateFrom, SectorEntityToken gateTo) {
        super.reportFleetTransitingGate(fleet, gateFrom, gateTo);

        if (this.fleet.getAI() == null) return;
        if (!fleet.isPlayerFleet()) return;

        if (!this.fleet.isInCurrentLocation()) {
            addToJumpList(gateFrom, gateTo);
        }
    }
}
