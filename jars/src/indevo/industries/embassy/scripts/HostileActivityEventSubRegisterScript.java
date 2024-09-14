package indevo.industries.embassy.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.utils.ModPlugin;

import java.util.ArrayList;
import java.util.List;

public class HostileActivityEventSubRegisterScript implements EveryFrameScript {

    public static void register() {
        if (!Global.getSector().hasScript(HostileActivityEventSubRegisterScript.class)) {
           Global.getSector().addScript(new HostileActivityEventSubRegisterScript());
        }
    }

    protected IntervalUtil tracker = new IntervalUtil(1f, 1f);

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        tracker.advance(amount);
        if (tracker.intervalElapsed()) {
            if (HostileActivityEventIntel.get() != null) {
                HostileActivityEventIntel intel = HostileActivityEventIntel.get();
                if (intel.getFactorOfClass(HAAmbassadorEventFactor.class) == null) {

                    //out with the old
                    List<EventFactor> currentFactors = new ArrayList<>(intel.getFactors());

                    for (EventFactor f : currentFactors) {
                        if (f instanceof BaseHostileActivityFactor) intel.removeFactor(f);
                    }

                    //in with the new (we do this so the order of the events isn't fucked up by adding a new factor)
                    intel.addFactor(new HAAmbassadorEventFactor());

                    for (EventFactor f : currentFactors) {
                        if (f instanceof BaseHostileActivityFactor) intel.addFactor(f);
                    }

                    ModPlugin.log("Adding Hostile Activity Ambassador Intel");
                }
            }
        }
    }
}
