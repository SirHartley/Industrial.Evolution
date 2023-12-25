package indevo.industries.embassy.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.embassy.AmbassadorItemHelper;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.industries.embassy.rules.IndEvo_ambassadorRemoval;
import indevo.items.installable.AmbassadorInstallableItemPlugin;
import indevo.items.specialitemdata.AmbassadorItemData;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.timers.NewDayListener;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static indevo.industries.embassy.listeners.AmbassadorPersonManager.adjustRelationship;

public class Embassy extends BaseIndustry implements EconomyTickListener, NewDayListener {
    private boolean debug = false;

    public FactionAPI alignedFaction = null;
    protected SpecialItemData ambassadorItemData = null;

    public static final float BASE_MAX_RELATION = 0.40f;
    public static final float ALPHA_CORE_REP_PENALTY_MULT = 0.50f;
    public static final float BETA_CORE_REP_PENALTY = -0.20f;
    public static final float BETA_CORE_MAX_RELATION = 0.55f;
    public static final float GAMMA_CORE_UPKEEP_RED_MULT = 0.90f;

    private float maxRelation = 0.40F;
    private boolean betaCoreInstalled = false;

    private int monthsPassed = 0;

    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();

        applyIndEvo_AmbassadorItemEffects();

        if (isFunctional() && market.isPlayerOwned()) {
            Global.getSector().getListenerManager().addListener(this, true);
        }

        if (!market.isPlayerOwned()) {
            if (getSpecialItem() != null) {
                setSpecialItem(null);
                AmbassadorPersonManager.removeAmbassadorFromMarket(market);
            }
        } else if (AmbassadorPersonManager.hasAmbassador(market) && getSpecialItem() == null) {
            AmbassadorPersonManager.removeAmbassadorFromMarket(market);
        }
    }

