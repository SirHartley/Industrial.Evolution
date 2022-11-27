package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;
import static java.lang.Math.ceil;

public class IndEvo_edict_lockColonySize extends IndEvo_baseEdict {

    private static final String WEIGHT_MEMORY_KEY = "$IndEvo_WeightMemory";

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getMemoryWithoutUpdate().set(WEIGHT_MEMORY_KEY, market.getPopulation().getWeightValue());
        market.getPopulation().setWeight(getWeightForMarketSizeStatic(market.getSize()));
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);

        market.getPopulation().setWeight(market.getMemoryWithoutUpdate().getFloat(WEIGHT_MEMORY_KEY));
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

        tooltip.addPara("Completely stops %s.",
                10f, Misc.getHighlightColor(), "colony growth");

        tooltip.addPara("Requires a %s in the star system.", 10f, Misc.getHighlightColor(), new String[]{"Senate"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Sometimes, the status quo is best kept.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Completely stops this colony from growing.");
        text.highlightInLastPara(Misc.getHighlightColor(), new String[]{"stops", "growing"});
    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return market.getName() + " does not meet the requirements for this Edict.";
    }

    @Override
    public String getShortDesc() {
        return "Stops colony growth";
    }

}
