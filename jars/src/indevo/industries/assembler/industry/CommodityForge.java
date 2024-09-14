package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.SharedSubmarketUserAPI;
import indevo.items.VPCItemPlugin;
import indevo.items.installable.VPCInstallableItemPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.SubMarketAddOrRemovePlugin;
import indevo.utils.timers.NewDayListener;
import org.json.JSONException;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.ids.ItemIds.NO_ENTRY;
import static indevo.utils.helper.MiscIE.getDaysOfCurrentMonth;

//this is one of the earliest things I made and is super cursed
public class CommodityForge extends VariableAssembler implements SharedSubmarketUserAPI, NewDayListener {

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
        applyIndEvo_VPCEffects();
        toggleVPCLock();

        demand(Commodities.HEAVY_MACHINERY, market.getSize());

        if (!isFunctional()) {
            supply.clear();
            demand.clear();
        }

        if (!market.isPlayerOwned()) {
            AImode();
        }
        applyDeficits();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        addSharedSubmarket();
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);

        supply.clear();
        demand.clear();

        if (currentVPC != null) {
            VPCInstallableItemPlugin.IndEvo_ItemEffect effect = VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(currentVPC.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }

        removeSharedSubmarket();
    }

    @Override
    public int getVPCMarketSizeOverride() {
        return VPC_MARKET_SIZE_OVERRIDE;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.COMFORGE);
    }

    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.COMFORGE);
    }

    private void autoFeed() {
        //autofeed
        if (getSpecialItem() == null) {
            if (!market.hasSubmarket(Ids.SHAREDSTORAGE)) return;
            CargoAPI cargo = market.getSubmarket(Ids.SHAREDSTORAGE).getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (stack.getPlugin() instanceof VPCItemPlugin) {
                    setSpecialItem(stack.getSpecialDataIfSpecial());
                    cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), 1);

                    Global.getSector().getCampaignUI().addMessage(StringHelper.getString("IndEvo_ComForge", "indStorageMessage"),
                            Global.getSettings().getColor("standardTextColor"),
                            Global.getSettings().getSpecialItemSpec(stack.getSpecialDataIfSpecial().getId()).getName(),
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
                && currentVPC != null
                && market.isPlayerOwned()) {

            vpcIsLocked = true;
            CargoAPI cargo;

            if (Settings.getBoolean(Settings.VARIND_DELIVER_TO_PRODUCTION_POINT) && Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
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
    public int getDepositAmount(String commodityId, boolean dual) {
        int amt;
        int sizeMult = market.getSize() - 2;
        float logIncrease;
        int deficit = getMaxDeficitAllDemand().two;
        float deficitMult = 1 - (deficit / (getSupply(commodityId).getQuantity().getModifiedValue() + deficit));
        float dualReduction = dual ? DUAL_OUTPUT_REDUCTION_MULT : 1f;
        float timeMult = (float) DEFAULT_MAX_DEPOSITS / maxDeposits;

        //gets the category depending on the industry ID
        try {
            amt = Global.getSettings().getJSONObject(getId()+ "_amounts").getInt(commodityId);
            logIncrease = Global.getSettings().getJSONObject(getId()+ "_amounts").getInt("logIncrease");
        } catch (JSONException e) {
            log.error(e.toString());
            amt = 0;
            logIncrease = 0;
        }

        int total = Math.round((Math.round(((logIncrease * Math.log(sizeMult)) + amt) / 10) * 10) * aiCoreMult * timeMult * dualReduction * deficitMult);
        return Math.max(0, total);
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        if (!vpcIsLocked) {
            ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
            list.add(new VPCInstallableItemPlugin(this));

            return list;
        }

        return new ArrayList<>();
    }

    public void toggleVPCLock() {
        if (currentVPC != null) {
            String id = currentVPC.getId();

            if (!id.equals(lastInstalledSpecialItemId)) {
                daysPassed = 0;
                lastInstalledSpecialItemId = id;
            }
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

            if (currTooltipMode.equals(IndustryTooltipMode.ADD_INDUSTRY) || currentVPC == null) {
                tooltip.addPara("%s", opad, highlight, StringHelper.getString("IndEvo_VarInd", "vpcNotice"));
            }

            if (isFunctional()
                    && currentVPC != null
                    && currTooltipMode.equals(IndustryTooltipMode.NORMAL)) {

                if (!vpcIsLocked) {
                    tooltip.addPara("%s", 10, bad, StringHelper.getStringAndSubstituteToken("IndEvo_ComForge", "canRemoveVPC", "$days", getVpcLockTimeDays() + ""));
                } else {
                    tooltip.addPara("%s", 10, bad, StringHelper.getString("IndEvo_ComForge", "canNotRemoveVPC"));
                }

                Pair<String, String> vpcCommodityIds = ItemIds.getVPCCommodityIds(getSpecialItem().getId());
                boolean dual = !vpcCommodityIds.two.equals(NO_ENTRY);

                Map<String, String> toReplace = new HashMap<>();
                toReplace.put("$commodityName1", ItemIds.getCommodityNameString(vpcCommodityIds.one));

                if (!dual) {
                    String str = StringHelper.getStringAndSubstituteTokens("IndEvo_VarInd", "singleOutput", toReplace);
                    String[] highlightString = new String[]{getDepositAmount(vpcCommodityIds.one, false) + ""};

                    tooltip.addPara(str, opad, highlight, highlightString);
                } else {
                    toReplace.put("$commodityName2", ItemIds.getCommodityNameString(vpcCommodityIds.two));
                    String str = StringHelper.getStringAndSubstituteTokens("IndEvo_VarInd", "doubleOutput", toReplace);
                    String[] highlightString = new String[]{getDepositAmount(vpcCommodityIds.one, true) + "", getDepositAmount(vpcCommodityIds.two, true) + ""};

                    tooltip.addPara(str, opad, highlight, highlightString);
                }

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
        return currentVPC != null ? Global.getSettings().getSpriteName("IndEvo", "comforge") : super.getCurrentImage();
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
                if (getSpecialItem() != null) return;

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
        if (!stack.isSpecialStack()) return false;

        return new ArrayList<>(Arrays.asList(
                ItemIds.VPC_SUPPLIES,
                ItemIds.VPC_MARINES,
                ItemIds.VPC_HEAVY_MACHINERY,
                ItemIds.VPC_DOMESTIC_GOODS,
                ItemIds.VPC_DRUGS,
                ItemIds.VPC_HAND_WEAPONS,
                ItemIds.VPC_LUXURY_GOODS,
                ItemIds.VPC_MARINES_HAND_WEAPONS,
                ItemIds.VPC_SUPPLIES_FUEL,
                ItemIds.VPC_PARTS
        )).contains(stack.getSpecialDataIfSpecial().getId());
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara(StringHelper.getString("IndEvo_ComForge", "indStorageTooltip"), 10f, Misc.getHighlightColor(), StringHelper.getString("IndEvo_items", "VPCs"));
    }

    @Override
    public void addSharedSubmarket() {
        if (currTooltipMode != IndustryTooltipMode.ADD_INDUSTRY && market.isPlayerOwned() && isFunctional() && !market.hasSubmarket(Ids.SHAREDSTORAGE)) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.SHAREDSTORAGE, false));
        }
    }

    @Override
    public void removeSharedSubmarket() {
        Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.SHAREDSTORAGE, true));
    }

}