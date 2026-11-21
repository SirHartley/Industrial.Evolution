package indevo.industries.senate.conditions;


/*Edict:	Subpopulation Cleansing
		Upside	Remove any subpopulation (Decived, pirate o.a)
		Downside	-5 stability, massive immiration penalty, reduce income 30%, leaves unrest for size/3 monts after done
		Prerequisite	deciv subpop, mil.presence
		Min. Runtime:	1M/market size, auto-end
*/

import indevo.industries.senate.EdictEffect;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.EdictRequirement;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Edict_Cleanse extends BaseEdict implements MarketImmigrationModifier {

    private final float income_penalty = 0.70F;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        market.addTransientImmigrationModifier(this);
        market.getIncomeMult().modifyMult(id, income_penalty, getName());
        market.getStability().modifyFlat(id, -(market.getSize() - 1), getName());
    }

    @Override
    public int getMinRuntime(MarketAPI market) {
        return withBetaCore(market.getSize() * 31, market);
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getIncomeMult().unmodify(id);
        market.getStability().unmodify(id);

        if (market.getTransientImmigrationModifiers().contains(this)) {
            market.removeTransientImmigrationModifier(this);
        }
    }

    @Override
    public void onNewDay() {
        super.onNewDay();

        if (minimumRuntimePassed()) {
            ArrayList<MarketConditionAPI> cl = new ArrayList<>();

            for (Iterator<MarketConditionAPI> i = market.getConditions().iterator(); i.hasNext(); ) {
                MarketConditionAPI cond = i.next();

                if (cond.getName().contains("ubpopulation")) {
                    cl.add(cond);
                }
            }

            for (MarketConditionAPI c : cl) {
                market.removeCondition(c.getId());
            }

            applyFinishPenalty();
            Global.getSector().getCampaignUI().addMessage("An Edict at %s was removed. It has %s.",
                    Global.getSettings().getColor("standardTextColor"), market.getName(), "achieved its purpose", Misc.getHighlightColor(), Misc.getPositiveHighlightColor());

            removeWithoutPenalty();
        }
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().unmodify(getModId());

        incoming.getWeight().modifyFlat(getModId(), -market.getSize(), getName());
    }

    @Override
    public boolean endsOnItsOwn() {
        return true;
    }

    @Override
    public int getFinishUnrest(MarketAPI market) {
        return (int) Math.ceil(market.getSize() / 3.0);
    }

    @Override
    protected String getRemainingTimeFormat() {
        return "This edict must stay in place until the cleansing is complete, which will take another %s.";
    }

    @Override
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        effects.add(EdictEffect.good("Subpopulations", "removed at the end"));
        effects.add(EdictEffect.bad("Income", percent(-(1 - income_penalty))));
        effects.add(EdictEffect.bad("Population growth", signed(-market.getSize())));
        effects.add(stability(market, -(market.getSize() - 1)));
        return effects;
    }

    @Override
    protected void addRuntimeLine(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Takes %s (one per colony size), then ends on its own and causes %s.", pad,
                new Color[]{Misc.getHighlightColor(), Misc.getNegativeHighlightColor()},
                getMonthString(getMinRuntime(market)), getFinishUnrest(market) + " unrest");
    }

    @Override
    public String getShortDesc() {
        return "Removes any subpopulations on this planet. Time required and unrest caused by this depend on population size, reduces income by 30% for the duration.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        requirements.add(military(market));
        requirements.add(new EdictRequirement("A subpopulation on this colony", "A subpopulation", marketHasConditionSubString(market, "ubpopulation")));
    }

    private boolean marketHasConditionSubString(MarketAPI market, String substring) {
        for (MarketConditionAPI cond : market.getConditions()) {
            if (cond.getName().contains(substring)) {
                return true;
            }
        }
        return false;
    }
}
