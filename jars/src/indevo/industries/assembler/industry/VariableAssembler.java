package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.items.VPCItemPlugin;
import indevo.items.installable.NamedItemSlotPlugin;
import indevo.items.memory.VPCCommodityRepo.Commodity;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StringHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

public class VariableAssembler extends BaseIndustry implements EconomyTickListener, VPCUserIndustryAPI {

    public static final Logger log = Global.getLogger(VariableAssembler.class);

    public static final float BETA_CORE_OUTPUT_MULT = 1.25f;
    public static final float ALPHA_CORE_OUTPUT_MULT = 1.15f;
    public static final float GAMMA_CORE_UPKEEP_RED_MULT = 0.90f;
    public static final float ALPHA_CORE_UPKEEP_RED_MULT = 0.80f;
    public static final float RAMP_UP_REDUCTION_MULT = 0.70f;
    public static final float LOG_INCREASE = 55f;

    private static final int VPC_MARKET_SIZE_OVERRIDE = -1;

    public SpecialItemData lastInstalledVPC = null;
    public boolean specialItemWasSet = false;
    private boolean rampUpExpired = false;
    public float aiCoreMult = 1.0f; //current AI core modifier for deposit

//Industry handling

    public void apply() {
        Global.getSector().getListenerManager().addListener(this, true);

        if (!market.isPlayerOwned()) {
            AImode();
        }

        applyVPCEffects();
        toggleRampUp();

        demand(Commodities.HEAVY_MACHINERY, market.getSize() - 3);

        applyOutputReduction();
        applyDeficits();

        super.apply(true);

        if (!isFunctional()) {
            supply.clear();
            demand.clear();
        }
    }

    //trash code
    public void superApply(){
        super.apply(true);
    }

    @Override
    public void unapply() {
        super.unapply();

        Global.getSector().getListenerManager().removeListener(this);

        if (!getId().equals(Ids.COMFORGE)) unmodifySupDem();

        supply.clear();
        demand.clear();
    }

    public int getVPCMarketSizeOverride() {
        return VPC_MARKET_SIZE_OVERRIDE;
    }

    public void applyDeficits() {
        String[] allSupply = supply.keySet().toArray(new String[0]);
        applyDeficitToProduction(1, getMaxDeficitAllDemand(), allSupply);
    }

    public Pair<String, Integer> getMaxDeficitAllDemand() {
        String[] allDemand = demand.keySet().toArray(new String[0]);
        return getMaxDeficit(allDemand);
    }

    protected void setSupply(Map<String, Integer> commodityIDWithMarketSizeModifier) {
        int size = market.getSize() + getVPCMarketSizeOverride();
        if (size <= 3)
            size++; //can't have output at 0, which would happen if the colony had size 3, -2 on demand, and -1 getSize modifier.

        for (Map.Entry<String, Integer> e : commodityIDWithMarketSizeModifier.entrySet()) {
            supply(e.getKey(), size + e.getValue());
        }
    }

    protected void setDemand(Map<String, Integer> commodityIDWithMarketSizeModifier) {
        int size = market.getSize() + getVPCMarketSizeOverride();
        if (size <= 3) size++;

        for (Map.Entry<String, Integer> e : commodityIDWithMarketSizeModifier.entrySet()) {
            demand(e.getKey(), size + e.getValue());
        }
    }

    public void vpcUnapply() {
        demand.clear();
        supply.clear();
        demand(Commodities.HEAVY_MACHINERY, market.getSize() - 3);
    }

    public Map<String, Integer> getDepositList() {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (!hasVPC()) return map;

        float outputMult = VPCItemPlugin.getDepositMult(special);
        for (Commodity c : VPCItemPlugin.getOutputs(special)) map.put(c.id(), getDepositAmount(c, outputMult));

        return map;
    }

    protected int getDepositAmount(Commodity commodity, float outputMult) {
        int deficit = getMaxDeficitAllDemand().two;
        float deficitMult = 1 - (deficit / (getSupply(commodity.id()).getQuantity().getModifiedValue() + deficit));

        return getDepositAmount(commodity.getDeposit(getId()), getLogIncrease(), market.getSize() - 2, aiCoreMult * getTimeMult() * outputMult * deficitMult);
    }

    public static int getDepositAmount(int base, float logIncrease, int sizeMult, float mult) {
        int total = Math.round((Math.round(((logIncrease * Math.log(sizeMult)) + base) / 10) * 10) * mult);
        return Math.max(0, total);
    }

    protected float getLogIncrease() {
        return LOG_INCREASE;
    }

    protected float getTimeMult() {
        return rampUpExpired ? 1f : RAMP_UP_REDUCTION_MULT;
    }

    public void AImode() {
        if (getSpecialItem() != null || specialItemWasSet) {
            specialItemWasSet = true;
            return;
        }

        setSpecialItem(VPCItemPlugin.rollForNPC(Misc.random));
        specialItemWasSet = true;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Setting.ADASSEM.enabled();
    }

    public boolean showWhenUnavailable() {
        return Setting.ADASSEM.enabled();
    }

    private void applyOutputReduction() {
        unmodifySupDem();

        for (MutableCommodityQuantity sup : getAllSupply()) {
            if (sup.getQuantity().getModifiedInt() > 3) {
                sup.getQuantity().modifyFlat(getModId(), 3 - sup.getQuantity().getModifiedInt(), StringHelper.getString(getId(), "outputRestriction"));
            }
        }

        for (MutableCommodityQuantity dem : getAllDemand()) {
            if (dem.getQuantity().getModifiedInt() > 4) {
                dem.getQuantity().modifyFlat(getModId(), 4 - dem.getQuantity().getModifiedInt(), StringHelper.getString(getId(), "outputRestriction"));
            }
        }
    }

