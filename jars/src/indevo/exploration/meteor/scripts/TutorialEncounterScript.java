package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.fleet.InterceptPlayerFleetAssignmentAI;
import indevo.utils.ModPlugin;
import org.lwjgl.util.vector.Vector2f;

public class TutorialEncounterScript implements EveryFrameScript {

    public boolean done = false;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean validLoc = MeteorSwarmManager.isValidMeteorLoc(player.getContainingLocation());

        if (!validLoc) return;

        //spawn fleet
        MarketAPI sourceMarket = Misc.getFactionMarkets(Factions.INDEPENDENT).isEmpty() ? Misc.getFactionMarkets(Factions.INDEPENDENT).get(0) : null; //null will make it travel and despawn in sector center

        FleetParamsV3 params = new FleetParamsV3(
                sourceMarket,
                player.getLocationInHyperspace(),
                Factions.INDEPENDENT,
                null,
                FleetTypes.SCAVENGER_SMALL,
                5f, // combatPts
                5f, // freighterPts
                2f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod
        );

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return;

        ModPlugin.log("spawning roider tutorial fleet");

        fleet.setTransponderOn(false);
        fleet.setNoFactionInName(true);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.NON_HOSTILE_OVERRIDES_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, true);

        fleet.setName("Independent Roider");
        fleet.getMemoryWithoutUpdate().set("$IndEvo_roider_tutorial", true);

        fleet.getAI().setActionTextOverride("Seeking immediate assistance");

        Misc.makeImportant(fleet, MemFlags.ENTITY_MISSION_IMPORTANT);

        Vector2f loc = Misc.getPointAtRadius(player.getLocation(), 800f);

        player.getContainingLocation().addEntity(fleet);
        Misc.fadeIn(fleet, 0.5f);
        fleet.setLocation(loc.x, loc.y);
        fleet.addScript(new InterceptPlayerFleetAssignmentAI(fleet));

        done = true;

        //force spawn meteor shower in x days
        //add intel noting the shower, location, time and what you are expected to do

    }
}
