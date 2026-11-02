package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;

public class PlanetoidDespawnScript implements Script {

    public final SectorEntityToken planet;

    public PlanetoidDespawnScript(SectorEntityToken planet) {
        this.planet = planet;
    }

    @Override
    public void run() {
        despawn(planet);
    }

    public static void despawn(SectorEntityToken planet) {
        LocationAPI loc = planet.getContainingLocation();

        if (loc != null) {
            for (SectorEntityToken t : new ArrayList<>(loc.getAllEntities())) {
                // fleets orbiting the planetoid have it as orbit focus too
                if (t instanceof CampaignFleetAPI) {
                    if (t.getOrbitFocus() == planet) t.setOrbit(null);
                    continue;
                }

                boolean isRing = t instanceof RingBandAPI && ((RingBandAPI) t).getFocus() == planet;
                if (isRing || t.getOrbitFocus() == planet) Misc.fadeAndExpire(t, 0.1f);
            }
        }

        Misc.fadeAndExpire(planet, 0.1f);
    }
}
