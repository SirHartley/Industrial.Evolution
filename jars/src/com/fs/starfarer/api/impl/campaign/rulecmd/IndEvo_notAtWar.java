package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.plugins.ambassadorPlugins.IndEvo_ambassadorPersonManager;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_notAtWar extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        return !(Global.getSector().getPlayerFaction().getRelationship(IndEvo_ambassadorPersonManager.getAmbassador(m).getFaction().getId()) <= -0.5F);
    }
}
