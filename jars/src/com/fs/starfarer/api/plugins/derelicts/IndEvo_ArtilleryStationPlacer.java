package com.fs.starfarer.api.plugins.derelicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.scripts.IndEvo_DerelictArtilleryStationScript;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_WatchtowerEntityPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.conditions.IndEvo_ArtilleryStationCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidBeltTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.RingSystemTerrainPlugin;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.fs.starfarer.api.artilleryStation.scripts.IndEvo_DerelictArtilleryStationScript.TYPE_KEY;

public class IndEvo_ArtilleryStationPlacer {

    public static void placeCoreWorldArtilleries(){
        if (Global.getSector().getEconomy().getMarket("culann") == null
                || !Global.getSettings().getBoolean("Enable_IndEvo_Artillery")
                || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedArtilleries")) return;

        MarketAPI m = Global.getSector().getEconomy().getMarket("eochu_bres");
        IndEvo_DerelictArtilleryStationScript.addDerelictArtyToPlanet(m.getPrimaryEntity(), true);
        placeWatchtowers(m.getStarSystem(), Factions.TRITACHYON);
        m.addIndustry(IndEvo_ids.ARTILLERY_RAILGUN);
        m.getIndustry(IndEvo_ids.ARTILLERY_RAILGUN).setAICoreId(Commodities.ALPHA_CORE);

        m = Global.getSector().getEconomy().getMarket("chicomoztoc");
        IndEvo_DerelictArtilleryStationScript.addDerelictArtyToPlanet(m.getPrimaryEntity(), true);
        placeWatchtowers(m.getStarSystem(), Factions.HEGEMONY);
        m.addIndustry(IndEvo_ids.ARTILLERY_MORTAR);

        m = Global.getSector().getEconomy().getMarket("kazeron");
        IndEvo_DerelictArtilleryStationScript.addDerelictArtyToPlanet(m.getPrimaryEntity(), true);
        placeWatchtowers(m.getStarSystem(), Factions.PERSEAN);
        m.addIndustry(IndEvo_ids.ARTILLERY_MISSILE);
        m.getIndustry(IndEvo_ids.ARTILLERY_MISSILE).setAICoreId(Commodities.GAMMA_CORE);

        m = Global.getSector().getEconomy().getMarket("sindria");
        IndEvo_DerelictArtilleryStationScript.addDerelictArtyToPlanet(m.getPrimaryEntity(), true);
        placeWatchtowers(m.getStarSystem(), Factions.DIKTAT);
        m.addIndustry(IndEvo_ids.ARTILLERY_MISSILE);

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

                    IndEvo_DerelictArtilleryStationScript.addDerelictArtyToPlanet(p, false);
                    placeWatchtowers(s, IndEvo_ids.DERELICT);

                    IndEvo_modPlugin.log("Placed artillery at " + p.getName() + " system: " + s.getName());
                    currentCount++;

                    continue OUTER;
                }
            }
        }

        IndEvo_modPlugin.log("Placed " + currentCount + " Artillery Stations");
        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedDerelictArtilleries", true);
    }

    public static void placeWatchtowers(StarSystemAPI system, String factionId){
        float minGap = 100f;
        Random random = new Random();
        FactionAPI faction = Global.getSector().getFaction(factionId);

        //jump points
        for (SectorEntityToken t : system.getJumpPoints()){
            IndEvo_WatchtowerEntityPlugin.spawn(t, Global.getSector().getFaction(factionId));
        }

        //gas giants
        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.isStar()) continue;
            if (!planet.isGasGiant()) continue;

            float ow = BaseThemeGenerator.getOrbitalRadius(planet);
            List<BaseThemeGenerator.OrbitGap> gaps = BaseThemeGenerator.findGaps(planet, 100f, 100f + ow + minGap, minGap);
            BaseThemeGenerator.EntityLocation loc = createLocationAtRandomGap(random, planet, gaps, BaseThemeGenerator.LocationType.GAS_GIANT_ORBIT);
            if (loc != null) {
                SectorEntityToken t = system.addCustomEntity(Misc.genUID(), faction.getDisplayName() + " Watchtower", "IndEvo_Watchtower", faction.getId(),null);
                t.setOrbit(loc.orbit);
                if (Misc.getMarketsInLocation(system).isEmpty()) MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);
            }
        }

        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (terrain.hasTag(Tags.ACCRETION_DISK)) continue;
            //if (terrain.getOrbit() == null || (terrain.getOrbit().getFocus() != null && !terrain.getOrbit().getFocus().isStar())) continue; //we only do debris fields around the sun

            CampaignTerrainPlugin plugin = terrain.getPlugin();

            if (plugin instanceof RingSystemTerrainPlugin) {
                RingSystemTerrainPlugin ring = (RingSystemTerrainPlugin) plugin;
                float start = ring.params.middleRadius - ring.params.bandWidthInEngine / 2f;
                List<BaseThemeGenerator.OrbitGap> gaps = BaseThemeGenerator.findGaps(terrain,
                        start - 100f, start + ring.params.bandWidthInEngine + 100f, minGap);

                BaseThemeGenerator.EntityLocation loc = createLocationAtRandomGap(random, terrain, gaps, BaseThemeGenerator.LocationType.IN_RING);
                if (loc != null) {
                    SectorEntityToken t = system.addCustomEntity(Misc.genUID(), faction.getDisplayName() + " Watchtower", "IndEvo_Watchtower", faction.getId(),null);
                    t.setOrbit(loc.orbit);
                    if (Misc.getMarketsInLocation(system).isEmpty()) MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);
                    break;
                }
            }
        }
    }

    private static BaseThemeGenerator.EntityLocation createLocationAtRandomGap(Random random, SectorEntityToken center, List<BaseThemeGenerator.OrbitGap> gaps, BaseThemeGenerator.LocationType type) {
        if (gaps.isEmpty()) return null;
        if (random == null) random = StarSystemGenerator.random;
        WeightedRandomPicker<BaseThemeGenerator.OrbitGap> picker = new WeightedRandomPicker<BaseThemeGenerator.OrbitGap>(random);
        picker.addAll(gaps);
        BaseThemeGenerator.OrbitGap gap = picker.pick();
        return createLocationAtGap(random, center, gap, type);
    }

    private static BaseThemeGenerator.EntityLocation createLocationAtGap(Random random, SectorEntityToken center, BaseThemeGenerator.OrbitGap gap, BaseThemeGenerator.LocationType type) {
        if (gap != null) {
            if (random == null) random = StarSystemGenerator.random;
            BaseThemeGenerator.EntityLocation loc = new BaseThemeGenerator.EntityLocation();
            loc.type = type;
            float orbitRadius = gap.start + (gap.end - gap.start) * (0.25f + 0.5f * random.nextFloat());
            float orbitDays = orbitRadius / (20f + random.nextFloat() * 5f);

            loc.orbit = Global.getFactory().createCircularOrbitWithSpin(center,
                    random.nextFloat() * 360f, orbitRadius, orbitDays, random.nextFloat() * 10f + 1f);
            return loc;
        }
        return null;
    }
}
