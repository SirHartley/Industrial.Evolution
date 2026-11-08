package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.SharedSubmarketUserAPI;
import indevo.items.VPCItemPlugin;
import indevo.submarkets.IndustrySubmarkets;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StringHelper;
import indevo.utils.timers.NewDayListener;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.utils.helper.MiscIE.getDaysOfCurrentMonth;

//this is one of the earliest things I made and is super cursed
public class CommodityForge extends VariableAssembler implements SharedSubmarketUserAPI, NewDayListener {

    public static final float LOG_INCREASE = 250f;

    private static final int VPC_LOCK_TIME_DAYS = 7;
    private static final int ALPHA_CORE_MAX_DEPOSITS = 2;
    private static final int DEFAULT_MAX_DEPOSITS = 3;
    private static final float BETA_CORE_UPKEEP_RED_MULT = 0.10f;

    private static final int VPC_MARKET_SIZE_OVERRIDE = 2;

    private int daysPassed = 0;
    private int depositCounter = 0;
    private int maxDeposits = 3;
    private boolean vpcIsLocked = false;

//industry handling

    @Override
    public void apply() {
        super.apply(true);

        Global.getSector().getListenerManager().addListener(this, true);

        if (!market.isPlayerOwned()) {
            AImode();
        }

        applyVPCEffects();
        toggleVPCLock();

        demand(Commodities.HEAVY_MACHINERY, market.getSize());

        if (!isFunctional()) {
            supply.clear();
            demand.clear();
        }

        applyDeficits();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        IndustrySubmarkets.ensure(this, Ids.SHAREDSTORAGE);
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        IndustrySubmarkets.release(this);
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);

