package indevo.industries.senate.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.senate.conditions.BaseEdict;
import indevo.utils.helper.IndustryHelper;

import java.util.List;
import java.util.Map;


public class IndEvo_systemHasSenate extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        return market.isPlayerOwned() && (BaseEdict.senateWithItemInRange(market) || IndustryHelper.systemHasIndustry(Ids.SENATE, market.getStarSystem(), market.getFaction(), false));
    }
}