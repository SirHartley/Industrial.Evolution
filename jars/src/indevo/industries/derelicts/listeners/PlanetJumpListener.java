package indevo.industries.derelicts.listeners;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public interface PlanetJumpListener {
    void reportPlanetJump(SectorEntityToken planet, StarSystemAPI from, StarSystemAPI to);
}
