package indevo.abilities.splitfleet.fleetManagement;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetAssignmentAIs.DetachmentAI;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Behaviour {
    public static final Logger log = Global.getLogger(Behaviour.class);

    public static final String DETACHMENT_BEHAVIOUR_MEMORY_KEY = "$SplinterFleetBehaviourMode";
    public static final String DETACHMENT_OVERRIDES_MEMORY_KEY = "$IndEvo_detachmentOverrides";

    //some highlights may not work unless these are also translated to chinese
    public enum FleetBehaviour {
        //base
        STAY,
        HIDE,
        ESCORT,
        INTERCEPT,
        FOLLOW,
        TRANSPORT,

        //overrides
        RETURN_TO_PLAYER_AND_MERGE,
        HEAD_TO_PLAYER_ON_TARGET,
        STRANDED,
        DORMANT,
        COMBAT,
    }

    //highest first, the first active override is the effective behaviour
    public static final List<FleetBehaviour> OVERRIDE_PRIORITY = Arrays.asList(
            FleetBehaviour.COMBAT,
            FleetBehaviour.DORMANT,
            FleetBehaviour.HEAD_TO_PLAYER_ON_TARGET,
            FleetBehaviour.STRANDED,
            FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE);

    public static Map<Integer, FleetBehaviour> behaviourIndexMap = new HashMap<Integer, FleetBehaviour>() {{
        put(1, FleetBehaviour.STAY);
        put(2, FleetBehaviour.FOLLOW);
        put(3, FleetBehaviour.HIDE);
        put(4, FleetBehaviour.ESCORT);
        put(5, FleetBehaviour.INTERCEPT);
        put(6, FleetBehaviour.TRANSPORT);
    }};

    public static Map<Integer, String> behaviourTooltipMap = new HashMap<Integer, String>() {{
        put(1, "Stay in place, do not engage enemy fleets unless attacked.");
        put(2, "Follow the main force, do not engage enemy fleets unless attacked.");
        put(3, "Go dark, attempt to hide in a good location, and avoid other fleets.");
        put(4, "Escort the main force, attack equal-strength visible hostile fleets.");
        put(5, "Escort, only intercept enemy fleets that target the main force.");
        put(6, "Travel to selected planet, then store all ships and cargo.");
    }};

    public static Map<FleetBehaviour, Color> behaviourColourMap = new HashMap<FleetBehaviour, Color>() {{
        put(FleetBehaviour.STAY, new Color(50, 150, 0, 255));
        put(FleetBehaviour.FOLLOW, new Color(0, 150, 150, 255));
        put(FleetBehaviour.HIDE, Color.BLUE);
        put(FleetBehaviour.ESCORT, Color.ORANGE);
        put(FleetBehaviour.INTERCEPT, Color.RED);
        put(FleetBehaviour.TRANSPORT, Color.MAGENTA);
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

        if (!ignoreOverride) {
            List<FleetBehaviour> overrides = getOverrides(fleet);
            for (FleetBehaviour b : OVERRIDE_PRIORITY) if (overrides.contains(b)) return b;
        }

        return (FleetBehaviour) fleet.getMemoryWithoutUpdate().get(DETACHMENT_BEHAVIOUR_MEMORY_KEY);
    }

    public static List<FleetBehaviour> getOverrides(CampaignFleetAPI fleet) {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        Object o = mem.get(DETACHMENT_OVERRIDES_MEMORY_KEY);
        if (o instanceof List) return (List<FleetBehaviour>) o;

        List<FleetBehaviour> overrides = new ArrayList<>();
        mem.set(DETACHMENT_OVERRIDES_MEMORY_KEY, overrides);
        return overrides;
    }

    public static boolean hasOverride(CampaignFleetAPI fleet, FleetBehaviour behaviour) {
        return fleet != null && getOverrides(fleet).contains(behaviour);
    }

    public static void addOverride(CampaignFleetAPI fleet, FleetBehaviour behaviour) {
        List<FleetBehaviour> overrides = getOverrides(fleet);
        if (overrides.contains(behaviour)) return;

        log.info("adding detachment override " + behaviour + " to " + fleet.getName());
        overrides.add(behaviour);
    }

    public static void removeOverride(CampaignFleetAPI fleet, FleetBehaviour behaviour) {
        if (getOverrides(fleet).remove(behaviour)) log.info("removing detachment override " + behaviour + " from " + fleet.getName());
    }

    public static boolean isBehaviourOverridden(CampaignFleetAPI fleet) {
        return !getOverrides(fleet).isEmpty();
    }

    public static boolean isDormant(CampaignFleetAPI fleet) {
        return hasOverride(fleet, FleetBehaviour.DORMANT);
    }

    public static boolean isStranded(CampaignFleetAPI fleet) {
        return hasOverride(fleet, FleetBehaviour.STRANDED);
    }

    public static boolean isReturning(CampaignFleetAPI fleet) {
        return hasOverride(fleet, FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE);
    }

    public static void setReturning(int num, boolean returnToFleet) {
        setReturning(DetachmentMemory.getDetachment(num), returnToFleet);
    }

    public static void setReturning(CampaignFleetAPI fleet, boolean returnToFleet) {
        if (returnToFleet) {
            addOverride(fleet, FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE);
            Global.getSector().getCampaignUI().addMessage(fleet.getName() + " is returning to the main force.");
        } else removeOverride(fleet, FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE);
    }

    public static boolean isBehaviour(CampaignFleetAPI fleet, Behaviour.FleetBehaviour behaviour) {
        if (fleet == null || behaviour == null) return false;
        return Behaviour.behaviourEquals(Behaviour.getFleetBehaviour(fleet, false), behaviour);
    }

    public static boolean canSwitchLiveTo(FleetBehaviour behaviour) {
        return behaviour != FleetBehaviour.TRANSPORT;
    }

    public static void changeBehaviourAndUpdateAI(CampaignFleetAPI fleet, FleetBehaviour newBehaviour) {
        DetachmentAI ai = FleetUtils.getAssignmentAI(fleet);
        if (ai != null && behaviourEquals(newBehaviour, getFleetBehaviour(fleet, true))) return;

        int num = DetachmentMemory.getNumForFleet(fleet);
        fleet.getMemoryWithoutUpdate().set(DETACHMENT_BEHAVIOUR_MEMORY_KEY, newBehaviour);
        if (num > 0) LoadoutMemory.getLoadout(num).behaviour = newBehaviour;
        log.info("changing detachment behaviour to " + newBehaviour + " for detachment " + num);

        if (ai == null) {
            ai = new DetachmentAI(fleet);
            fleet.addScript(ai);
        }

        ai.baseBehaviourChanged();
    }

    public static void updateActiveDetachmentBehaviour() {
        if (!DetachmentMemory.isAnyDetachmentActive()) return;

        for (int i : DetachmentMemory.SLOTS) {
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
