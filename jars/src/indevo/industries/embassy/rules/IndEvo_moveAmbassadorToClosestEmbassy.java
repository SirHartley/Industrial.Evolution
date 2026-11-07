package indevo.industries.embassy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.utils.helper.BillingHelper;

import java.util.List;
import java.util.Map;

public class IndEvo_moveAmbassadorToClosestEmbassy extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        MarketAPI closestEmbassy = AmbassadorPersonManager.getClosestEmptyEmbassyToMarket(market);

        if (market != null && closestEmbassy != null) {

            Global.getSector().addTransientScript(new AmbassadorPersonManager.moveAmbassadorToMarket(market, closestEmbassy));

            float cost = IndEvo_displayAmbassadorMoveOption.getTransoportCost(market, closestEmbassy);
            BillingHelper.charge(market.getIndustry(Ids.EMBASSY), "transfer", "Ambassador transfer", cost);
        }

        return true;
    }
}