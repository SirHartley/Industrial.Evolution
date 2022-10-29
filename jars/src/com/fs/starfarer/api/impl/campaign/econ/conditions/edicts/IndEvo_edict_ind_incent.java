package com.fs.starfarer.api.impl.campaign.econ.conditions.edicts;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

/*Edict:	Industrial incentives
		Upside	output of industries +1
		Downside	triple industry upkeep or upkeep reduction skills no longer apply
		Prerequisite	at least 2 industries
		Min. Runtime:	6M
*/
public class IndEvo_edict_ind_incent extends IndEvo_baseEdict {

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().modifyFlat(getModId(), 1, getName());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().modifyFlat(getModId(), 1, getName());
                }
                industry.getUpkeep().modifyMult(getModId(), 2f, getName());
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().unmodify(getModId());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().unmodify(getModId());
                }
                industry.getUpkeep().unmodify(getModId());
            }
        }
    }


    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if (getRemainingDays() != 0 && getRemainingDays() > 31) {
            tooltip.addPara("This edict must stay in place for another %s before it can be removed without penalty.", 10f, Misc.getHighlightColor(), (int) ceil(getRemainingDays() / 31.0) + " Months");
        } else if (getRemainingDays() != 0 && getRemainingDays() <= 31) {
            tooltip.addPara("This edict must stay in place for another %s before it can be removed without penalty.", 10f, Misc.getHighlightColor(), (int) getRemainingDays() + " days");
        } else {
            tooltip.addPara("This edict can be %s", 10f, Misc.getPositiveHighlightColor(), "removed without penalty.");
        }

        tooltip.addPara("%s of all industries on this planet by %s",
                10f, Misc.getPositiveHighlightColor(), new String[]{"Increases the output", 1 + ""});
        tooltip.addPara("Upkeep %s for those industries.",
                10f, Misc.getNegativeHighlightColor(), "doubled");

        tooltip.addPara("Requires a %s in the star system.", 10f, Misc.getHighlightColor(), new String[]{"Senate"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("It's an age old mantra - spend money to earn money.");

    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("All industries on this colony have their output increased by 1.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "output increased by 1");
        text.addParagraph("Upkeep for those industries is doubled.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "Upkeep");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "doubled");
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return market.getName() + " does not meet the requirements for this Edict.";
    }

    @Override
    public String getShortDesc() {
        return "Increases all industry output by 1, doubles upkeep.";
    }
}
