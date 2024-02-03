package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.timers.NewDayListener;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;
import static indevo.items.installable.SpecialItemEffectsRepo.SIMULATOR_BASE_INCREASE;

public class Supercomputer extends SharedSubmarketUser implements EconomyTickListener, NewDayListener {

    private final Random random = new Random();
    private final float gammaCoreUpkeepRed = 0.90f;
    private final float betaCoreUpkeepRed = 0.80f;
    private String lastApplied = null;
    private boolean coreSet = true;

    private int daysPassed = 0;

    private static void debugMessage(String Text) {
        boolean DEBUG = false; //set to false once done
        if (DEBUG) {
            Global.getLogger(Supercomputer.class).info(Text);
        }
    }

    private float runtimeMult = 1f;

    public void setRuntimeMult(float mult){
        runtimeMult = mult;
    }

    public void resetRuntimeMult(){
        runtimeMult = 1f;
    }

    public void apply() {
        super.apply(true);

        //needed to init on AI usage
        if (!market.isPlayerOwned() && coreSet) {
            setAICoreId(Commodities.GAMMA_CORE);
            coreSet = false;
        }

        modifyAllMarketIncome();
        Global.getSector().getListenerManager().addListener(this, true);
        addSharedSubmarket();
    }

    public int getCoreUseTimes(String AICoreId) {
        HashMap<String, Integer> list = new HashMap<>();

        list.put(Commodities.ALPHA_CORE, Settings.getInt(Settings.ALPHA_CORE_RUNTIME));
        list.put(Commodities.BETA_CORE, Settings.getInt(Settings.BETA_CORE_RUNTIME));
        list.put(Commodities.GAMMA_CORE, Settings.getInt(Settings.GAMMA_CORE_RUNTIME));

        return Math.round(list.get(AICoreId) * runtimeMult);
    }

    private void getNextCore() {
        if (getAICoreId() == null && market.hasSubmarket(Ids.SHAREDSTORAGE) && market.isPlayerOwned()) {
            SubmarketAPI storage = market.getSubmarket(Ids.SHAREDSTORAGE);

            //replenish it from AIstorage with one of the same hullSize. if not available, use another hullSize, starting with gamma
            if (!storage.getCargo().isEmpty()) {
                if (coreReplace(lastApplied, storage)) return;
                if (coreReplace(Commodities.GAMMA_CORE, storage)) return;
                if (coreReplace(Commodities.BETA_CORE, storage)) return;
                coreReplace(Commodities.ALPHA_CORE, storage);
            }
        }

        //if there is an ai core applied, and there is no ongoing effect, eat it
        if (getAICoreId() != null && lastApplied == null) {
            lastApplied = getAICoreId();
            setAICoreId(null);
        }
    }

    private boolean coreReplace(String coreid, SubmarketAPI storage) {
        if (coreid != null && storage.getCargo().getCommodityQuantity(coreid) != 0) {
            setAICoreId(coreid);
            storage.getCargo().removeCommodity(coreid, 1);

            Global.getSector().getCampaignUI().addMessage("A Supercomputer has taken a %s from the AI Core storage at %s.",
                    Global.getSettings().getColor("standardTextColor"), Global.getSettings().getCommoditySpec(coreid).getName(), market.getName(), Misc.getHighlightColor(), Misc.getHighlightColor());
            return true;
        }
        return false;
    }

    public String getLastApplied() {
        return lastApplied;
    }