        supply.clear();
        demand.clear();
    }

    @Override
    public int getVPCMarketSizeOverride() {
        return VPC_MARKET_SIZE_OVERRIDE;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Setting.COMFORGE.enabled();
    }

    public boolean showWhenUnavailable() {
        return Setting.COMFORGE.enabled();
    }

    private void autoFeed() {
        //autofeed
        if (getSpecialItem() == null) {
            if (!market.hasSubmarket(Ids.SHAREDSTORAGE)) return;
            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (VPCItemPlugin.isValid(stack.getSpecialDataIfSpecial())) {
                    setSpecialItem(stack.getSpecialDataIfSpecial());
                    cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), 1);

                    Global.getSector().getCampaignUI().addMessage(StringHelper.getString("IndEvo_ComForge", "indStorageMessage"),
                            Global.getSettings().getColor("standardTextColor"),
                            stack.getDisplayName(),
                            market.getName(),
                            Misc.getHighlightColor(),
                            Misc.getHighlightColor());
                    break;
                }
            }
        }
    }

    public void reportEconomyTick(int iterIndex) {
    }

    public void reportEconomyMonthEnd() {
        if (isFunctional()
                && hasVPC()
                && market.isPlayerOwned()) {

            vpcIsLocked = true;
            CargoAPI cargo;

            if (Setting.VARIND_DELIVER_TO_PRODUCTION_POINT.enabled() && Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
                cargo = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
            } else if (MiscIE.getStorageCargo(market) != null) {
                cargo = MiscIE.getStorageCargo(market);
            } else {
                return;
            }

            if (cargo != null) {
                for (Map.Entry<String, Integer> e : getDepositList().entrySet()) {
                    cargo.addCommodity(e.getKey(), e.getValue());
                }
            }

            depositCounter++;
            if (depositCounter >= maxDeposits) {
                reset();
                fireBurnedOutMessage();
                autoFeed();
            }
        }
    }

    private void reset() {
        vpcIsLocked = false;
        setSpecialItem(null);
        depositCounter = 0;
        daysPassed = 0;
    }

    @Override
    public void onNewDay() {
        if (!isFunctional()) return;

        if (hasVPC() && !vpcIsLocked) daysPassed++;
        if (daysPassed > VPC_LOCK_TIME_DAYS) vpcIsLocked = true;

        autoFeed();
    }

    @Override
    protected float getLogIncrease() {
        return LOG_INCREASE;
    }

    @Override
    protected float getTimeMult() {
        return (float) DEFAULT_MAX_DEPOSITS / maxDeposits;
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        return vpcIsLocked ? new ArrayList<>() : super.getInstallableItems();
    }

    public void toggleVPCLock() {
        if (hasVPC() && !special.equals(lastInstalledVPC)) {
            daysPassed = 0;
            lastInstalledVPC = special;
        }
    }

    private void fireBurnedOutMessage() {
        MessageIntel intel = new MessageIntel(StringHelper.getString("IndEvo_ComForge", "burnOutMessage") + market.getName(), Misc.getNegativeHighlightColor());
        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "VPCRemovalIcon"));
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!market.isPlayerOwned()) return;

        if (!isBuilding()) {
            float opad = 5.0F;
            Color highlight = Misc.getHighlightColor();
            Color bad = Misc.getNegativeHighlightColor();

            if (currTooltipMode.equals(IndustryTooltipMode.ADD_INDUSTRY) || !hasVPC()) {
                tooltip.addPara("%s", opad, highlight, StringHelper.getString("IndEvo_VarInd", "vpcNotice"));
            }

            if (isFunctional()
                    && hasVPC()
                    && currTooltipMode.equals(IndustryTooltipMode.NORMAL)) {

                if (!vpcIsLocked) {
                    tooltip.addPara("%s", 10, bad, StringHelper.getStringAndSubstituteToken("IndEvo_ComForge", "canRemoveVPC", "$days", getVpcLockTimeDays() + ""));
                } else {
                    tooltip.addPara("%s", 10, bad, StringHelper.getString("IndEvo_ComForge", "canNotRemoveVPC"));
                }

                addOutputLine(tooltip, opad);

                String timeString = "";
                int remaining = maxDeposits - depositCounter;
                if (remaining > 1) {
                    timeString = remaining + StringHelper.getString("monthsWithFrontSpace");
                } else {
                    timeString = (getDaysOfCurrentMonth() - Global.getSector().getClock().getDay() + 1) + StringHelper.getString("daysWithFrontSpace");
                }

                tooltip.addPara(StringHelper.getString("IndEvo_ComForge", "vpcLockTime"), opad, highlight, timeString);

            }
        }
    }

    private int getVpcLockTimeDays() {
        CampaignClockAPI clock = Global.getSector().getClock();

        int remainingDays = (getDaysOfCurrentMonth()) - (clock.getDay() - 1);
        int daysRequiredUntilLock = VPC_LOCK_TIME_DAYS - daysPassed;

        return Math.min(remainingDays, daysRequiredUntilLock) + 1;
    }

    public String getCurrentImage() {
        return hasVPC() ? Global.getSettings().getSpriteName("IndEvo", "comforge") : super.getCurrentImage();
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = StringHelper.getString("IndEvo_ComForge", "aCoreEffect");
        String highlightString = (DEFAULT_MAX_DEPOSITS - ALPHA_CORE_MAX_DEPOSITS) + StringHelper.getString("monthWithFrontSpace");

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
        String effect = StringHelper.getString("IndEvo_ComForge", "bCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(BETA_CORE_UPKEEP_RED_MULT, true);

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlightString);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlightString);
        }
    }

    protected void applyAlphaCoreModifiers() {
        maxDeposits = ALPHA_CORE_MAX_DEPOSITS;
    }

    protected void applyBetaCoreModifiers() {
    }

    protected void applyNoAICoreModifiers() {
        maxDeposits = DEFAULT_MAX_DEPOSITS;
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        String name;

        switch (MiscIE.getAiCoreIdNotNull(this)) {
            case Commodities.BETA_CORE:
                if (getSpecialItem() != null) {
                    getUpkeep().unmodifyMult("ind_core");
                    return;
                }

                name = StringHelper.getString("IndEvo_AICores", "bCoreStatModAssigned");
                getUpkeep().modifyMult("ind_core", BETA_CORE_UPKEEP_RED_MULT, name);
                break;
            case Commodities.GAMMA_CORE:
                name = StringHelper.getString("IndEvo_AICores", "gCoreStatModAssigned");
                getUpkeep().modifyMult("ind_core", GAMMA_CORE_UPKEEP_RED_MULT, name);
                break;
            default:
                getUpkeep().unmodifyMult("ind_core");
        }
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        return stack.isSpecialStack() && VPCItemPlugin.isVPC(stack.getSpecialDataIfSpecial());
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara(StringHelper.getString("IndEvo_ComForge", "indStorageTooltip"), 10f, Misc.getHighlightColor(), "VPCs");
    }

}