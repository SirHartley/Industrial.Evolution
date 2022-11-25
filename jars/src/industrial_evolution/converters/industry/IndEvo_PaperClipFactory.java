package industrial_evolution.converters.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_PaperClipFactory extends BaseIndustry {
    //outputs paperclips
    //paperclips are a dummy commodity built on consumer goods
    //output is nominal
    //when log core is installed, increase output by 4, and quadratically each month up to 30
    //if installed at month end, take over the colony, shut the player out, and send 2 takeover fleets to other planets
    //after first planet, every planet sends 1 takeover fleet per amount of planets in the faction (3 planets - 3 fleets after 3 months, resulting in 6 planets sending 6 fleets after 6 more months)
    //if the player satbombs the planet, the nanite paperclip infection is lifted, the factories are destroyed and the planet gets returned to the original controlling faction

    @Override
    public void apply() {

    }

    @Override
    public void unapply() {
        super.unapply();
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = IndEvo_StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String coreHighlights = IndEvo_StringHelper.getString(getId(), "aCoreHighlights");
        String effect = IndEvo_StringHelper.getStringAndSubstituteToken(getId(), "aCoreEffect", "$aCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{Integer.toString(ALPHA_CORE_BONUS_REPAIR), IndEvo_StringHelper.getAbsPercentString(ALPHA_CORE_UPKEEP_RED_MULT, true), coreHighlights};

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = IndEvo_StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String coreHighlights = IndEvo_StringHelper.getString(getId(), "bCoreHighlights");
        String effect = IndEvo_StringHelper.getStringAndSubstituteToken(getId(), "bCoreEffect", "$bCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{IndEvo_StringHelper.getAbsPercentString(BETA_CORE_COST_RED_MULT, true), coreHighlights};

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = IndEvo_StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String coreHighlights = IndEvo_StringHelper.getString(getId(), "gCoreHighlights");
        String effect = IndEvo_StringHelper.getStringAndSubstituteToken(getId(), "gCoreEffect", "$gCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{Integer.toString(GAMMA_CORE_MAX_REPAIR), coreHighlights};


        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void applyAlphaCoreModifiers() {
        super.applyAlphaCoreModifiers();
    }

    @Override
    protected void applyBetaCoreModifiers() {

    }

    @Override
    protected void applyNoAICoreModifiers() {

    }

}
