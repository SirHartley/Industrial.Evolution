package indevo.utils.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class UniqueFleetRespawnTracker implements FleetEventListener, EveryFrameScript {

    //statics, change these to fit
    private static final int DAYS_UNTIL_RESPAWN = 14;
    private static final int DAYS_UNTIL_FP_RECOVERY = 14;
    private static final boolean FLAGSHIP_MUST_BE_DEAD_TO_COUNT_AS_DEAD = true; //if this is false the player can softlock themselves because the fleet might go despawn even if the flagship aint dead
    private static final boolean ALWAYS_RETURN_AND_DESPAWN_WHEN_FLAGSHIP_DEAD_BY_PLAYER = true; //if false the fleet will linger until someone takes it behind the barn even if it's just one ship
    private static final float FP_LOST_PERCENT_TO_COUNT_AS_DEAD = 80f; //only relevant when any of the above is FALSE - if this much is lost the fleet will go die for reals
    private static final String MEMORY_KEY_PLAYER_KILLED_FLEET = "$pagsm_grand_fleet_is_out_of_gas";

    //script variables set when the script is registered
    private MarketAPI respawnMarket;
    private String requiredMarketFaction;
    private CampaignFleetAPI fleet;
    private FleetMemberAPI flagship;
    private float originalFP;

    //timekeeping
    private float daysPassed = 0f;
    private boolean isDamaged = false;
    private boolean isTemporaryDead = false;
    private boolean isPermaDead = false;
    private boolean playerInvolvedInLastEngagement = false;

    private List<FleetMemberAPI> lostMembers = new ArrayList<>();

    public UniqueFleetRespawnTracker(CampaignFleetAPI fleet) {
        this.fleet = fleet;
        this.respawnMarket = Misc.getSourceMarket(fleet);
        this.requiredMarketFaction = fleet.getFaction().getId();
        this.flagship = fleet.getFlagship();
        this.originalFP = fleet.getFleetPoints();

        fleet.addEventListener(this);
    }

    private void setMemoryKeyPlayerKilledFleet(){
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY_PLAYER_KILLED_FLEET, true);
    }

    private void returnToPlanetAndDespawn(){
        MarketAPI target = respawnMarket != null && !respawnMarket.isPlanetConditionMarketOnly() ? respawnMarket : getAlternateMarket();

        if (target == null || target.getPrimaryEntity() == null) {
            fleet.addScript(new AutoDespawnScript(fleet));
            return;
        }

        Misc.giveStandardReturnAssignments(fleet, target.getPrimaryEntity(), "Limping back to" ,true);
    }

    private MarketAPI getAlternateMarket(){
        LocationAPI loc = fleet.getContainingLocation();
        List<MarketAPI> marketList = Misc.getMarketsInLocation(loc, requiredMarketFaction);

        if (!marketList.isEmpty()) return marketList.get(0);

        marketList = Misc.getMarketsInLocation(loc);

        if (marketList.isEmpty()) return null;
        // TODO: 09/02/2023 finish
        return null;
    }

    public void respawnFleet(){
        //or reset respawn time if market is wrong faction or dead
        // TODO: 09/02/2023 respawn when semi dead
    }

    public void respawnLostMembers(){
        for (FleetMemberAPI member : lostMembers) {
            fleet.getFleetData().addFleetMember(member);
            member.getStatus().repairFully();
            RepairTrackerAPI repairs = member.getRepairTracker();
            repairs.setCR(Math.max(repairs.getCR(), repairs.getMaxCR()));

            member.setStatUpdateNeeded(true);
        }

        isDamaged = false;
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {

        //if player not involved in last engagement but it's dead we respawn after 14 days, otherwise, we are dead and never respawn
        if (playerInvolvedInLastEngagement || isPermaDead) { // TODO: 09/02/2023 this sucks find a way to check if player was involved in engagement
            isPermaDead = true;
            setMemoryKeyPlayerKilledFleet();
            fleet.removeEventListener(this);
        } else {
            isTemporaryDead = true;
            daysPassed = 0;
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        List<FleetMemberAPI> membersLost = Misc.getSnapshotMembersLost(fleet);
        float totalFPLost = originalFP - fleet.getFleetPoints();

        boolean fpLimitLossExceeded = totalFPLost / originalFP >= FP_LOST_PERCENT_TO_COUNT_AS_DEAD;
        boolean playerInvolved = battle.isPlayerInvolved();
        boolean flagshipDeadByPlayer = playerInvolved && membersLost.contains(flagship);

        // TODO: 09/02/2023 add return and despawn temporary when flagship killed by non player
        // TODO: 09/02/2023 add handling when fleet attacked by player after losing flagship to npc fleet

        if (flagshipDeadByPlayer && FLAGSHIP_MUST_BE_DEAD_TO_COUNT_AS_DEAD){
            if (ALWAYS_RETURN_AND_DESPAWN_WHEN_FLAGSHIP_DEAD_BY_PLAYER || fpLimitLossExceeded){ //when flagship dead or flagship dead and limit exceeded then despawn
                setPermaDead();
                return;
            }

        } else if (playerInvolved && !FLAGSHIP_MUST_BE_DEAD_TO_COUNT_AS_DEAD && fpLimitLossExceeded){ //flagship might be alive but losses so high we go die in a ditch
            setPermaDead();
            return;
        }

        //if we are here the fleet was damaged but not killed
        if (!membersLost.isEmpty()) {
            if (!isDamaged) daysPassed = 0; //if we are already damaged, don't reset the clock

            isDamaged = true;
            lostMembers.addAll(Misc.getSnapshotMembersLost(fleet));
        }
    }

    private void setPermaDead(){
        isPermaDead = true;
        setMemoryKeyPlayerKilledFleet();
        if (!isDespawningOrDead()) returnToPlanetAndDespawn();
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

    @Override
    public void advance(float amount) {
        if (isPermaDead) return;

        //schrödingers fleet checks
        if (isTemporaryDead || isDamaged) daysPassed += Global.getSector().getClock().convertToDays(amount);
        if (isDamaged && daysPassed > DAYS_UNTIL_FP_RECOVERY) respawnLostMembers();
        if (isTemporaryDead && daysPassed > DAYS_UNTIL_RESPAWN) respawnFleet();
    }
}
