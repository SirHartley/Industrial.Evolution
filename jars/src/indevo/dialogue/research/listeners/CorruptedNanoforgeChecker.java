package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import indevo.dialogue.research.dialogue.RequiredItem;
import indevo.dialogue.research.dialogue.ResearchProjectDialoguePlugin;
import indevo.dialogue.research.intel.GalatiaNewProjectsIntel;
import indevo.ids.Ids;

public class CorruptedNanoforgeChecker implements EconomyTickListener {

    public static final String MEM_KEY = "$IndEvo_HasHadCorruptedNanoforge";

    public static void register() {
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(CorruptedNanoforgeChecker.class)) return;
        manager.addListener(new CorruptedNanoforgeChecker(), true);
    }

    public static boolean isDone() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(MEM_KEY);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (isDone()) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        RequiredItem nanoforge = new RequiredItem(Items.CORRUPTED_NANOFORGE, CargoAPI.CargoItemType.SPECIAL, 1f);
        if (ResearchProjectDialoguePlugin.getQuantity(cargo, nanoforge) <= 0f) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set(MEM_KEY, true);
        GalatiaNewProjectsIntel.notifyNewProjects(Ids.PROJ_REFORGE);
    }

    @Override
    public void reportEconomyMonthEnd() {
    }
}
