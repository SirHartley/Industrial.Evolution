package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.timers.TimeTracker;

import java.util.List;
import java.awt.*;
import indevo.industries.senate.EdictRequirement;

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
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        Color neg = Misc.getNegativeHighlightColor();
        int stabilityPenalty = TimeTracker.marketHasTimeTag(market, ident) ? TimeTracker.getTagNum(market, ident) : 4;

        tooltip.addPara("All buildings on this colony have %s.", pad, Misc.getPositiveHighlightColor(), Math.round((1 - upkeep_mult) * 100) + "% decreased upkeep");
        tooltip.addPara("Colony growth is %s.", 3f, neg, "reduced by " + market.getSize() * 2);
        if (stabilityPenalty > 0) tooltip.addPara("Stability %s, goes down by one point every month.", 3f, neg, "reduced by " + stabilityPenalty);
    }

    @Override
    public String getShortDesc() {
        return "Massively reduces the upkeep of all buildings, but causes a reduction in colony growth and temporary unrest.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        requirements.add(military(market));
    }
}
