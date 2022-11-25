package industrial_evolution.campaign.rulecmd.ambassadorRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import industrial_evolution.plugins.ambassadorPlugins.IndEvo_ambassadorPersonManager;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_moveAmbassadorToClosestEmbassy extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        MarketAPI closestEmbassy = IndEvo_ambassadorPersonManager.getClosestEmptyEmbassyToMarket(market);

        if (market != null && closestEmbassy != null) {

            Global.getSector().addTransientScript(new IndEvo_ambassadorPersonManager.moveAmbassadorToMarket(market, closestEmbassy));

            float cost = IndEvo_displayAmbassadorMoveOption.getTransoportCost(market, closestEmbassy);

            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
            MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
            MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");
            MonthlyReport.FDNode iNode = report.getNode(indNode, IndEvo_ids.EMBASSY);
            iNode.upkeep += cost;
        }

        return true;
    }
}