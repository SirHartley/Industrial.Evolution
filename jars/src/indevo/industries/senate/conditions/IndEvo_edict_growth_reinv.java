package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

@Deprecated
public class IndEvo_edict_growth_reinv extends IndEvo_baseEdict implements EconomyTickListener {

    private float investAmount = 0;
    private final int limitForInvest = 20000;
    private final float edictBonus = 0.25f;
    private float fullSum = 0f;
    private float extraIncentiveCredits = 0;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        incentiveCalculation();
        float redAmount = (investAmount / market.getUpkeepMult().computeMultMod());
        if (market.hasIndustry(Industries.POPULATION))
            market.getIndustry(Industries.POPULATION).getUpkeep().modifyFlatAlways(getModId(), redAmount, getName());
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getIndustry(Industries.POPULATION).getUpkeep().unmodify(getModId());
    }

    public void reportEconomyTick(int iterIndex) {
        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1; //Get the last iteration

        if (iterIndex != lastIterInMonth) return;
        applyIncentiveCredits();
    }

    public void reportEconomyMonthEnd() {
    }

    private void incentiveCalculation() {
        investAmount = 0;

        float possibleIncentiveBudget = market.getNetIncome() - limitForInvest; //everything over 20k?
        float marketIncentiveCreditLimit = 100000f * ((float) Math.pow(2.0, (market.getSize() - 3.0)));
        float currentlyFilledIncentiveCredits = market.getIncentiveCredits();

        boolean budgetOverLimit = (currentlyFilledIncentiveCredits + possibleIncentiveBudget) > marketIncentiveCreditLimit;
        float amountOverLimit = (currentlyFilledIncentiveCredits + possibleIncentiveBudget) - marketIncentiveCreditLimit;

        extraIncentiveCredits = possibleIncentiveBudget * edictBonus;
        if (extraIncentiveCredits < 0) {
            extraIncentiveCredits = 0;
        }

        fullSum = currentlyFilledIncentiveCredits + possibleIncentiveBudget + extraIncentiveCredits;

        if (fullSum > marketIncentiveCreditLimit) {
            fullSum -= (fullSum - marketIncentiveCreditLimit);
        }

        if (budgetOverLimit) {
            investAmount = possibleIncentiveBudget - amountOverLimit;
        } else if (possibleIncentiveBudget > 0) {
            investAmount = possibleIncentiveBudget;
        }
    }

    public void applyIncentiveCredits() {
        if (market.getNetIncome() < 0) {
            return;
        }
        market.setIncentiveCredits(fullSum);
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

        tooltip.addPara("The following amount will be reinvested next month: %s",
                10f, Misc.getPositiveHighlightColor(), (int) investAmount + " credits");
        tooltip.addPara("Bonus investment due to Edict: %s",
                3f, Misc.getPositiveHighlightColor(), (int) extraIncentiveCredits + " credits");

        tooltip.addPara("Requires a %s in the star system.", 10f, Misc.getHighlightColor(), new String[]{"Senate"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Direct investments into the colonies growth incentives" +
                " increase the amount of money that actually ends up in the pot without getting misappropriated.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Invests any colony income above 20.000 credits into growth incentives on month end, with a 25% bonus.");
        text.highlightInLastPara(Misc.getHighlightColor(), "above 20.000 credits");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "25% bonus");
        text.addParagraph("If the investment is larger than the limit, the difference is refunded.");
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return market.getName() + " does not meet the requirements for this Edict.";
    }

    @Override
    public String getShortDesc() {
        return "Invests income from this market over 20.000 credits into growth incentives, " +
                "with a 25% bonus. Excess income over the incentive limit is refunded.";
    }
}
