package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import java.awt.*;
import java.util.List;

public class TradeCenter extends BaseIndustry implements MarketImmigrationModifier {

    public static float BASE_BONUS = 50f;
    public static float ALPHA_CORE_BONUS = 25f;
    public static float IMPROVE_BONUS = 25f;
    public static float ALT_IMPROVE_BONUS = 10f;
    public static float ALPHA_CORE_TARIFF_MULT = 0.75f;

    public static float STABILITY_PELANTY = 3f;

    protected boolean balanceChange = false;
    public float improveBonus = 25f;

    //protected transient CargoAPI savedCargo = null;
    protected transient SubmarketAPI saved = null;

    @Override
    public void init(String id, MarketAPI market) {
        adjustForBalanceChanges();
        super.init(id, market);
    }

    @Override
    public void initWithParams(List<String> params) {
        adjustForBalanceChanges();
        super.initWithParams(params);
    }

    private void adjustForBalanceChanges() {
        balanceChange = Settings.getBoolean(Settings.COMMERCE_BALANCE_CHANGES);

        improveBonus = balanceChange ? ALT_IMPROVE_BONUS : IMPROVE_BONUS;
    }

    public void apply() {
        super.apply(true);

        adjustForBalanceChanges();

        if (isFunctional() && market.isPlayerOwned()) {
            SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            if (open == null) {
                if (saved != null) {
                    market.addSubmarket(saved);
                } else {
                    market.addSubmarket(Submarkets.SUBMARKET_OPEN);
                    SubmarketAPI sub = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
                    sub.setFaction(Global.getSector().getFaction(Factions.INDEPENDENT));
                    Global.getSector().getEconomy().forceStockpileUpdate(market);
                }
            }

        } else if (market.isPlayerOwned()) {
            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
        }

        //modifyStabilityWithBaseMod();
        market.getStability().modifyFlat(getModId(), -STABILITY_PELANTY, getNameForModifier());

        market.getIncomeMult().modifyPercent(getModId(0), BASE_BONUS, getNameForModifier());

        if (!isFunctional()) {
            unapply();
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market.isPlayerOwned()) {
            SubmarketAPI open = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            saved = open;

            market.removeSubmarket(Submarkets.SUBMARKET_OPEN);
        }

        market.getStability().unmodifyFlat(getModId());
        market.getIncomeMult().unmodifyPercent(getModId(0));
    }

    protected void addStabilityPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        Color h = Misc.getHighlightColor();
        float opad = 10f;

        float a = BASE_BONUS;
        String aStr = "+" + (int) Math.round(a * 1f) + "%";
        tooltip.addPara("Colony income: %s", opad, h, aStr);

        h = Misc.getNegativeHighlightColor();
        tooltip.addPara("Stability penalty: %s", opad, h, "" + -(int) STABILITY_PELANTY);
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (market.isPlayerOwned() || currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY) {
            tooltip.addPara("Adds an independent \'Open Market\' that the colony's owner is able to trade with.", 10f);
        }

        if (balanceChange && currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY && isAvailableToBuild()) {
            tooltip.addPara("%s", 10F, Misc.getHighlightColor(), new String[]{"Can only have one in the star system"});
        }
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            addStabilityPostDemandSection(tooltip, hasDemand, mode);
        }
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.add(Factions.TRITACHYON, 10f);
    }


    public boolean isAvailableToBuild() {
        boolean isAvailable = true;
        if (balanceChange)
            isAvailable = MiscIE.isOnlyInstanceInSystemExcludeMarket(Ids.COMMERCE, market.getStarSystem(), market, market.getFaction());

        return super.isAvailableToBuild() && market.hasSpaceport() && isAvailable;
    }

    public String getUnavailableReason() {
        if (balanceChange && !MiscIE.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction()))
            return "Can only have one in the star system";

        return "Requires a functional spaceport";
    }

    @Override
    public String getCurrentImage() {
        float size = market.getSize();
        if (size <= SIZE_FOR_SMALL_IMAGE) {
            return Global.getSettings().getSpriteName("industry", "commerce_low");
        }
        if (size >= SIZE_FOR_LARGE_IMAGE) {
            return Global.getSettings().getSpriteName("industry", "commerce_high");
        }

        return super.getCurrentImage();
    }


    //market.getIncomeMult().modifyMult(id, INCOME_MULT, "Industrial planning");
    @Override
    protected void applyAlphaCoreModifiers() {
        if (balanceChange && market.isPlayerOwned())
            market.getTariff().modifyMult(getModId(1), ALPHA_CORE_TARIFF_MULT, "Alpha core (" + getNameForModifier() + ")");
        else
            market.getIncomeMult().modifyPercent(getModId(1), ALPHA_CORE_BONUS, "Alpha core (" + getNameForModifier() + ")");
    }

    @Override
    protected void applyNoAICoreModifiers() {
        market.getIncomeMult().unmodifyPercent(getModId(1));
        market.getTariff().unmodify(getModId());
    }

    @Override
    protected void applyAlphaCoreSupplyAndDemandModifiers() {
        demandReduction.modifyFlat(getModId(0), DEMAND_REDUCTION, "Alpha core");
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }
        float a = ALPHA_CORE_BONUS;
        String str = "" + (int) Math.round(a) + "%";

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);

            if (balanceChange) text.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                            "Reduces tariffs by %s.", 0f, highlight,
                    "" + (int) ((1f - UPKEEP_MULT) * 100f) + "%", StringHelper.getAbsPercentString(ALPHA_CORE_TARIFF_MULT, true),
                    str);

            else text.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                            "Increases colony income by %s.", 0f, highlight,
                    "" + (int) ((1f - UPKEEP_MULT) * 100f) + "%", "" + DEMAND_REDUCTION,
                    str);
            tooltip.addImageWithText(opad);
            return;
        }

        if (balanceChange) tooltip.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                        "Reduces tariffs by %s.", 0f, highlight,
                "" + (int) ((1f - UPKEEP_MULT) * 100f) + "%", StringHelper.getAbsPercentString(ALPHA_CORE_TARIFF_MULT, true),
                str);

        else tooltip.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s unit. " +
                        "Increases colony income by %s.", 0f, highlight,
                "" + (int) ((1f - UPKEEP_MULT) * 100f) + "%", "" + DEMAND_REDUCTION,
                str);

    }


    @Override
    public boolean canImprove() {
        return true;
    }

    protected void applyImproveModifiers() {
        if (isImproved()) {
            market.getIncomeMult().modifyPercent(getModId(2), improveBonus,
                    getImprovementsDescForModifiers() + " (" + getNameForModifier() + ")");
        } else {
            market.getIncomeMult().unmodifyPercent(getModId(2));
        }
    }

    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        float a = improveBonus;
        String aStr = "" + (int) Math.round(a * 1f) + "%";

        if (mode == ImprovementDescriptionMode.INDUSTRY_TOOLTIP) {
            info.addPara("Colony income increased by %s.", 0f, highlight, aStr);
        } else {
            info.addPara("Increases colony income by %s.", 0f, highlight, aStr);
        }

        info.addSpacer(opad);
        super.addImproveDesc(info, mode);
    }
}





