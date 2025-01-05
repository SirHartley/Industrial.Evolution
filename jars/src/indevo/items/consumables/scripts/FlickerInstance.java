package indevo.items.consumables.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

public class FlickerInstance implements EveryFrameScript {

    public IntervalUtil interval;

    public FlickerInstance(float interval){
        this.interval = new IntervalUtil(interval, interval);
    }

    //transient
    public static FlickerInstance getOrCreateInstance(float interval) {
        for (EveryFrameScript transientScript : Global.getSector().getTransientScripts()) {
            if (transientScript instanceof FlickerInstance) {
               FlickerInstance script = (FlickerInstance) transientScript;
               if (script.interval.getMaxInterval() == interval) return script;
            }
        }

        FlickerInstance script = new FlickerInstance(interval);
        Global.getSector().addTransientScript(script);
        return script;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);
    }

    public float getIntervalFraction(){
        return Math.min(interval.getElapsed() / interval.getIntervalDuration(), 1f);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
