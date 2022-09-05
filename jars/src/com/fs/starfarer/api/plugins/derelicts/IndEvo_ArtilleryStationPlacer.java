package com.fs.starfarer.api.plugins.derelicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.scripts.IndEvo_DerelictArtilleryStationScript;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.conditions.IndEvo_ArtilleryStationCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.Random;

import static com.fs.starfarer.api.artilleryStation.scripts.IndEvo_DerelictArtilleryStationScript.TYPE_KEY;

public class IndEvo_ArtilleryStationPlacer {

    public static void placeCoreWorldArtilleries(){
        if (Global.getSector().getEconomy().getMarket("culann") == null
                || !Global.getSettings().getBoolean("Enable_IndEvo_Artillery")
                || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedArtilleries")) return;

        MarketAPI m = Global.getSector().getEconomy().getMarket("eochu_bres");
        m.addIndustry(IndEvo_ids.ARTILLERY_RAILGUN);
        m.getIndustry(IndEvo_ids.ARTILLERY_RAILGUN).setAICoreId(Commodities.ALPHA_CORE);
        m.addTag(IndEvo_ids.TAG_ARTILLERY_STATION);

        m = Global.getSector().getEconomy().getMarket("chicomoztoc");
        m.addIndustry(IndEvo_ids.ARTILLERY_MORTAR);
        m.addTag(IndEvo_ids.TAG_ARTILLERY_STATION);

        m = Global.getSector().getEconomy().getMarket("kazeron");
        m.addIndustry(IndEvo_ids.ARTILLERY_MISSILE);
        m.getIndustry(IndEvo_ids.ARTILLERY_MISSILE).setAICoreId(Commodities.GAMMA_CORE);
        m.addTag(IndEvo_ids.TAG_ARTILLERY_STATION);

        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedArtilleries", true);
    }

    public static void placeDerelictArtilleries(){
        if(!Global.getSettings().getBoolean("Enable_IndEvo_Artillery") || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedDerelictArtilleries")) return;

        int currentCount = 0;
        Random r = new Random();

        OUTER: for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null || s.getPlanets().isEmpty() || !Misc.getMarketsInLocation(s).isEmpty()) continue;

            float baseMod = 0.01f;
            if (s.getTags().contains(Tags.THEME_REMNANT_SUPPRESSED)) baseMod += 0.02f;
            if (s.getTags().contains(Tags.THEME_REMNANT_RESURGENT)) baseMod += 0.1f;
            if (s.getTags().contains(Tags.THEME_DERELICT_CRYOSLEEPER)) baseMod += 0.05f;
            if (s.getTags().contains(Tags.THEME_RUINS)) baseMod += 0.05f;

            boolean hasRemnantStation = false;
            for (CampaignFleetAPI fleet : s.getFleets()){
                if (fleet.isStationMode() && fleet.getFaction().getId().equals(Factions.REMNANTS)) hasRemnantStation = true;
            }

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null) continue;

                float planetMod = baseMod;
                planetMod += TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.2f;

                if (r.nextFloat() < planetMod){
                    if (hasRemnantStation) p.getMarket().getMemoryWithoutUpdate().set(TYPE_KEY, IndEvo_ArtilleryStationEntityPlugin.TYPE_MISSILE);
                    IndEvo_DerelictArtilleryStationScript.addDerelictArtyToPlanet(p);
                    p.getMarket().addCondition(IndEvo_ArtilleryStationCondition.ID);

                    IndEvo_modPlugin.log("Placed artillery at " + p.getName() + " system: " + s.getName());
                    currentCount++;

                    continue OUTER;
                }
            }
        }

        IndEvo_modPlugin.log("Placed " + currentCount + " Artillery Stations");
        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedDerelictArtilleries", true);
    }
}
