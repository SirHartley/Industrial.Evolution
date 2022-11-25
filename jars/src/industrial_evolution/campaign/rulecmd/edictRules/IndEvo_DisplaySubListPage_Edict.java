package industrial_evolution.campaign.rulecmd.edictRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import industrial_evolution.industries.senate.conditions.IndEvo_EdictAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static industrial_evolution.campaign.rulecmd.edictRules.IndEvo_EdictVariables.EDICT_LIST_CONTAINER;
import static industrial_evolution.campaign.rulecmd.edictRules.IndEvo_EdictVariables.EDICT_OPTION_PREFIX;

public class IndEvo_DisplaySubListPage_Edict extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));

        OptionPanelAPI opts = dialog.getOptionPanel();

        if (!memory.contains(EDICT_LIST_CONTAINER)) return false;

        int pageNumber = Integer.parseInt(params.get(0).getString(memoryMap));
        ArrayList<ArrayList<String>> splitList = (ArrayList<ArrayList<String>>) memory.get(EDICT_LIST_CONTAINER);

        opts.clearOptions();

        for (String id : splitList.get(pageNumber - 1)) {
            Pair<IndEvo_EdictAPI, String> optionData = generateEdictEntry(id, dialog);

            if (!optionData.one.isPresenceConditionMet(market)) {
                opts.setEnabled(optionData.two, false);
                opts.setTooltip(optionData.two, optionData.one.getUnavailableReason(market));
            }
        }

        opts.addOption("Next Page", "IndEvo_EdictSelector_TriggerNext");
        opts.setEnabled("IndEvo_EdictSelector_TriggerNext", pageNumber < splitList.size());
        opts.addOption("Previous Page", "IndEvo_EdictSelector_TriggerPrevious");
        opts.setEnabled("IndEvo_EdictSelector_TriggerPrevious", pageNumber > 1);
        opts.addOption("Return", "IndEvo_SelectreturnToMenu");
        opts.setShortcut("IndEvo_SelectreturnToMenu", Keyboard.KEY_ESCAPE, false, false, false, false);

        return true;
    }

    private Pair<IndEvo_EdictAPI, String> generateEdictEntry(String id, InteractionDialogAPI dialog) {
        OptionPanelAPI opts = dialog.getOptionPanel();

        MarketAPI dummyMarket = Global.getFactory().createMarket(Misc.genUID(), Misc.genUID(), 1);
        dummyMarket.addCondition(id);
        MarketConditionPlugin plugin = dummyMarket.getCondition(id).getPlugin();

        String title = plugin.getName();
        String desc = ((IndEvo_EdictAPI) plugin).getShortDesc();
        String optionId = EDICT_OPTION_PREFIX + id;

        opts.addOption(title, optionId, desc);

        return new Pair<>((IndEvo_EdictAPI) plugin, optionId);
    }
}
