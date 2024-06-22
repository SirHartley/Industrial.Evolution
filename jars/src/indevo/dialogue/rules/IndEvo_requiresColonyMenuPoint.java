package indevo.dialogue.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import indevo.ids.Ids;
import indevo.utils.helper.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_requiresColonyMenuPoint extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<com.fs.starfarer.api.util.Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));

        if (market == null || market.isPlanetConditionMarketOnly() || Factions.NEUTRAL.equals(market.getFaction().getId()))
            return false;

        boolean playerOwned = market.isPlayerOwned();

        boolean senate = playerOwned
                && Misc.systemHasIndustry(Ids.SENATE, market.getStarSystem(), market.getFaction());
        boolean academy = market.hasIndustry(Ids.ACADEMY);
        boolean riftGen = playerOwned
                && market.hasIndustry(Ids.RIFTGEN);
        boolean shippingPort = playerOwned
                && market.hasIndustry(Ids.PORT);
        boolean salvageYards = market.hasIndustry(Ids.SCRAPYARD);

        int howManyBooleansAreTrue =
                (senate ? 1 : 0)
                        + (academy ? 1 : 0)
                        + (riftGen ? 1 : 0)
                        + (salvageYards ? 1 : 0)
                        + (shippingPort ? 1 : 0);

        return howManyBooleansAreTrue >= 2;
    }
}
