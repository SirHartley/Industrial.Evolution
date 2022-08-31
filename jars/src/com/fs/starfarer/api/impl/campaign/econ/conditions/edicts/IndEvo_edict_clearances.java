package com.fs.starfarer.api.impl.campaign.econ.conditions.edicts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

public class IndEvo_edict_clearances extends IndEvo_baseEdict {

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        boolean hasEffect = false;

        for (Industry i : market.getIndustries()) {
            int shipsup = i.getSupply(Commodities.SHIPS).getQuantity().getModifiedInt();
            if (shipsup > 1) {
                int supplyMalus = (int) Math.ceil(shipsup * 0.3);
                i.getSupply(Commodities.SHIPS).getQuantity().modifyFlat(getModId(), -supplyMalus, getName());
                hasEffect = true;
            }
        }

        if (hasEffect) {
            for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
                if (!market.isPlayerOwned()) {
                    continue;
                }
                market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(), 0.2f, getName());
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry i : market.getIndustries()) {
            int shipsup = i.getSupply(Commodities.SHIPS).getQuantity().getModifiedInt();
            if (shipsup > 0) {
                i.getSupply(Commodities.SHIPS).getQuantity().unmodify(getModId());
            }
        }

        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (!market.isPlayerOwned()) {
                continue;
            }
            market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodify(getModId());
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

        tooltip.addPara("Local production quality %s.",
                10f, Misc.getPositiveHighlightColor(), "increased by 20%");

        tooltip.addPara("%s of all industries on this planet %s. This affects production budget.",
                10f, Misc.getNegativeHighlightColor(), new String[]{"Ship hull output", "decreased by 30%"});

        tooltip.addPara("Requires a %s in the star system, and %s ont this planet.", 10f, Misc.getHighlightColor(), new String[]{"Senate", "ship hull exports"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Forcing a focus on quality over quantity during ship production will result in fewer, but better hulls " +
                "- a luxury not every faction can afford.");

    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Local ship quality increased by 20%.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "increased by 20%");
        text.addParagraph("Ship hull output and the production budget provided by this colony decreased by 30%.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "decreased by 30%");
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return "Requires ship hull production on this planet.";
    }

    @Override
    public String getShortDesc() {
        return "increases local ship quality, decreases ship hull output and production budget.";
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        return super.isPresenceConditionMet(market)
                && market.getCommodityData(Commodities.SHIPS).getMaxSupply() > 0;
    }
}