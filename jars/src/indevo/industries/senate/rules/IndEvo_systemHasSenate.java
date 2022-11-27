package indevo.industries.senate.rules;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.industries.senate.conditions.IndEvo_baseEdict;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;


public class IndEvo_systemHasSenate extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        return market.isPlayerOwned() && (IndEvo_baseEdict.senateWithItemInRange(market) || IndEvo_IndustryHelper.systemHasIndustry(IndEvo_ids.SENATE, market.getStarSystem(), market.getFaction(), false));
    }
}