/*
    public boolean createAndSetAmbassador(String factionId){
        PersonAPI amb = AmbassadorPersonManager.createAmbassador(market, false, factionId);

        if(amb != null){
            setAmbassadorItemData(new AmbassadorItemData(ItemIds.AMBASSADOR, null, amb));
            amb.getMemoryWithoutUpdate().set("$IndEvo_ForcedAmbassador", true);
        } else return false;

        return true;
    }*/

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
    }

    @Override
    public void initWithParams(java.util.List<String> params) {
        super.initWithParams(params);

        for (String str : params) {
            if (AmbassadorInstallableItemPlugin.AMBASSADOR_EFFECTS.containsKey(str)) {
                setAmbassadorItemData(new SpecialItemData(str, null));
                break;
            }
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.EMBASSY) && market.getFactionId().equals("player");
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) return super.getUnavailableReason();
        else return "Can only be established at your own colonies!";
    }

    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.EMBASSY);
    }

    private void monthlyRepIncrease() {
        if (ambassadorItemData == null || !isFunctional() || !market.isPlayerOwned()) {
            return;
        }

        FactionAPI baseFaction = market.getFaction();
        String alignedFactionId = alignedFaction.getId();
        float currentRelationship = baseFaction.getRelationship(alignedFactionId);

        if (currentRelationship >= maxRelation) return;

        //If rel difference smaller than 10, get difference, else it's 10
        float addition = currentRelationship > (maxRelation - 0.10F) ? maxRelation - currentRelationship : 0.1F;

        //adjust and update current relationship level
        adjustRelationship(baseFaction, alignedFaction, addition);
        AmbassadorPersonManager.getInstance().reportEmbassyRepChange(alignedFaction, addition);
    }

    @Override
    public void onNewDay() {
        if (market.isPlayerOwned() && betaCoreInstalled && !IndustryHelper.getAiCoreIdNotNull(this).equals(Commodities.BETA_CORE)) {
            betaCoreRemovalPenalty();
            betaCoreInstalled = false;
        }
    }

    public void reportEconomyTick(int iterIndex) {
        if (!market.isPlayerOwned()) return;

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (!market.isPlayerOwned() || (iterIndex != lastIterInMonth && !debug)) return;

        monthlyRepIncrease();

        if (getSpecialItem() != null && monthsPassed <= 3) {
            monthsPassed++;
        }

        if (getAICoreId() != null && getAICoreId().equals(Commodities.BETA_CORE)) {
            betaCoreInstalled = true;
        }
    }

    public void reportEconomyMonthEnd() {
    }

    public boolean isAmbMovementAllowed() {
        return monthsPassed >= 3 || Global.getSettings().isDevMode();
    }

    public int getMonthsPassed() {
        return monthsPassed;
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {

        if (isFunctional() && market.getFactionId().equals("player")) {
            float opad = 5.0F;
            Color highlight = Misc.getHighlightColor();
            Color bad = Misc.getNegativeHighlightColor();

            String warn1 = StringHelper.getString(getId(), "requiresAmbassador");
            String warn2 = StringHelper.getString(getId(), "canHireAt");

            if (currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY) {
                tooltip.addPara("%s", opad, highlight, warn1);
                tooltip.addPara("%s", 2F, highlight, warn2);
            } else if (getSpecialItem() == null) {
                tooltip.addPara("%s", opad, bad, warn1);
                tooltip.addPara("%s", 2F, bad, warn2);
            }

            if (currTooltipMode == IndustryTooltipMode.NORMAL && getSpecialItem() != null) {
                if (AmbassadorPersonManager.getAmbassador(market) == null) {
                    tooltip.addPara("%s", opad, Misc.getPositiveHighlightColor(), StringHelper.getString(getId(), "currentlyTakingOffice"));
                } else {
                    FactionAPI player = Global.getSector().getPlayerFaction();
                    RepLevel level = player.getRelationshipLevel(alignedFaction.getId());
                    Color relColor = alignedFaction.getRelColor(player.getId());
                    int repInt = (int) Math.ceil((Math.round(player.getRelationship(alignedFaction.getId()) * 100f)));
                    String standing = "" + repInt + "/" + (int) (maxRelation * 100) + " (" + level.getDisplayName().toLowerCase() + ")";

                    tooltip.addPara(StringHelper.getString(getId(), "currentPersonInOffice"), opad, Misc.getHighlightColor(), AmbassadorPersonManager.getAmbassador(market).getNameString());
                    tooltip.addPara(StringHelper.getString(getId(), "factionJusrisdiction"), opad, alignedFaction.getColor(), alignedFaction.getDisplayName());
                    tooltip.addPara(StringHelper.getString(getId(), "currentStanding"), opad, relColor, standing);
                }
            }
        } else if (isFunctional() && market.isPlayerOwned()) {
            tooltip.addPara("Can not inaugurate an Ambassador on governed colonies!", 10f, Misc.getNegativeHighlightColor());
        }
    }


    @Override
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (ambassadorItemData == null) return false;

        float opad = 10f;

        TooltipMakerAPI text = tooltip.beginImageWithText(AmbassadorItemHelper.getPersonForItem(ambassadorItemData).getPortraitSprite(), 48);
        AmbassadorInstallableItemPlugin.IndEvo_AmbassadorEffect effect = AmbassadorInstallableItemPlugin.AMBASSADOR_EFFECTS.get(ambassadorItemData.getId());
        effect.addItemDescription(text, ambassadorItemData, InstallableIndustryItemPlugin.InstallableItemDescriptionMode.INDUSTRY_TOOLTIP);

        tooltip.addImageWithText(opad);

        return true;
    }

    private void betaCoreRemovalPenalty() {
        if (alignedFaction != null) {
            adjustRelationship(market.getFaction(), alignedFaction, BETA_CORE_REP_PENALTY);

            FactionAPI player = Global.getSector().getPlayerFaction();
            int repInt = (int) Math.ceil((Math.round(player.getRelationship(alignedFaction.getId()) * 100f)));
            RepLevel level = player.getRelationshipLevel(alignedFaction.getId());
            Color relColor = alignedFaction.getRelColor(player.getId());
            String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";

            Map<String, String> toReplace = new HashMap<>();
            toReplace.put("$factionName", alignedFaction.getDisplayName());
            toReplace.put("$decreasedByPenalty", StringHelper.getString(getId(), "decreasedByInt") + StringHelper.getFloatToIntStrx100(-BETA_CORE_REP_PENALTY));

            MessageIntel intel = new MessageIntel(StringHelper.getStringAndSubstituteTokens(getId(), "betaCorePenalty", toReplace),
                    Misc.getTextColor(),
                    new String[]{toReplace.get("$factionName"), toReplace.get("$decreasedByPenalty")},
                    alignedFaction.getColor(),
                    Misc.getNegativeHighlightColor());

            intel.addLine(BaseIntelPlugin.BULLET + StringHelper.getString(getId(), "currentAt"), null, new String[]{standing}, relColor);
            intel.addLine(BaseIntelPlugin.BULLET + StringHelper.getStringAndSubstituteToken(getId(), "betaCorePenaltyChange", "$marketName", market.getName()), Misc.getTextColor());

            intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "reputation"));
            intel.setSound(BaseIntelPlugin.getSoundMinorMessage());
            intel.setIcon(alignedFaction.getCrest());
            Global.getSector().getCampaignUI().addMessage(intel);
        }
    }

