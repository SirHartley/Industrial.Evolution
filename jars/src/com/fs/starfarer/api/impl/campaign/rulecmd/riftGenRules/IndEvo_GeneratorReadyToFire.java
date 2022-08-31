package com.fs.starfarer.api.impl.campaign.rulecmd.riftGenRules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.derelicts.IndEvo_RiftGen;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_GeneratorReadyToFire extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        IndEvo_RiftGen rg = (IndEvo_RiftGen) market.getIndustry(IndEvo_ids.RIFTGEN);

        return rg.isReadyToMove();
    }
}
