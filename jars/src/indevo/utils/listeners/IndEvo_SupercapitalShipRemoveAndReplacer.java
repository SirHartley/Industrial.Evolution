package indevo.utils.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*This script must be registered in your modplugin under onGameLoad, by calling SupercapitalShipRemoveAndReplacer.init();
It will then automatically search for a fleet with ships that have the "supercapital" tag, and clear any past the first one from the chosen fleet.

This is Transient.*/

@Deprecated
public class IndEvo_SupercapitalShipRemoveAndReplacer extends BaseCampaignEventListener {

    private static final String TAG = "supercapital";

    private IndEvo_SupercapitalShipRemoveAndReplacer() {
        super(false);
    }

    public static void init() {

        for (Object o : Global.getSector().getAllListeners()){
            if (o instanceof IndEvo_SupercapitalShipRemoveAndReplacer) return;
        }

        IndEvo_SupercapitalShipRemoveAndReplacer instance = new IndEvo_SupercapitalShipRemoveAndReplacer();
        instance.replaceTargetShipInAllFleets();

        Global.getSector().addTransientListener(instance);
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (fleet.isPlayerFleet()) return;

        replaceIllegalFleetMembers(fleet);
    }

    public List<FleetMemberAPI> getFleetMembersTargetShip(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> toRemoveList = new ArrayList<>();

        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (isTargetShip(m)) {
                toRemoveList.add(m);
            }
        }

        return toRemoveList;
    }

    public void replaceTargetShipInAllFleets() {
        List<CampaignFleetAPI> targetFleetList = getAllFleetsWithTargetShip();

        for (CampaignFleetAPI fleetAPI : targetFleetList) {
            replaceIllegalFleetMembers(fleetAPI);
        }
    }

    public List<CampaignFleetAPI> getAllFleetsWithTargetShip() {
        List<CampaignFleetAPI> fleetList = new ArrayList<>();

        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI f : loc.getFleets()) {
                if (f.isPlayerFleet()) continue;

                if (containsTargetShip(f)) fleetList.add(f);
            }
        }

        return fleetList;
    }

    private boolean containsTargetShip(CampaignFleetAPI fleet) {
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            if (isTargetShip(m)) {
                return true;
            }
        }

        return false;
    }

    private void replaceIllegalFleetMembers(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> toRemoveList = getFleetMembersTargetShip(fleet);

        float totalFPToReplace = 0f;

        int i = 0;
        for (FleetMemberAPI m : toRemoveList) {
            i++;

            if (i == 1) continue; //skip the first one

            fleet.getFleetData().removeFleetMember(m);
            totalFPToReplace += m.getFleetPointCost();
        }

        if (totalFPToReplace > 1f) {
            FleetParamsV3 params = new FleetParamsV3(
                    fleet.getMarket(),
                    null, // locInHyper
                    fleet.getFaction().getId(),
                    fleet.getMarket().getShipQualityFactor(), // qualityOverride
                    getFleetType(fleet), // ,
                    totalFPToReplace, // combatPts
                    0f, // freighterPts
                    0f, // tankerPts
                    0f, // transportPts
                    0f, // linerPts
                    0f, // utilityPts
                    0f //-0.5f // qualityBonus
            );

            params.mode = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;

            FleetFactoryV3.addCombatFleetPoints(fleet, new Random(), totalFPToReplace, 0f, 0f, params);
        }
    }

    public String getFleetType(CampaignFleetAPI fleet) {
        if (!fleet.getMemoryWithoutUpdate().contains(MemFlags.MEMORY_KEY_FLEET_TYPE))
            return "";
        return fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
    }

    private boolean isTargetShip(FleetMemberAPI m) {
        return m.getHullSpec().getTags().contains(TAG);
    }
}

