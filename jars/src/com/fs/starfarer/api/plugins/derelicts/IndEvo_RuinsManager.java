package com.fs.starfarer.api.plugins.derelicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.conditions.IndEvo_RuinsCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.econ.impl.derelicts.IndEvo_Ruins.INDUSTRY_ID_MEMORY_KEY;
import static com.fs.starfarer.api.impl.campaign.ids.Tags.*;

public class IndEvo_RuinsManager {
    private static void log(String Text) {Global.getLogger(IndEvo_RuinsManager.class).info(Text);
    }
    public static void placeRuinedInfrastructure() {
        if (!Global.getSettings().getBoolean("Enable_Indevo_Derelicts")) return;

        float amount = 0;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (Global.getSector().getEconomy().getStarSystemsWithMarkets().contains(s)) continue;

            if (s == null || s.getPlanets().isEmpty()) continue;
            boolean ruinsTheme = s.getTags().contains(THEME_RUINS) || s.getTags().contains(THEME_DERELICT);

            int currentCount = 0;

            //iterate through the planets
            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null || TechMining.getTechMiningRuinSizeModifier(p.getMarket()) <= 0f)
                    continue;

                float chance = TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.5f;
                chance *= ruinsTheme ? 1.5 : 1;
                chance /= 1 + (currentCount * 0.3f);

