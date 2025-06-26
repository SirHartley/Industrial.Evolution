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
import indevo.exploration.meteor.intel.MeteorShowerLocationIntel;
import indevo.exploration.salvage.specials.CreditStashSpecial;
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

        fleet.setTransponderOn(false);
        fleet.setNoFactionInName(true);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        fleet.setName("Asteroid Hunter");
        fleet.getMemoryWithoutUpdate().set("$IndEvo_roider_tutorial", true);

        Vector2f loc = Misc.getPointAtRadius(player.getLocation(), 800f);

        player.getContainingLocation().addEntity(fleet);
        Misc.fadeIn(fleet, 1f);
        fleet.setLocation(loc.x, loc.y);

        CreditStashSpecial.makeFleetInterceptPlayer(fleet, false, false, false, 30f);
        Misc.giveStandardReturnToSourceAssignments(fleet, false);

        //Spawn meteor shower with 3 day delay
        Global.getSector().getIntelManager().addIntel(new MeteorShowerLocationIntel(player.getContainingLocation(), 4f, MeteorSwarmManager.MeteroidShowerType.ASTEROID, 3));

        done = true;

        //force spawn meteor shower in x days
        //add intel noting the shower, location, time and what you are expected to do

    }
}
