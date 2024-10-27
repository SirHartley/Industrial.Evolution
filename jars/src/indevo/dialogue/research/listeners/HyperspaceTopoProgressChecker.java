package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel;
import indevo.dialogue.research.intel.GalatiaNewProjectsIntel;
import indevo.ids.Ids;

public class HyperspaceTopoProgressChecker implements EconomyTickListener {

    public static void register(){
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(HyperspaceTopographyEventIntel.class)) return;
        manager.addListener(new HyperspaceTopoProgressChecker(), true);
    }

    public static final String MEM_KEY = "$IndEvo_HyperTopoCheckerDone";
    @Override
    public void reportEconomyTick(int iterIndex) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        HyperspaceTopographyEventIntel intel = HyperspaceTopographyEventIntel.get();

        if (intel == null || mem.getBoolean(MEM_KEY) || intel.getProgress() < 100) return;

        mem.set(MEM_KEY, true);
        GalatiaNewProjectsIntel.notifyNewProjects(Ids.PROJ_PROSPECTOR);
    }

    @Override
    public void reportEconomyMonthEnd() {

    }
}
