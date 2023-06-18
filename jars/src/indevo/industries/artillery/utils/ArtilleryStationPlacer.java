package indevo.industries.artillery.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.impl.campaign.terrain.RingSystemTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.industries.artillery.conditions.ArtilleryStationCondition;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.scripts.ArtilleryStationScript;
import indevo.utils.ModPlugin;

import java.util.List;
import java.util.Random;

import static indevo.industries.artillery.scripts.ArtilleryStationScript.SCRIPT_KEY;
import static indevo.industries.artillery.scripts.ArtilleryStationScript.TYPE_KEY;

public class ArtilleryStationPlacer {

    public static void placeCoreWorldArtilleries() {

        if (Global.getSector().getEconomy().getMarket("culann") == null
                || !Global.getSettings().getBoolean("Enable_IndEvo_Artillery")
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
        if (!Global.getSettings().getBoolean("Enable_IndEvo_Artillery") || Global.getSector().getMemoryWithoutUpdate().contains("$IndEvo_placedDerelictArtilleries"))
            return;

        int currentCount = 0;
        Random r = new Random();

        OUTER:
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == null
                    || s.getPlanets().isEmpty()
                    || !Misc.getMarketsInLocation(s).isEmpty()
                    || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                    || s.hasTag(Tags.THEME_HIDDEN)) continue;

            float baseMod = 0.002f;
            if (s.getTags().contains(Tags.THEME_REMNANT_SUPPRESSED)) baseMod += 0.02f;
            if (s.getTags().contains(Tags.THEME_REMNANT_RESURGENT)) baseMod += 0.1f;
            if (s.getTags().contains(Tags.THEME_DERELICT_CRYOSLEEPER)) baseMod += 0.05f;
            if (s.getTags().contains(Tags.THEME_RUINS)) baseMod += 0.02f;

            boolean hasRemnantStation = false;
            for (CampaignFleetAPI fleet : s.getFleets()) {
                if (fleet.isStationMode() && fleet.getFaction().getId().equals(Factions.REMNANTS))
                    hasRemnantStation = true;
            }

            for (PlanetAPI p : s.getPlanets()) {
                if (p.isStar() || p.getMarket() == null || p.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) continue;

                float planetMod = baseMod;
                planetMod += TechMining.getTechMiningRuinSizeModifier(p.getMarket()) * 0.1f;
                planetMod *= Global.getSettings().getFloat("IndEvo_Artillery_spawnWeight");

                if (r.nextFloat() < planetMod) {
                    if (hasRemnantStation)
                        p.getMarket().getMemoryWithoutUpdate().set(TYPE_KEY, ArtilleryStationEntityPlugin.TYPE_MISSILE);

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
        if (!planet.hasScriptOfClass(ArtilleryStationScript.class)) {

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

/*    public static void addArtillery(SectorEntityToken planet, ) {
        String faction = planet.getMarket() != null && (planet.getMarket().isPlanetConditionMarketOnly() || Factions.NEUTRAL.equals(planet.getMarket().getFactionId())) ? Ids.DERELICT_FACTION_ID : planet.getMarket().getFactionId();
        CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(faction, FleetTypes.BATTLESTATION, null);

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, type);
        fleet.getFleetData().addFleetMember(member);

        //fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        fleet.addTag(Tags.NEUTRINO_HIGH);

        fleet.setStationMode(true);

        addRemnantStationInteractionConfig(fleet);

        data.system.addEntity(fleet);

        //fleet.setTransponderOn(true);
        fleet.clearAbilities();
        fleet.addAbility(Abilities.TRANSPONDER);
        fleet.getAbility(Abilities.TRANSPONDER).activate();
        fleet.getDetectedRangeMod().modifyFlat("gen", 1000f);

        fleet.setAI(null);

        setEntityLocation(fleet, loc, null);
        convertOrbitWithSpin(fleet, 5f);

        boolean damaged = type.toLowerCase().contains("damaged");
        String coreId = Commodities.ALPHA_CORE;
        if (damaged) {
            // alpha for both types; damaged is already weaker
            //coreId = Commodities.BETA_CORE;
            fleet.getMemoryWithoutUpdate().set("$damagedStation", true);
            fleet.setName(fleet.getName() + " (Damaged)");
        }

        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(coreId);
        PersonAPI commander = plugin.createPerson(coreId, fleet.getFaction().getId(), random);

        fleet.setCommander(commander);
        fleet.getFlagship().setCaptain(commander);

        if (!damaged) {
            RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(fleet.getFlagship());
            RemnantOfficerGeneratorPlugin.addCommanderSkills(commander, fleet, null, 3, random);
        }

        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());


        //RemnantSeededFleetManager.addRemnantAICoreDrops(random, fleet, mult);

        result.add(fleet);

//				MarketAPI market = Global.getFactory().createMarket("station_market_" + fleet.getId(), fleet.getName(), 0);
//				market.setPrimaryEntity(fleet);
//				market.setFactionId(fleet.getFaction().getId());
//				market.addCondition(Conditions.ABANDONED_STATION);
//				market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
//				((StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin()).setPlayerPaidToUnlock(true);
//				fleet.setMarket(market);

        return result;
    }


    public static void addRemnantStationInteractionConfig(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN,
                new RemnantThemeGenerator.RemnantStationInteractionConfigGen());
    }

    public static class RemnantStationInteractionConfigGen implements FleetInteractionDialogPluginImpl.FIDConfigGen {
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
    }*/

    //---------- end new

    public static void placeWatchtowers(StarSystemAPI system, String factionId) {
        float minGap = 100f;
        Random random = new Random();
        FactionAPI faction = Global.getSector().getFaction(factionId);

        //jump points
        for (SectorEntityToken t : system.getJumpPoints()) {
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
