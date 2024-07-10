package indevo.exploration.crucible;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.NebulaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.campaign.CircularOrbit;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrucibleSpawner {
    //crucible always spawns in nebula with the most planets

    public static final float MIN_RADIUS_AROUND_CRUCIBLE = 7000f;
    public static final float DIST_PER_FITTING_ATTEMPT = 700f;
    public static final float MAGNETIC_FIELD_WIDTH = 300f;
    public static final float CATAPULT_ADDITIONAL_ORBIT_DIST = 45f;

    public static void removeFromCurrentLoc(){
        StarSystemAPI targetSystem = (StarSystemAPI) Global.getSector().getPlayerFleet().getContainingLocation();
        List<SectorEntityToken> tokens = new ArrayList<>();
        tokens.addAll(targetSystem.getEntitiesWithTag("IndEvo_crucible"));
        tokens.addAll(targetSystem.getEntitiesWithTag("IndEvo_crucible_bottom"));
        tokens.addAll(targetSystem.getEntitiesWithTag("IndEvo_yeetopult"));
        tokens.addAll(targetSystem.getEntitiesWithTag("IndEvo_crucible_arm"));

        for (SectorEntityToken t : targetSystem.getCustomEntities()) {
            if (t.getCustomEntitySpec().getId().equals("IndEvo_crucible_bottom")) tokens.add(t);
        }

        for (CampaignTerrainAPI terrain : targetSystem.getTerrainCopy()){
            if(terrain.getPlugin() instanceof MagneticFieldTerrainPlugin) if (Misc.getDistance(terrain.getLocation(), targetSystem.getEntitiesWithTag("IndEvo_crucible").get(0).getLocation()) < 500f) {
                ModPlugin.log("Adding the stupid fucking mag field");
                tokens.add(terrain);
            }
        }

        for (SectorEntityToken t : tokens) targetSystem.removeEntity(t);
        //runcode indevo.exploration.crucible.CrucibleSpawner.removeFromCurrentLoc();
    }

    public static void spawnInCurrentLoc(){
        StarSystemAPI targetSystem = (StarSystemAPI) Global.getSector().getPlayerFleet().getContainingLocation();
        if (targetSystem == null) return;
        Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

        SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc);
        spawnCatapults(crucible);

        //runcode indevo.exploration.crucible.CrucibleSpawner.spawnInCurrentLoc();
    }

    public static void spawn() {
        StarSystemAPI targetSystem = getTargetSystem();
        if (targetSystem == null) return;
        Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

        SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc);
        spawnCatapults(crucible);
        ModPlugin.log("Spawned Crucible in " + targetSystem.getName() + " --- " + targetSystem.getBaseName());
    }

    private static Vector2f getSpawnLoc(StarSystemAPI targetSystem){
        //get loc away from all planets
        List<PlanetAPI> planets = targetSystem.getPlanets();
        Vector2f spawnLoc = null;
        float radius = 0f;

        //we just check alongside a circle that we increase by 500 units each time, checking every 500 px (scale with radius) times per increment to see if there's a fitting location
        while (spawnLoc == null) {
            float circumference = (float) (2f * Math.PI * radius);
            int positions = (int) Math.ceil(circumference / DIST_PER_FITTING_ATTEMPT);
            float increment = 360f / positions;

            for (int i = 1; i < positions; i++){
                Vector2f check = MathUtils.getPointOnCircumference(new Vector2f(0, 0), radius, increment * i);
                boolean tooClose = false;
                for (PlanetAPI planet : planets) {
                    if (Misc.getDistance(planet.getLocation(), check) < MIN_RADIUS_AROUND_CRUCIBLE){
                        tooClose = true;
                        break;
                    }
                }

                if (!tooClose) {
                    spawnLoc = check;
                    break;
                }
            }

            radius += DIST_PER_FITTING_ATTEMPT;
        }

        return spawnLoc;
    }

    private static StarSystemAPI getTargetSystem() {
        int planetAmt = 0;
        int oldPlanetAmt = 0;
        StarSystemAPI nebulaSystem = null;
        //StarSystemAPI oldNebulaSystem = null;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.isNebula()) continue;

            int amt = system.getPlanets().size();

            //any age
            if (amt > planetAmt) {
                planetAmt = amt;
                nebulaSystem = system;
            }

            //old
           /* if (system.getConstellation().getAge() == StarAge.OLD) {
                if (amt > oldPlanetAmt) {
                    oldPlanetAmt = amt;
                    oldNebulaSystem = system;
                }
            }*/
        }

        //we prefer old nebula
        //no we don't
        //StarSystemAPI targetSystem = oldNebulaSystem != null && oldPlanetAmt >= 2 ? oldNebulaSystem : nebulaSystem;
        return nebulaSystem;
    }

    public static SectorEntityToken spawnCrucible(LocationAPI loc, Vector2f pos) {
        SectorEntityToken bottom = loc.addCustomEntity(Misc.genUID(), null, "IndEvo_crucible_bottom", null, null);
        SectorEntityToken top = loc.addCustomEntity(Misc.genUID(), null, "IndEvo_crucible_top", null, null);

        PlanetAPI sun = ((StarSystemAPI) loc).getStar();
        if (sun != null && !loc.isNebula()) {
            float adjustedOrbitDur = Math.min(364f, 31f / (1000f / Misc.getDistance(pos, sun.getLocation())));
            bottom.setCircularOrbit(sun, Misc.getAngleInDegrees(pos, sun.getLocation()), Misc.getDistance(pos, sun.getLocation()), adjustedOrbitDur);
            top.setCircularOrbit(sun, Misc.getAngleInDegrees(pos, sun.getLocation()), Misc.getDistance(pos, sun.getLocation()), adjustedOrbitDur);
        }  else {
            bottom.setLocation(pos.x, pos.y);
            top.setLocation(pos.x, pos.y);
        }

        CrucibleStationEntityPlugin.generateMagneticField(top, 1f, MAGNETIC_FIELD_WIDTH);

        CampaignTerrainAPI nebula = null;
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            if (terrain.getPlugin() instanceof NebulaTerrainPlugin) nebula = terrain;
            break;
        }

        if (nebula != null) {
            NebulaTerrainPlugin nebulaPlugin = (NebulaTerrainPlugin) nebula.getPlugin();
            NebulaEditor editor = new NebulaEditor(nebulaPlugin);
            editor.clearArc(pos.x, pos.y, 0, MAGNETIC_FIELD_WIDTH * 2, 0f, 360f);
        }

        return top;
    }

    public static void spawnCatapults(SectorEntityToken crucible){
        List<PlanetAPI> planets = crucible.getContainingLocation().getPlanets();
        WeightedRandomPicker<Color> colourPicker = YeetopultColourList.getWeightedRandomPicker();
        List<SectorEntityToken> crucibleBoundCatapults = new ArrayList<>();

        for (PlanetAPI planet : planets){
            if (planet.isStar()) continue;
            if (planet.getOrbitFocus() != null
                    && (planet.getOrbitFocus() instanceof PlanetAPI && ((PlanetAPI) planet.getOrbitFocus()).isGasGiant())) continue; //if planet is orbiting gas giant only the giant gets a catapult

            //add a catapult to the planet and another one on the crucible
            Color color = colourPicker.pickAndRemove();
            SectorEntityToken crucibleCatapult = getCatapult(crucible, planet, color);
            crucibleBoundCatapults.add(crucibleCatapult);
            SectorEntityToken catapult = getCatapult(planet, crucibleCatapult, color);

            float angle = Misc.getAngleInDegrees(planet.getLocation(), crucible.getLocation());
            float orbitRadius = planet.getRadius() + Math.max(100f, planet.getRadius() * 0.2f);
            catapult.setCircularOrbit(planet, angle, orbitRadius, orbitRadius / 10f);
        }

        //add crucible catapults
        //spawn in a circle around the crucible, fuck alignment
        int amt = crucibleBoundCatapults.size();
        float angleSpacing = 360f / amt;
        float orbitRadius = crucible.getRadius() + CATAPULT_ADDITIONAL_ORBIT_DIST;
        int i = 1;

        for (SectorEntityToken catapult : crucibleBoundCatapults) {
            //"IndEvo_crucible_arm"
            crucible.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_crucible_arm", null, new CrucibleArmEntityPlugin.CrucibleArmEntityPluginParams(catapult, crucible));
            catapult.setCircularOrbit(crucible, angleSpacing * i, orbitRadius, orbitRadius / 10f);
            i++;
        }
    }

    public static SectorEntityToken getCatapult(SectorEntityToken focus, SectorEntityToken target, Color color){
        return focus.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_yeetopult", null, new YeetopultEntityPlugin.YeetopultParams(color, target.getId()));
    }
}
