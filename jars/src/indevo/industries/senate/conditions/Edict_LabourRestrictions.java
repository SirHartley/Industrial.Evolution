package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

/*Edict:	Substance Legalization
		Upside	Increase pop growth slightly, increase stab, legalize drug export
		Downside	Reduce industry output by 1, increase required drugs by 2, unrest on change to freeport - "cancelled policy"
		Prerequisite	size >4, drug demand filled, not a free port
		Min. Runtime:	6M
*/
public class Edict_LabourRestrictions extends BaseEdict {
    private final int stabBonus = 4;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getStability().modifyFlat(getModId(), stabBonus, getName());

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().modifyFlat(getModId(), -1, getName());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().modifyFlat(getModId(), -1, getName());
                }
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getStability().unmodify(getModId());

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().unmodify(getModId());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().unmodify(getModId());
                }
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

        tooltip.addPara("The stability of this colony is %s.",
                10f, Misc.getPositiveHighlightColor(), "increased by " + stabBonus);
        tooltip.addPara("%s of all industries on this planet by %s",
                3f, Misc.getNegativeHighlightColor(), new String[]{"Decreases the output", 1 + ""});

        tooltip.addPara("Requires a %s in the star system, and is %s.", 10f, Misc.getHighlightColor(), new String[]{"Senate", "not a free port"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Less time worked is more time spent to realize ones dreams. " +
                "Less time worked is also less time spent earning money for you.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Stability increased by 4.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "increased by 4");
        text.addParagraph("All industries on this colony have their output decreased by 1.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "output decreased by 1");
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        return super.isPresenceConditionMet(market) && !market.isFreePort();
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        if (market.isFreePort()) return market.getName() + " is a free port and will not abide by restrictions.";
        return market.getName() + " does not meet the requirements for this Edict.";
    }

    @Override
    public String getShortDesc() {
        return "Increases stability, decreases all industry output.";
    }
}
