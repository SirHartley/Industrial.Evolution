package indevo.industries.senate.conditions;

import com.fs.starfarer.api.Global;
import indevo.utils.helper.IndustryHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import indevo.utils.timers.TimeTracker;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

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

        for (MarketAPI market : IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {

            market.addTransientImmigrationModifier(this);
            market.getStability().modifyFlat(id, -stabPenalty, getName());
        }
    }

    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (MarketAPI market : IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
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
        }

        if (!isPresenceConditionMet(market)) removeWithPenalty();
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        int otherPlanetCount = IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId()).size() - 1;

        //if market = this.martket
        if (market.equals(this.market)) {
            incoming.getWeight().modifyFlat(getModId(), -this.market.getSize() * popMult, getName());
        } else {
            incoming.getWeight().modifyFlat(getModId(), (float) (this.market.getSize() / otherPlanetCount) * popMult, getName() + " from " + this.market.getName());
        }
    }

    @Override
    public void applyFinishPenalty() {
        RecentUnrest.get(market).add(3, getName());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        int otherPlanetCount = IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId()).size() - 1;
        float otherPlanetIncrease = (float) ceil((1.0 * this.market.getSize() / otherPlanetCount) * popMult);

        if (getRemainingDays() > 31) {
            tooltip.addPara("This edict must stay in place until the population transfer is complete, which will take another %s.", 10f, Misc.getHighlightColor(), (int) Math.ceil(getRemainingDays() / 31.0) + " Months");
        } else {
            tooltip.addPara("This edict must stay in place until the population transfer is complete, which will take another %s.", 10f, Misc.getHighlightColor(), getRemainingDays() + " Days");
        }

        tooltip.addPara("Stability %s for all colonies in the system.",
                10f, Misc.getNegativeHighlightColor(), "reduced by " + stabPenalty);
        tooltip.addPara("Population growth of this colony %s.",
                3f, Misc.getNegativeHighlightColor(), "reduced by " + (int) (market.getSize() * popMult));
        tooltip.addPara("Population growth for:",
                3f);
        for (MarketAPI market : IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            if (!market.getId().equals(this.market.getId())) {
                tooltip.addPara(BaseIntelPlugin.BULLET + market.getName() + ": increased by %s.",
                        1f, Misc.getPositiveHighlightColor(), (int) otherPlanetIncrease + "");
            }
        }

        tooltip.addPara("Requires a %s and at least two colonies in the star system, %s on this planet, and the colony must not have the maximum size.", 10f, Misc.getHighlightColor(), new String[]{"Senate", "Military Presence"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Relocating your populace to other colonies might sound " +
                "slightly unethical, but is definitely the most effective way to grow a small colony.");

    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        int otherPlanetCount = IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId()).size() - 1;
        int otherPlanetIncrease = (int) ceil((1.0 * market.getSize() / otherPlanetCount) * 10);
        String s1 = otherPlanetIncrease + "";
        String s2 = "reduced by " + (market.getSize() * 10);

        text.addParagraph("Population growth for this colony " + s2);
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), s2);
        text.addParagraph("Population growth for:");
        for (MarketAPI market1 : IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId())) {
            if (!market1.getId().equals(market.getId())) {
                text.addParagraph(BaseIntelPlugin.BULLET + market1.getName() + ": increased by " + s1);
                text.highlightInLastPara(Misc.getHighlightColor(), market1.getName());
                text.highlightInLastPara(Misc.getPositiveHighlightColor(), s1);
            }
        }

        text.addParagraph("Stability reduced by 3 for all colonies in the star system.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "reduced by 3");
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return "Requires military presence on this planet, at least two colonies in the system, " +
                "can only be active on one colony at a time (Star system), and the colony must not have the maximum size.";
    }

    @Override
    public String getShortDesc() {
        return "Relocates parts of the populace to other colonies in this star system, " +
                "causing rapid growth and some unrest. Effect depending on population size.";
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        boolean twoMarkets = IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId()).size() > 1;

        return super.isPresenceConditionMet(market)
                && IndustryHelper.marketHasMilitaryIncludeRelays(market)
                && conditionUniqueInSystem(market, condition.getId())
                && twoMarkets
                && market.getSize() < Global.getSettings().getInt("maxColonySize");
    }
}
