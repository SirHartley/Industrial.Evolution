package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.ItemPatternProject;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import indevo.dialogue.research.dialogue.ResearchProjectDialoguePlugin;
import indevo.dialogue.research.intel.GalatiaNewProjectsIntel;
import indevo.industries.compatibility.AotdManager;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.List;

// records which colony items the player has owned and posts the galatia notice once a pattern project opens
public class ItemPatternChecker implements EconomyTickListener {

    public static final String FOUND_KEY = "$IndEvo_ItemPatternFound_";
    public static final String NOTIFIED_KEY = "$IndEvo_ItemPatternNotified_";

    public static void register() {
        if (!AotdManager.isVokEnabled()) return;

        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(ItemPatternChecker.class)) return;
        manager.addListener(new ItemPatternChecker(), true);
    }

    public static boolean isFound(String itemId) {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(FOUND_KEY + itemId);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        List<String> opened = new ArrayList<>();

        for (String itemId : AotdManager.PRODUCTION_ITEMS) {
            if (!isFound(itemId) && isOwned(itemId)) mem.set(FOUND_KEY + itemId, true);

            String projectId = ItemPatternProject.getProjectId(itemId);
            if (mem.getBoolean(NOTIFIED_KEY + itemId) || !ResearchProjectTemplateRepo.RESEARCH_PROJECTS.get(projectId).display()) continue;

            mem.set(NOTIFIED_KEY + itemId, true);
            opened.add(projectId);
        }

        if (!opened.isEmpty()) GalatiaNewProjectsIntel.notifyNewProjects(opened.toArray(new String[0]));
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    // fleet cargo, player storage or installed in a player colony
    private static boolean isOwned(String itemId) {
        if (hasItem(Global.getSector().getPlayerFleet().getCargo(), itemId)) return true;

        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            if (hasItem(MiscIE.getStorageCargo(market), itemId)) return true;

            for (Industry industry : market.getIndustries()) {
                if (industry.getSpecialItem() != null && itemId.equals(industry.getSpecialItem().getId())) return true;
            }
        }

        return false;
    }

    private static boolean hasItem(CargoAPI cargo, String itemId) {
        return cargo != null && ResearchProjectDialoguePlugin.getQuantity(cargo, CargoAPI.CargoItemType.SPECIAL, itemId) > 0f;
    }
}
