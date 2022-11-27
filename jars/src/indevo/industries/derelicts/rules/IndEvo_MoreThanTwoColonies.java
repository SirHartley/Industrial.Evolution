package indevo.industries.derelicts.rules;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndEvo_MoreThanTwoColonies extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));

        Set<StarSystemAPI> systemList = new HashSet<>();
        for (MarketAPI ma : IndEvo_IndustryHelper.getFactionMarkets(m.getFaction())) {
            systemList.add(ma.getStarSystem());
        }

        return systemList.size() >= 2;
    }
}