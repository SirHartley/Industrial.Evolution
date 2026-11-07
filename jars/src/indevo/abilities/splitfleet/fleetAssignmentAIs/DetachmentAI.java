package indevo.abilities.splitfleet.fleetAssignmentAIs;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.OrbitFocus;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;
import org.apache.log4j.Logger;

import java.util.Set;

// one per detachment: picks vanilla assignments for the effective behaviour (base or highest override), travel is vanilla
public class DetachmentAI implements EveryFrameScript {

    public static final Logger log = Global.getLogger(DetachmentAI.class);

    public static final float FOREVER = 99999f;
    public static final float BATTLE_RELEASE_SECONDS = 3f;
    public static final float HEAD_TO_PLAYER_RANGE = 1000f;
    public static final float HIDE_DRIFT_RANGE = 100f;
    public static final float ESCORT_FP_MULT = 1.6f;

    protected CampaignFleetAPI fleet;
    protected FleetBehaviour applied = null;
    protected SectorEntityToken holdFocus = null;
    protected SectorEntityToken hideFocus = null;
    protected String transportMarketId = null;
    protected float battleReleaseTimer = 0f;
    protected IntervalUtil marketCheck = new IntervalUtil(1f, 1f);
    protected IntervalUtil engageCheck = new IntervalUtil(0.05f, 0.1f);

    public static class MergeScript implements Script {
        protected CampaignFleetAPI fleet;

        public MergeScript(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public void run() {
            if (fleet.isAlive()) FleetUtils.mergeFleetWithPlayerFleet(fleet);
        }
    }

    public static class HideArrivalScript implements Script {
        protected CampaignFleetAPI fleet;
        protected SectorEntityToken focus;

        public HideArrivalScript(CampaignFleetAPI fleet, SectorEntityToken focus) {
            this.fleet = fleet;
            this.focus = focus;
        }

        @Override
        public void run() {
            if (focus.getOrbit() != null) fleet.setOrbit(focus.getOrbit().makeCopy());
            lobotomize(fleet, "Hiding");
        }
    }

    public static class GoSlowScript implements EveryFrameScript {
        private static final float MAX_DISTANCE_FOR_STOP = 500f;
        private static final float RUNTIME_SECONDS = 3f;

        protected CampaignFleetAPI fleet;
        protected float elapsed = 0f;

        public GoSlowScript(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        @Override
        public boolean isDone() {
            return elapsed > RUNTIME_SECONDS || !fleet.isAlive();
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            elapsed += amount;
            if (Misc.getDistance(Global.getSector().getPlayerFleet(), fleet) > MAX_DISTANCE_FOR_STOP) fleet.goSlowOneFrame(true);
        }
    }

    public DetachmentAI(CampaignFleetAPI fleet) {
        this.fleet = fleet;
    }

    public CampaignFleetAPI getFleet() {
        return fleet;
    }

    public String getTransportMarketId() {
        return transportMarketId;
    }

    @Override
    public boolean isDone() {
        return !fleet.isAlive() || fleet.getContainingLocation() == null;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    public void baseBehaviourChanged() {
        FleetBehaviour base = Behaviour.getFleetBehaviour(fleet, true);

        holdFocus = base == FleetBehaviour.STAY ? OrbitFocus.getOrbitFocusAtTokenPosition(fleet) : null;
        hideFocus = base == FleetBehaviour.HIDE ? OrbitFocus.getHideOrbitFocus(fleet) : null;

        int num = DetachmentMemory.getNumForFleet(fleet);
        transportMarketId = base == FleetBehaviour.TRANSPORT && num > 0 ? LoadoutMemory.getLoadout(num).transportTargetMarket : null;

        refresh();
    }

    public void refresh() {
        applied = null;
    }

    public void addUsedEntities(Set<SectorEntityToken> used) {
        if (holdFocus != null) used.add(holdFocus);
        if (hideFocus != null) used.add(hideFocus);
    }

    @Override
    public void advance(float amount) {
        if (isDone()) return;

        updateCombat(amount);
        updateDormancy();
        updateHeadToPlayer();
        updateStranded();

        FleetBehaviour current = Behaviour.getFleetBehaviour(fleet, false);
        if (current == null) return;

        if (current != applied) {
            log.info(fleet.getName() + " applying " + current + (applied != null ? ", was " + applied : ""));
            apply(current);
            applied = current;
        }

        act(current, amount);
    }

    private void updateCombat(float amount) {
        battleReleaseTimer += amount;

        if (fleet.getBattle() != null) {
            battleReleaseTimer = 0f;
            Behaviour.addOverride(fleet, FleetBehaviour.COMBAT);
        } else if (battleReleaseTimer > BATTLE_RELEASE_SECONDS) Behaviour.removeOverride(fleet, FleetBehaviour.COMBAT);
    }

    private void updateDormancy() {
        boolean noSupplies = fleet.getCargo().getSupplies() < 1f;
        boolean dormant = Behaviour.isDormant(fleet);

        if (noSupplies && !dormant) {
            Behaviour.addOverride(fleet, FleetBehaviour.DORMANT);
            Global.getSector().getCampaignUI().addMessage(fleet.getName() + " is out of supplies and has gone dormant.", Misc.getNegativeHighlightColor());
        } else if (!noSupplies && dormant) {
            Behaviour.removeOverride(fleet, FleetBehaviour.DORMANT);
            Global.getSector().getCampaignUI().addMessage(fleet.getName() + " has been resupplied and is no longer dormant.");
        }
    }

    private void updateHeadToPlayer() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        SectorEntityToken target = player.getInteractionTarget();
        boolean targeted = target == fleet && fleet.getContainingLocation() == player.getContainingLocation();

        if (targeted && canHeadToPlayer()) Behaviour.addOverride(fleet, FleetBehaviour.HEAD_TO_PLAYER_ON_TARGET);
        else if (!targeted) Behaviour.removeOverride(fleet, FleetBehaviour.HEAD_TO_PLAYER_ON_TARGET);
    }

