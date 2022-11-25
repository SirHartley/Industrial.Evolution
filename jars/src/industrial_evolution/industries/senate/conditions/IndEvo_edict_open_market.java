package industrial_evolution.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

/*Edict:	Substance Legalization
		Upside	Increase pop growth slightly, increase stab, legalize drug export
		Downside	Reduce industry output by 1, increase required drugs by 2, unrest on change to freeport - "cancelled policy"
		Prerequisite	size >4, drug demand filled, not a free port
		Min. Runtime:	6M
*/
public class IndEvo_edict_open_market extends IndEvo_baseEdict {
    private final float INCOME_RED_MULT = -10f;

    public static float STABILITY_PELANTY = 2f;
    protected transient SubmarketAPI saved = null;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        if (market.isPlayerOwned() && !market.hasIndustry("commerce")) {
            market.getStability().modifyFlat(getModId(), -STABILITY_PELANTY, getName());

            SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            if (open == null) {
                if (saved != null) {
                    market.addSubmarket(saved);
                } else {
                    market.addSubmarket(Submarkets.SUBMARKET_OPEN);
                    SubmarketAPI sub = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
                    sub.setFaction(Global.getSector().getFaction(Factions.INDEPENDENT));
                    Global.getSector().getEconomy().forceStockpileUpdate(market);
                }
            }

        }
        //market.getIncomeMult().modifyPercent(getModId(), INCOME_RED_MULT, getName());
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        if (market.isPlayerOwned()) {
            SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            saved = open;

            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
        }

        market.getStability().unmodifyFlat(getModId());
        //market.getIncomeMult().unmodifyPercent(getModId());
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
                10f, Misc.getPositiveHighlightColor(), "reduced by " + STABILITY_PELANTY);

        tooltip.addPara("Requires a %s in the star system", 10f, Misc.getHighlightColor(), new String[]{"Senate"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Buy and sell anything your heart desires! Allowing independent traders to open up their inventory for sale in dedicated areas " +
                "without additional infrastructure does not have a major impact on colony income, but still allows you to buy from them.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Adds an open market to trade on.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "open market");

        text.addParagraph("Stability reduced by " + STABILITY_PELANTY);
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "reduced by " + Math.round(STABILITY_PELANTY));
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return market.getName() + " does not meet the requirements for this Edict.";
    }

    @Override
    public String getShortDesc() {
        return "Adds an open market, reduces stability.";
    }
}
