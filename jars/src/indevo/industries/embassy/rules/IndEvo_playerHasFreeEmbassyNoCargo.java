package indevo.industries.embassy.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import indevo.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_playerHasFreeEmbassyNoCargo extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.isPlayerOwned()
                    && market.hasIndustry(IndEvo_ids.EMBASSY)
                    && market.getIndustry(IndEvo_ids.EMBASSY).getSpecialItem() == null) {
                return true;
            }
        }

        return false;
    }
}
