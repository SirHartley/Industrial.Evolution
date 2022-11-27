package indevo.industries.academy.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

import static indevo.industries.academy.rules.IndEvo_CreatePersonSelectionList.getMarket;

public class IndEvo_MarketIsTraining extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String type = params.get(0).getString(memoryMap);
        String key;
        if (type.equals("OFFICER")) key = "$IndEvo_officerIsTraining";
        else if (type.equals("ADMIN")) key = "$IndEvo_adminIsTraining";
        else return false;

        MarketAPI market = getMarket(memoryMap);
        return market.getMemoryWithoutUpdate().getBoolean(key);
    }
}