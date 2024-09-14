package indevo.industries.senate.conditions;


/*Edict:	Subpopulation Cleansing
		Upside	Remove any subpopulation (Decived, pirate o.a)
		Downside	-5 stability, massive immiration penalty, reduce income 30%, leaves unrest for size/3 monts after done
		Prerequisite	deciv subpop, mil.presence
		Min. Runtime:	1M/market size, auto-end
*/

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.senate.industry.Senate;
import indevo.utils.helper.MiscIE;

import java.util.ArrayList;
import java.util.Iterator;

public class Edict_Cleanse extends BaseEdict implements MarketImmigrationModifier {

    private int stabPenalty = 0;
    private final float income_penalty = 0.70F;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        stabPenalty = market.getSize() - 1;

        market.addTransientImmigrationModifier(this);
        market.getIncomeMult().modifyMult(id, income_penalty, getName());
        market.getStability().modifyFlat(id, -stabPenalty, getName());
    }

    @Override
    public int getMinRuntime() {
        int minRunTime = market.getSize() * 31;
        return senateHasBetaCore() ? minRunTime - Senate.EDICT_RUNTIME_DAY_RED : minRunTime;
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
    public void applyFinishPenalty() {
        RecentUnrest.get(market).add((int) Math.ceil(market.getSize() / 3.0), getName());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addPara("RemainingDays: %s.", 10f, Misc.getHighlightColor(), getRemainingDays() + " days");

        if (getRemainingDays() > 31) {
            tooltip.addPara("This edict must stay in place until the cleansing is complete, which will take another %s.", 10f, Misc.getHighlightColor(), (int) Math.ceil(getRemainingDays() / 31.0) + " Months");
        } else {
            tooltip.addPara("This edict must stay in place until the cleansing is complete, which will take another %s.", 10f, Misc.getHighlightColor(), getRemainingDays() + " Days");
        }

        tooltip.addPara("%s.", 3f, Misc.getPositiveHighlightColor(), "All Subpopulations will be removed on completion.");

        tooltip.addPara("Colony income is %s to fund the cleanse.", 10f, Misc.getNegativeHighlightColor(), "reduced by " + (int) ((1 - income_penalty) * 100) + "%");
        tooltip.addPara("Colony growth is %s.", 3f, Misc.getNegativeHighlightColor(), "reduced by " + market.getSize() + " points");
        tooltip.addPara("Stability %s for the duration of this.", 3f, Misc.getNegativeHighlightColor(), "reduced by " + stabPenalty);

        tooltip.addPara("Requires a %s in the star system and %s on this planet.", 10f, Misc.getHighlightColor(), new String[]{"Senate", "Military Presence"});
    }

    @Override
    public void printEdictRuntimeText(TextPanelAPI text) {
        int minRunTime = (int) Math.ceil(getMinRuntime() / 31f);
        String s1 = "" + minRunTime + " months";

        text.addPara("The minimum runtime of this Edict is " + s1 + ", after which it will end automatically.");
        text.highlightInLastPara(Misc.getHighlightColor(), s1);
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Purging undesirables from a planet is gruesome work. " +
                "It's also a lot harder if you can't remove them via strategic measures due to urbanization.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        String s1 = "reduced by " + market.getSize();
        String s2 = "reduced by " + (market.getSize() - 1);

        text.addParagraph("All Subpopulations will be removed on completion.");
        text.highlightInLastPara(Misc.getHighlightColor(), "All Subpopulations will be removed on completion.");
        text.addParagraph("Colony income is reduced by 30% to fund the cleanse.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "reduced by 30%");
        text.addParagraph("Colony growth is " + s1);
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), s1);
        text.addParagraph("Stability " + s2 + " for the duration of this.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), s2);
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return "Requires military presence on this planet and a subpopulation to cleanse.";
    }

    @Override
    public String getShortDesc() {
        return "Removes any subpopulations on this planet. Time required and unrest caused by this depend on population size, reduces income by 30% for the duration.";
    }

    @Override
    public boolean isPresenceConditionMet(MarketAPI market) {
        return super.isPresenceConditionMet(market) && MiscIE.marketHasMilitaryIncludeRelays(market) && marketHasConditionSubString(market, "ubpopulation");
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
