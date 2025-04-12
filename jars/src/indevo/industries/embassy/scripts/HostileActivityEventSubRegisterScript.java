package indevo.industries.embassy.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.impl.campaign.intel.events.ttcr.TriTachyonCommerceRaiding;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.utils.ModPlugin;
import indevo.utils.helper.ReflectionUtils;

/**
 * USE THE LISTENER INSTEAD
 */

@Deprecated
public class HostileActivityEventSubRegisterScript implements EveryFrameScript {

    public static void register() {
        if (!Global.getSector().hasScript(HostileActivityEventSubRegisterScript.class)) {
            Global.getSector().addScript(new HostileActivityEventSubRegisterScript());
        }
    }

    protected IntervalUtil tracker = new IntervalUtil(1f, 1f);

    @Override
    public boolean isDone() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean("$IndEvo_HAE_ScriptCleanupDone");
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.getBoolean("$IndEvo_HAE_ScriptCleanupDone")){
            HostileActivityEventIntel intel = HostileActivityEventIntel.get();
            if (intel != null){
                intel.removeActivityOfClass(TriTachyonHostileActivityFactor.class);
                intel.removeActivityOfClass(LuddicChurchHostileActivityFactor.class);
                intel.addActivity(new TriTachyonHostileActivityFactor(intel), new TriTachyonStandardActivityCause(intel));
                intel.addActivity(new LuddicChurchHostileActivityFactor(intel), new LuddicChurchStandardActivityCause(intel));
            }

            mem.set("$IndEvo_HAE_ScriptCleanupDone", true);
        }
        /*
        tracker.advance(amount);
        if (tracker.intervalElapsed()) {
            if (HostileActivityEventIntel.get() != null) {
                HostileActivityEventIntel intel = HostileActivityEventIntel.get();
                if (intel.getFactorOfClass(HAAmbassadorEventFactor.class) == null) {
                    intel.getFactors().add(0, new HAAmbassadorEventFactor());
                    ModPlugin.log("Adding Hostile Activity Ambassador Intel");
                }
            }
        }*/
    }
}