                Random r = new Random(Misc.getSalvageSeed(p));
                if (r.nextFloat() <= chance) {
                    p.getMarket().addCondition(IndEvo_ids.COND_INFRA);
                    log(IndEvo_ids.COND_INFRA.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName());
                    currentCount++;
                    amount++;
                }
            }
        }

        log("Total ruined Infra in sector: " + amount);
    }

    public static class ResolveRuinsToUpgradeListener implements SurveyPlanetListener {

        public static void register(){
            Global.getSector().getListenerManager().addListener(new ResolveRuinsToUpgradeListener(), true);
        }

        @Override
        public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
            if (planet.getMarket().hasCondition(IndEvo_ids.COND_RUINS)
                    && !planet.getMarket().getMemoryWithoutUpdate().contains(INDUSTRY_ID_MEMORY_KEY)) {
                IndEvo_RuinsManager.setUpgradeSpec(planet);
            }
        }
    }

    public static class DerelictRuinsPlacer implements PlayerColonizationListener {

        public static void register(){
            Global.getSector().getListenerManager().addListener(new DerelictRuinsPlacer(), true);
        }

        @Override
        public void reportPlayerColonizedPlanet(PlanetAPI planetAPI) {
            MarketAPI m = planetAPI.getMarket();

            if(m.hasCondition(IndEvo_ids.COND_RUINS)){
                IndEvo_RuinsCondition cond = (IndEvo_RuinsCondition) m.getCondition(IndEvo_ids.COND_RUINS).getPlugin();
                cond.addRuinsIfNeeded();
            }
        }

        @Override
        public void reportPlayerAbandonedColony(MarketAPI marketAPI) {

        }
    }

    public static void forceCleanCoreRuins() {
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null || s.getPlanets().isEmpty()) continue;

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null) continue;

                MarketAPI pMarket = p.getMarket();

                if (pMarket.hasCondition(IndEvo_ids.COND_RUINS)) {
                    if (Misc.getMarketsInLocation(p.getContainingLocation()).size() > 0
                            || s.getTags().contains(THEME_CORE)
                            || s.getTags().contains(THEME_CORE_POPULATED)
                            || s.getTags().contains(THEME_CORE_UNPOPULATED)) {

                        if (!pMarket.getFactionId().equals("player")) {
                            pMarket.removeCondition(IndEvo_ids.COND_RUINS);

                            if (pMarket.isPlanetConditionMarketOnly()) continue;

                            pMarket.removeIndustry(IndEvo_ids.RUINS, null, false);
                            pMarket.removeIndustry(IndEvo_ids.HULLFORGE, null, false);
                            pMarket.removeIndustry(IndEvo_ids.DECONSTRUCTOR, null, false);
                            pMarket.removeIndustry(IndEvo_ids.RIFTGEN, null, false);
                            pMarket.removeIndustry(IndEvo_ids.LAB, null, false);
                        }
                    }
                }
            }
        }
    }

    public static void placeIndustrialRuins() {
        boolean forbidden = !Global.getSettings().getBoolean("Enable_Indevo_Derelicts");

        float amount = 0;
        float total = 0;

        //count ruinsConditions + clean all conditions from planets that are not remnant themed
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null || s.getPlanets().isEmpty()) continue;

            Random r;
            float mod = 0.1f;
            boolean remnant = s.getTags().contains(THEME_REMNANT);
            if (s.getTags().contains(Tags.THEME_REMNANT_SUPPRESSED)) mod = 0.3f;
            if (s.getTags().contains(Tags.THEME_REMNANT_RESURGENT)) mod = 0.5f;
            int currentCount = 0;

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null) continue;

                MarketAPI m = p.getMarket();

                if (p.getTags().contains(IndEvo_ids.TAG_NEVER_REMOVE_RUINS)) {
                    if (!m.hasCondition(IndEvo_ids.COND_RUINS)) m.addCondition(IndEvo_ids.COND_RUINS);
                    seedRuinsIfNeeded(p);
                }

                if (m.hasCondition(IndEvo_ids.COND_RUINS)) {
                    if (forbidden || !remnant || Misc.getMarketsInLocation(p.getContainingLocation()).size() > 0) {
                        m.removeCondition(IndEvo_ids.COND_RUINS);
                    } else {
                        switch (currentCount) {
                            case 0:
                                r = new Random();
                                if (r.nextFloat() < mod) {
                                    currentCount++;
                                    seedRuinsIfNeeded(p);
                                    setUpgradeSpec(p);

                                    log(IndEvo_ids.COND_RUINS.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName());
                                    amount++;
                                } else m.removeCondition(IndEvo_ids.COND_RUINS);
                                break;

                            case 1:
                                r = new Random();
                                if (r.nextFloat() < (mod * 0.70f)) {
                                    currentCount++;
                                    seedRuinsIfNeeded(p);
                                    setUpgradeSpec(p);

                                    log(IndEvo_ids.COND_RUINS.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName() + " - Second Planet");
                                    amount++;
                                } else m.removeCondition(IndEvo_ids.COND_RUINS);
                                break;
                            default:
                                m.removeCondition(IndEvo_ids.COND_RUINS);
                        }
                    }
                }
            }
        }

        log("Total RuinConditions found in sector: " + amount);
        log("Total Remnant themed planets found in sector: " + total);
    }

    public static void setUpgradeSpec(PlanetAPI planet) {
        MarketAPI market = planet.getMarket();
        if (market == null) return;

        //if the system has a hullForge/decon, this will always resolve to the opposite
        //if system has another unset cond, resolve into either decon or forge
        for (PlanetAPI p : market.getStarSystem().getPlanets()) {
            MarketAPI m = p.getMarket();

            if (m == null || m.getId().equals(market.getId())) continue;

            MemoryAPI localMem = m.getMemoryWithoutUpdate();
            if (m.hasCondition(IndEvo_ids.COND_RUINS)) {
                if (localMem.contains(INDUSTRY_ID_MEMORY_KEY)) {
                    String id = localMem.getString(INDUSTRY_ID_MEMORY_KEY);

                    if(id.equals(IndEvo_ids.DECONSTRUCTOR) || id.equals(IndEvo_ids.HULLFORGE)){
                        id = id.equals(IndEvo_ids.DECONSTRUCTOR) ? IndEvo_ids.HULLFORGE : IndEvo_ids.DECONSTRUCTOR;
                        market.getMemoryWithoutUpdate().set(INDUSTRY_ID_MEMORY_KEY, id);
                        return;
                    }
                }
            }
        }

        //otherwise,
        String chosenIndustry;

        WeightedRandomPicker<String> industryIdPicker = new WeightedRandomPicker<>();
        for (Map.Entry<String, Float> entry : getChanceMap().entrySet()) industryIdPicker.add(entry.getKey(), entry.getValue());

        //remove riftGen if the planet has rings
        if (market.hasCondition(Conditions.SOLAR_ARRAY)
                || market.hasCondition(IndEvo_ids.COND_MINERING)
                || market.hasCondition("niko_MPC_antiAsteroidSatellites_derelict")
                || IndEvo_IndustryHelper.planetHasRings(planet)
                || (planet.isGasGiant())) {
            industryIdPicker.remove(IndEvo_ids.RIFTGEN);
        }

        Random random = new Random(Misc.getSalvageSeed(planet));
        chosenIndustry = industryIdPicker.pick(random);

        market.getMemoryWithoutUpdate().set(INDUSTRY_ID_MEMORY_KEY, chosenIndustry);


        IndEvo_modPlugin.log("resolving to - " + chosenIndustry);
    }

    private static Map<String, Float> getChanceMap() {
        Map<String, Float> indMap = new HashMap<>();
        indMap.put(IndEvo_ids.LAB, 100f);
        indMap.put(IndEvo_ids.DECONSTRUCTOR, 70f);
        indMap.put(IndEvo_ids.HULLFORGE, 70f);
        indMap.put(IndEvo_ids.RIFTGEN, 30f);
        //indMap.put(IndEvo_ids.BEACON, 0.1f);

        return indMap;
    }

    private static void seedRuinsIfNeeded(PlanetAPI p) {
        //seed ruins if it doesn't have them
        boolean ruins = false;
        for (MarketConditionAPI condition : p.getMarket().getConditions()) {
            if (condition.getId().contains("ruins_")) ruins = true;
        }

        if (!ruins) {
            p.getMarket().addCondition(Conditions.RUINS_SCATTERED);
        }
    }
}
