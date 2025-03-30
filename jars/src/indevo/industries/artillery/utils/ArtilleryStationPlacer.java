package indevo.industries.artillery.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.terrain.RingSystemTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.artillery.conditions.ArtilleryStationCondition;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.scripts.ArtilleryStationScript;
import indevo.items.consumables.listeners.LocatorSystemRatingUpdater;
import indevo.utils.ModPlugin;
import indevo.utils.helper.Settings;

import java.util.List;
import java.util.Random;

import static indevo.industries.artillery.scripts.ArtilleryStationScript.SCRIPT_KEY;
import static indevo.industries.artillery.scripts.ArtilleryStationScript.TYPE_KEY;

public class ArtilleryStationPlacer {

    public static void placeCoreWorldArtilleries() {

        if (Global.getSector().getEconomy().getMarket("culann") == null
                || !Settings.getBoolean(Settings.ENABLE_ARTILLERY)
                || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedArtilleries")) return;

        MarketAPI m = Global.getSector().getEconomy().getMarket("eochu_bres");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_RAILGUN);
        m.getIndustry(Ids.ARTILLERY_RAILGUN).setAICoreId(Commodities.ALPHA_CORE);

        m = Global.getSector().getEconomy().getMarket("chicomoztoc");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_MORTAR);

        m = Global.getSector().getEconomy().getMarket("kazeron");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_MISSILE);
        m.getIndustry(Ids.ARTILLERY_MISSILE).setAICoreId(Commodities.GAMMA_CORE);

        m = Global.getSector().getEconomy().getMarket("sindria");
        addArtilleryToPlanet(m.getPrimaryEntity(), true);
        m.addIndustry(Ids.ARTILLERY_MISSILE);

        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedArtilleries", true);
    }

    public static void placeDerelictArtilleries() {
        if (!Settings.getBoolean(Settings.ENABLE_ARTILLERY) || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedDerelictArtilleries"))
            return;

        int currentCount = 0;
        Random r = new Random();

        OUTER:
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null
                    || s.getPlanets().isEmpty()
                    || !Misc.getMarketsInLocation(s).isEmpty()
                    || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                    || s.hasTag(Tags.THEME_HIDDEN)
                    || s.hasTag(Tags.THEME_SPECIAL)
                    || s.hasTag(Tags.SYSTEM_ABYSSAL)) continue;

            float baseMod = 0f;
            if (s.getTags().contains(Tags.THEME_REMNANT_RESURGENT)) baseMod += 0.2f;
            if (s.getTags().contains(Tags.THEME_DERELICT_CRYOSLEEPER)) baseMod += 0.2f;
            if (s.getTags().contains(Tags.THEME_RUINS)) baseMod += 0.02f;

            boolean hasRemnantStation = false;
            for (CampaignFleetAPI fleet : s.getFleets()) {
                if (fleet.isStationMode() && fleet.getFaction().getId().equals(Factions.REMNANTS))
                    hasRemnantStation = true;
            }

            float rating = LocatorSystemRatingUpdater.getRating(s);
            baseMod += rating * 0.002f;

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null || p.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) continue;

                float planetMod = baseMod;
                planetMod += TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.1f;
                planetMod *= Settings.getFloat(Settings.ARTILLERY_SPAWN_WEIGHT);

                if (r.nextFloat() < planetMod) {
                    if (hasRemnantStation) p.getMarket().getMemoryWithoutUpdate().set(TYPE_KEY, ArtilleryStationEntityPlugin.TYPE_MISSILE);

                    //addTestArtilleryToPlanet(p);
                    addArtilleryToPlanet(p, false);

                    ModPlugin.log("Placed artillery at " + p.getName() + " system: " + s.getName());
                    currentCount++;

                    continue OUTER;
                }
            }
        }

        ModPlugin.log("Placed " + currentCount + " Artillery Stations");
        Global.getSector().getMemoryWithoutUpdate().set("$IndEvo_placedDerelictArtilleries", true);
    }

    public static void addArtilleryToPlanet(SectorEntityToken planet, boolean isDestroyed) {
        if (!planet.hasScriptOfClass(ArtilleryStationScript.class) && !planet.hasTag(Ids.TAG_ENTITY_HAS_ARTILLERY_STATION)) {

            planet.addTag(Ids.TAG_ENTITY_HAS_ARTILLERY_STATION);

            ArtilleryStationScript script = new ArtilleryStationScript(planet.getMarket());
            script.setDestroyed(isDestroyed);
            planet.addScript(script);
            planet.getMemoryWithoutUpdate().set(SCRIPT_KEY, script);
            planet.getMarket().addTag(Ids.TAG_ARTILLERY_STATION);
            planet.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
            planet.getContainingLocation().addTag(Ids.TAG_SYSTEM_HAS_ARTILLERY);

            StarSystemAPI starSystem = planet.getStarSystem();

            if (starSystem.getEntitiesWithTag(Ids.TAG_WATCHTOWER).isEmpty()) {
                String faction = planet.getMarket() != null && (planet.getMarket().isPlanetConditionMarketOnly() || Factions.NEUTRAL.equals(planet.getMarket().getFactionId())) ? Ids.DERELICT_FACTION_ID : planet.getMarket().getFactionId();
                ArtilleryStationPlacer.placeWatchtowers(starSystem, faction);
            }

            if (!planet.getMarket().hasCondition(ArtilleryStationCondition.ID))
                planet.getMarket().addCondition(ArtilleryStationCondition.ID);
        }
    }

    public static class ArtilleryStationInteractionConfigGen implements FleetInteractionDialogPluginImpl.FIDConfigGen {
        public FleetInteractionDialogPluginImpl.FIDConfig createConfig() {
            FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();

            config.alwaysAttackVsAttack = true;
            config.leaveAlwaysAvailable = true;
            config.showFleetAttitude = false;
            config.showTransponderStatus = false;
            config.showEngageText = false;

            config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
                public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
                    new RemnantSeededFleetManager.RemnantFleetInteractionConfigGen().createConfig().delegate.
                            postPlayerSalvageGeneration(dialog, context, salvage);
                }
                public void notifyLeave(InteractionDialogAPI dialog) {
                }
                public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                    bcc.aiRetreatAllowed = false;
                    bcc.objectivesAllowed = false;
                }
            };
            return config;
        }
    }

    public static void placeWatchtowers(StarSystemAPI system, String factionId) {
        float minGap = 100f;
        Random random = new Random();
        FactionAPI faction = Global.getSector().getFaction(factionId);

        //jump points
        for (SectorEntityToken t : system.getJumpPoints()) {
            WatchtowerEntityPlugin.spawn(t, Global.getSector().getFaction(factionId));
        }

        //gates
        for (SectorEntityToken t : system.getEntitiesWithTag(Tags.GATE)){
            WatchtowerEntityPlugin.spawn(t, Global.getSector().getFaction(factionId));
        }

        //gas giants
        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.isStar()) continue;
            if (!planet.isGasGiant()) continue;

            float ow = BaseThemeGenerator.getOrbitalRadius(planet);
            List<BaseThemeGenerator.OrbitGap> gaps = BaseThemeGenerator.findGaps(planet, 100f, 100f + ow + minGap, minGap);
            BaseThemeGenerator.EntityLocation loc = createLocationAtRandomGap(random, planet, gaps, BaseThemeGenerator.LocationType.GAS_GIANT_ORBIT);
            if (loc != null) {
                SectorEntityToken t = system.addCustomEntity(Misc.genUID(), "Watchtower", "IndEvo_Watchtower", faction.getId(), null);
                t.setOrbit(loc.orbit);
                if (Misc.getMarketsInLocation(system).isEmpty())
                    MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);
            }
        }

        for (CampaignTerrainAPI terrain : system.getTerrainCopy()) {
            if (terrain.hasTag(Tags.ACCRETION_DISK)) continue;
            //if (terrain.getOrbit() == null || (terrain.getOrbit().getFocus() != null && !terrain.getOrbit().getFocus().isStar())) continue; //we only do debris fields around the sun
            if (terrain.getOrbitFocus() != null && (!terrain.getOrbitFocus().isStar() || !terrain.getOrbitFocus().isSystemCenter()))
                continue;

            CampaignTerrainPlugin plugin = terrain.getPlugin();

            if (plugin instanceof RingSystemTerrainPlugin) {
                RingSystemTerrainPlugin ring = (RingSystemTerrainPlugin) plugin;
                float start = ring.params.middleRadius - ring.params.bandWidthInEngine / 2f;
                List<BaseThemeGenerator.OrbitGap> gaps = BaseThemeGenerator.findGaps(terrain,
                        start - 100f, start + ring.params.bandWidthInEngine + 100f, minGap);

                BaseThemeGenerator.EntityLocation loc = createLocationAtRandomGap(random, terrain, gaps, BaseThemeGenerator.LocationType.IN_RING);
                if (loc != null) {
                    SectorEntityToken t = system.addCustomEntity(Misc.genUID(), faction.getDisplayName() + " Watchtower", "IndEvo_Watchtower", faction.getId(), null);
                    t.setOrbit(loc.orbit);
                    if (Misc.getMarketsInLocation(system).isEmpty())
                        MiscellaneousThemeGenerator.makeDiscoverable(t, 200f, 2000f);
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
