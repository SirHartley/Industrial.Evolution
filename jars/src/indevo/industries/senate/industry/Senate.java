package indevo.industries.senate.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class Senate extends BaseIndustry {

    private final int alphaStabBonus = 1;
    private final float gammaCoreUpkeepRed = 0.90f;
    public static final int EDICT_RUNTIME_DAY_RED = 31;

    public void apply() {
        super.apply(true);
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.SENATE) && Misc.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction()) && super.isAvailableToBuild();
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) {
            return super.getUnavailableReason();
        }

        if (!Misc.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction()) && super.isAvailableToBuild()) {
            return "There can only be one Senate in a star system.";
        } else {
            return super.getUnavailableReason();
        }
    }

    private boolean systemHasEdict() {
        for (MarketAPI market : Misc.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
            for (MarketConditionAPI cond : market.getConditions()) {
                if (cond.getIdForPluginModifications().contains("edict")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.SENATE);
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!this.market.isPlayerOwned()) {
            return;
        }

        if (!this.isBuilding()) {
            float opad = 5.0F;

            if (!systemHasEdict()) {
                if (currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY) {
                    tooltip.addPara("%s", 10F, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{"A senate allows you to issue Edicts on all colonies in the system."});
                } else {
                    tooltip.addPara("%s", 10F, com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), new String[]{"You can issue an edict from the main colony menu."});
                }
            }
        }
    }

    //AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases colony stability by %s.", 0.0F, highlight, new String[]{alphaStabBonus + ""});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases colony stability by %s.", opad, highlight, new String[]{alphaStabBonus + ""});
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the minimum runtime of Edicts by %s. Does not affect temporary edicts.", 0f, highlight, new String[]{"1 month"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases the minimum runtime of Edicts by %s. Does not affect temporary edicts.", opad, highlight, new String[]{"1 month"});
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Reduces upkeep cost by %s.", 0.0F, highlight, new String[]{(int) ((1.0F - gammaCoreUpkeepRed) * 100.0F) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Reduces upkeep cost by %s.", 0.0F, highlight, new String[]{(int) ((1.0F - gammaCoreUpkeepRed) * 100.0F) + "%"});
        }
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        if (aiCoreId != null && !"beta_core".equals(aiCoreId) && !"gamma_core".equals(aiCoreId)) {
            String name = "Senate: Alpha Core";
            market.getStability().modifyFlat("ind_core", alphaStabBonus, name);
        } else if ("gamma_core".equals(aiCoreId)) {
            String name = "Gamma Core assigned";
            this.getUpkeep().modifyMult("ind_core", gammaCoreUpkeepRed, name);
        } else {
            this.getUpkeep().unmodifyMult("ind_core");
            market.getStability().unmodify("ind_core");
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }


    public static Pair<MarketAPI, Float> getNearestSenateWithItem(Vector2f locInHyper) {
        MarketAPI nearest = null;
        float minDist = Float.MAX_VALUE;

        for (MarketAPI market : com.fs.starfarer.api.util.Misc.getFactionMarkets("player")) {
            if (market.hasIndustry(Ids.SENATE)) {
                Senate senate = (Senate) market.getIndustry(Ids.SENATE);

                if (senate.isFunctional() && senate.getSpecialItem() != null) {
                    float dist = com.fs.starfarer.api.util.Misc.getDistanceLY(locInHyper, senate.market.getLocationInHyperspace());
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = market;
                    }
                }
            }
        }

        if (nearest == null) return null;

        return new Pair<>(nearest, minDist);
    }

    //register this in mod plugin
    public static class SenateFactor implements ColonyOtherFactorsListener {
        public boolean isActiveFactorFor(SectorEntityToken entity) {
            return getNearestSenateWithItem(entity.getLocationInHyperspace()) != null;
        }

        public void printOtherFactors(TooltipMakerAPI text, SectorEntityToken entity) {
            Pair<MarketAPI, Float> p = getNearestSenateWithItem(entity.getLocationInHyperspace());

            if (p != null) {
                Color h = com.fs.starfarer.api.util.Misc.getHighlightColor();
                float opad = 10f;

                String dStr = "" + com.fs.starfarer.api.util.Misc.getRoundedValueMaxOneAfterDecimal(p.two);
                String lights = "light-years";
                if (dStr.equals("1")) lights = "light-year";

                if (p.two > RANGE_LY_TWELVE) {
                    text.addPara("The nearest Senate with Neuroconditioning Compounds is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away. The maximum " +
                                    "range in which covert compound deployment is possible is %s light-years. It is %s to issue edicts through compound application here.",
                            opad, h,
                            "" + com.fs.starfarer.api.util.Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "" + (int) RANGE_LY_TWELVE,
                            "not possible");
                } else {
                    text.addPara("The nearest Senate with Neuroconditioning Compounds is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away, allowing " +
                                    "you to %s on this colony.",
                            opad, h,
                            "" + com.fs.starfarer.api.util.Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "issue edicts");
                }
            }
        }
    }
}

