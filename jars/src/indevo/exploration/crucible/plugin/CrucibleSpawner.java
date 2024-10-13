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
import indevo.exploration.crucible.entities.*;
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

        ((BaseCrucibleEntityPlugin) crucible.getCustomPlugin()).enable();

        //runcode indevo.exploration.crucible.plugin.CrucibleSpawner.spawnInCurrentLoc();
    }

    public static void spawnInCurrentLocSubUnit(){
        StarSystemAPI targetSystem = (StarSystemAPI) Global.getSector().getPlayerFleet().getContainingLocation();
        if (targetSystem == null) return;
        Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

        boolean subUnit = true;
        SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc, subUnit);
        spawnCatapults(crucible, subUnit);

        ((BaseCrucibleEntityPlugin) crucible.getCustomPlugin()).enable();

        //runcode indevo.exploration.crucible.plugin.CrucibleSpawner.spawnInCurrentLocSubUnit();
    }

    public static void spawn() {
        if (Global.getSector().getPersistentData().containsKey(HAS_PLACED_STATIONS)) return;

        int amt = (int) Math.ceil(Global.getSector().getEntitiesWithTag(Tags.CORONAL_TAP).size() * AMOUNT_MULT);

        if (amt == 0) return;
        spawnCrucible(false); //one per default in a nebula

        for (int i = 0; i < amt; i++) {
            spawnCrucible(true);
        }

        Global.getSector().getPersistentData().put(HAS_PLACED_STATIONS, true);
    }

    private static void spawnCrucible(boolean nonNebulaOnly){
        StarSystemAPI targetSystem = getTargetSystem(nonNebulaOnly);
        if (targetSystem == null) return;
        Vector2f spawnLoc = getSpawnLoc(targetSystem); //no need to nullcheck because it will hang the game if it doesn't find one

        boolean subUnit = false;
        SectorEntityToken crucible = spawnCrucible(targetSystem, spawnLoc, subUnit);
        spawnCatapults(crucible, subUnit);
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

    private static StarSystemAPI getTargetSystem(boolean nonNebulOnly) {
        int planetAmt = 0;
        int oldPlanetAmt = 0;
        StarSystemAPI starSystem = null;
        //StarSystemAPI oldNebulaSystem = null;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if ((nonNebulOnly && system.isNebula()) || !system.getEntitiesWithTag(Ids.TAG_YEETOPULT).isEmpty()) continue;

            int amt = system.getPlanets().size();

            //any age
            if (amt > planetAmt) {
                planetAmt = amt;
                starSystem = system;
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
        return starSystem;
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

        if (subUnit) spawnGearsSubUnit(top);
        else spawnGears(top);

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
            catapult.setFacing(MathUtils.getRandomNumberInRange(0, 360));
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
            catapult.setCircularOrbit(crucible, angleSpacing * i, orbitRadius, orbitRadius);
            catapult.setFacing(MathUtils.getRandomNumberInRange(0, 360));
            i++;
        }
    }

    public static SectorEntityToken getCatapult(SectorEntityToken focus, SectorEntityToken target, Color color, boolean showIcon){
        return focus.getContainingLocation().addCustomEntity(Misc.genUID(), null, showIcon ? "IndEvo_yeetopult" : "IndEvo_yeetopult_no_icon", null, new YeetopultEntityPlugin.YeetopultParams(focus, color, target.getId()));
    }


    public static void spawnGearsSubUnit(SectorEntityToken crucible){
        float outerRim = (crucible.getCustomEntitySpec().getSpriteHeight() * 0.6f) / 2f;
        Color defaultGearColour = new Color(20,15,15,255);
        float defaultOrbitDays = 90f;

        //cluster 1
        SectorEntityToken gMed1 = getGear(crucible, 30f, defaultGearColour, 0.6f);
        SectorEntityToken gMed2 = getGear(crucible, 30f, defaultGearColour, -0.6f);
        SectorEntityToken gSmall1 = getGear(crucible, 18f, defaultGearColour.brighter(), 0.85f);

        gMed1.setCircularOrbit(crucible, 0f, outerRim, defaultOrbitDays);
        gMed2.setCircularOrbit(crucible, -35f, outerRim, defaultOrbitDays);
        gSmall1.setCircularOrbit(crucible, 20f, outerRim + 3f, defaultOrbitDays);

        //cluster 2
        SectorEntityToken gLarge1 = getGear(crucible, 45f, defaultGearColour.darker(), 0.35f);
        SectorEntityToken gMed3 = getGear(crucible, 30f, defaultGearColour, -0.5f);
        SectorEntityToken gSmall2 = getGear(crucible, 20f, defaultGearColour.brighter(), 0.8f);

        gLarge1.setCircularOrbit(crucible, 120f - 20f , outerRim, defaultOrbitDays);
        gMed3.setCircularOrbit(crucible, 120f + 20f , outerRim, defaultOrbitDays);
        gSmall2.setCircularOrbit(crucible, 120f, outerRim + 3f, defaultOrbitDays);

        //cluster 3
        SectorEntityToken gLarge2 = getGear(crucible, 50f, defaultGearColour.darker(), 0.4f);
        SectorEntityToken gSmall3 = getGear(crucible, 25f, defaultGearColour.brighter(), -1f);

        gLarge2.setCircularOrbit(crucible, 120f + 100f, outerRim, defaultOrbitDays);
        gSmall3.setCircularOrbit(crucible, 120f + 100f + 20f, outerRim + 3f, defaultOrbitDays);
    }

    public static void spawnGears(SectorEntityToken crucible){
        float outerRim = (crucible.getCustomEntitySpec().getSpriteHeight() * 0.55f) / 2f;
        Color defaultGearColour = new Color(15,10,10,255);
        float defaultOrbitDays = 120f;

        //cluster 1
        SectorEntityToken gLarge1 = getGear(crucible, 80f, defaultGearColour.darker(), 0.2f);
        SectorEntityToken gMed1 = getGear(crucible, 65f, defaultGearColour, -0.4f);
        SectorEntityToken gMed2 = getGear(crucible, 65f, defaultGearColour, -0.4f);
        SectorEntityToken gSmall1 = getGear(crucible, 40f, defaultGearColour.brighter(), 0.8f);

        gLarge1.setCircularOrbit(crucible, 0f, outerRim, defaultOrbitDays);
        gMed1.setCircularOrbit(crucible, 45f, outerRim, defaultOrbitDays);
        gMed2.setCircularOrbit(crucible, -45f, outerRim, defaultOrbitDays);
        gSmall1.setCircularOrbit(crucible, 15f, outerRim, defaultOrbitDays);

        //cluster 2
        SectorEntityToken gMed3 = getGear(crucible, 70f, defaultGearColour, 0.3f);
        SectorEntityToken gMed4 = getGear(crucible, 70f, defaultGearColour, -0.3f);
        SectorEntityToken gSmall2 = getGear(crucible, 45f, defaultGearColour.brighter(), 0.6f);

        gMed3.setCircularOrbit(crucible, 120f - 15f , outerRim, defaultOrbitDays);
        gMed4.setCircularOrbit(crucible, 120f + 20f, outerRim, defaultOrbitDays);
        gSmall2.setCircularOrbit(crucible, 120f, outerRim, defaultOrbitDays);

        //cluster 3
        SectorEntityToken gLarge2 = getGear(crucible, 80f, defaultGearColour.darker(), 0.2f);
        SectorEntityToken gSmall3 = getGear(crucible, 45f, defaultGearColour.brighter(), -1f);
        SectorEntityToken gSmall4 = getGear(crucible, 45f, defaultGearColour.brighter(), -1f);

        gLarge2.setCircularOrbit(crucible, 120f + 100f, outerRim, defaultOrbitDays);
        gSmall3.setCircularOrbit(crucible, 120f + 100f + 17f, outerRim, defaultOrbitDays);
        gSmall4.setCircularOrbit(crucible, 120f + 100f - 17f, outerRim, defaultOrbitDays);

        //reverse top cluster
        outerRim -= 5f;
        defaultGearColour = defaultGearColour.brighter();
        defaultOrbitDays = -60f;
        float offsetAngle = 90f;

        //errant cluster 1
        SectorEntityToken gLarge3 = getGear(crucible, 70f, defaultGearColour.darker(), -0.4f);
        SectorEntityToken gMed5 = getGear(crucible, 60f, defaultGearColour, 0.8f);
        SectorEntityToken gSmall5 = getGear(crucible, 35f, defaultGearColour.brighter(), 2f);

        gLarge3.setCircularOrbit(crucible, offsetAngle + 0f, outerRim, defaultOrbitDays);
        gMed5.setCircularOrbit(crucible, offsetAngle + 45f, outerRim, defaultOrbitDays);
        gSmall5.setCircularOrbit(crucible, offsetAngle + 20f, outerRim + 5f, defaultOrbitDays);

        //errant cluster 2
        offsetAngle += 120f;

        SectorEntityToken gMed6 = getGear(crucible, 55f, defaultGearColour, -0.7f);
        SectorEntityToken gMed7 = getGear(crucible, 55f, defaultGearColour, -0.7f);
        SectorEntityToken gSmall6 = getGear(crucible, 35f, defaultGearColour.brighter(), 1.5f);

        gMed6.setCircularOrbit(crucible, offsetAngle + 0f, outerRim, defaultOrbitDays);
        gMed7.setCircularOrbit(crucible, offsetAngle - 30f, outerRim, defaultOrbitDays);
        gSmall6.setCircularOrbit(crucible, offsetAngle + 5f, outerRim, defaultOrbitDays);

        //errant cluster 3
        offsetAngle += 100f;

        SectorEntityToken gMed8 = getGear(crucible, 65f, defaultGearColour, -1f);
        SectorEntityToken gSmall7 = getGear(crucible, 40f, defaultGearColour.brighter(), 3f);
        SectorEntityToken gSmall8 = getGear(crucible, 40f, defaultGearColour.brighter(), -3f);

        gMed8.setCircularOrbit(crucible, offsetAngle + 0f, outerRim, defaultOrbitDays);
        gSmall7.setCircularOrbit(crucible, offsetAngle - 15f, outerRim + 10f, defaultOrbitDays);
        gSmall8.setCircularOrbit(crucible, offsetAngle - 40f, outerRim + 10f, defaultOrbitDays);

    }

    public static SectorEntityToken getGear(SectorEntityToken focus, float size, Color colour, float speed){
        return focus.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_crucible_gear", null, size, size, size,
                new CrucibleGearEntityPlugin.CrucibleGearParams(
                        colour,
                        speed,
                        size
                ));
    }
}
