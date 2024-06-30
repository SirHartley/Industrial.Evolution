package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.TimeTracker;

import static java.lang.Math.ceil;

public class Edict_Conscription extends BaseEdict implements MarketImmigrationModifier, EconomyTickListener {

    public final String ident = "edict_stab_penalty";
    private int penaltyCounter = 4;
    private final float upkeep_mult = 0.40F;
    private float pop_penalty = 0;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        pop_penalty = -market.getSize() * 2f;

        market.addTransientImmigrationModifier(this);
        market.getUpkeepMult().modifyMult(id, upkeep_mult, getName());

        if (!TimeTracker.marketHasTimeTag(market, ident)) {
            market.addTag(ident + penaltyCounter);
        } else {
            penaltyCounter = TimeTracker.getTagNum(market, ident);
            market.getStability().modifyFlat(id, -penaltyCounter, getName());
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getUpkeepMult().unmodify(id);
        market.getStability().unmodify(id);

        if (market.getTransientImmigrationModifiers().contains(this)) {
            market.removeTransientImmigrationModifier(this);
        }
    }

    public void reportEconomyTick(int iterIndex) {
        debugMessage("601");
        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;

        if (penaltyCounter > 0) {
            TimeTracker.incrementTagNum(market, ident, -1);
        }
    }

    public void reportEconomyMonthEnd() {
    }

    public void removeWithoutPenalty() {
        TimeTracker.removeTimeTag(market, ident);
        super.removeWithoutPenalty();
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().unmodify(getModId());
        incoming.getWeight().modifyFlat(getModId(), pop_penalty, getName());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if (getRemainingDays() != 0 && getRemainingDays() > 31) {
            tooltip.addPara("This edict must stay in place for another %s before it can be removed without penalty.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), (int) ceil(getRemainingDays() / 31.0) + " Months");
        } else if (getRemainingDays() != 0 && getRemainingDays() <= 31) {
            tooltip.addPara("This edict must stay in place for another %s before it can be removed without penalty.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), (int) getRemainingDays() + " days");
        } else {
            tooltip.addPara("This edict can be %s", 10f, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "removed without penalty.");
        }

        tooltip.addPara("All buildings on this colony have %s.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), (int) ((1 - upkeep_mult) * 100) + "% decreased upkeep");
        tooltip.addPara("Colony growth is %s.", 3f, com.fs.starfarer.api.util.Misc.getNegativeHighlightColor(), "reduced by " + (int) (-pop_penalty));

        if (penaltyCounter > 0) {
            tooltip.addPara("Stability %s, goes down by one point every month.", 5f, com.fs.starfarer.api.util.Misc.getNegativeHighlightColor(), "reduced by " + penaltyCounter);
        }

        tooltip.addPara("Requires a %s in the star system and %s on this planet.", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{"Senate", "Military Presence"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("The realities of the sector do not allow for such trifles as human rights, and a conscripted workforce is a cheap workforce. Your populace is likely to have a different opinion.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {

        String s1 = "reduced by " + (market.getSize() * 2);

        text.addParagraph("All buildings on this colony have 60% decreased upkeep.");
        text.highlightInLastPara(com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "60% decreased upkeep");
        text.addPara("Colony growth is " + s1 + ".");
        text.highlightInLastPara(com.fs.starfarer.api.util.Misc.getNegativeHighlightColor(), s1);
        text.addPara("Stability reduced by 4, goes down by one point every month.");
        text.highlightInLastPara(com.fs.starfarer.api.util.Misc.getNegativeHighlightColor(), "reduced by 4");
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return "Requires military presence on this planet.";
    }

    @Override
    public String getShortDesc() {
        return "Massively reduces the upkeep of all buildings, but causes a reduction in colony growth and temporary unrest.";
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        return super.isPresenceConditionMet(market) && MiscIE.marketHasMilitaryIncludeRelays(market);
    }

}

