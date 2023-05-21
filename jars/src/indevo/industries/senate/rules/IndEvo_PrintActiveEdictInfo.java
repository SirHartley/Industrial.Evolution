package indevo.industries.senate.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.conditions.EdictAPI;

import java.util.List;
import java.util.Map;

public class IndEvo_PrintActiveEdictInfo extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        TextPanelAPI text = dialog.getTextPanel();

        text.addPara("");
        text.addPara("Currently Active:");

        for (String id : IndEvo_CreateEdictSelectionList.getEdictIdSet()) {
            if (market.hasCondition(id)) {
                EdictAPI plugin = (EdictAPI) market.getCondition(id).getPlugin();

                text.addPara(market.getCondition(id).getName(), Misc.getHighlightColor());
                plugin.printEdictEffectText(text, market);
                return true;
            }
        }

        text.addPara("There is no active Edict on this planet");

        return false;

    }
}
