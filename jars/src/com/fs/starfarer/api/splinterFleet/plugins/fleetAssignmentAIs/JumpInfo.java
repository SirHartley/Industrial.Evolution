package com.fs.starfarer.api.splinterFleet.plugins.fleetAssignmentAIs;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public class JumpInfo {
    SectorEntityToken from;
    JumpPointAPI.JumpDestination to;

    public JumpInfo(SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        this.from = from;
        this.to = to;
    }

    public boolean isRegularJump() {
        return from instanceof JumpPointAPI;
    }
}
