package indevo.industries.senate.conditions;

import indevo.industries.senate.EdictEffect;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;
import indevo.utils.timers.TimeTracker;

import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import indevo.industries.senate.EdictRequirement;

/*Edict:	Forced relocation (relocate people from this planet to other in system colonies at a fast rate - can reduce market size of this planet!)
		Upside	massively increase pop growth for planets in system other than this one
		Downside	massively reduce pop growth on this planet, reduce stability on all planets in system, on end: 3 pts unrest
		Prerequisite	Market size >=6 on start, military base in system, no other planet has it active
		Min. Runtime:	6M

		-10x marekt size = y
		distribution: y/planetAmount per planet
		if growth % 0 > reduce market size by 1
*/

public class Edict_ForcedRelocation extends BaseEdict implements MarketImmigrationModifier {

    private final int stabPenalty = 3;
    private final float popMult = 10f;

    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        for (MarketAPI market : MiscIE.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            market.addTransientImmigrationModifier(this);
            market.getStability().modifyFlat(id, -stabPenalty, getName());
        }
    }

    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (MarketAPI market : MiscIE.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            market.removeTransientImmigrationModifier(this);
            market.getStability().unmodify(id);
        }
    }

    @Override
    public void onNewDay() {
        daysPassed = TimeTracker.getTimeTagPassed(market, getModId());

        if (minimumRuntimePassed() || market.getSize() >= Global.getSettings().getInt("maxColonySize")) {
            applyFinishPenalty();
            Global.getSector().getCampaignUI().addMessage("An Edict at %s was removed. It has %s.",
                    Global.getSettings().getColor("standardTextColor"), market.getName(), "achieved its purpose", Misc.getHighlightColor(), Misc.getPositiveHighlightColor());

            removeWithoutPenalty();
            return;
        }

        if (!isPresenceConditionMet(market)) removeWithPenalty();
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        int otherPlanetCount = MiscIE.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId()).size() - 1;

        //if market = this.martket
        if (market.equals(this.market)) {
            incoming.getWeight().modifyFlat(getModId(), -this.market.getSize() * popMult, getName());
        } else {
            incoming.getWeight().modifyFlat(getModId(), (float) Math.ceil((1.0 * this.market.getSize() / otherPlanetCount) * popMult), getName() + " from " + this.market.getName());
        }
    }

    @Override
    public boolean endsOnItsOwn() {
        return true;
    }

    @Override
    public int getFinishUnrest(MarketAPI market) {
        return 3;
    }

    @Override
    protected String getRemainingTimeFormat() {
        return "This edict must stay in place until the population transfer is complete, which will take another %s.";
    }

    @Override
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        List<MarketAPI> others = getOtherColoniesInSystem(market);
        effects.add(EdictEffect.bad("Population growth", signed(-(int) (market.getSize() * popMult))));
        effects.add(stability(market, -stabPenalty));

        if (others.isEmpty()) return effects;
        int increase = (int) Math.ceil(1.0 * market.getSize() / others.size() * popMult);
        for (MarketAPI other : others) {
            effects.add(EdictEffect.good(other.getName() + ": population growth", signed(increase)).onOtherColonies());
            effects.add(EdictEffect.bad(other.getName() + ": stability", signed(-stabPenalty)).onOtherColonies());
        }
        return effects;
    }

    @Override
    protected void addRuntimeLine(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Ends on its own after %s or at the maximum colony size, then causes %s.", pad,
                new Color[]{Misc.getHighlightColor(), Misc.getNegativeHighlightColor()},
                getMonthString(getMinRuntime(market)), getFinishUnrest(market) + " unrest");
    }

    @Override
    public String getShortDesc() {
        return "Relocates parts of the populace to other colonies in this star system, " +
                "causing rapid growth and some unrest. Effect depending on population size.";
    }

    @Override
    protected void addRequirements(List<EdictRequirement> requirements, MarketAPI market) {
        int maxSize = Global.getSettings().getInt("maxColonySize");
        int size = market.getSize();

        requirements.add(military(market));
        requirements.add(twoColonies(market));
        requirements.add(uniqueInSystem(market));
        requirements.add(new EdictRequirement("Colony size 4 to " + (maxSize - 1) + " (now " + size + ")", "Size 4 to " + (maxSize - 1), size > 3 && size < maxSize));
    }
}
