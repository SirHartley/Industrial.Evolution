package indevo.abilities.splitfleet.locationFollower;

import com.fs.starfarer.api.campaign.SectorEntityToken;

public interface LocationFollowListenerAPI {
    void reportLocationUpdated(SectorEntityToken token);
}
