package com.fs.starfarer.api.mobileColony.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;

public class ColonyFleetAssignmentAI extends BaseAssignmentAI {

    public ColonyFleetAssignmentAI(CampaignFleetAPI fleet) {
        super(fleet);
    }

    @Override
    protected void giveInitialAssignments() {
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, Global.getSector().getPlayerFleet(), 99);
    }

    @Override
    protected void pickNext() {

    }
}
