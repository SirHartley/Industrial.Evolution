package indevo.abilities.splitfleet.fleetManagement;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.utils.IndEvo_modPlugin;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetAssignmentAIs.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Behaviour {
    public static final Logger log = Global.getLogger(Behaviour.class);

    public static final String DETACHMENT_BEHAVIOUR_MEMORY_KEY = "$SplinterFleetBehaviourMode";
    public static final String DETACHMENT_BEHAVIOUR_OVERRIDE_MEMORY_KEY = "$SplinterFleetBehaviourModeOverride";
    public static final String RETURNING_MEMORY_KEY = "$SplinterFleet_IsReturning";

    //some highlights may not work unless these are also translated to chinese
    public enum FleetBehaviour {
        //base
        STAY,
        HIDE,
        ESCORT,
        INTERCEPT,
        FOLLOW,
        TRANSPORT,
        DELIVER,

        //overrides
        RETURN_TO_PLAYER_AND_MERGE,
        CARGO_DETACHMENT_CHEAT_MODE,
        HEAD_TO_PLAYER_ON_TARGET,
        DORMANT,
        COMBAT,
    }

    public static Map<Integer, FleetBehaviour> behaviourIndexMap = new HashMap<Integer, FleetBehaviour>() {{
        put(1, FleetBehaviour.STAY);
        put(2, FleetBehaviour.FOLLOW);
        put(3, FleetBehaviour.HIDE);
        put(4, FleetBehaviour.ESCORT);
        put(5, FleetBehaviour.INTERCEPT);
        put(6, FleetBehaviour.TRANSPORT);
        //put(7, FleetBehaviour.DELIVER);
    }};

    public static Map<Integer, String> behaviourTooltipMap = new HashMap<Integer, String>() {{
        put(1, "Stay in place, do not engage enemy fleets unless attacked.");
        put(2, "Follow the main force, do not engage enemy fleets unless attacked.");
        put(3, "Go dark, attempt to hide in a good location, and avoid other fleets.");
        put(4, "Escort the main force, attack equal-strength visible hostile fleets.");
        put(5, "Escort, only intercept enemy fleets that target the main force.");
        put(6, "Travel to selected planet, then store all ships and cargo.");
        put(7, "Travel to selected planet, deliver cargo, then return to main force.");
    }};

    public static Map<FleetBehaviour, Color> behaviourColourMap = new HashMap<FleetBehaviour, Color>() {{
        put(FleetBehaviour.STAY, new Color(50, 150, 0, 255));
        put(FleetBehaviour.FOLLOW, new Color(0, 150, 150, 255));
        put(FleetBehaviour.HIDE, Color.BLUE);
        put(FleetBehaviour.ESCORT, Color.ORANGE);
        put(FleetBehaviour.INTERCEPT, Color.RED);
        put(FleetBehaviour.TRANSPORT, Color.MAGENTA);
        put(FleetBehaviour.DELIVER, Color.PINK);
    }};

    public static Color getColourForBehaviour(FleetBehaviour behaviour) {
        return behaviourColourMap.get(behaviour);
    }

    public static Color getColourForBehaviour(int i) {
        return behaviourColourMap.get(getBehaviourForIndex(i));
    }

    public static void addBehaviourListTooltip(TextPanelAPI text) {
        float opad = 10f;
        float spad = 3f;

        TooltipMakerAPI tooltip = text.beginTooltip();

        tooltip.addSectionHeading("Behaviour modes", Alignment.MID, opad);
        text.addTooltip();

        text.setFontSmallInsignia();

        for (int i = 1; i <= Behaviour.behaviourIndexMap.size(); i++) {
            FleetBehaviour b = Behaviour.getBehaviourForIndex(i);
            String s = Misc.ucFirst(b.toString().toLowerCase());

            text.addPara(s + " - " + Behaviour.behaviourTooltipMap.get(i));
            text.highlightInLastPara(Behaviour.getColourForBehaviour(b), s);
        }

        text.setFontInsignia();

        /*TooltipMakerAPI tooltip = text.beginTooltip();
        tooltip.addSectionHeading("Behaviour modes", Alignment.MID, opad);

        tooltip.setParaSmallInsignia();

        for (int i = 1; i <= Behaviour.behaviourIndexMap.size(); i++){
            String s = Misc.ucFirst(Behaviour.getBehaviourForIndex(i).toString());
            tooltip.addPara("%s - " + Behaviour.behaviourTooltipMap.get(i), spad, Behaviour.getColourForBehaviour(i), s);
        }

        text.addTooltip();*/
    }

    public static FleetBehaviour getBehaviourForIndex(int num) {
        return behaviourIndexMap.get(num);
    }

    public static boolean behaviourEquals(FleetBehaviour a, FleetBehaviour b) {
        boolean aNotNull = a != null;
        boolean bNotNull = b != null;

        return (!aNotNull && !bNotNull) //both are null
                || (aNotNull && bNotNull && a.toString().equals(b.toString()));
    }

    public static int getIndexForBehaviour(FleetBehaviour behaviour) {
        for (Map.Entry<Integer, FleetBehaviour> e : behaviourIndexMap.entrySet()) {
            if (behaviourEquals(behaviour, e.getValue())) return e.getKey();
        }

        return 1;
    }

    public static FleetBehaviour getFleetBehaviour(CampaignFleetAPI fleet, boolean ignoreOverride) {
        if (fleet == null) return null;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        if (!ignoreOverride && isBehaviourOverridden(fleet))
            return (FleetBehaviour) mem.get(DETACHMENT_BEHAVIOUR_OVERRIDE_MEMORY_KEY);
        else return (FleetBehaviour) mem.get(DETACHMENT_BEHAVIOUR_MEMORY_KEY);
    }

    public static boolean isDormant(CampaignFleetAPI fleet) {
        return isBehaviour(fleet, FleetBehaviour.DORMANT);
    }

    private static void changeBehaviour(CampaignFleetAPI fleet, FleetBehaviour behaviour) {
        int num = DetachmentMemory.getNumForFleet(fleet);

        fleet.getMemoryWithoutUpdate().set(DETACHMENT_BEHAVIOUR_MEMORY_KEY, behaviour);
        if(num > 0) LoadoutMemory.getLoadout(num).behaviour = behaviour;

        IndEvo_modPlugin.log("changing detachment behaviour to " + behaviour.toString() + " for detachment " + num);
    }

    public static void setFleetBehaviourOverride(CampaignFleetAPI fleet, FleetBehaviour behaviour) {
        log.info("Setting detachment behaviour override " + behaviour.toString());
        fleet.getMemoryWithoutUpdate().set(DETACHMENT_BEHAVIOUR_OVERRIDE_MEMORY_KEY, behaviour);
    }

    public static boolean isBehaviourOverridden(CampaignFleetAPI fleet) {
        return fleet.getMemoryWithoutUpdate().get(DETACHMENT_BEHAVIOUR_OVERRIDE_MEMORY_KEY) != null;
    }

    public static void clearBehaviourOverride(CampaignFleetAPI fleet) {
        log.info("clearing detachment behaviour override");
        fleet.getMemoryWithoutUpdate().unset(DETACHMENT_BEHAVIOUR_OVERRIDE_MEMORY_KEY);
    }

    public static boolean isReturning(CampaignFleetAPI fleet) {
        return isBehaviour(fleet, FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE);
    }

    public static void setReturning(int num, boolean returnToFleet) {
        setReturning(DetachmentMemory.getDetachment(num), returnToFleet);
    }

    public static void setReturning(CampaignFleetAPI fleet, boolean returnToFleet) {
        if (returnToFleet) Behaviour.setFleetBehaviourOverride(fleet, FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE);

        if (!returnToFleet && isBehaviour(fleet, FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE)) {
            Behaviour.clearBehaviourOverride(fleet);
        }
    }

    public static boolean isBehaviour(CampaignFleetAPI fleet, Behaviour.FleetBehaviour behaviour) {
        if (fleet == null || behaviour == null) return false;
        return Behaviour.behaviourEquals(Behaviour.getFleetBehaviour(fleet, false), behaviour);
    }

    public static void changeBehaviourAndUpdateAI(CampaignFleetAPI fleet, FleetBehaviour newBehaviour) {
        FleetBehaviour currentBehaviour = getFleetBehaviour(fleet, true);
        if (behaviourEquals(newBehaviour, currentBehaviour) && FleetUtils.getAssignmentAI(fleet) != null) return;

        FleetUtils.log.info("Assigning new detachment AI for behaviour " + newBehaviour);

        switch (newBehaviour) {
            case HIDE:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new HideAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
            case ESCORT:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new EscortAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
            /*case PATROL:
                setOrReplaceAssignmentAI(fleet, new PatrolAssignmentAI(fleet, system));
                break;*/
            case INTERCEPT:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new InterceptAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
            case FOLLOW:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new FollowAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
            case TRANSPORT:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new TransportAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
            case DELIVER:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new DeliverAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
            case RETURN_TO_PLAYER_AND_MERGE:
            case DORMANT:
                setFleetBehaviourOverride(fleet, newBehaviour);
                break;
            default:
                FleetUtils.setOrReplaceAssignmentAI(fleet, new StayAssignmentAI(fleet));
                changeBehaviour(fleet, newBehaviour);
                break;
        }
    }

    public static void updateActiveDetachmentBehaviour() {
        if (!DetachmentMemory.isAnyDetachmentActive()) return;

        for (int i : Arrays.asList(1, 2, 3)) {
            if (DetachmentMemory.isDetachmentActive(i)) {
                CampaignFleetAPI fleet = DetachmentMemory.getDetachment(i);
                LoadoutMemory.Loadout loadout = LoadoutMemory.getLoadout(i);

                if (!behaviourEquals(loadout.behaviour, getFleetBehaviour(fleet, true))) {

                    changeBehaviourAndUpdateAI(fleet, loadout.behaviour);
                }
            }
        }
    }
}
