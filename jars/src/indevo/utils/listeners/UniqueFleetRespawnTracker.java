package indevo.utils.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class UniqueFleetRespawnTracker implements FleetEventListener, EveryFrameScript {

    /**
     * Script revolves around the flagship, which has to be killed by the player for the fleet to count as dead
     * If the flagship is killed by an NPC and the player attacks the remaining fleet afterwards, the fleet will still respawn as if entirely defeated by an NPC
     */

    //statics, change these to fit
    private static final int DAYS_UNTIL_RESPAWN = 1;
    private static final int DAYS_UNTIL_FP_RECOVERY = 7;
    private static final String MEMORY_KEY_PLAYER_KILLED_FLEET = "$pagsm_grand_fleet_is_out_of_gas";

    //permanent memory entries set when the script is registered
    private final MarketAPI respawnMarket;
    private final String requiredMarketFaction;
    private final FleetMemberAPI flagship;
    private final List<FleetMemberAPI> originalMemberBackup;
    private final FleetParamsV3 paramsBackup;
    private final FleetParamsV3 paramsAfterDefeat;
    private final FleetAssignmentDataAPI assignmentBackup;

    //fleet state tracking
    private CampaignFleetAPI fleet;
    private List<FleetMemberAPI> lostMembers = new ArrayList<>();
    private float daysPassed = 0f;
    private boolean isDamaged = false;
    private boolean isTemporaryDead = false;
    private boolean defeated = false;
    private boolean flagshipKilledByPlayer = false;

    public static void register(CampaignFleetAPI fleet, FleetParamsV3 originalSpawnParams, FleetParamsV3 paramsAfterDefeat){
        Global.getSector().addScript(new UniqueFleetRespawnTracker(fleet, originalSpawnParams, paramsAfterDefeat));
    }

    private UniqueFleetRespawnTracker(CampaignFleetAPI fleet, FleetParamsV3 originalSpawnParams, FleetParamsV3 paramsAfterDefeat) {
        this.fleet = fleet;
        this.respawnMarket = Misc.getSourceMarket(fleet);
        this.requiredMarketFaction = fleet.getFaction().getId();
        this.flagship = fleet.getFlagship();
        this.paramsAfterDefeat = paramsAfterDefeat;
        this.originalMemberBackup = fleet.getFleetData().getMembersListCopy();
        this.paramsBackup = originalSpawnParams;
        this.assignmentBackup = fleet.getCurrentAssignment();
        Misc.makeImportant(fleet, MemFlags.ENTITY_MISSION_IMPORTANT);

        fleet.addEventListener(this);
    }


    @Override
    public void advance(float amount) {
        //if the respawn market does not exist we don't do jack shit except an hero
        boolean respawnMarketValid = respawnMarket != null && respawnMarket.getFactionId().equals(requiredMarketFaction);
        if (!respawnMarketValid){
            if (!isDespawningOrDead() && !fleet.getCurrentAssignment().getAssignment().equals(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN)) returnToPlanetAndDespawn();
            daysPassed = 0;
            return;
        }

        //schrödingers fleet checks
        if (isTemporaryDead || isDamaged) daysPassed += Global.getSector().getClock().convertToDays(amount);

        if (!isTemporaryDead && isDamaged && daysPassed > DAYS_UNTIL_FP_RECOVERY) {
            respawnLostMembers();
            if (!defeated) Misc.makeImportant(fleet, MemFlags.ENTITY_MISSION_IMPORTANT);
            restoreInitialAssignment();
        }

        if (isTemporaryDead && daysPassed > DAYS_UNTIL_RESPAWN) {
            respawnFleet();
        }
    }

    private void setMemoryKeyPlayerKilledFleet(){
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY_PLAYER_KILLED_FLEET, true);
    }

    private void returnToPlanetAndDespawn(){
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);

        MarketAPI target = getTargetMarket();

        //if market is null it means there is no alternate market and our market is dead, so we just yeet ourselves out the window
        if (target == null || target.getPrimaryEntity() == null) {
            fleet.addScript(new AutoDespawnScript(fleet));
            return;
        }

        Misc.giveStandardReturnAssignments(fleet, target.getPrimaryEntity(), "Heading back to" ,true);
    }

    private void returnToPlanetAndRecuperate(){
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, respawnMarket.getPrimaryEntity(), DAYS_UNTIL_FP_RECOVERY, "Getting Repaired and Refuelled");
    }

    private MarketAPI getTargetMarket(){
        if (respawnMarket != null && !respawnMarket.isPlanetConditionMarketOnly()) return respawnMarket;

        LocationAPI loc = fleet.getContainingLocation();
        List<MarketAPI> marketList = Misc.getMarketsInLocation(loc, requiredMarketFaction);

        //there's another faction market, go there instead
        if (!marketList.isEmpty()) return marketList.get(0);

        marketList = Misc.getMarketsInLocation(loc);
        MarketAPI target = null;
        float maxRel = Float.MIN_VALUE;

        //we go to the market with the best relation to our faction that's not hostile
        for (MarketAPI m : marketList){
            if (m.getFaction().isHostileTo(requiredMarketFaction)) continue;

            float rel = m.getFaction().getRelationship(requiredMarketFaction);
            if (rel > maxRel) target = m;
        }

        return target;
    }

    private void overrideFleetBackupWithBlankCopy(){
        String name = fleet.getName();
        boolean noFactionInName = fleet.isNoFactionInName();

        fleet = FleetFactoryV3.createFleet(paramsBackup);
        fleet.setName(name);
        fleet.setNoFactionInName(noFactionInName);

        //purge the vigilance
        //purge it
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()){
            if (m.isFlagship()) continue;
            fleet.getFleetData().removeFleetMember(m);
        }
    }

    private void overrideFleetWithPostDefeatVersion(){
        String name = fleet.getName();
        boolean noFactionInName = fleet.isNoFactionInName();

        fleet = FleetFactoryV3.createFleet(paramsAfterDefeat);
        fleet.setName(name);
        fleet.setNoFactionInName(noFactionInName);
    }

    private void initPostDefeatFleet(){
        respawnMarket.getContainingLocation().addEntity(fleet);
        fleet.setAI(Global.getFactory().createFleetAI(fleet));

        fleet.setLocation(respawnMarket.getPrimaryEntity().getLocation().x, respawnMarket.getPrimaryEntity().getLocation().y);
        fleet.setFacing((float) Math.random() * 360f);

        fleet.getAI().addAssignment(FleetAssignment.DEFEND_LOCATION, respawnMarket.getPrimaryEntity(), Integer.MAX_VALUE, null);
        fleet.setAI(Global.getFactory().createFleetAI(fleet));
    }

    private void initFleetBackup(){
        for (FleetMemberAPI m : originalMemberBackup) {
            if (m.isFlagship() || m.getId().equals(flagship.getId())) continue;

            fleet.getFleetData().addFleetMember(m);
        }

        //flagship.setFlagship(true);
        fleet.getFlagship().setCaptain(flagship.getCaptain());
        fleet.setCommander(flagship.getCaptain());

        respawnMarket.getContainingLocation().addEntity(fleet);
        fleet.setAI(Global.getFactory().createFleetAI(fleet));

        fleet.setLocation(respawnMarket.getPrimaryEntity().getLocation().x, respawnMarket.getPrimaryEntity().getLocation().y);
        fleet.setFacing((float) Math.random() * 360f);

        fleet.getAI().addAssignment(FleetAssignment.DEFEND_LOCATION, respawnMarket.getPrimaryEntity(), Integer.MAX_VALUE, null);
        fleet.getMemoryWithoutUpdate().set("$chatter_introSplash_name", fleet.getCommander().getNameString());

        Misc.makeImportant(fleet, MemFlags.ENTITY_MISSION_IMPORTANT);

        fleet.setAI(Global.getFactory().createFleetAI(fleet));
    }

    public void respawnFleet(){
        isDamaged = false;
        lostMembers.clear();

        isTemporaryDead = false;
        daysPassed = 0;

        if (!defeated) initFleetBackup();
        else initPostDefeatFleet();

        restoreInitialAssignment();

        fleet.addEventListener(this);
    }

    public void respawnLostMembers(){
        for (FleetMemberAPI member : lostMembers) {
            fleet.getFleetData().addFleetMember(member);
            member.getStatus().repairFully();
            RepairTrackerAPI repairs = member.getRepairTracker();
            repairs.setCR(Math.max(repairs.getCR(), repairs.getMaxCR()));

            member.setStatUpdateNeeded(true);
        }

        lostMembers.clear();

        flagship.setFlagship(true);
        fleet.setCommander(flagship.getCaptain());

        isDamaged = false;
        daysPassed = 0;
    }

    public void restoreInitialAssignment(){
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        fleet.addAssignment(assignmentBackup.getAssignment(), assignmentBackup.getTarget(), assignmentBackup.getMaxDurationInDays());
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        //we only truly die if flagship was killed by player
        if (flagshipKilledByPlayer && !defeated) {
            defeated = true;
            setMemoryKeyPlayerKilledFleet();
            overrideFleetWithPostDefeatVersion();

        } else if (!defeated) overrideFleetBackupWithBlankCopy();
        else overrideFleetWithPostDefeatVersion();

        isTemporaryDead = true;
        daysPassed = 0;

        fleet.removeEventListener(this); //else we'll keep the empty fleet in memory.
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        //if flagship dead we go fuck off, else we go recuperate

        List<FleetMemberAPI> membersLost = Misc.getSnapshotMembersLost(fleet);
        boolean playerInvolved = battle.isPlayerInvolved();
        boolean flagshipDead = false;

        for (FleetMemberAPI m : membersLost){
            if (m.getVariant().getHullVariantId().equals(flagship.getVariant().getHullVariantId())) {
                flagshipDead = true;
                break;
            }
        }

        if (!flagshipKilledByPlayer){
            if (playerInvolved && flagshipDead){
                flagshipKilledByPlayer = true;
                setMemoryKeyPlayerKilledFleet();
                fleet.getMemoryWithoutUpdate().unset(MemFlags.ENTITY_MISSION_IMPORTANT);

                if (!isDespawningOrDead()) returnToPlanetAndDespawn();
                return;
            }
        }

        //if we are here the fleet was damaged but not killed or it's in moes bar mode
        if (!membersLost.isEmpty()) {
            if (!isDamaged) daysPassed = 0; //if we are already damaged, don't reset the clock

            isDamaged = true;
            lostMembers.addAll(Misc.getSnapshotMembersLost(fleet));

            if (flagshipDead) fleet.getMemoryWithoutUpdate().unset(MemFlags.ENTITY_MISSION_IMPORTANT);
            if (!isDespawningOrDead()) returnToPlanetAndRecuperate();
        }
    }

    private boolean isDespawningOrDead(){
        return fleet.getFleetData().getMembersListCopy().isEmpty() || fleet.isEmpty() || fleet.isDespawning() || !fleet.isAlive();
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