    private boolean canHeadToPlayer() {
        FleetBehaviour base = Behaviour.getFleetBehaviour(fleet, true);
        if (base == null || Behaviour.hasOverride(fleet, FleetBehaviour.HEAD_TO_PLAYER_ON_TARGET) || Behaviour.isReturning(fleet)) return false;

        switch (base) {
            case STAY:
                return Misc.getDistance(fleet, Global.getSector().getPlayerFleet()) < HEAD_TO_PLAYER_RANGE;
            case FOLLOW:
            case ESCORT:
            case INTERCEPT:
                return true;
            case TRANSPORT:
                //not once it is unloading or landing
                return fleet.getCurrentAssignment() != null && fleet.getCurrentAssignment().getAssignment() == FleetAssignment.GO_TO_LOCATION;
            default:
                return false;
        }
    }

    // vanilla StrategicModule.updateJumpPlanTo drops the jump plan at 0 fuel with ai mode off, NoFuelDriftScript only covers hyperspace
    private void updateStranded() {
        SectorEntityToken target = getTravelTarget();
        boolean stranded = target != null
                && fleet.getCargo().getFuel() <= 0f
                && !fleet.isInHyperspace()
                && !fleet.isInHyperspaceTransition()
                && target.getContainingLocation() != fleet.getContainingLocation();

        boolean wasStranded = Behaviour.isStranded(fleet);
        if (stranded && !wasStranded) {
            Behaviour.addOverride(fleet, FleetBehaviour.STRANDED);
            Global.getSector().getCampaignUI().addMessage(fleet.getName() + " is out of fuel and stranded in " + fleet.getContainingLocation().getName() + ".", Misc.getNegativeHighlightColor());
        } else if (!stranded && wasStranded) {
            Behaviour.removeOverride(fleet, FleetBehaviour.STRANDED);
        }
    }

    public SectorEntityToken getTravelTarget() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (Behaviour.isReturning(fleet)) return player;

        FleetBehaviour base = Behaviour.getFleetBehaviour(fleet, true);
        if (base == null) return null;

        switch (base) {
            case STAY:
                return holdFocus;
            case HIDE:
                return hideFocus;
            case FOLLOW:
            case ESCORT:
            case INTERCEPT:
                return player;
            case TRANSPORT:
                MarketAPI market = getTransportMarket();
                return market != null ? market.getPrimaryEntity() : null;
            default:
                return null;
        }
    }

