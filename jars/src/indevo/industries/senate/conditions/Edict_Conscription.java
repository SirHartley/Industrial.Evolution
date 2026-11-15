package indevo.industries.senate.conditions;

import indevo.industries.senate.EdictEffect;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import indevo.utils.timers.TimeTracker;

import java.util.ArrayList;
import java.util.List;
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
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        int stabilityPenalty = TimeTracker.marketHasTimeTag(market, ident) ? TimeTracker.getTagNum(market, ident) : 4;

        effects.add(EdictEffect.good("Upkeep of all buildings", percent(-(1 - upkeep_mult))));
        effects.add(EdictEffect.bad("Population growth", signed(-market.getSize() * 2)));
        if (stabilityPenalty > 0) effects.add(EdictEffect.bad("Stability, eases by 1 per month", signed(-stabilityPenalty)));
        return effects;
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
