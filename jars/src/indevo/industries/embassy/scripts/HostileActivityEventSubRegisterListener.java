package indevo.industries.embassy.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ColonyCrisesSetupListener;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import indevo.utils.ModPlugin;

public class HostileActivityEventSubRegisterListener implements ColonyCrisesSetupListener {

    public static void register() {
        Global.getSector().getListenerManager().addListener(new HostileActivityEventSubRegisterListener(), true);
    }

    @Override
    public void finishedAddingCrisisFactors(HostileActivityEventIntel intel) {

        if (intel.getFactorOfClass(HAAmbassadorEventFactor.class) == null) {
            intel.getFactors().add(0, new HAAmbassadorEventFactor());
            ModPlugin.log("Adding Hostile Activity Ambassador Intel");
        }
    }
}
