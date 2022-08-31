package com.fs.starfarer.api.betterColonies;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class BetterColoniesTechMining extends TechMining {

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        float mult = getTechMiningMult();
        float decay = Global.getSettings().getFloat("techMiningDecay");

        tooltip.addPara("%s%% of salvage in ruins still remains.", opad, Misc.getHighlightColor(), String.format("%.2f", mult * 100f));
        tooltip.addPara("Around %s%% of salvage will remain for recovery at the beginning of the next month.", opad, Misc.getHighlightColor(), String.format("%.2f", mult * 100f * decay));

    }
}
