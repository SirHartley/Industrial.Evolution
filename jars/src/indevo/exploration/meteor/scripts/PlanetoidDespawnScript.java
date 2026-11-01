package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.spawners.PlanetoidSwarmSpawner;

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
                boolean isRing = t instanceof RingBandAPI && ((RingBandAPI) t).getFocus() == planet;
                if (isRing || t.getOrbitFocus() == planet) Misc.fadeAndExpire(t, 0.1f);
            }
        }

        Misc.fadeAndExpire(planet, 0.1f);
    }

    public static void despawnStrandedPlanetoids() {
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken t : new ArrayList<>(system.getAllEntities())) {
                SectorEntityToken focus = t instanceof RingBandAPI ring ? ring.getFocus() : t.getOrbitFocus();
                boolean orphan = focus != null && focus.hasTag(PlanetoidSwarmSpawner.PLANETOID_TAG) && (focus.isExpired() || focus.getContainingLocation() != system);
                if (orphan) Misc.fadeAndExpire(t, 0.1f);
            }

            for (PlanetAPI planet : new ArrayList<>(system.getPlanets())) {
                if (!planet.hasTag(PlanetoidSwarmSpawner.PLANETOID_TAG) || planet.isExpired()) continue;

                PayloadOnFinishedModuleRunner runner = null;
                DespawningMovementModuleRunner oldRunner = null;

                for (EveryFrameScript s : planet.getScripts()) {
                    if (s instanceof PayloadOnFinishedModuleRunner r) runner = r;
                    else if (s instanceof DespawningMovementModuleRunner r) oldRunner = r;
                }

                // 4.1.b planets run DespawningMovementModuleRunner, which leaves rings and stations behind
                if (oldRunner != null) {
                    planet.removeScript(oldRunner);
                    planet.addScript(new PayloadOnFinishedModuleRunner(oldRunner.module, planet, new PlanetoidDespawnScript(planet)));
                } else if (runner == null) despawn(planet);
                // the unreleased lambda payload comes back from the save as null
                else if (runner.script == null) runner.script = new PlanetoidDespawnScript(planet);
            }
        }
    }
}
