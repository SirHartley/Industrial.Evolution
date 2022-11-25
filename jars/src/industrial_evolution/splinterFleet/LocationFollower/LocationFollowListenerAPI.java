package industrial_evolution.splinterFleet.LocationFollower;

import com.fs.starfarer.api.campaign.SectorEntityToken;

public interface LocationFollowListenerAPI {
    void reportLocationUpdated(SectorEntityToken token);
}
