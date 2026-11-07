package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.ReflectionUtils;
import indevo.utils.helper.StarReflectionUtils;

import java.util.HashMap;
import java.util.Map;


public class Edict_FastBuild extends BaseEdict {
    public static final String ID = "IndEvo_edict_fastBuild";
    public static final float FAST_BUILD_MULT = 0.5f;
    private static final float OUTPUT_DECREASE_MULT = 0.5f;

    private Map<String, Float> originalBuildTimes = new HashMap<>();

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        for (Industry ind : market.getIndustries()) {
            if (!ind.isBuilding() || !(ind instanceof BaseIndustry industry)) continue;

            float current = getBuildTime(industry);
            Float original = getOriginalBuildTimes().get(ind.getId());
            if (original == null || current != original * FAST_BUILD_MULT) original = current;

            getOriginalBuildTimes().put(ind.getId(), original);
            setBuildTime(industry, original * FAST_BUILD_MULT);
        }

        for (Industry industry : market.getIndustries()) {
            if (industry.isIndustry()) {
                for (MutableCommodityQuantity supply : industry.getAllSupply()) {
                    industry.getSupply(supply.getCommodityId()).getQuantity().modifyMult(getModId(), OUTPUT_DECREASE_MULT, getName());
                }
                for (MutableCommodityQuantity demand : industry.getAllDemand()) {
                    industry.getDemand(demand.getCommodityId()).getQuantity().modifyMult(getModId(), OUTPUT_DECREASE_MULT, getName());
                }
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

        for (Industry ind : market.getIndustries()) {
            Float original = getOriginalBuildTimes().get(ind.getId());
            if (original == null || !ind.isBuilding() || !(ind instanceof BaseIndustry industry)) continue;

            // a build started since apply set its own time, keep that
            if (getBuildTime(industry) == original * FAST_BUILD_MULT) setBuildTime(industry, original);
        }

        getOriginalBuildTimes().clear();
    }

    public static boolean isActive(MarketAPI market) {
        return market != null && market.hasCondition(ID);
    }

    // same truncation as the vanilla "days left" text once the halved build runs
    public static int getReducedDays(int days) {
        return Math.max(1, (int) (days * FAST_BUILD_MULT));
    }

    private Map<String, Float> getOriginalBuildTimes() {
        if (originalBuildTimes == null) originalBuildTimes = new HashMap<>();
        return originalBuildTimes;
    }

    private static float getBuildTime(BaseIndustry industry) {
        return (float) StarReflectionUtils.get(industry, "buildTime", float.class, true);
    }

    private static void setBuildTime(BaseIndustry industry, float days) {
        ReflectionUtils.setWithSuper("buildTime", industry, days, 5, null);
    }

    @Override
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Construction times on this colony %s.", pad, Misc.getPositiveHighlightColor(), "reduced by " + Math.round((1 - FAST_BUILD_MULT) * 100) + "%");
        tooltip.addPara("All industries on this colony have their output and demand %s.", 3f, Misc.getNegativeHighlightColor(), "decreased by " + Math.round((1 - OUTPUT_DECREASE_MULT) * 100) + "%");
    }

    @Override
    public String getShortDesc() {
        return "Decreases construction times, decreases all industry output.";
    }
}
