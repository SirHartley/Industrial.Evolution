package industrial_evolution.splinterFleet.fleetAssignmentAIs;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * Manages all fleet movement behaviour (combat ect is managed by CombatAndDerelictionScript)
 */
public interface SplinterFleetAssignmentAIV2 extends EveryFrameScript {
    public void setDone();
    public void setFlags();
}
