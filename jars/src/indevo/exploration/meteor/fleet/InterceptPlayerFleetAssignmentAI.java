package indevo.exploration.meteor.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

/**
 * I do not know why the FUCKING fleet keeps running from the player like an absolute RETARD even if I set all the fucking keys there are so here you go, get lobotomized you absolute gremlin
 */
public class InterceptPlayerFleetAssignmentAI extends BaseCampaignEventListener implements EveryFrameScript {

    public CampaignFleetAPI fleet;
    public boolean done = false;
    public boolean hasInteractedWithPlayer = false;

    public InterceptPlayerFleetAssignmentAI(CampaignFleetAPI fleet) {
        super(true);
        this.fleet = fleet;
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
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        super.reportShownInteractionDialog(dialog);

        if (dialog.getInteractionTarget() == fleet) hasInteractedWithPlayer = true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        if (hasInteractedWithPlayer) {
            fleet.getAI().clearAssignments();
            Misc.giveStandardReturnToSourceAssignments(fleet);
            fleet.getAI().setActionTextOverride(null);
            Global.getSector().removeListener(this);

            Misc.makeUnimportant(fleet, MemFlags.ENTITY_MISSION_IMPORTANT);

            done = true;
            return;
        }

        //FUCK YOU PLEASE JUST INTERCEPT THE PLAYER I SWEAR TO GOD
        if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.INTERCEPT) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(), 30);
        };
    }
}