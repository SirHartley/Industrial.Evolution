package com.fs.starfarer.api.gachaStation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.HashMap;
import java.util.Map;

public class IndEvo_GachaStationPlacer {

    public static final String HAS_PLACED_STATIONS = "$IndEvo_hasPlacedStations";
    public static final float AMOUNT_MULT = Global.getSettings().getFloat("IndEvo_AutomatedShipyardNum"); //default 1.5f

    public static void place(){
        if(Global.getSector().getPersistentData().containsKey(HAS_PLACED_STATIONS)) return;

        int amt = (int) Math.ceil(Global.getSector().getEntitiesWithTag(Tags.CORONAL_TAP).size() * AMOUNT_MULT);

        Map<String, Float> starTypes = new HashMap<>();
        starTypes.put("star_red_supergiant", 80f);
        starTypes.put("star_red_giant", 50f);
        starTypes.put("star_red_dwarf", 10f);

        WeightedRandomPicker<StarSystemAPI> systemPicker = new WeightedRandomPicker<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()){
            PlanetAPI p = system.getStar();

            if (p == null) continue;

            String type = p.getTypeId();

            if (type != null && starTypes.containsKey(type)) {
                systemPicker.add(system, starTypes.get(type));
            } else if (type != null) {
                systemPicker.add(system, 0.01f);
            }
        }

        for (int i = 0; i < amt; i++){
            placeStation(systemPicker.pickAndRemove());
        }

        Global.getSector().getPersistentData().put(HAS_PLACED_STATIONS, true);
    }

    public static void placeStation(StarSystemAPI system){
        SectorEntityToken t = system.addCustomEntity(Misc.genUID(), null, "IndEvo_GachaStation", null);
        t.setCircularOrbitPointingDown(system.getStar(), 0f, system.getStar().getRadius() + 200f, 31);

        IndEvo_modPlugin.log("placed GachaStation (Automated Shipyard) at " + system.getName());
    }
}
