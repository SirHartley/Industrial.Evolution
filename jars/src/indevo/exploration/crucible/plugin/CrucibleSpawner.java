package indevo.exploration.crucible.plugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.NebulaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.crucible.YeetopultColourList;
import indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin;
import indevo.exploration.crucible.entities.CrucibleArmEntityPlugin;
import indevo.exploration.crucible.entities.CrucibleStationEntityPlugin;
import indevo.exploration.crucible.entities.YeetopultEntityPlugin;
import indevo.ids.Ids;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CrucibleSpawner {
    //crucible always spawns in nebula with the most planets

    public static final float MIN_RADIUS_AROUND_CRUCIBLE = 7000f;
    public static final float DIST_PER_FITTING_ATTEMPT = 700f;
    public static final float MAGNETIC_FIELD_WIDTH = 300f;
    public static final float CATAPULT_ADDITIONAL_ORBIT_DIST = 45f;
    public static final float CATAPULT_SUBUNIT_ADDITIONAL_ORBIT_DIST = 53f;


    public static final String HAS_PLACED_STATIONS = "$IndEvo_hasPlacedCrucibles";
    public static final float AMOUNT_MULT = Settings.getFloat(Settings.CRUCIBLE_NUM); //default 0.5f

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
        //runcode indevo.exploration.crucible.plugin.CrucibleSpawner.removeFromCurrentLoc();
    }

    public static void spawnInCurrentLoc(){
        StarSystemAPI targetSystem = (StarSystemAPI) Global.getSector().getPlayerFleet().getContainingLocation();
        if (targetSystem == null) return;
        Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

        boolean subUnit = false;
        SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc, subUnit);
        spawnCatapults(crucible, subUnit);

        //runcode indevo.exploration.crucible.plugin.CrucibleSpawner.spawnInCurrentLoc();
    }

    public static void spawnInCurrentLocSubUnit(){
        StarSystemAPI targetSystem = (StarSystemAPI) Global.getSector().getPlayerFleet().getContainingLocation();
        if (targetSystem == null) return;
        Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

        boolean subUnit = true;
        SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc, subUnit);
        spawnCatapults(crucible, subUnit);

        //runcode indevo.exploration.crucible.plugin.CrucibleSpawner.spawnInCurrentLocSubUnit();
    }



    public static void spawn() {
        if (Global.getSector().getPersistentData().containsKey(HAS_PLACED_STATIONS)) return;

        int amt = (int) Math.ceil(Global.getSector().getEntitiesWithTag(Tags.CORONAL_TAP).size() * AMOUNT_MULT);

        for (int i = 0; i < amt; i++) {
            StarSystemAPI targetSystem = getTargetSystem();
            if (targetSystem == null) continue;
            Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

            boolean subUnit = false;
            SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc, subUnit);
            spawnCatapults(crucible, subUnit);
            ModPlugin.log("Spawned Crucible in " + targetSystem.getName() + " --- " + targetSystem.getBaseName());
        }

        Global.getSector().getPersistentData().put(HAS_PLACED_STATIONS, true);
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
            if (!system.isNebula() || !system.getEntitiesWithTag(Ids.TAG_YEETOPULT).isEmpty()) continue;

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

    public static SectorEntityToken spawnCrucible(LocationAPI loc, Vector2f pos, boolean subUnit) {
        SectorEntityToken bottom = loc.addCustomEntity(Misc.genUID(), null, (subUnit ? "IndEvo_sub_crucible_bottom" : "IndEvo_crucible_bottom"), null, null);
        SectorEntityToken top = loc.addCustomEntity(Misc.genUID(), null, (subUnit ? "IndEvo_sub_crucible_top" : "IndEvo_crucible_top"), null, null);
        SectorEntityToken scaffold = null;
        if (!subUnit) {
            scaffold = loc.addCustomEntity(Misc.genUID(), null, "IndEvo_crucible_scaffold", null, null);
            scaffold.setFacing(MathUtils.getRandomNumberInRange(0, 360));
        }

        PlanetAPI sun = ((StarSystemAPI) loc).getStar();
        if (sun != null && !loc.isNebula()) {
            float adjustedOrbitDur = Math.min(364f, 31f / (1000f / Misc.getDistance(pos, sun.getLocation())));
            bottom.setCircularOrbit(sun, Misc.getAngleInDegrees(pos, sun.getLocation()), Misc.getDistance(pos, sun.getLocation()), adjustedOrbitDur);
            if (scaffold != null) scaffold.setCircularOrbit(sun, Misc.getAngleInDegrees(pos, sun.getLocation()), Misc.getDistance(pos, sun.getLocation()), adjustedOrbitDur);
            top.setCircularOrbit(sun, Misc.getAngleInDegrees(pos, sun.getLocation()), Misc.getDistance(pos, sun.getLocation()), adjustedOrbitDur);
            top.setFacing(MathUtils.getRandomNumberInRange(0, 360));
        }  else {
            bottom.setLocation(pos.x, pos.y);
            if (scaffold != null) scaffold.setLocation(pos.x, pos.y);
            top.setLocation(pos.x, pos.y);
            top.setFacing(MathUtils.getRandomNumberInRange(0, 360));
        }

        CampaignTerrainAPI nebula = null;
        for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
            if (terrain.getPlugin() instanceof NebulaTerrainPlugin) nebula = terrain;
            break;
        }

        if (nebula != null) {
            NebulaTerrainPlugin nebulaPlugin = (NebulaTerrainPlugin) nebula.getPlugin();
            NebulaEditor editor = new NebulaEditor(nebulaPlugin);
            editor.clearArc(pos.x, pos.y, 1, MAGNETIC_FIELD_WIDTH * 2, 0f, 360f);
        }

        top.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "IndEvo_Haplogynae_derelict_theme");

        return top;
    }

    public static void spawnCatapults(SectorEntityToken crucible, boolean subUnit){
        List<PlanetAPI> planets = crucible.getContainingLocation().getPlanets();
        WeightedRandomPicker<Color> colourPicker = YeetopultColourList.getWeightedRandomPicker();
        List<SectorEntityToken> crucibleBoundCatapults = new ArrayList<>();

        for (PlanetAPI planet : planets){
            if (planet.isStar()) continue;
            if (planet.getOrbitFocus() != null
                    && (planet.getOrbitFocus() instanceof PlanetAPI && ((PlanetAPI) planet.getOrbitFocus()).isGasGiant())) continue; //if planet is orbiting gas giant only the giant gets a catapult

            //add a catapult to the planet and another one on the crucible
            Color color = colourPicker.pickAndRemove();
            SectorEntityToken crucibleCatapult = getCatapult(crucible, planet, color, false);
            crucibleBoundCatapults.add(crucibleCatapult);
            SectorEntityToken catapult = getCatapult(planet, crucibleCatapult, color, true);

            ((YeetopultEntityPlugin) crucibleCatapult.getCustomPlugin()).setPairedCatapult(catapult);
            ((YeetopultEntityPlugin) catapult.getCustomPlugin()).setPairedCatapult(crucibleCatapult);

            float angle = Misc.getAngleInDegrees(planet.getLocation(), crucible.getLocation());
            float orbitRadius = planet.getRadius() + Math.max(100f, planet.getRadius() * 0.2f);
            catapult.setCircularOrbit(planet, angle, orbitRadius, orbitRadius / 10f);
        }

        //add crucible catapults
        //spawn in a circle around the crucible, fuck alignment
        int amt = crucibleBoundCatapults.size();
        float angleSpacing = 360f / amt;
        float orbitRadius = crucible.getRadius() + (subUnit ? CATAPULT_SUBUNIT_ADDITIONAL_ORBIT_DIST : CATAPULT_ADDITIONAL_ORBIT_DIST);
        int i = 1;

        //crucible arms
        for (SectorEntityToken catapult : crucibleBoundCatapults) {
            //"IndEvo_crucible_arm"
            crucible.getContainingLocation().addCustomEntity(Misc.genUID(), null, (subUnit ? "IndEvo_sub_crucible_arm" : "IndEvo_crucible_arm"), null, new CrucibleArmEntityPlugin.CrucibleArmEntityPluginParams(catapult, crucible));
     /*       Vector2f loc = MathUtils.getPointOnCircumference(crucible.getLocation(), orbitRadius, angleSpacing * i);
            catapult.setLocation(loc.x, loc.y);
            catapult.setFacing(MathUtils.getRandomNumberInRange(0, 360));
*/
            catapult.setCircularOrbit(crucible, angleSpacing * i, orbitRadius, orbitRadius / 10f);
            i++;
        }
    }

    public static SectorEntityToken getCatapult(SectorEntityToken focus, SectorEntityToken target, Color color, boolean showIcon){
        return focus.getContainingLocation().addCustomEntity(Misc.genUID(), null, showIcon ? "IndEvo_yeetopult" : "IndEvo_yeetopult_no_icon", null, new YeetopultEntityPlugin.YeetopultParams(focus, color, target.getId()));
    }
}