    @Override
    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);

        List<MarketAPI> PlayerMarketsInSystem = IndustryHelper.getMarketsInLocation(this.market.getStarSystem(), this.market.getFactionId());
        for (MarketAPI PlayerMarket : PlayerMarketsInSystem) {
            PlayerMarket.getIncomeMult().unmodify(Ids.SUPCOM);
        }

        removeSharedSubmarket();

    }

    private boolean hascond(String id) {
        return market.hasCondition(id) && !market.isConditionSuppressed(id);
    }

    public float getCurrentIncrease() {
        if (!isFunctional() || lastApplied == null) return 0f;

        float bonusSum = 0F;
        float aicoreincrease = Commodities.ALPHA_CORE.equals(lastApplied) ? 1.2f : 1f;

        if (hascond(Conditions.VERY_COLD)) {
            bonusSum += 0.20F * aicoreincrease;
        } else if (hascond(Conditions.COLD)) {
            bonusSum += 0.15F * aicoreincrease;
        } else {
            bonusSum += 0.05F * aicoreincrease;
        }

        if (getSpecialItem() != null) bonusSum += SIMULATOR_BASE_INCREASE;

        return bonusSum;
    }

    private void modifyAllMarketIncome() {
        List<MarketAPI> playerMarketsInSystem = IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId());
        for (MarketAPI playerMarket : playerMarketsInSystem) playerMarket.getIncomeMult().unmodify(getModId());

        if (!isFunctional()) return;

        float bonusSum = 0F;
        int amount = 0;
        float totalPercentMod;

        for (MarketAPI playerMarket : playerMarketsInSystem) {
            if (playerMarket.hasIndustry(getId())) {
                Supercomputer supercomputer = (Supercomputer) playerMarket.getIndustry(Ids.SUPCOM);
                float currentIncrease = supercomputer.getCurrentIncrease();

                if (currentIncrease > 0f) {
                    bonusSum += currentIncrease;
                    amount++;
                }
            }
        }

        float closestSimEngBonus = getApplicableSimulatorBonus();
        if (closestSimEngBonus > 0f) {
            bonusSum += closestSimEngBonus;
            amount++;
        }

        if (amount > 1) {
            totalPercentMod = 1F + (bonusSum * ((float) (Math.pow(0.88F, amount)) * 1F));
            for (MarketAPI playerMarket : playerMarketsInSystem)
                playerMarket.getIncomeMult().modifyMult(getModId(), totalPercentMod, "Supercomputer Network");

        } else if (amount == 1) {
            totalPercentMod = 1F + bonusSum;
            for (MarketAPI playerMarket : playerMarketsInSystem)
                playerMarket.getIncomeMult().modifyMult(getModId(), totalPercentMod, market.getName() + " Supercomputer");
        }
    }

    private void coreDump() {
        if (!market.hasIndustry(Ids.LAB)) return;

        if (isFunctional() && !isBuilding() && depositAICore()) {
            String coreType = aiCoreType();
            market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addCommodity(coreType, 1);

            MessageIntel intel = new MessageIntel("A server bank at the %s on %s has become %s.", Misc.getPositiveHighlightColor(), new String[]{"Supercomputer", market.getName(), "self aware"});
            intel.addLine(BaseIntelPlugin.BULLET + "It has been dumped into a core and deposited in storage.");
            intel.addLine(BaseIntelPlugin.BULLET + "Probably caused by using equipment from the %s.", Misc.getHighlightColor(), new String[]{"Ancient Laboratory"});
            intel.setIcon(Global.getSettings().getSpriteName("IndEvo", coreType));
            intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
            Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, market);
        }
    }

    //check the economy iteration and throw message on month end if the ai core is insufficient
    public void reportEconomyTick(int iterIndex) {
        if (!market.isPlayerOwned()) return;

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;

        if (lastApplied == null && isFunctional()) {
            Global.getSector().getCampaignUI().addMessage("A Supercomputer at %s is %s. It is currently inactive.",
                    Global.getSettings().getColor("standardTextColor"), this.market.getName(), "missing an AI core", Misc.getHighlightColor(), Misc.getNegativeHighlightColor());
        }

        coreDump();
        betaCoreRandomIncome();
    }

    private boolean getRandomBoolean(float p) {
        debugMessage("401");
        return random.nextFloat() < p;
    }

    private boolean depositAICore() {
        float betacoreMult = 1F;

        if (lastApplied == null) {
            return false;
        }

        if (Commodities.BETA_CORE.equals(lastApplied)) {
            betacoreMult = 1.50F;
        }

        if (hascond(Conditions.COLD)) {
            debugMessage("302");
            return getRandomBoolean(0.05F * betacoreMult);

        } else if (hascond(Conditions.VERY_COLD)) {
            debugMessage("303");
            return getRandomBoolean(0.10F * betacoreMult);
        }
        return false;
    }

    private String aiCoreType() {
        WeightedRandomPicker<String> corePicker = new WeightedRandomPicker<>();
        if (Commodities.ALPHA_CORE.equals(this.lastApplied)) {
            corePicker.add(Commodities.GAMMA_CORE, 40);
            corePicker.add(Commodities.BETA_CORE, 35);
            corePicker.add(Commodities.ALPHA_CORE, 25);

        } else {
            corePicker.add(Commodities.GAMMA_CORE, 65);
            corePicker.add(Commodities.BETA_CORE, 25);
            corePicker.add(Commodities.ALPHA_CORE, 10);

        }

        return corePicker.pick();
    }

    @Override
    public List<SpecialItemData> getVisibleInstalledItems() {
        List<SpecialItemData> l = super.getVisibleInstalledItems();

        if (lastApplied != null) l.add(ItemIds.convertAICoreToSpecial(lastApplied));

        return l;
    }

    private void betaCoreRandomIncome() {
        if (Commodities.BETA_CORE.equals(this.lastApplied)) {
            if (hascond(Conditions.COLD)) {

                int amount = (int) ((Math.round(random.nextFloat() * 1.20 * 10F) * (this.market.getSize() - 2)) * 1000);

                Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
                Global.getSector().getCampaignUI().addMessage("The supercomputer at " + this.market.getName() + " has generated " + Misc.getDGSCredits(amount) + " credits.",
                        Global.getSettings().getColor("standardTextColor"), this.market.getName(), Misc.getDGSCredits(amount), Misc.getPositiveHighlightColor(), Misc.getHighlightColor());

            } else if (hascond(Conditions.VERY_COLD)) {
                int amount = (int) ((Math.round(random.nextFloat() * 1.40 * 10F) * (this.market.getSize() - 2)) * 1000);

                Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
                Global.getSector().getCampaignUI().addMessage("The supercomputer at " + this.market.getName() + " has generated " + Misc.getDGSCredits(amount) + " credits.",
                        Global.getSettings().getColor("standardTextColor"), this.market.getName(), Misc.getDGSCredits(amount), Misc.getPositiveHighlightColor(), Misc.getHighlightColor());
            } else {
                int amount = (int) ((Math.round(random.nextFloat() * 10F) * (this.market.getSize() - 2)) * 750);

                Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
                Global.getSector().getCampaignUI().addMessage("The supercomputer at " + this.market.getName() + " has generated " + Misc.getDGSCredits(amount) + " credits.",
                        Global.getSettings().getColor("standardTextColor"), this.market.getName(), Misc.getDGSCredits(amount), Misc.getPositiveHighlightColor(), Misc.getHighlightColor());

            }
        }
    }

    @Override
    public void onNewDay() {
        getNextCore();

        if (lastApplied != null && isFunctional()) {
            daysPassed++;

            if (getAICoreId() == null && getCoreUseTimes(lastApplied) - daysPassed == 30 && market.isPlayerOwned()) {
                MessageIntel intel = new MessageIntel("The supercomputer at " + market.getName() + " is running out of AI cores.", Misc.getNegativeHighlightColor());
                intel.addLine("You have 30 days left before it ceases to function.", Misc.getTextColor());
                intel.addLine("Deposit some cores in the AI core storage for automatic replacement.", Misc.getTextColor());
                intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "productionwarning"));
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, this.market);
            }

            if (daysPassed >= getCoreUseTimes(lastApplied)) {
                daysPassed = 0;
                lastApplied = null;

                if (!market.isPlayerOwned()) {
                    setAICoreId(Commodities.GAMMA_CORE);
                    return;
                }

                getNextCore();
            }

            if (lastApplied == null && getAICoreId() == null && market.isPlayerOwned()) {
                MessageIntel intel = new MessageIntel("The supercomputer at " + this.market.getName() + " has ceased to function.", Misc.getNegativeHighlightColor());
                intel.addLine("There are no AI cores left in the AI core storage.", Misc.getTextColor());
                intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "productionwarning"));
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO, this.market);
            }
        }
    }

    public void reportEconomyMonthEnd() {
    }

    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.SUPCOM);
    }

    @Override
    public boolean isAvailableToBuild() {
        if (!Settings.getBoolean(Settings.SUPCOM)) return false;

        if (hascond(Conditions.HOT) || hascond(Conditions.VERY_HOT)) {
            return false;
        } else if (market.getPrimaryEntity() != null && this.market.getPrimaryEntity().hasTag("station") || (market.getPlanetEntity() != null && market.getPlanetEntity().isGasGiant())) {
            return false;
        }

        return super.isAvailableToBuild();
    }

    @Override
    public String getUnavailableReason() {
        if (hascond(Conditions.HOT) || hascond(Conditions.VERY_HOT)) {
            return "This planet is too hot for a Supercomputer.";
        } else if (this.market.getPrimaryEntity().hasTag("station")) {
            return "Stations can not handle the heat generated by a Supercomputer.";
        } else if (this.market.getPlanetEntity().isGasGiant()) {
            return "Supercomputers cannot be built on a gas giant.";
        } else {
            return super.getUnavailableReason();
        }

    }

    @Override
    public String getCurrentImage() {

        if (hascond(Conditions.COLD)) {
            return Global.getSettings().getSpriteName("IndEvo", "supcom_cold");
        } else if (hascond(Conditions.VERY_COLD)) {
            return Global.getSettings().getSpriteName("IndEvo", "supcom_vcold");
        }
        return super.getCurrentImage();
    }

    public float getPatherInterest() {
        return 6f + super.getPatherInterest();
    }

    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        if (!this.isBuilding() && this.isFunctional()) {
            float opad = 5.0F;

            if (market.isPlayerOwned() || currTooltipMode == Industry.IndustryTooltipMode.NORMAL) {
                if (currTooltipMode == Industry.IndustryTooltipMode.ADD_INDUSTRY) {
                    if (hascond(Conditions.COLD)) {
                        tooltip.addPara("%s", opad, Misc.getPositiveHighlightColor(), new String[]{"This planet has good conditions for a Supercomputer"});
                    } else if (hascond(Conditions.VERY_COLD)) {
                        tooltip.addPara("%s", opad, Misc.getPositiveHighlightColor(), new String[]{"This planet has ideal conditions for a Supercomputer"});
                    } else if (this.isAvailableToBuild()) {
                        tooltip.addPara("%s", opad, Misc.getNegativeHighlightColor(), new String[]{"This planet does not offer good conditions for a Supercomputer"});
                    }
                }

                if (this.lastApplied == null && currTooltipMode == Industry.IndustryTooltipMode.ADD_INDUSTRY) {
                    tooltip.addPara("%s", 10, Misc.getHighlightColor(), new String[]{"This industry consumes AI cores to function."});
                } else if (this.lastApplied == null) {
                    tooltip.addPara("%s", 10, Misc.getNegativeHighlightColor(), new String[]{"This industry requires an AI core to function."});
                } else {
                    tooltip.addPara("The current bonus for this colony is: %s", 10f, Misc.getHighlightColor(), StringHelper.getAbsPercentString(market.getIncomeMult().getMultStatMod(getModId()).getValue() - 1, false));
                }
            }
        }
    }

