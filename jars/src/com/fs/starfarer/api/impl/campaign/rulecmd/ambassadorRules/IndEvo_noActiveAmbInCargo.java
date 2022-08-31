package com.fs.starfarer.api.impl.campaign.rulecmd.ambassadorRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.plugins.ambassadorPlugins.IndEvo_ambassadorItemTrackerPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

//returns false if an ambassador of the same faction as $person is currently in transit.

public class IndEvo_noActiveAmbInCargo extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String factionId = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id")).getFactionId();

        boolean activeScript = Global.getSector().getListenerManager().hasListenerOfClass(IndEvo_ambassadorItemTrackerPlugin.class);
        boolean noSameFactionScript = true;

        if (activeScript) {
            for (Object script : Global.getSector().getListenerManager().getListeners(IndEvo_ambassadorItemTrackerPlugin.class)) {
                if (((IndEvo_ambassadorItemTrackerPlugin) script).faction.getId().equals(factionId)) {
                    noSameFactionScript = false;
                }
            }
        }
        return noSameFactionScript;
    }
}
