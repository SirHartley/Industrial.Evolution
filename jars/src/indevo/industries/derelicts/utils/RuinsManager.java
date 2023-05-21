package indevo.industries.derelicts.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.derelicts.conditions.RuinsCondition;
import indevo.utils.ModPlugin;
import indevo.utils.helper.IndustryHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.ids.Tags.*;
import static indevo.industries.derelicts.industry.Ruins.INDUSTRY_ID_MEMORY_KEY;

public class RuinsManager {
    private static void log(String Text) {
        Global.getLogger(RuinsManager.class).info(Text);
    }

    public static class ResolveRuinsToUpgradeListener implements SurveyPlanetListener {

        public static void register() {
            Global.getSector().getListenerManager().addListener(new ResolveRuinsToUpgradeListener(), true);
        }

        @Override
        public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
            if (planet.getMarket().hasCondition(Ids.COND_RUINS)
                    && !planet.getMarket().getMemoryWithoutUpdate().contains(INDUSTRY_ID_MEMORY_KEY)) {
                RuinsManager.setUpgradeSpec(planet);
            }
        }
    }

    public static class DerelictRuinsPlacer implements PlayerColonizationListener {

        public static void register() {
            Global.getSector().getListenerManager().addListener(new DerelictRuinsPlacer(), true);
        }

        @Override
        public void reportPlayerColonizedPlanet(PlanetAPI planetAPI) {
            MarketAPI m = planetAPI.getMarket();

            if (m.hasCondition(Ids.COND_RUINS)) {
                RuinsCondition cond = (RuinsCondition) m.getCondition(Ids.COND_RUINS).getPlugin();
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

                if (pMarket.hasCondition(Ids.COND_RUINS)) {
                    if (Misc.getMarketsInLocation(p.getContainingLocation()).size() > 0
                            || s.getTags().contains(THEME_CORE)
                            || s.getTags().contains(THEME_CORE_POPULATED)
                            || s.getTags().contains(THEME_CORE_UNPOPULATED)) {

                        if (!pMarket.getFactionId().equals("player")) {
                            pMarket.removeCondition(Ids.COND_RUINS);

                            if (pMarket.isPlanetConditionMarketOnly()) continue;

                            pMarket.removeIndustry(Ids.RUINS, null, false);
                            pMarket.removeIndustry(Ids.HULLFORGE, null, false);
                            pMarket.removeIndustry(Ids.DECONSTRUCTOR, null, false);
                            pMarket.removeIndustry(Ids.RIFTGEN, null, false);
                            pMarket.removeIndustry(Ids.LAB, null, false);
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

                if (p.getTags().contains(Ids.TAG_NEVER_REMOVE_RUINS)) {
                    if (!m.hasCondition(Ids.COND_RUINS)) m.addCondition(Ids.COND_RUINS);
                    seedRuinsIfNeeded(p);
                }

                if (m.hasCondition(Ids.COND_RUINS)) {
                    if (forbidden || !remnant || Misc.getMarketsInLocation(p.getContainingLocation()).size() > 0) {
                        m.removeCondition(Ids.COND_RUINS);
                    } else {
                        switch (currentCount) {
                            case 0:
                                r = new Random();
                                if (r.nextFloat() < mod) {
                                    currentCount++;
                                    seedRuinsIfNeeded(p);
                                    setUpgradeSpec(p);

                                    log(Ids.COND_RUINS.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName());
                                    amount++;
                                } else m.removeCondition(Ids.COND_RUINS);
                                break;

                            case 1:
                                r = new Random();
                                if (r.nextFloat() < (mod * 0.70f)) {
                                    currentCount++;
                                    seedRuinsIfNeeded(p);
                                    setUpgradeSpec(p);

                                    log(Ids.COND_RUINS.toLowerCase() + " found on " + p.getName() + " (" + p.getTypeId() + ") in " + s.getName() + " - Second Planet");
                                    amount++;
                                } else m.removeCondition(Ids.COND_RUINS);
                                break;
                            default:
                                m.removeCondition(Ids.COND_RUINS);
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
            if (m.hasCondition(Ids.COND_RUINS)) {
                if (localMem.contains(INDUSTRY_ID_MEMORY_KEY)) {
                    String id = localMem.getString(INDUSTRY_ID_MEMORY_KEY);

                    if (id.equals(Ids.DECONSTRUCTOR) || id.equals(Ids.HULLFORGE)) {
                        id = id.equals(Ids.DECONSTRUCTOR) ? Ids.HULLFORGE : Ids.DECONSTRUCTOR;
                        market.getMemoryWithoutUpdate().set(INDUSTRY_ID_MEMORY_KEY, id);
                        return;
                    }
                }
            }
        }

        //otherwise,
        String chosenIndustry;

        WeightedRandomPicker<String> industryIdPicker = new WeightedRandomPicker<>();
        for (Map.Entry<String, Float> entry : getChanceMap().entrySet())
            industryIdPicker.add(entry.getKey(), entry.getValue());

        //remove riftGen if the planet has rings
        if (market.hasCondition(Conditions.SOLAR_ARRAY)
                || market.hasCondition(Ids.COND_MINERING)
                || market.hasCondition("niko_MPC_antiAsteroidSatellites_derelict")
                || IndustryHelper.planetHasRings(planet)
                || (planet.isGasGiant())) {
            industryIdPicker.remove(Ids.RIFTGEN);
        }

        Random random = new Random(Misc.getSalvageSeed(planet));
        chosenIndustry = industryIdPicker.pick(random);

        market.getMemoryWithoutUpdate().set(INDUSTRY_ID_MEMORY_KEY, chosenIndustry);


        ModPlugin.log("resolving to - " + chosenIndustry);
    }

    private static Map<String, Float> getChanceMap() {
        Map<String, Float> indMap = new HashMap<>();
        indMap.put(Ids.LAB, 100f);
        indMap.put(Ids.DECONSTRUCTOR, 70f);
        indMap.put(Ids.HULLFORGE, 70f);
        indMap.put(Ids.RIFTGEN, 30f);
        //indMap.put(Ids.BEACON, 0.1f);

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
