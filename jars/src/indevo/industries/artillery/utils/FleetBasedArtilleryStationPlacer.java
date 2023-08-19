package indevo.industries.artillery.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.scripts.CampaignAttackScript;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

public class FleetBasedArtilleryStationPlacer {

    //types possible: "mortar", "railgun", "missile"
    public static void addBattlestations(String type, SectorEntityToken primaryEntity) {
        //BaseThemeGenerator.EntityLocation loc = pickCommonLocation(random, data.system, 200f, true, null);

        CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(Ids.DERELICT_FACTION_ID, FleetTypes.BATTLESTATION, null);

        //add station to the fleet
        String variantId = null;
        try {
            JSONObject json = new JSONObject(Global.getSettings().getIndustrySpec("IndEvo_Artillery_" + type).getData());
            variantId = json.getString("variant");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
        fleet.getFleetData().addFleetMember(member);

        fleet.setNoFactionInName(true);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);

        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set("$" + Ids.TAG_ARTILLERY_STATION, true);

        fleet.addTag(Tags.NEUTRINO_HIGH);

        fleet.setStationMode(true);

        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN,
                new ArtilleryStationPlacer.ArtilleryStationInteractionConfigGen());

        fleet.clearAbilities();
        fleet.addAbility(Abilities.TRANSPONDER);
        fleet.getAbility(Abilities.TRANSPONDER).activate();
        fleet.getDetectedRangeMod().modifyFlat("gen", 1000f);

        fleet.setAI(null);

        //Add entity
        Random random = new Random();

        MarketAPI market = primaryEntity.getMarket();
        primaryEntity.getContainingLocation().spawnFleet(primaryEntity, 0f, 0f, fleet);

        float angle = (float) Math.random() * 360f;
        float radius = primaryEntity.getRadius() + 150f;
        float period = radius / 10f;

        fleet.setCircularOrbitWithSpin(primaryEntity,
                angle,
                radius,
                period,
                5f,
                5f);

        fleet.setName(market.getName() + " " + Misc.ucFirst(type) + " Station");
        if (Misc.getMarketsInLocation(primaryEntity.getContainingLocation()).isEmpty())
            MiscellaneousThemeGenerator.makeDiscoverable(fleet, 300f, 2000f);

        String coreId = Commodities.GAMMA_CORE;

        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(coreId);
        PersonAPI commander = plugin.createPerson(coreId, fleet.getFaction().getId(), random);

        fleet.setCommander(commander);
        fleet.getFlagship().setCaptain(commander);
        RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(fleet.getFlagship());
        RemnantOfficerGeneratorPlugin.addCommanderSkills(commander, fleet, null, 3, random);

        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());

        fleet.addScript(new CampaignAttackScript(fleet, type));
    }

    public static void addTestArtilleryToPlanet(SectorEntityToken planet) {
        if (!planet.hasTag(Ids.TAG_ARTILLERY_STATION)) {
            planet.addTag(Tags.NOT_RANDOM_MISSION_TARGET);
            planet.getContainingLocation().addTag(Ids.TAG_SYSTEM_HAS_ARTILLERY);

            StarSystemAPI starSystem = planet.getStarSystem();

            if (starSystem.getEntitiesWithTag(Ids.TAG_WATCHTOWER).isEmpty()) {
                String faction = planet.getMarket() != null && (planet.getMarket().isPlanetConditionMarketOnly() || Factions.NEUTRAL.equals(planet.getMarket().getFactionId())) ? Ids.DERELICT_FACTION_ID : planet.getMarket().getFactionId();
                ArtilleryStationPlacer.placeWatchtowers(starSystem, faction);
            }

            addBattlestations("mortar", planet);
        }
    }
}
