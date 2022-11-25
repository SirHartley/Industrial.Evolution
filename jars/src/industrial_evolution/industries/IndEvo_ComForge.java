package industrial_evolution.industries;

import com.fs.starfarer.api.Global;
import industrial_evolution.IndEvo_IndustryHelper;
import industrial_evolution.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import industrial_evolution.items.IndEvo_VPCItemPlugin;
import industrial_evolution.items.installable.IndEvo_VPCInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import industrial_evolution.campaign.ids.IndEvo_Items;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import industrial_evolution.plugins.addOrRemovePlugins.IndEvo_subMarketAddOrRemovePlugin;
import industrial_evolution.plugins.timers.IndEvo_newDayListener;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.json.JSONException;

import java.awt.*;
import java.util.List;
import java.util.*;

import static industrial_evolution.IndEvo_IndustryHelper.getDaysOfCurrentMonth;
import static industrial_evolution.campaign.ids.IndEvo_Items.NO_ENTRY;

public class IndEvo_ComForge extends IndEvo_AdAssem implements IndEvo_SharedSubmarketUserAPI, IndEvo_newDayListener {

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

        addSharedSubmarket();
        applyDeficits();
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);

        supply.clear();
        demand.clear();

        if (currentVPC != null) {
            IndEvo_VPCInstallableItemPlugin.IndEvo_ItemEffect effect = IndEvo_VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(currentVPC.getId());
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
        return Global.getSettings().getBoolean("ComForge");
    }

    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("ComForge");
    }

    private void autoFeed() {
        //autofeed
        if (getSpecialItem() == null) {
            if (!market.hasSubmarket(IndEvo_ids.SHAREDSTORAGE)) return;
            CargoAPI cargo = market.getSubmarket(IndEvo_ids.SHAREDSTORAGE).getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                if (stack.getPlugin() instanceof IndEvo_VPCItemPlugin) {
                    setSpecialItem(stack.getSpecialDataIfSpecial());
                    cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), 1);

                    Global.getSector().getCampaignUI().addMessage(IndEvo_StringHelper.getString("IndEvo_ComForge", "indStorageMessage"),
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

            if (Global.getSettings().getBoolean("VarInd_deliverToProductionPoint") && Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
                cargo = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();
            } else if (Misc.getStorageCargo(market) != null) {
                cargo = Misc.getStorageCargo(market);
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
            amt = Global.getSettings().getJSONObject(getId()).getInt(commodityId);
            logIncrease = Global.getSettings().getJSONObject(getId()).getInt("logIncrease");
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
            list.add(new IndEvo_VPCInstallableItemPlugin(this));

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
        MessageIntel intel = new MessageIntel(IndEvo_StringHelper.getString("IndEvo_ComForge", "burnOutMessage") + market.getName(), Misc.getNegativeHighlightColor());
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
                tooltip.addPara("%s", opad, highlight, IndEvo_StringHelper.getString("IndEvo_VarInd", "vpcNotice"));
            }

            if (isFunctional()
                    && currentVPC != null
                    && currTooltipMode.equals(IndustryTooltipMode.NORMAL)) {

                if (!vpcIsLocked) {
                    tooltip.addPara("%s", 10, bad, IndEvo_StringHelper.getStringAndSubstituteToken("IndEvo_ComForge", "canRemoveVPC", "$days", getVpcLockTimeDays() + ""));
                } else {
                    tooltip.addPara("%s", 10, bad, IndEvo_StringHelper.getString("IndEvo_ComForge", "canNotRemoveVPC"));
                }

                Pair<String, String> vpcCommodityIds = IndEvo_Items.getVPCCommodityIds(getSpecialItem().getId());
                boolean dual = !vpcCommodityIds.two.equals(NO_ENTRY);

                Map<String, String> toReplace = new HashMap<>();
                toReplace.put("$commodityName1", IndEvo_Items.getCommodityNameString(vpcCommodityIds.one));

                if (!dual) {
                    String str = IndEvo_StringHelper.getStringAndSubstituteTokens("IndEvo_VarInd", "singleOutput", toReplace);
                    String[] highlightString = new String[]{getDepositAmount(vpcCommodityIds.one, false) + ""};

                    tooltip.addPara(str, opad, highlight, highlightString);
                } else {
                    toReplace.put("$commodityName2", IndEvo_Items.getCommodityNameString(vpcCommodityIds.two));
                    String str = IndEvo_StringHelper.getStringAndSubstituteTokens("IndEvo_VarInd", "doubleOutput", toReplace);
                    String[] highlightString = new String[]{getDepositAmount(vpcCommodityIds.one, true) + "", getDepositAmount(vpcCommodityIds.two, true) + ""};

                    tooltip.addPara(str, opad, highlight, highlightString);
                }

                String timeString = "";
                int remaining = maxDeposits - depositCounter;
                if (remaining > 1) {
                    timeString = remaining + IndEvo_StringHelper.getString("monthsWithFrontSpace");
                } else {
                    timeString = (getDaysOfCurrentMonth() - Global.getSector().getClock().getDay() + 1) + IndEvo_StringHelper.getString("daysWithFrontSpace");
                }

                tooltip.addPara(IndEvo_StringHelper.getString("IndEvo_ComForge", "vpcLockTime"), opad, highlight, timeString);

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
        String pre = IndEvo_StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = IndEvo_StringHelper.getString("IndEvo_ComForge", "aCoreEffect");
        String highlightString = (DEFAULT_MAX_DEPOSITS - ALPHA_CORE_MAX_DEPOSITS) + IndEvo_StringHelper.getString("monthWithFrontSpace");

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
        String effect = IndEvo_StringHelper.getString("IndEvo_ComForge", "bCoreEffect");
        String highlightString = IndEvo_StringHelper.getAbsPercentString(BETA_CORE_UPKEEP_RED_MULT, true);

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

        switch (IndEvo_IndustryHelper.getAiCoreIdNotNull(this)) {
            case Commodities.BETA_CORE:
                if (getSpecialItem() != null) return;

                name = IndEvo_StringHelper.getString("IndEvo_AICores", "bCoreStatModAssigned");
                getUpkeep().modifyMult("ind_core", BETA_CORE_UPKEEP_RED_MULT, name);
                break;
            case Commodities.GAMMA_CORE:
                name = IndEvo_StringHelper.getString("IndEvo_AICores", "gCoreStatModAssigned");
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
                IndEvo_Items.VPC_SUPPLIES,
                IndEvo_Items.VPC_MARINES,
                IndEvo_Items.VPC_HEAVY_MACHINERY,
                IndEvo_Items.VPC_DOMESTIC_GOODS,
                IndEvo_Items.VPC_DRUGS,
                IndEvo_Items.VPC_HAND_WEAPONS,
                IndEvo_Items.VPC_LUXURY_GOODS,
                IndEvo_Items.VPC_MARINES_HAND_WEAPONS,
                IndEvo_Items.VPC_SUPPLIES_FUEL,
                IndEvo_Items.VPC_PARTS
        )).contains(stack.getSpecialDataIfSpecial().getId());
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara(IndEvo_StringHelper.getString("IndEvo_ComForge", "indStorageTooltip"), 10f, Misc.getHighlightColor(), IndEvo_StringHelper.getString("IndEvo_items", "VPCs"));
    }

    @Override
    public void addSharedSubmarket() {
        if (currTooltipMode != IndustryTooltipMode.ADD_INDUSTRY && market.isPlayerOwned() && isFunctional()) {
            Global.getSector().addScript(new IndEvo_subMarketAddOrRemovePlugin(market, IndEvo_ids.SHAREDSTORAGE, false));
        }
    }

    @Override
    public void removeSharedSubmarket() {
        Global.getSector().addScript(new IndEvo_subMarketAddOrRemovePlugin(market, IndEvo_ids.SHAREDSTORAGE, true));
    }

}