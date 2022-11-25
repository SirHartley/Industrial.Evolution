package industrial_evolution.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import industrial_evolution.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_requiresColonyMenuPoint extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));

        if (market == null || market.isPlanetConditionMarketOnly() || Factions.NEUTRAL.equals(market.getFaction().getId()))
            return false;

        boolean playerOwned = market.isPlayerOwned();

        boolean senate = playerOwned
                && IndEvo_IndustryHelper.systemHasIndustry(IndEvo_ids.SENATE, market.getStarSystem(), market.getFaction());
        boolean academy = market.hasIndustry(IndEvo_ids.ACADEMY);
        boolean riftGen = playerOwned
                && market.hasIndustry(IndEvo_ids.RIFTGEN);
        boolean shippingPort = playerOwned
                && market.hasIndustry(IndEvo_ids.PORT);
        boolean salvageYards = market.hasIndustry(IndEvo_ids.SCRAPYARD);

        int howManyBooleansAreTrue =
                (senate ? 1 : 0)
                        + (academy ? 1 : 0)
                        + (riftGen ? 1 : 0)
                        + (salvageYards ? 1 : 0)
                        + (shippingPort ? 1 : 0);

        return howManyBooleansAreTrue >= 2;
    }
}
