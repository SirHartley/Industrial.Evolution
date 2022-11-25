package industrial_evolution.industries.senate.conditions;

import industrial_evolution.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static java.lang.Math.ceil;

public class IndEvo_edict_fastBuild extends IndEvo_baseEdict {
    private static final float FAST_BUILD_MULT = 0.5f;
    private static final float OUTPUT_DECREASE_MULT = 0.5f;

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().modifyMult(getModId(), OUTPUT_DECREASE_MULT, getName());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().modifyFlat(getModId(), OUTPUT_DECREASE_MULT, getName());
                }
            }
        }
    }

    @Override
    public void onNewDay() {
        super.onNewDay();

        for (Industry ind : market.getIndustries()){
            if (ind.isBuilding() || ind.isUpgrading()){
                BaseIndustry industry = (BaseIndustry) ind;
                industry.setBuildProgress(industry.getBuildProgress() + 1f);
            }
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().unmodify(getModId());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().unmodify(getModId());
                }
            }
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

        tooltip.addPara("%s of all buildings on this planet by %s",
                3f, Misc.getPositiveHighlightColor(), new String[]{"Decreases construction time", IndEvo_StringHelper.getAbsPercentString(FAST_BUILD_MULT, false)});

        tooltip.addPara("%s of all industries on this planet by %s",
                3f, Misc.getNegativeHighlightColor(), new String[]{"Decreases the output", IndEvo_StringHelper.getAbsPercentString(OUTPUT_DECREASE_MULT, false)});

        tooltip.addPara("Requires a %s in the star system.", 10f, Misc.getHighlightColor(), new String[]{"Senate"});
    }

    @Override
    public void printEdictFlavourText(TextPanelAPI text) {
        text.addParagraph("Mandating a 16 hour work day and conscripting labour from non-essential industries " +
                "will hasten construction projects beyond belief. Anything else is unlikely to get done, though.");
    }

    @Override
    public void printEdictEffectText(TextPanelAPI text, MarketAPI market) {
        text.addParagraph("Construction times reduced by 50%%.");
        text.highlightInLastPara(Misc.getPositiveHighlightColor(), "reduced by 50%%");
        text.addParagraph("All industries on this colony have their output decreased by 50%%.");
        text.highlightInLastPara(Misc.getNegativeHighlightColor(), "output decreased by 50%%");

    }

    @Override
    public String getUnavailableReason(MarketAPI market) {
        return market.getName() + " does not meet the requirements for this Edict.";
    }

    @Override
    public String getShortDesc() {
        return "Decreases construction times, decreases all industry output.";
    }
}
