package industrial_evolution.plugins.addOrRemovePlugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*This script must be registered in your modplugin under onGameLoad, by calling UniqueShipRemoveAndReplacer.init("TARGET SHIP ID");
It will then automatically search for a fleet with the target ship, remember it, clear duplicates from the chosen fleet,
then iterate and replace it in any other fleet in the sector.

If another fleet spawns with it, it will auto-clear it and replace it with ship(s) of similar DP according to faction doctrine.

You can run this for multiple different ships at the same time. Calling it twice with the same ID will have no effect.
If the ship gets destroyed, or the fleet despawns, it will wait until the ship spawns again in any other fleet and do it all again.

This is Non-Transient*/

public class IndEvo_UniqueShipRemoveAndReplacer extends BaseCampaignEventListener implements FleetEventListener {

    private String targetShipId;
    private static final String FLEET_KEY = "$IndEvo_ContainingFleet_";
    private static final String LISTENER_INSTANCE = "$IndEvo_UniqueShipListenerInstance_";

    private IndEvo_UniqueShipRemoveAndReplacer(String id) {
        super(false);
        this.targetShipId = id;
    }

    public static void init(String targetShipId) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains(LISTENER_INSTANCE + targetShipId)) {
            IndEvo_UniqueShipRemoveAndReplacer instance = new IndEvo_UniqueShipRemoveAndReplacer(targetShipId);

            Global.getSector().addListener(instance);
            mem.set(LISTENER_INSTANCE + targetShipId, instance);

            instance.performInitialSetup();
        }
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
        if (fleet.isPlayerFleet()) return;

        //this listener gets applied to (and removed from) the fleet in setOrReplaceChosenFleet()
        if (getChosenFleet() == null || !getChosenFleet().isAlive() || !containsTargetShip(getChosenFleet())) {
            setOrReplaceChosenFleet(fleet);
            replaceIllegalFleetMembers(fleet, true);
            return;
        }

        replaceIllegalFleetMembers(fleet, false);
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        setOrReplaceChosenFleet(null);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!containsTargetShip(fleet)) {
            setOrReplaceChosenFleet(null);


        }
    }

    public void performInitialSetup() {
        CampaignFleetAPI targetFleet = getChosenFleet();

        if (targetFleet == null || !targetFleet.isAlive() || !containsTargetShip(targetFleet)) {
            findAndSetFleet();
        }

        replaceTargetShipInAllFleets();
    }

    public void findAndSetFleet() {
        List<CampaignFleetAPI> allFleetsWithTargetShip = getAllFleetsWithTargetShip();
        CampaignFleetAPI chosenFleet = null;

        if (!allFleetsWithTargetShip.isEmpty()) {
            //set chosen fleet, remove dupes and clear everything else

            chosenFleet = allFleetsWithTargetShip.get(0);

            if (chosenFleet != null) {
                setOrReplaceChosenFleet(chosenFleet);
                replaceIllegalFleetMembers(chosenFleet, true);
            }

            for (CampaignFleetAPI fleet : allFleetsWithTargetShip) {
                if (!isChosenFleet(fleet)) replaceIllegalFleetMembers(fleet, false);
            }
        }
    }

    public List<FleetMemberAPI> getFleetMembersWithTargetShip(CampaignFleetAPI fleet) {
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
            if (isChosenFleet(fleetAPI)) continue;

            replaceIllegalFleetMembers(fleetAPI, false);
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

    private void replaceIllegalFleetMembers(CampaignFleetAPI fleet, boolean skipFirst) {
        List<FleetMemberAPI> toRemoveList = getFleetMembersWithTargetShip(fleet);

        float totalFPToReplace = 0f;

        int i = 0;
        for (FleetMemberAPI m : toRemoveList) {
            i++;
            if (skipFirst && i == 1) continue; //skip the first one

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
        String hullId = m.getHullId();
        String baseHull = m.getHullSpec().getBaseHullId();

        return targetShipId.equals(hullId) || targetShipId.equals(baseHull);
    }

    public void setOrReplaceChosenFleet(CampaignFleetAPI fleet) {
        CampaignFleetAPI oldChosenFleet = getChosenFleet();
        if (oldChosenFleet != null) oldChosenFleet.removeEventListener(this);

        Global.getSector().getMemoryWithoutUpdate().set(FLEET_KEY + targetShipId, fleet);
        if (fleet != null) fleet.addEventListener(this);
    }

    public CampaignFleetAPI getChosenFleet() {
        return Global.getSector().getMemoryWithoutUpdate().getFleet(FLEET_KEY + targetShipId);
    }

    public boolean isChosenFleet(CampaignFleetAPI fleet) {
        return fleet.getId().equals(Global.getSector().getMemoryWithoutUpdate().getFleet(FLEET_KEY + targetShipId).getId());
    }

}

