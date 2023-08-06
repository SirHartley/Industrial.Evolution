package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.industries.embassy.industry.Embassy;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.submarkets.RequisitionsCenterSubmarketPlugin;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.SubMarketAddOrRemovePlugin;

import java.awt.*;
import java.util.List;

public class RequisitionCenter extends BaseIndustry {

    private static final float MIN_REP_REQUIREMENT = 0.35f;
    public static final float MED_REP_REQUIREMENT = 0.5f;
    public static final float HIGH_REP_REQUIREMENT = 0.7f;

    public static final String IDENT = "IndEvo_ReqCenter";

    public void apply() {
        super.apply(true);
        Global.getSector().getListenerManager().addListener(this, true);

        if (isFunctional()) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.REQMARKET, false));
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.REQMARKET, true));
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.REQCENTER;
    }

    @Override
    public boolean showWhenUnavailable() {
        return Settings.REQCENTER;
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        publicAddAfterDescriptionSection(tooltip, mode); //Submarket has to access the tooltip to avoid duplicate code, so it has to be public - made a duplicate since I am forced by the API to use the protected access method.
    }

    public void publicAddAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {

        FactionAPI marketFaction = Global.getSector().getPlayerFaction(); //always get the player faction, for AI control options
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        if (!isBuilding() && isFunctional() && mode.equals(IndustryTooltipMode.NORMAL)) {
            float opad = 5.0F;

            tooltip.addSectionHeading("Weapon Imports", color, dark, Alignment.MID, 10f);

            int repInt = (int) Math.ceil((Math.round(MIN_REP_REQUIREMENT * 100f)));
            tooltip.addPara("The minimum reputation for weapon imports is %s", opad, Misc.getTextColor(), Misc.getHighlightColor(), "+" + repInt);

            if (market.hasIndustry(Ids.EMBASSY)
                    && AmbassadorPersonManager.hasAmbassador(market)
                    && IndustryHelper.getAiCoreIdNotNull(this).equals(Commodities.GAMMA_CORE)) {

                FactionAPI alignedFaction = ((Embassy) market.getIndustry(Ids.EMBASSY)).alignedFaction;

                if (alignedFaction != null) {
                    float rel = marketFaction.getRelationship(alignedFaction.getId());
                    Pair<String, Color> standing = StringHelper.getRepIntTooltipPair(alignedFaction);
                    String weaponString;

                    if (repInt < Math.round(MIN_REP_REQUIREMENT * 100)) {

                        weaponString = getAmountString(rel, true);
                        tooltip.addPara("The Centre is currently sourcing %s as your standing with " + alignedFaction.getDisplayNameWithArticle() + " is not high enough. %s", opad, Misc.getTextColor(), standing.two, new String[]{weaponString, standing.one});
                        tooltip.addPara("Consider installing a %s.", 2f, Misc.getTextColor(), Misc.getHighlightColor(), Global.getSettings().getCommoditySpec(Commodities.GAMMA_CORE).getName());
                    } else {

                        weaponString = getAmountString(rel, true);
                        tooltip.addPara("The Centre is currently sourcing %s of weapons from " + alignedFaction.getDisplayNameWithArticle() + ". %s", opad, Misc.getTextColor(), standing.two, new String[]{weaponString, standing.one});
                    }
                }
            } else { //if it doesn't have an embassy
                List<FactionAPI> activeFactionList = RequisitionsCenterSubmarketPlugin.getActiveFactionList(MIN_REP_REQUIREMENT, marketFaction);

                tooltip.addPara("Weapon sourcing overview:", opad);
                tooltip.beginTable(marketFaction, 20f, "Faction", 180f, "Weapon #", 80f, "Standing", 130f);

                for (FactionAPI faction : activeFactionList) {
                    RepLevel level = marketFaction.getRelationshipLevel(faction.getId());
                    float rel = marketFaction.getRelationship(faction.getId());
                    repInt = (int) Math.ceil((Math.round(rel * 100f)));

                    String factionName = faction.getDisplayName();
                    String weaponAmount = getAmountString(rel, false);
                    String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";

                    tooltip.addRow(factionName, weaponAmount, standing);
                }

                tooltip.addTable("You are not in good enough favour with any faction.", 0, opad);
            }
        }
    }

    private static String getAmountString(Float reputation, boolean longDesc) {
        String weaponString = "w0";

        if (reputation < MIN_REP_REQUIREMENT) weaponString = longDesc ? "w1" : "none";
        else if (reputation >= MIN_REP_REQUIREMENT && reputation < MED_REP_REQUIREMENT)
            weaponString = longDesc ? "w2" : "low";
        else if (reputation >= MED_REP_REQUIREMENT && reputation < HIGH_REP_REQUIREMENT)
            weaponString = longDesc ? "w3" : "medium";
        else if (reputation >= HIGH_REP_REQUIREMENT) weaponString = longDesc ? "w4" : "high";

        return StringHelper.getString(IDENT, weaponString);
    }

    /*AI core effect:
Gamma	Sets the submarket to ignore any ambassador present and continue sourcing from everywhere (maybe make button in tooltip?
Beta	Stops sourcing weapons you have 5 or more of in storage (anywhere), increases the total amount of weapons for sale
Alpha	Reduces stock-up time, Increases the amount of rare/large weapons for sale

actual effect handling is in RequisitionsCenterSubmarketPlugin*/

    //AI-Core tooltips
    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Tries to avoid weapons you have %s or more of in storage, increases the %s for sale.", 0f, Misc.getPositiveHighlightColor(), new String[]{"10", "total amount of weapons"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Tries to avoid weapons you have %s or more of in storage, increases the %s for sale.", opad, Misc.getPositiveHighlightColor(), new String[]{"10", "total amount of weapons"});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Increases the amount of %s for sale.", 0f, Misc.getPositiveHighlightColor(), new String[]{"large and rare weapons"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Increases the amount of %s for sale.", opad, Misc.getPositiveHighlightColor(), new String[]{"large and rare weapons"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Sets the Requisitions Centre to %s installed in the Embassy, sourcing from any eligible faction.", 0f, Misc.getPositiveHighlightColor(), new String[]{"ignore any ambassador"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Sets the Requisitions Centre to %s installed in the Embassy, sourcing from any eligible faction.", opad, Misc.getPositiveHighlightColor(), new String[]{"ignore any ambassador"});
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }
}