    public void unmodifySupDem() {
        for (MutableCommodityQuantity sup : getAllSupply()) {
            sup.getQuantity().unmodify(getModId());
        }

        for (MutableCommodityQuantity dem : getAllDemand()) {
            dem.getQuantity().unmodify(getModId());
        }
    }

    public void toggleRampUp() {
        if (hasVPC() && !special.equals(lastInstalledVPC)) {
            rampUpExpired = false;
            lastInstalledVPC = special;
        }
    }

    public void reportEconomyTick(int iterIndex) {
    }

    public void reportEconomyMonthEnd() {
        if (isFunctional()
                && hasVPC()
                && market.isPlayerOwned()) {

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

            rampUpExpired = true;
        }
    }


    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        Pair<String, Integer> deficit = getMaxDeficitAllDemand();
        if (deficit.two <= 0) return false;

        return mode != IndustryTooltipMode.NORMAL || isFunctional();
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

                if (!rampUpExpired) {
                    tooltip.addPara(StringHelper.getString("IndEvo_VarInd", "rampUp"), 10, bad, StringHelper.getAbsPercentString(RAMP_UP_REDUCTION_MULT, true));
                }

                addOutputLine(tooltip, opad);
            }
        }
    }

    protected void addOutputLine(TooltipMakerAPI tooltip, float pad) {
        List<String> outputs = new ArrayList<>();
        List<String> amounts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : getDepositList().entrySet()) {
            outputs.add("%s " + Global.getSettings().getCommoditySpec(e.getKey()).getName());
            amounts.add(String.valueOf(e.getValue()));
        }

        tooltip.addPara("Currently outputs " + Misc.getAndJoined(outputs) + " per month.", pad, Misc.getHighlightColor(), amounts.toArray(new String[0]));
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode == IndustryTooltipMode.NORMAL && isFunctional()) {
            Color h = Misc.getHighlightColor();
            float pad = 3f;

            boolean deficit = getAllDeficit().size() > 0;

            if (deficit) {
                tooltip.addPara("%s", pad, h, StringHelper.getString("IndEvo_VarInd", "shortageTooltip"));
            }
        }
    }

    public float getPatherInterest() {
        VPCItemPlugin.Tier tier = VPCItemPlugin.getTier(special);
        return super.getPatherInterest() + (tier != null ? tier.patherInterest : 0f);
    }

    public String getCurrentImage() {
        if (hasVPC()) {
            return Global.getSettings().getSpriteName("IndEvo", "assembler");
        }
        return super.getCurrentImage();
    }

    protected void applyVPCEffects() {
        if (!hasVPC()) return;

        setSupply(VPCItemPlugin.getSupply(special));
        setDemand(VPCItemPlugin.getDemand(special));
    }

    @Override
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (special == null) return false;

        TooltipMakerAPI text = tooltip.beginImageWithText(VPCItemPlugin.getIconName(special), 48);
        ItemEffectsRepo.ITEM_EFFECTS.get(special.getId()).addItemDescription(this, text, special, InstallableIndustryItemPlugin.InstallableItemDescriptionMode.INDUSTRY_TOOLTIP);
        tooltip.addImageWithText(10f);

        return true;
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> list = new ArrayList<>();
        list.add(new NamedItemSlotPlugin(this, "VPC", "Installing a Tri-Tachyon Variable Production Chip (VPC) will change the economic properties of this industry.") {
            @Override
            protected String getIconName(SpecialItemData data) {
                return VPCItemPlugin.getIconName(data);
            }
        });

        return list;
    }

    @Override
    public void initWithParams(List<String> params) {
        super.initWithParams(params);

        // market configs only name the item id, the content is rolled here
        if (hasVPC() && special.getData() == null) setSpecialItem(VPCItemPlugin.rollForNPC(Misc.random));
    }

    @Override
    public boolean hasVPC() {
        return VPCItemPlugin.isVPC(special);
    }

    //AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = StringHelper.getString("IndEvo_AdAssem", "aCoreEffect");
        String[] highlightString = new String[]{StringHelper.getAbsPercentString(ALPHA_CORE_UPKEEP_RED_MULT, true), StringHelper.getAbsPercentString(ALPHA_CORE_OUTPUT_MULT, true)};

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
        String effect = StringHelper.getString("IndEvo_AdAssem", "bCoreEffect");
        String highlightString = StringHelper.getAbsPercentString(BETA_CORE_OUTPUT_MULT, true);

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
        String effect = StringHelper.getString("IndEvo_VarInd", "gCoreEffect");
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

    protected void applyAlphaCoreModifiers() {
        aiCoreMult = ALPHA_CORE_OUTPUT_MULT;
    }

    protected void applyBetaCoreModifiers() {
        aiCoreMult = BETA_CORE_OUTPUT_MULT;
    }

    protected void applyNoAICoreModifiers() {
        aiCoreMult = 1f;
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        String name;

        switch (MiscIE.getAiCoreIdNotNull(this)) {
            case Commodities.ALPHA_CORE:
                name = StringHelper.getString("IndEvo_AICores", "aCoreStatModAssigned");
                getUpkeep().modifyMult("ind_core", ALPHA_CORE_UPKEEP_RED_MULT, name);
                break;
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