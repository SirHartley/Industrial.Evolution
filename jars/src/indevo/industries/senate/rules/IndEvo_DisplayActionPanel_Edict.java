package indevo.industries.senate.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.utils.helper.StringHelper;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

import static indevo.industries.senate.rules.IndEvo_EdictVariables.SELECTED_EDICT;

public class IndEvo_DisplayActionPanel_Edict extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
        OptionPanelAPI opts = dialog.getOptionPanel();

        if (memory.getString(SELECTED_EDICT) == null) {
            return false;
        }

        String edictId = memory.getString(SELECTED_EDICT);

        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));

        market.addCondition(edictId);
        MarketConditionPlugin plugin = market.getCondition(edictId).getPlugin();
        market.removeCondition(edictId);

        opts.clearOptions();

        printEdictDescriptionTooltip(edictId, plugin, market, dialog);

        opts.addOption("Enact this Edict", "IndEvo_ApplyEdict");

        opts.addOption("Return", "IndEvo_EdictSelector_LoadCurrent");
        opts.setShortcut("IndEvo_EdictSelector_LoadCurrent", Keyboard.KEY_ESCAPE, false, false, false, false);
        return true;
    }

    public static void printEdictDescriptionTooltip(String id, MarketConditionPlugin plugin, MarketAPI market, InteractionDialogAPI dialog) {
        TextPanelAPI text = dialog.getTextPanel();
        EdictAPI edictPlugin = (EdictAPI) plugin;

        text.setFontSmallInsignia();
        text.addPara(plugin.getName());

        text.addParagraph(StringHelper.HR);

        edictPlugin.printEdictFlavourText(text);
        text.addParagraph("");
        edictPlugin.printEdictEffectText(text, market);
        text.addParagraph("");
        edictPlugin.printEdictRuntimeText(text);

        text.addParagraph(StringHelper.HR);
        text.setFontInsignia();
    }
}
