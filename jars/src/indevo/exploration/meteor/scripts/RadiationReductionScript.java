package indevo.exploration.meteor.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import indevo.exploration.meteor.renderers.RadiationEffectHandler;

import static indevo.exploration.meteor.renderers.RadiationEffectHandler.ACTIVITY_REDUCTION_PER_SECOND_AFTER_DELAY;

public class RadiationReductionScript implements EveryFrameScript {

    public SectorEntityToken fleet;

    public RadiationReductionScript(SectorEntityToken fleet) {
        this.fleet = fleet;
    }

    @Override
    public boolean isDone() {
        return RadiationEffectHandler.get().getActivityLevel(fleet) <= 0;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        float activity = RadiationEffectHandler.get().getActivityLevel(fleet);
        activity -= ACTIVITY_REDUCTION_PER_SECOND_AFTER_DELAY * amount;
        if (activity < 0) activity = 0;
        RadiationEffectHandler.get().setActivityLevel(fleet, activity);
    }
}
