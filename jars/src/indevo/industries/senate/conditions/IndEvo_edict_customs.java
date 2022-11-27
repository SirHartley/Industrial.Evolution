package indevo.industries.senate.conditions;

/*Edict:	Customs control
		Upside	increase accessability of this colony by 5% for every other colony in the system
		    "Routing all cargo shipments through this polity has a very good effect on the local economy, but is detrimental to the accessability of the other planets"
		Downside	decrease accessability of all other colonies by 10%
		Prerequisite	megaport, no other planet has it active
		Min. Runtime:	6M
*/

import indevo.utils.helper.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

public class IndEvo_edict_customs extends IndEvo_baseEdict {

    private final float foreignAccessMod = -0.07F;
    private float thisAccessIncrease = 0F;

    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        int foreignCount = 0;
        thisAccessIncrease = 0.05F;

        for (MarketAPI market : IndEvo_IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            if (!market.getId().equals(this.market.getId())) {
                market.getAccessibilityMod().modifyFlat(id, foreignAccessMod, getName());
                foreignCount++;
            }
        }

        thisAccessIncrease *= foreignCount;
        if (foreignCount > 0) {
            market.getAccessibilityMod().modifyFlat(id, thisAccessIncrease, getName());
        }
    }

    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (MarketAPI market : IndEvo_IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            if (!market.getId().equals(this.market.getId())) {
                market.getAccessibilityMod().unmodifyFlat(id);
            }
        }

        if (market.getAccessibilityMod().getFlatBonus(id) != null) {
            market.getAccessibilityMod().unmodifyFlat(id);
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

        if (IndEvo_IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId()).size() > 1) {
            tooltip.addPara("The accessability of this colony is %s.",
                    10f, Misc.getPositiveHighlightColor(), "increased by " + (int) (thisAccessIncrease * 100) + "%");

            tooltip.addPara("Accessability for:", 3f);
            for (MarketAPI market : IndEvo_IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
                if (!market.getId().equals(this.market.getId())) {
                    tooltip.addPara(BaseIntelPlugin.BULLET + market.getName() + ": decreased by %s,",
                            1f, Misc.getNegativeHighlightColor(), "reduced by " + (int) (-foreignAccessMod * 100) + "%");
                }
            }

        } else {
            tooltip.addPara("The accessability of this colony unchanged as there are %s in the star system", 10f, Misc.getNegativeHighlightColor(), "no other colonies");
        }

        tooltip.addPara("Requires a %s and at least two colonies in the star system, and %s on this planet.", 10f, Misc.getHighlightColor(), new String[]{"Senate", "Megaport"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Routing all systembound traffic though a single polity will make a few people very, very rich, while leaving the other planets to pay for it.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        String s1 = "" + (IndEvo_IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId()).size() - 1) * 5 + "%";

        text.addParagraph("Accessability for this polity increased by 5% for every other colony in the system.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "increased by 5%");
        text.highlightInLastPara(Misc.getHighlightColor(), "for every other colony");
        text.addParagraph("Current possible bonus: " + s1);
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), s1);
        text.addParagraph("Accessability for all other colonies reduced by 7%.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "reduced by 7%");

    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return "Requires military presence on this planet, at least two colonies in the system, can only be active on one colony at a time (Star system).";
    }

    @Override
    public String getShortDesc() {
        return "Increases accessability of this planet for every other colony in the system. Reduces accessability for all other colonies.";
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        boolean twoMarkets = IndEvo_IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId()).size() > 1;
        return super.isPresenceConditionMet(market)
                && market.hasIndustry(Industries.MEGAPORT)
                && conditionUniqueInSystem(market, condition.getId())
                && twoMarkets;
    }
}
