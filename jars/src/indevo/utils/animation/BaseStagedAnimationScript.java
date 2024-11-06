package indevo.utils.animation;

import com.fs.starfarer.api.EveryFrameScript;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseStagedAnimationScript implements EveryFrameScript, CampaignAnimationScript {

    public List<AnimationStage> stages = new ArrayList<>();
    public boolean done = false;

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
        if (done) return;
        if (stages.isEmpty()) loadStages();

        for (AnimationStage stage : stages) stage.advance(amount);
        for (AnimationStage stage : stages) if (!stage.isDone()) return;
        done = true; //only reached if all stages in anim are done
    }

    @Override
    public void addStage(AnimationStage stage) {
        stages.add(stage);
    }
}
