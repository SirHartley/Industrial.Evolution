package com.fs.starfarer.api.splinterFleet.plugins.LocationFollower;

import com.fs.starfarer.api.campaign.SectorEntityToken;

public interface LocationFollowListenerAPI {
    void reportLocationUpdated(SectorEntityToken token);
}