    protected void apply(FleetBehaviour behaviour) {
        switch (behaviour) {
            case COMBAT:
                fleet.clearAssignments();
                lobotomize(fleet, "In combat");
                break;
            case DORMANT:
                applyDormant();
                break;
            case STRANDED:
                reset();
                setFlags(false, true, true);
                lobotomize(fleet, "Out of fuel");
                fleet.setVelocity(0, 0);
                break;
            case HEAD_TO_PLAYER_ON_TARGET:
                reset();
                setFlags(true, true, false);
                fleet.addFloatingText("Moving towards main force", fleet.getFaction().getBaseUIColor(), 1f);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, Global.getSector().getPlayerFleet(), FOREVER, "Moving towards main force");
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, Global.getSector().getPlayerFleet(), FOREVER, "Waiting for orders");
                fleet.addScript(new GoSlowScript(fleet));
                break;
            case RETURN_TO_PLAYER_AND_MERGE:
                reset();
                setFlags(true, true, false);
                fleet.addFloatingText("Returning to main force", fleet.getFaction().getBaseUIColor(), 1f);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, Global.getSector().getPlayerFleet(), FOREVER, "Returning to main force", new MergeScript(fleet));
                break;
            default:
                applyBase(behaviour);
                break;
        }
    }

    protected void applyBase(FleetBehaviour behaviour) {
        reset();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        switch (behaviour) {
            case STAY:
                setFlags(false, true, true);
                if (holdFocus == null) holdFocus = OrbitFocus.getOrbitFocusAtTokenPosition(fleet);
                fleet.addAssignment(FleetAssignment.HOLD, holdFocus, FOREVER, "Holding position");
                break;
            case FOLLOW:
                setFlags(false, true, true);
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, player, FOREVER, "Following");
                break;
            case HIDE:
                setFlags(true, true, false);
                if (hideFocus == null) hideFocus = OrbitFocus.getHideOrbitFocus(fleet);
                fleet.addFloatingText("Heading to hiding place", fleet.getFaction().getBaseUIColor(), 1f);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, hideFocus, FOREVER, "Heading to hiding place", new HideArrivalScript(fleet, hideFocus));
                useAbility(Abilities.GO_DARK, true);
                break;
            case ESCORT:
                setFlags(false, true, true);
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, player, FOREVER, "Escorting");
                break;
            case INTERCEPT:
                setFlags(false, true, true);
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, player, FOREVER, "Waiting to intercept");
                break;
            case TRANSPORT:
                setFlags(true, true, false);
                if (getTransportMarket() == null) checkTransportMarket();
                MarketAPI market = getTransportMarket();
                if (market == null) {
                    log.warn(fleet.getName() + " has no market to transport to, returning to the main force");
                    Behaviour.setReturning(fleet, true);
                    return;
                }

                SectorEntityToken entity = market.getPrimaryEntity();
                fleet.addFloatingText("Heading to " + market.getName(), fleet.getFaction().getBaseUIColor(), 1f);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, entity, FOREVER, "Delivering cargo to " + market.getName());
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, entity, 3f, "Unloading cargo");
                //despawn reports REACHED_DESTINATION, CombatAndDerelictionScript stores ships and cargo there
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, entity, 3f, "Landing");
                break;
        }
    }

    private void applyDormant() {
        fleet.clearAssignments();
        clearFlags();

        fleet.setFaction(Factions.NEUTRAL);
        //ai mode stops supply use, accidents and crew requirements while dormant
        fleet.setAIMode(true);
        fleet.setTransponderOn(false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.ENTITY_MISSION_IMPORTANT, true);
        lobotomize(fleet, "Derelict");

        SectorEntityToken focus = OrbitFocus.getOrbitFocusAtTokenPosition(fleet);
        if (focus != null) {
            if (focus.getOrbit() != null) fleet.setOrbit(focus.getOrbit().makeCopy());
            focus.getContainingLocation().removeEntity(focus);
        }

        fleet.setVelocity(0, 0);
    }

    private void reset() {
        fleet.setOrbit(null);
        restoreAI();
        fleet.clearAssignments();
        fleet.setAIMode(false);
        fleet.setTransponderOn(true);
        fleet.setFaction(Factions.PLAYER);
        useAbility(Abilities.GO_DARK, false);
        clearFlags();
    }

    protected void act(FleetBehaviour behaviour, float amount) {
        if (fleet.getAI() == null) {
            if (behaviour == FleetBehaviour.HIDE && hideFocus != null && Misc.getDistance(fleet.getLocation(), hideFocus.getLocation()) > HIDE_DRIFT_RANGE) {
                log.info(fleet.getName() + " was moved while hiding, heading back");
                refresh();
            }

            return;
        }

        if (fleet.getCurrentAssignment() == null) {
            refresh();
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);

        //no callback for a hostile fleet coming into view or targeting the player, so escorts scan every 0.05 to 0.1 days
        switch (behaviour) {
            case ESCORT:
            case INTERCEPT:
                engageCheck.advance(days);
                if (engageCheck.intervalElapsed()) engage(behaviour == FleetBehaviour.ESCORT);
                break;
            case TRANSPORT:
                marketCheck.advance(days);
                if (marketCheck.intervalElapsed()) checkTransportMarket();
                break;
        }
    }

    private void engage(boolean attackVisible) {
        FleetAssignment current = fleet.getCurrentAssignment().getAssignment();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (current == FleetAssignment.INTERCEPT) {
            SectorEntityToken target = fleet.getCurrentAssignment().getTarget();
            if (target == null || !target.isAlive() || !target.isVisibleToSensorsOf(fleet)) {
                fleet.addFloatingText("Standing down", fleet.getFaction().getBaseUIColor(), 1f);
                refresh();
            }

            return;
        }

        if (current != FleetAssignment.ORBIT_PASSIVE) return;

        CampaignFleetAPI enemy = null;
        boolean useEBurn = true;

        if (player.getInteractionTarget() instanceof CampaignFleetAPI target && target.isHostileTo(player)) enemy = target;

        if (enemy == null) {
            for (CampaignFleetAPI other : Misc.getVisibleFleets(fleet, true)) {
                if (!other.isHostileTo(fleet)) continue;

                SectorEntityToken otherTarget = other.getInteractionTarget();
                boolean targetsPlayer = otherTarget != null && otherTarget == player;
                boolean fleeing = other.getAI() != null && other.getAI().isFleeing();
                boolean defeatable = other.getFleetPoints() < fleet.getFleetPoints() * ESCORT_FP_MULT;

                if (targetsPlayer || (attackVisible && (defeatable || fleeing))) {
                    enemy = other;
                    useEBurn = targetsPlayer || !attackVisible;
                    break;
                }
            }
        }

        if (enemy == null) return;

        fleet.clearAssignments();
        clearFlags();
        fleet.addFloatingText("Intercepting " + enemy.getName(), fleet.getFaction().getBaseUIColor(), 1f);
        fleet.addAssignment(FleetAssignment.INTERCEPT, enemy, 3f, "Intercepting " + enemy.getName());

        if (useEBurn) {
            AbilityPlugin eb = fleet.getAbility(Abilities.EMERGENCY_BURN);
            if (eb != null && eb.isUsable()) eb.activate();
        }
    }

    public MarketAPI getTransportMarket() {
        if (transportMarketId == null) {
            int num = DetachmentMemory.getNumForFleet(fleet);
            if (num > 0) transportMarketId = LoadoutMemory.getLoadout(num).transportTargetMarket;
        }

        return transportMarketId != null ? Global.getSector().getEconomy().getMarket(transportMarketId) : null;
    }

    private void checkTransportMarket() {
        if (getTransportMarket() != null) return;

        MarketAPI alternate = FleetUtils.getClosestStorageMarket(fleet);
        if (alternate == null) return;

        transportMarketId = alternate.getId();
        Global.getSector().getCampaignUI().addMessage(fleet.getName() + " changed its destination to %s in %s.", Misc.getTextColor(), alternate.getName(), alternate.getContainingLocation().getName(), alternate.getFaction().getBaseUIColor(), Misc.getHighlightColor());
        refresh();
    }

    public static void lobotomize(CampaignFleetAPI fleet, String reason) {
        fleet.setAI(null);
        fleet.setDoNotAdvanceAI(true);
        fleet.setNullAIActionText(reason);
    }

    private void restoreAI() {
        if (fleet.getAI() == null) fleet.setAI(Global.getFactory().createFleetAI(fleet));
        fleet.setDoNotAdvanceAI(false);
    }

    private void setFlags(boolean ignoredByOthers, boolean ignoresOthers, boolean onlyEngagedWhenVisible) {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (ignoredByOthers) mem.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        if (ignoresOthers) mem.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        if (onlyEngagedWhenVisible) mem.set(MemFlags.CAN_ONLY_BE_ENGAGED_WHEN_VISIBLE_TO_PLAYER, true);
    }

    private void clearFlags() {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.unset(MemFlags.ENTITY_MISSION_IMPORTANT);
        mem.unset(MemFlags.CAN_ONLY_BE_ENGAGED_WHEN_VISIBLE_TO_PLAYER);
        mem.unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        mem.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
    }

    private void useAbility(String id, boolean state) {
        AbilityPlugin ability = fleet.getAbility(id);
        if (ability == null) return;

        if (state && ability.isUsable() && !ability.isActive()) fleet.getMemoryWithoutUpdate().set(FleetUtils.USE_ABILITY_MEM_KEY, id);
        else if (!state) fleet.getMemoryWithoutUpdate().unset(FleetUtils.USE_ABILITY_MEM_KEY);
    }
}
