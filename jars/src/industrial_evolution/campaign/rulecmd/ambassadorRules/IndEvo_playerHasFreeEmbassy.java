package industrial_evolution.campaign.rulecmd.ambassadorRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import industrial_evolution.plugins.ambassadorPlugins.IndEvo_ambassadorItemTrackerPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_playerHasFreeEmbassy extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        int freeEmbassies = 0;
        int currentAmbassadorsInCargo = 0;

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.isPlayerOwned()
                    && market.hasIndustry(IndEvo_ids.EMBASSY)
                    && market.getIndustry(IndEvo_ids.EMBASSY).getSpecialItem() == null) {

                freeEmbassies++;
            }
        }

        if (Global.getSector().getListenerManager().hasListener(IndEvo_ambassadorItemTrackerPlugin.class)) {
            currentAmbassadorsInCargo = Global.getSector().getListenerManager().getListeners(IndEvo_ambassadorItemTrackerPlugin.class).size();
        }

        return freeEmbassies > currentAmbassadorsInCargo;
    }
}

