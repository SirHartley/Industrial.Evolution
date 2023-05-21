package indevo.industries.embassy.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;

import java.util.List;
import java.util.Map;

public class IndEvo_playerHasFreeEmbassyNoCargo extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.isPlayerOwned()
                    && market.hasIndustry(Ids.EMBASSY)
                    && market.getIndustry(Ids.EMBASSY).getSpecialItem() == null) {
                return true;
            }
        }

        return false;
    }
}