//AI core stuff

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Alpha-level AI core assigned for next use. ";
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Will Increase the income multiplication effect provided by this Supercomputer by %s. It will last %s before burning out.", 0.0F, highlight, new String[]{"20%", getCoreUseTimes(Commodities.ALPHA_CORE) / 31 + " months"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Will Increase the income multiplication effect provided by this Supercomputer by %s. It will last %s before burning out.", opad, highlight, new String[]{"20%", getCoreUseTimes(Commodities.ALPHA_CORE) / 31 + " months"});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Beta-level AI core assigned for next use. ";
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Will reduce upkeep cost by %s and generate %s each month, determined by colony size and chance. It will last %s before burning out.", 0.0F, highlight, new String[]{"20%", "extra income", getCoreUseTimes(Commodities.BETA_CORE) / 31 + " months"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Will reduce upkeep cost by %s and generate %s each month, determined by colony size and chance. It will last %s before burning out.", opad, highlight, new String[]{"20%", "extra income", getCoreUseTimes(Commodities.BETA_CORE) / 31 + " months"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Gamma-level AI core assigned for next use. ";
        if (mode == Industry.AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Will reduce upkeep cost by %s. It will last %s before burning out.", 0.0F, highlight, new String[]{"10%", getCoreUseTimes(Commodities.GAMMA_CORE) / 31 + " months"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Will reduce upkeep cost by %s. It will last %s before burning out.", opad, highlight, new String[]{"10%", getCoreUseTimes(Commodities.GAMMA_CORE) / 31 + " months"});
        }
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        if (Commodities.BETA_CORE.equals(this.lastApplied)) {
            String name = "Beta Core assigned";
            this.getUpkeep().modifyMult("ind_core", betaCoreUpkeepRed, name);
        } else if (Commodities.GAMMA_CORE.equals(this.lastApplied)) {
            String name = "Gamma Core assigned";
            this.getUpkeep().modifyMult("ind_core", gammaCoreUpkeepRed, name);
        } else {
            this.getUpkeep().unmodifyMult("ind_core");
        }
    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
        if (this.lastApplied != null) {
            boolean alpha = this.lastApplied.equals(Commodities.ALPHA_CORE);
            boolean beta = this.lastApplied.equals(Commodities.BETA_CORE);
            boolean gamma = this.lastApplied.equals(Commodities.GAMMA_CORE);
            if (alpha) {
                this.applyAlphaCoreSupplyAndDemandModifiers();
            } else if (beta) {
                this.applyBetaCoreSupplyAndDemandModifiers();
            } else if (gamma) {
                this.applyGammaCoreSupplyAndDemandModifiers();
            }
        }
    }

    protected void applyAlphaCoreSupplyAndDemandModifiers() {
    }

    protected void applyBetaCoreSupplyAndDemandModifiers() {
    }

    protected void applyGammaCoreSupplyAndDemandModifiers() {
    }

    @Override
    public void addInstalledItemsSection(Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();

        LabelAPI heading = tooltip.addSectionHeading("Items", color, dark, Alignment.MID, opad);

        boolean addedSomething = false;
        if (aiCoreId != null) {
            tooltip.addSectionHeading("Next AI Core to be used", color, dark, Alignment.MID, opad);

            Industry.AICoreDescriptionMode aiCoreDescMode = Industry.AICoreDescriptionMode.INDUSTRY_TOOLTIP;
            addAICoreSection(tooltip, aiCoreId, aiCoreDescMode);
            addedSomething = true;
        }

        if (lastApplied != null) {
            tooltip.addSectionHeading("Currently active", color, dark, Alignment.MID, opad);
            addCurrentlyUsingAICoreSection(tooltip);
            addedSomething = true;
        }

        addedSomething |= addNonAICoreInstalledItems(mode, tooltip, expanded);

        if (!addedSomething) {
            heading.setText("No items installed");
            //tooltip.addPara("None.", opad);
        }
    }

    protected boolean addCurrentlyUsingAICoreSection(TooltipMakerAPI tooltip) {

        boolean alpha = this.lastApplied.equals(Commodities.ALPHA_CORE);
        boolean beta = this.lastApplied.equals(Commodities.BETA_CORE);
        boolean gamma = this.lastApplied.equals(Commodities.GAMMA_CORE);

        if (alpha) {
            addSecondAlphaCoreDescription(tooltip);
        } else if (beta) {
            addSecondBetaCoreDescription(tooltip);
        } else if (gamma) {
            addSecondGammaCoreDescription(tooltip);
        }

        return true;
    }

    protected void addSecondAlphaCoreDescription(TooltipMakerAPI tooltip) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Alpha-level AI core currently active. ";

        CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.lastApplied);
        TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
        text.addPara(pre + "Increase the income multiplication effect provided by this Supercomputer by %s. This core will last for another %s.", opad, highlight, new String[]{"20%", (this.getCoreUseTimes(this.lastApplied) - this.daysPassed) + " days"});
        tooltip.addImageWithText(opad);
    }

    protected void addSecondBetaCoreDescription(TooltipMakerAPI tooltip) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Beta-level AI core currently active. ";

        CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.lastApplied);
        TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
        text.addPara(pre + "Reduces upkeep cost by %s and has a monthly chance to generate %s. This core will last for another %s.", opad, highlight, new String[]{"20%", "extra income", (this.getCoreUseTimes(this.lastApplied) - this.daysPassed) + " days"});
        tooltip.addImageWithText(opad);
    }

    protected void addSecondGammaCoreDescription(TooltipMakerAPI tooltip) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Gamma-level AI core currently active. ";

        CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.lastApplied);
        TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
        text.addPara(pre + "Reduces upkeep cost by %s. This core will last for another %s.", opad, highlight, new String[]{"10%", (this.getCoreUseTimes(this.lastApplied) - this.daysPassed) + " days"});
        tooltip.addImageWithText(opad);
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        if (!stack.isCommodityStack()) return false;
        return isAICoreId(stack.getCommodityId());
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Supercomputer: pulls %s from this storage to use.", 10f, Misc.getHighlightColor(), "AI cores");
    }

    public Float getApplicableSimulatorBonus() {
        MarketAPI nearest = null;
        float minDist = Float.MAX_VALUE;
        float bonus = 0f;

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.getStarSystem() != null && market.getStarSystem().getId().equals(this.market.getStarSystem().getId())) continue;

            if (market.hasIndustry(Ids.SUPCOM)) {
                Supercomputer supercomputer = (Supercomputer) market.getIndustry(Ids.SUPCOM);
                if (supercomputer.isFunctional() && supercomputer.getLastApplied() != null && supercomputer.getSpecialItem() != null) {
                    float dist = Misc.getDistanceLY(this.market.getLocationInHyperspace(), supercomputer.market.getLocationInHyperspace());
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = market;

                        if (dist > RANGE_LY_TWELVE) bonus = 0f;
                        else {
                            float f = 1f - dist / RANGE_LY_TWELVE;
                            if (f < 0f) f = 0f;
                            if (f > 1f) f = 1f;

                            bonus = supercomputer.getCurrentIncrease() * f;
                        }
                    }
                }
            }
        }

        if (nearest == null) return 0f;

        return bonus;
    }

    public static Pair<MarketAPI, Float> getNearestSupCom(Vector2f locInHyper) {
        MarketAPI nearest = null;
        float minDist = Float.MAX_VALUE;

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.hasIndustry(Ids.SUPCOM)) {
                Supercomputer supercomputer = (Supercomputer) market.getIndustry(Ids.SUPCOM);
                if (supercomputer.isFunctional() && supercomputer.getLastApplied() != null && supercomputer.getSpecialItem() != null) {
                    float dist = Misc.getDistanceLY(locInHyper, supercomputer.market.getLocationInHyperspace());
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

    public static float getDistanceIncomeMult(Vector2f locInHyper) {
        Pair<MarketAPI, Float> p = getNearestSupCom(locInHyper);
        if (p == null) return 0f;
        if (p.two > RANGE_LY_TWELVE) return 0f;

        float f = 1f - p.two / RANGE_LY_TWELVE;
        if (f < 0f) f = 0f;
        if (f > 1f) f = 1f;

        //return the current income multiplicator of the closest supercomputer with item times distance
        Supercomputer supercomputer = (Supercomputer) p.one.getIndustry(Ids.SUPCOM);
        return supercomputer.getCurrentIncrease() * f;
    }

    //register this in mod plugin
    public static class SuperComputerFactor implements ColonyOtherFactorsListener {
        public boolean isActiveFactorFor(SectorEntityToken entity) {
            return getNearestSupCom(entity.getLocationInHyperspace()) != null;
        }

        public void printOtherFactors(TooltipMakerAPI text, SectorEntityToken entity) {
            float distMult = getDistanceIncomeMult(entity.getLocationInHyperspace());

            Pair<MarketAPI, Float> p = getNearestSupCom(entity.getLocationInHyperspace());
            if (p != null) {
                Color h = Misc.getHighlightColor();
                float opad = 10f;

                String dStr = "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two);
                String lights = "light-years";
                if (dStr.equals("1")) lights = "light-year";

                if (p.two > RANGE_LY_TWELVE) {
                    text.addPara("The nearest Simulation Engine is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away. The maximum " +
                                    "range before the signal delay is too large for accurate calculations is %s light-years.",
                            opad, h,
                            "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "" + (int) RANGE_LY_TWELVE);
                } else {
                    text.addPara("The nearest Simulation Engine is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away, allowing " +
                                    "a colony here to accrue an additional %s income.",
                            opad, h,
                            "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "" + (int) Math.round(distMult * 100f) + "%");
                }
            }
        }
    }
}





