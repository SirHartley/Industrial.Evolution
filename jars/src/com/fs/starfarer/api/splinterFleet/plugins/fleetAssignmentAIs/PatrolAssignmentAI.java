package com.fs.starfarer.api.splinterFleet.plugins.fleetAssignmentAIs;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class PatrolAssignmentAI extends BaseSplinterFleetAssignmentAIV2 {

    public PatrolAssignmentAI(CampaignFleetAPI fleet) {
        super();
        this.fleet = fleet;

        //FleetAssignment.PATROL_SYSTEM;
        //check PatrolAssignmentAIV4
        //no idea how to implement this...
    }

    @Override
    protected void pickNext() {
        if (fleet.getAI() == null) return;

    }

    @Override
    public void setFlags() {
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
    }
}
