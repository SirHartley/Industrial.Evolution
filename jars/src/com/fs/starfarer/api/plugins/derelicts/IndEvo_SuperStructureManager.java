package com.fs.starfarer.api.plugins.derelicts;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_SuperStructureManager {

    public static void addDysonSwarmEntity(StarSystemAPI system) {
        //second Sun
        system.setType(StarSystemGenerator.StarSystemType.BINARY_CLOSE);

        PlanetAPI star_b = system.addPlanet(Misc.genUID(), system.getCenter(), "Dyson Core " + Misc.genUID().substring(0, 5), "IndEvo_star_red_dwarf", 270, 200, 2500, 365);
        system.setSecondary(star_b);
        system.addCorona(star_b, 200, 2f, 0.1f, 2f);
        system.addRingBand(star_b, "misc", "rings_dust0", 256f, 3, Color.DARK_GRAY, 256f, 550, 30f, Terrain.RING, null);

        system.autogenerateHyperspaceJumpPoints(false, true);

        //Dyspn Swarm
        PlanetAPI swarm = system.addPlanet(Misc.genUID(), star_b, "Dyson Swarm " + Misc.genUID().substring(0, 5), "IndEvo_dysonSwarm", 180f, 250f, 0f, 0f);
        swarm.getSpec().setUseReverseLightForGlow(true);
        swarm.getMarket().setPrimaryEntity(star_b);

        star_b.addTag("dyson_swarm");
        swarm.addTag("dyson_swarm");

       /* SectorEntityToken overlay = system.addCustomEntity(Misc.genUID(), "Dyson Swarm", "IndEvo_objective_overlay", "invisibleColour", 255f,0f, 0f);
        overlay.setCircularOrbit(star_b, 0f, 0f, 0f);
        overlay.setCustomDescriptionId("int_array");*/

    }


}
