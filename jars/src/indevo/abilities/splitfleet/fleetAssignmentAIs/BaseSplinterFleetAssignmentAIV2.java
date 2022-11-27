package indevo.abilities.splitfleet.fleetAssignmentAIs;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.GateTransitListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.OrbitFocus;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseSplinterFleetAssignmentAIV2 extends BaseAssignmentAI implements SplinterFleetAssignmentAIV2, CampaignEventListener, GateTransitListener {

    public static final int ASSIGNMENT_DURATION_FOREVER = 99999;
    public static final int ASSIGNMENT_DURATION_3_DAY = 3;

    public static final String CURRENTLY_JUMPING_FLAG = "SplinterFleet_DetachmentIsJumping";

    protected boolean isOverridden = false;
    private boolean register = true;

    boolean inBattle = false;
    private float battleReleaseTimer = 0f;
    private static final float BATTLE_RELEASE_TIME = 3f;

    public Map<Integer, JumpInfo> jumpInfoMap = new HashMap<>();

    private static class GoSlowThreeSecondsScript implements EveryFrameScript{

        private static final float MAX_DISTANCE_FOR_STOP = 500f;
        private static final float RUNTIME_SECONDS = 3f;

        public boolean done = false;
        private CampaignFleetAPI fleet;
        private CampaignFleetAPI playerFleet;

        private float amt = 0f;

        public GoSlowThreeSecondsScript(CampaignFleetAPI fleet){
            this.fleet = fleet;
            this.playerFleet = Global.getSector().getPlayerFleet();
        }

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
            if(isDone()) return;
            else if (amt > RUNTIME_SECONDS || fleet == null || !fleet.isAlive()) done = true;

            amt += amount;

            if(!done && Misc.getDistance(playerFleet, fleet) > MAX_DISTANCE_FOR_STOP){
                fleet.goSlowOneFrame(true);
            }
        }
    }

    @Override
    public boolean isDone() {
        return super.isDone() || !fleet.isAlive();
    }

    public void clearFlags() {
        MemoryAPI splinterFleetMemory = fleet.getMemoryWithoutUpdate();
        splinterFleetMemory.unset(MemFlags.ENTITY_MISSION_IMPORTANT);
        splinterFleetMemory.unset(MemFlags.CAN_ONLY_BE_ENGAGED_WHEN_VISIBLE_TO_PLAYER);
        splinterFleetMemory.unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        splinterFleetMemory.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
    }

    public void resetOverride(){
        isOverridden = false;
    }

    @Override
    protected void giveInitialAssignments() {
        FleetUtils.log.info("SplinterFleet AI Init");

        restoreAI();
        fleet.clearAssignments();
        fleet.setAIMode(false);
        fleet.setTransponderOn(true);
        fleet.setFaction("player");

        useAbility(Abilities.GO_DARK, false);

        clearFlags();
        setFlags();

        pickNext();
    }

    @Override
    protected void pickNext() {

    }

    @Override
    public void advance(float amount) {
        if (isDone()) {
            Global.getSector().removeListener(this);
            return;
        }

        battleReleaseTimer += amount;

        regsterIfNeeded();
        checkForCombatActions();
        checkCargoForDormancyTriggers();
        checkCargoForDormancyRemoval();
        doOverrideActions();
        goToPlayerInOtherSystemActions();

        if (!inBattle && Behaviour.isBehaviourOverridden(fleet) && Behaviour.behaviourEquals(Behaviour.getFleetBehaviour(fleet, false), Behaviour.FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE)) {
            checkForJumpActions();
        }

        if (!inBattle) super.advance(amount);
    }

    // TODO: 06/02/2022 check if it actually reports anything without being added to ListenerManager
    @Override
    public void reportFleetTransitingGate(CampaignFleetAPI fleet, SectorEntityToken gateFrom, SectorEntityToken gateTo) {
        if (this.fleet.getAI() == null) return;

        if (fleet.isPlayerFleet() && !this.fleet.isInCurrentLocation()) {
            if (Behaviour.isBehaviourOverridden(fleet) && Behaviour.behaviourEquals(Behaviour.getFleetBehaviour(fleet, false), Behaviour.FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE)) {
                addToJumpList(gateFrom, gateTo);
            }
        }
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        if (fleet.getId().equals(this.fleet.getId())) {
            int toRemove = 0;

            for (Map.Entry<Integer, JumpInfo> e : jumpInfoMap.entrySet()) {
                if (e.getValue().to.getDestination().getId().equals(to.getDestination().getId())) {
                    toRemove = e.getKey();
                    break;
                }
                ;
            }

            if (from == null)
                Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has jumped to %s", Misc.getTextColor(), to.getDestination().getContainingLocation().getName(), null, Misc.getHighlightColor(), null);
            else if (from.getCustomPlugin() instanceof GateEntityPlugin)
                Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has used a gate to jump from %s to %s", Misc.getTextColor(), from.getContainingLocation().getName(), to.getDestination().getContainingLocation().getName(), Misc.getHighlightColor(), Misc.getHighlightColor());
            else
                Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has jumped from %s to %s", Misc.getTextColor(), from.getContainingLocation().getName(), to.getDestination().getContainingLocation().getName(), Misc.getHighlightColor(), Misc.getHighlightColor());

            if (toRemove != 0) jumpInfoMap.remove(toRemove);
            fleet.getTags().remove(CURRENTLY_JUMPING_FLAG);
        }
    }

    public void checkForJumpActions() {
        if (inBattle) return;

        if (!jumpInfoMap.isEmpty() && fleet.getAI() != null && !fleet.getTags().contains(CURRENTLY_JUMPING_FLAG)) {
            int smallest = Integer.MAX_VALUE;
            for (Map.Entry<Integer, JumpInfo> e : jumpInfoMap.entrySet()) {
                if (e.getKey() < smallest) {
                    smallest = e.getKey();
                }
            }

            JumpInfo jumpInfo = jumpInfoMap.get(smallest);

            if (fleet.getContainingLocation().getId().equals(jumpInfo.to.getDestination().getContainingLocation().getId())) {
                jumpInfoMap.remove(smallest);
                FleetUtils.log.info("removing jump order to " + jumpInfo.to.getDestination().getContainingLocation().getName() + ", already in location");
                return;
            }

            FleetUtils.log.info("Initializing jump for " + fleet.getName() + ": from " + (jumpInfo.from != null ? jumpInfo.from.getContainingLocation().getName() : "null") + " to " + jumpInfo.to.getDestination().getContainingLocation().getName());
            FleetUtils.log.info("there are " + (jumpInfoMap.size() - 1) + " jumps remaining in the list");

            fleet.addTag(CURRENTLY_JUMPING_FLAG);

            if (jumpInfo.isRegularJump())
                Global.getSector().doHyperspaceTransition(fleet, jumpInfo.from, jumpInfo.to); // TODO: 02/02/2022 might have to clear assignments here
            else Global.getSector().doHyperspaceTransition(fleet, null, jumpInfo.to);
        }
    }

    private void checkForCombatActions() {
        //get combat state
        //reset timer constantly when in battle
        //once out, let it run so it disengages when over time

        if (fleet.getBattle() != null) {
            battleReleaseTimer = 0f;

            if (!inBattle) {
                inBattle = true;
                Behaviour.setFleetBehaviourOverride(fleet, Behaviour.FleetBehaviour.COMBAT);
            }
        } else if (inBattle && battleReleaseTimer > BATTLE_RELEASE_TIME) {
            inBattle = false;
            Behaviour.clearBehaviourOverride(fleet);
        }
    }

    private void checkCargoForDormancyRemoval() {
        if (Behaviour.getFleetBehaviour(fleet, false) != Behaviour.FleetBehaviour.CARGO_DETACHMENT_CHEAT_MODE && !Behaviour.isDormant(fleet)) return;

        boolean hasFuel = fleet.getCargo().getFuel() >= 1f;
        boolean hasSupplies = fleet.getCargo().getSupplies() >= 1f;
        boolean isHyper = fleet.isInHyperspace();

        if (hasSupplies && !isHyper) Behaviour.clearBehaviourOverride(fleet);
        else if(hasSupplies && hasFuel) Behaviour.clearBehaviourOverride(fleet);
    }

    private void checkCargoForDormancyTriggers() {
        if (Behaviour.isDormant(fleet) || Behaviour.getFleetBehaviour(fleet, false) == Behaviour.FleetBehaviour.CARGO_DETACHMENT_CHEAT_MODE) return;

        if (fleet.getCargo().getCommodityQuantity(Commodities.SUPPLIES) < 1f)
            Behaviour.setFleetBehaviourOverride(fleet, Behaviour.FleetBehaviour.DORMANT);
        if (fleet.isInHyperspace() && fleet.getCargo().getFuel() < 1f){
            if(this instanceof DeliverAssignmentAI){
                boolean hasDelivered = false;

                DeliverAssignmentAI ai = (DeliverAssignmentAI) FleetUtils.getAssignmentAI(fleet);
                if (ai != null) hasDelivered = ai.cargoTransferScript.finished;

                if(hasDelivered) Behaviour.setFleetBehaviourOverride(fleet, Behaviour.FleetBehaviour.CARGO_DETACHMENT_CHEAT_MODE);
                else Behaviour.setFleetBehaviourOverride(fleet, Behaviour.FleetBehaviour.DORMANT);

            } else Behaviour.setFleetBehaviourOverride(fleet, Behaviour.FleetBehaviour.DORMANT);
        }
    }

    private void doOverrideActions() {
        boolean isOverridden = Behaviour.isBehaviourOverridden(fleet);

        if (isOverridden != this.isOverridden) {
            FleetUtils.log.info("SplinterFleet AI performing override actions for " + Behaviour.getFleetBehaviour(fleet, false));

            if (!isOverridden) {
                //its no longer overridden, revert to base behaviour
                giveInitialAssignments();

            } else {
                //it is overridden, activate override behaviour
                MemoryAPI splinterFleetMemory = fleet.getMemoryWithoutUpdate();

                switch (Behaviour.getFleetBehaviour(fleet, false)) {
                    case RETURN_TO_PLAYER_AND_MERGE:
                        FleetUtils.log.info("SplinterFleet return to player mode");
                        Global.getSector().getCampaignUI().addMessage(fleet.getName() + " is returning to the main force.");

                        restoreAI();
                        fleet.clearAssignments();
                        clearFlags();

                        splinterFleetMemory.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
                        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);

                        fleet.addFloatingText("Returning to Main Fleet", fleet.getFaction().getBaseUIColor(), 1f);
                        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, Global.getSector().getPlayerFleet(), ASSIGNMENT_DURATION_FOREVER, "Returning to Main Fleet", new Script() {
                            @Override
                            public void run() {
                                FleetUtils.mergeFleetWithPlayerFleet(fleet);
                            }
                        });

                        break;
                    case DORMANT:
                        FleetUtils.log.info("SplinterFleet Dormant mode");
                        Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has gone dormant.");

                        fleet.setFaction(Factions.NEUTRAL);
                        fleet.setAIMode(true);
                        fleet.setTransponderOn(false);

                        lobotomize("Derelict");

                        fleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);

                        SectorEntityToken focus = OrbitFocus.getOrbitFocusAtTokenPosition(fleet);
                        if (focus != null && focus.getOrbit() != null) fleet.setOrbit(focus.getOrbit().makeCopy());
                        fleet.setVelocity(0, 0);

                        break;
                    case COMBAT:
                        FleetUtils.log.info("SplinterFleet combat mode");
                        Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has entered combat.");

                        fleet.clearAssignments();
                        fleet.setAIMode(true);
                        clearFlags();

                        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);

                        fleet.clearAssignments();
                        lobotomize("In Combat");

                        //fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, fleet.getBattle().getClosestInvolvedFleetTo(fleet), ASSIGNMENT_DURATION_FOREVER, "In Combat");
                        break;
                    case HEAD_TO_PLAYER_ON_TARGET:
                        FleetUtils.log.info("setting head to player assignment");

                        fleet.clearAssignments();
                        clearFlags();

                        splinterFleetMemory.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
                        splinterFleetMemory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);

                        fleet.addFloatingText("Moving towards Main Fleet", fleet.getFaction().getBaseUIColor(), 1f);
                        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, Global.getSector().getPlayerFleet(), ASSIGNMENT_DURATION_FOREVER, "Moving towards Main Force");

                        fleet.addScript(new GoSlowThreeSecondsScript(fleet));
                        break;
                    case CARGO_DETACHMENT_CHEAT_MODE:
                        restoreAI();
                        fleet.setAIMode(false);
                        break;
                }
            }

            this.isOverridden = isOverridden;
        }
    }

    public void restoreAI() {
        if (fleet.getAI() == null) fleet.setAI(Global.getFactory().createFleetAI(fleet));
        fleet.setDoNotAdvanceAI(false);
    }

    public void lobotomize(String reason) {
        fleet.setAI(null);
        fleet.setDoNotAdvanceAI(true);
        fleet.setNullAIActionText(reason);
    }

    public void headToPlayerIfTargettedAssignment() {
        if (inBattle) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        SectorEntityToken target = playerFleet.getInteractionTarget();

        if (!Behaviour.isBehaviourOverridden(fleet)
                && target != null
                && target.getId().equals(fleet.getId())) {

            Behaviour.setFleetBehaviourOverride(fleet, Behaviour.FleetBehaviour.HEAD_TO_PLAYER_ON_TARGET);

        } else if (Behaviour.behaviourEquals(Behaviour.getFleetBehaviour(fleet, false), Behaviour.FleetBehaviour.HEAD_TO_PLAYER_ON_TARGET)
                && (target == null || !target.getId().equals(fleet.getId()))) {

            Behaviour.clearBehaviourOverride(fleet);
        }
    }

    public void useAbility(String abilitiy, boolean state) {
        AbilityPlugin ability = fleet.getAbility(abilitiy);
        if (state && ability != null && ability.isUsable() && !ability.isActive())
            fleet.getMemoryWithoutUpdate().set(FleetUtils.USE_ABILITY_MEM_KEY, abilitiy);
        else if (!state && ability != null && ability.isActive())
            fleet.getMemoryWithoutUpdate().unset(FleetUtils.USE_ABILITY_MEM_KEY);
    }

    public void goToPlayerInOtherSystemActions() {
        if (Global.getSector().getPlayerFleet().isInHyperspaceTransition()) return;

        if (fleet != null
                && !fleet.isInCurrentLocation()
                && fleet.getAI() != null
                && jumpInfoMap.isEmpty()
                && !inBattle
                && fleet.getCurrentAssignment() != null
                && fleet.getCurrentAssignment().getTarget().isPlayerFleet()
        ) {

            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

            //player is in hyperspace means we are not
            if (playerFleet.isInHyperspace()) {
                //get a valid system exit location into hyperspace
                JumpPointAPI jp = Misc.getDistressJumpPoint(fleet.getStarSystem());

                if (jp != null) {
                    for (JumpPointAPI.JumpDestination jpDest : jp.getDestinations()) {
                        if (jpDest.getDestination().isInHyperspace()) {
                            addToJumpList(jp, jpDest);
                            return;
                        }
                    }
                } else {
                    //no way to exit the system? brute force it
                    StarSystemAPI sys = fleet.getStarSystem();
                    SectorEntityToken hSJumpFocus = Global.getSector().getHyperspace().createToken(sys.getLocation().x + 50f, sys.getLocation().y);

                    addToJumpList(null, hSJumpFocus);
                }
            } else if (fleet.isInHyperspace() && !playerFleet.isInHyperspace()) {
                //we are in hyperspace means we have to go to where the entry to the system is first
                Pair<JumpPointAPI, JumpPointAPI.JumpDestination> jp = getJumpPointTo(playerFleet.getStarSystem());
                if (jp != null) addToJumpList(jp.one, jp.two);

                else {
                    //being here means there are no JPs in hyperspace, so we head to the anchor and transverse
                    StarSystemAPI sys = Global.getSector().getPlayerFleet().getStarSystem();

                    SectorEntityToken hSJumpFocus = Global.getSector().getHyperspace().addCustomEntity(Misc.genUID(), null, "SplinterFleet_OrbitFocus", Factions.NEUTRAL);
                    hSJumpFocus.setLocation(sys.getLocation().x + 50f, sys.getLocation().y);

                    SectorEntityToken inSystemTarget = null;
                    float largest = Float.MIN_VALUE;

                    for (PlanetAPI p : sys.getPlanets()) {
                        if (p.getRadius() > largest) {
                            largest = p.getRadius();
                            inSystemTarget = p;
                        }
                    }

                    if (inSystemTarget != null) {
                        addToJumpList(hSJumpFocus, inSystemTarget);
                        return;
                    }

                    //there is neither a jp in HS nor an entity to jump to in the system, just add both of them
                    SectorEntityToken inSystemTempJumpFocus = sys.addCustomEntity(Misc.genUID(), null, "SplinterFleet_OrbitFocus", Factions.NEUTRAL);
                    inSystemTempJumpFocus.setLocation(sys.getCenter().getLocation().x + 3000f, sys.getCenter().getLocation().y);

                    addToJumpList(hSJumpFocus, inSystemTempJumpFocus);
                }
            }
        }
    }

    public void addToJumpList(SectorEntityToken from, SectorEntityToken to) {
        addToJumpList(from, new JumpPointAPI.JumpDestination(to, "Jump Point"));
    }

    public void addToJumpList(SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        FleetUtils.log.info("Adding target to jump list for " + fleet.getName() + ": from " + (from != null ? from.getContainingLocation().getName() : "null") + " to " + to.getDestination().getContainingLocation().getName());
        jumpInfoMap.put(jumpInfoMap.size() + 1, new JumpInfo(from, to));
    }

    public static Pair<JumpPointAPI, JumpPointAPI.JumpDestination> getJumpPointTo(StarSystemAPI systemAPI) {
        for (Object entity : Global.getSector().getHyperspace().getEntities(JumpPointAPI.class)) {
            JumpPointAPI jp = (JumpPointAPI) entity;

            for (JumpPointAPI.JumpDestination jpDest : jp.getDestinations()) {
                if (jpDest != null && jpDest.getDestination().getStarSystem() != null && jpDest.getDestination().getStarSystem().getId().equals(systemAPI.getId()))
                    return new Pair<>(jp, jpDest);
            }
        }

        return null;
    }

    public void regsterIfNeeded() {
        if (register) {
            Global.getSector().addListener(this);
            register = false;
        }
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {

    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {

    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {

    }

    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {

    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {

    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {

    }

    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {

    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {

    }

    @Override
    public void reportFleetReachedEntity(CampaignFleetAPI fleet, SectorEntityToken entity) {

    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {

    }

    @Override
    public void reportPlayerReputationChange(String faction, float delta) {

    }

    @Override
    public void reportPlayerReputationChange(PersonAPI person, float delta) {

    }

    @Override
    public void reportPlayerActivatedAbility(AbilityPlugin ability, Object param) {

    }

    @Override
    public void reportPlayerDeactivatedAbility(AbilityPlugin ability, Object param) {

    }

    @Override
    public void reportPlayerDumpedCargo(CargoAPI cargo) {

    }

    @Override
    public void reportPlayerDidNotTakeCargo(CargoAPI cargo) {

    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {

    }
}
