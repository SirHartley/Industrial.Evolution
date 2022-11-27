package indevo.abilities.splitfleet.fleetAssignmentAIs;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

@Deprecated
public class ForceSingleJumpScript implements EveryFrameScript {
    private float days = 0f;
    private float daysUntilAction;

    private CampaignFleetAPI fleet;

    private SectorEntityToken from;
    private SectorEntityToken to;

    private boolean done = false;

    public ForceSingleJumpScript(CampaignFleetAPI fleet, float daysUntilAction, SectorEntityToken from, SectorEntityToken to) {
        this.daysUntilAction = daysUntilAction;
        this.from = from;
        this.to = to;
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
    public void advance(float amount) {
        if(done) return;
        if(fleet.isInCurrentLocation()) {
            done = true;
            return;
        }

        days += Global.getSector().getClock().convertToDays(amount);

        if (days > daysUntilAction && fleet != null && fleet.getStarSystem() != null) {
            jump();
        }
    }

    private void jump(){
        Global.getSector().doHyperspaceTransition(fleet, from, new JumpPointAPI.JumpDestination(to, ""));
        done = true;
    }
}