//Special Item handling

    protected void applyIndEvo_AmbassadorItemEffects() {
        if (ambassadorItemData != null) {
            AmbassadorInstallableItemPlugin.IndEvo_AmbassadorEffect effect = AmbassadorInstallableItemPlugin.AMBASSADOR_EFFECTS.get(ambassadorItemData.getId());
            if (effect != null) {
                effect.apply(this);
            }
        }
    }

    public void setAmbassadorItemData(SpecialItemData data) {
        if (data == null && ambassadorItemData != null) {
            AmbassadorInstallableItemPlugin.IndEvo_AmbassadorEffect effect = AmbassadorInstallableItemPlugin.AMBASSADOR_EFFECTS.get(ambassadorItemData.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }

        ambassadorItemData = data;
        alignedFaction = AmbassadorItemHelper.getFactionForItem(data);
        monthsPassed = 0;
    }

    //This adds/removes the menu point to install items in the industry menu when 7d have passed
    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        if (getAmbassadorItemData() == null && !isBuilding()) {
            ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
            list.add(new AmbassadorInstallableItemPlugin(this));

            return list;
        }

        return new ArrayList<>();
    }

    public AmbassadorItemData getAmbassadorItemData() {
        return (AmbassadorItemData) ambassadorItemData;
    }

    public SpecialItemData getSpecialItem() {
        return ambassadorItemData;
    }

    public void setSpecialItem(SpecialItemData special) {
        ambassadorItemData = special;
        alignedFaction = AmbassadorItemHelper.getFactionForItem(special);
        monthsPassed = 0;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        return market.getFactionId().equals("player") &&
                ambassadorItemData == null &&
                data != null &&
                AmbassadorInstallableItemPlugin.AMBASSADOR_EFFECTS.containsKey(data.getId());
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        if (ambassadorItemData != null && !forUpgrade) {
            IndEvo_ambassadorRemoval.removeAmbassadorWithPenalty(market);
        }
    }

    @Override
    public java.util.List<SpecialItemData> getVisibleInstalledItems() {
        List<SpecialItemData> result = super.getVisibleInstalledItems();

        if (ambassadorItemData != null) {
            result.add(ambassadorItemData);
        }

        return result;
    }

    @Override
    public String getCurrentImage() {
        if (getSpecialItem() != null) {
            switch (AmbassadorItemHelper.getFactionForItem(getSpecialItem()).getId()) {
                case Factions.PERSEAN:
                    return Global.getSettings().getSpriteName("IndEvo", "amb_pers");
                case Factions.TRITACHYON:
                    return Global.getSettings().getSpriteName("IndEvo", "amb_tt");
                case Factions.HEGEMONY:
                    return Global.getSettings().getSpriteName("IndEvo", "amb_heg");
                case Factions.DIKTAT:
                    return Global.getSettings().getSpriteName("IndEvo", "amb_sind");
                case Factions.LUDDIC_CHURCH:
                    return Global.getSettings().getSpriteName("IndEvo", "amb_church");
                default:
                    return Global.getSettings().getSpriteName("IndEvo", "amb_mod");
            }
        }

        return super.getCurrentImage();
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "aCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(ALPHA_CORE_REP_PENALTY_MULT, true);

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
        String pre = StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "bCoreEffect");
        String[] highlightString = new String[]{StringHelper.getFloatToIntStrx100(BETA_CORE_MAX_RELATION), StringHelper.getFloatToIntStrx100(-BETA_CORE_REP_PENALTY)};

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
        String pre = StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "gCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(GAMMA_CORE_UPKEEP_RED_MULT, true);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void applyBetaCoreModifiers() {
        maxRelation = BETA_CORE_MAX_RELATION;
    }

    protected void applyNoAICoreModifiers() {
        maxRelation = BASE_MAX_RELATION;
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        String name;
        switch (IndustryHelper.getAiCoreIdNotNull(this)) {
            case Commodities.GAMMA_CORE:
                name = StringHelper.getString("IndEvo_AICores", "gCoreStatModAssigned");
                getUpkeep().modifyMult("ind_core", GAMMA_CORE_UPKEEP_RED_MULT, name);
                break;
            default:
                getUpkeep().unmodifyMult("ind_core");
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

}
