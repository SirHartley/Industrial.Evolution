package com.fs.starfarer.api.impl.campaign.econ.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.econ.impl.installableItemPlugins.IndEvo_SpecialItemEffectsRepo.RANGE_LY_TEN;

public class IndEvo_senate extends BaseIndustry {

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
        return Global.getSettings().getBoolean("Senate") && IndEvo_IndustryHelper.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction()) && super.isAvailableToBuild();
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) {
            return super.getUnavailableReason();
        }

        if (!IndEvo_IndustryHelper.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction()) && super.isAvailableToBuild()) {
            return "There can only be one Senate in a star system.";
        } else {
            return super.getUnavailableReason();
        }
    }

    private boolean systemHasEdict() {
        for (MarketAPI market : IndEvo_IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId())) {
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
        return Global.getSettings().getBoolean("Senate");
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
                    tooltip.addPara("%s", 10F, Misc.getHighlightColor(), new String[]{"A senate allows you to issue Edicts on all colonies in the system."});
                } else {
                    tooltip.addPara("%s", 10F, Misc.getPositiveHighlightColor(), new String[]{"You can issue an edict from the main colony menu."});
                }
            }
        }
    }

    //AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
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
        Color highlight = Misc.getHighlightColor();
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
        Color highlight = Misc.getHighlightColor();
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

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.hasIndustry(IndEvo_ids.SENATE)) {
                IndEvo_senate senate = (IndEvo_senate) market.getIndustry(IndEvo_ids.SENATE);

                if (senate.isFunctional() && senate.getSpecialItem() != null) {
                    float dist = Misc.getDistanceLY(locInHyper, senate.market.getLocationInHyperspace());
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
                Color h = Misc.getHighlightColor();
                float opad = 10f;

                String dStr = "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two);
                String lights = "light-years";
                if (dStr.equals("1")) lights = "light-year";

                if (p.two > RANGE_LY_TEN) {
                    text.addPara("The nearest Senate with Neuroconditioning Compounds is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away. The maximum " +
                                    "range in which covert compound deployment is possible is %s light-years.",
                            opad, h,
                            "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "" + (int) RANGE_LY_TEN);
                } else {
                    text.addPara("The nearest Senate with Neuroconditioning Compounds is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away, allowing " +
                                    "you to %s on this colony.",
                            opad, h,
                            "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "issue edicts");
                }
            }
        }
    }
}

