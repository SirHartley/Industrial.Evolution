package indevo.industries.assembler.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.installable.VPCInstallableItemPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.ids.ItemIds.NO_ENTRY;

public class VariableAssembler extends BaseIndustry implements EconomyTickListener, VPCUserIndustryAPI {

    public static final Logger log = Global.getLogger(VariableAssembler.class);

    public static final float BETA_CORE_OUTPUT_MULT = 1.25f;
    public static final float ALPHA_CORE_OUTPUT_MULT = 1.15f;
    public static final float GAMMA_CORE_UPKEEP_RED_MULT = 0.90f;
    public static final float ALPHA_CORE_UPKEEP_RED_MULT = 0.80f;
    public static final float RAMP_UP_REDUCTION_MULT = 0.70f;
    public static final float DUAL_OUTPUT_REDUCTION_MULT = 0.80f;

    private static final int VPC_MARKET_SIZE_OVERRIDE = -1;

    protected SpecialItemData currentVPC = null;

    public String lastInstalledSpecialItemId = null;
    public boolean specialItemWasSet = false;
    private boolean rampUpExpired = false;
    public float aiCoreMult = 1.0f; //current AI core modifier for deposit

//Industry handling

    public void apply() {
        Global.getSector().getListenerManager().addListener(this, true);
        applyIndEvo_VPCEffects();
        toggleRampUp();

        demand(Commodities.HEAVY_MACHINERY, market.getSize() - 3);

        if (!market.isPlayerOwned()) {
            AImode();
        }

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

        if (currentVPC != null) {
            VPCInstallableItemPlugin.IndEvo_ItemEffect effect = VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(currentVPC.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }
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

    @Override
    public void setSupply(Map<String, Integer> commodityIDWithMarketSizeModifier) {
        int size = market.getSize() + getVPCMarketSizeOverride();
        if (size <= 3)
            size++; //can't have output at 0, which would happen if the colony had size 3, -2 on demand, and -1 getSize modifier.

        for (Map.Entry<String, Integer> e : commodityIDWithMarketSizeModifier.entrySet()) {
            supply(e.getKey(), size + e.getValue());
        }
    }

    @Override
    public void setDemand(Map<String, Integer> commodityIDWithMarketSizeModifier) {
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
        Pair<String, String> p = ItemIds.getVPCCommodityIds(getSpecialItem().getId());
        boolean dual = !p.two.equals(NO_ENTRY);

        if (p.one != null) map.put(p.one, getDepositAmount(p.one, dual));
        if (dual) map.put(p.two, getDepositAmount(p.two, true));

        return map;
    }

    public int getDepositAmount(String commodityId, boolean dual) {
        int amt;
        int sizeMult = market.getSize() - 2;
        float logIncrease;
        int deficit = getMaxDeficitAllDemand().two;
        float deficitMult = 1 - (deficit / (getSupply(commodityId).getQuantity().getModifiedValue() + deficit));
        float dualReduction = dual ? DUAL_OUTPUT_REDUCTION_MULT : 1f;
        float timeMult = rampUpExpired ? 1f : RAMP_UP_REDUCTION_MULT;

        //gets the category depending on the industry ID
        try {
            amt = Global.getSettings().getJSONObject(getId() + "_amounts").getInt(commodityId);
            logIncrease = Global.getSettings().getJSONObject(getId() + "_amounts").getInt("logIncrease");
        } catch (JSONException e) {
            log.error("[Industrial.Evolution] can not set VarInd output - " + e.toString());
            amt = 0;
            logIncrease = 0;
        }

        int total = Math.round((Math.round(((logIncrease * Math.log(sizeMult)) + amt) / 10) * 10) * aiCoreMult * timeMult * dualReduction * deficitMult);
        return Math.max(0, total);
    }

    public void AImode() {
        if (getSpecialItem() != null || specialItemWasSet) {
            specialItemWasSet = true;
            return;
        }

        WeightedRandomPicker<String> VPCtype = new WeightedRandomPicker<>();
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            int weight = 0;

            if (spec.hasTag(ItemIds.TAG_VPC_COMMON)) weight = 70;
            if (spec.hasTag(ItemIds.TAG_VPC_UNCOMMON)) weight = 25;
            if (spec.hasTag(ItemIds.TAG_VPC_RARE)) weight = 5;

            VPCtype.add(spec.getId(), weight);
        }

        SpecialItemData specVPC = new SpecialItemData(VPCtype.pick(), null);

        setSpecialItem(specVPC);
        specialItemWasSet = true;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.ADASSEM);
    }

    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.ADASSEM);
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
        if (currentVPC != null) {
            String id = currentVPC.getId();

            if (!id.equals(lastInstalledSpecialItemId)) {
                rampUpExpired = false;
                lastInstalledSpecialItemId = id;
            }
        }
    }

    public void reportEconomyTick(int iterIndex) {
    }

    public void reportEconomyMonthEnd() {
        if (isFunctional()
                && currentVPC != null
                && market.isPlayerOwned()) {

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

            if (currTooltipMode.equals(IndustryTooltipMode.ADD_INDUSTRY) || currentVPC == null) {
                tooltip.addPara("%s", opad, highlight, StringHelper.getString("IndEvo_VarInd", "vpcNotice"));
            }

            if (isFunctional()
                    && currentVPC != null
                    && currTooltipMode.equals(IndustryTooltipMode.NORMAL)) {

                if (!rampUpExpired) {
                    tooltip.addPara(StringHelper.getString("IndEvo_VarInd", "rampUp"), 10, bad, StringHelper.getAbsPercentString(RAMP_UP_REDUCTION_MULT, true));
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
            }
        }
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
        float base = 0;
        return base + super.getPatherInterest();
    }

    public String getCurrentImage() {
        if (currentVPC != null) {
            return Global.getSettings().getSpriteName("IndEvo", "assembler");
        }
        return super.getCurrentImage();
    }

    @Override
    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);

        if (previous instanceof VariableManufactory) {
            setCurrentVPC(((VariableManufactory) previous).getCurrentVPC());
        }
    }

    protected void applyIndEvo_VPCEffects() {
        if (currentVPC != null) {
            VPCInstallableItemPlugin.IndEvo_ItemEffect effect = VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(currentVPC.getId());
            if (effect != null) {
                effect.apply(this);
            }
        }
    }

    public void setCurrentVPC(SpecialItemData vpcItemData) {
        if (vpcItemData == null && this.currentVPC != null) {
            VPCInstallableItemPlugin.IndEvo_ItemEffect effect = VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(this.currentVPC.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }
        this.currentVPC = vpcItemData;
    }

    public SpecialItemData getCurrentVPC() {
        return currentVPC;
    }

    public SpecialItemData getSpecialItem() {
        return currentVPC;
    }

    public void setSpecialItem(SpecialItemData special) {
        currentVPC = special;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        return currentVPC == null &&
                data != null &&
                VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.containsKey(data.getId());
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        if (currentVPC != null && !forUpgrade) {
            CargoAPI cargo = getCargoForInteractionMode(mode);
            if (cargo != null) {
                cargo.addSpecial(currentVPC, 1);
            }
        }
    }

    @Override
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (currentVPC == null) return false;

        float opad = 10f;

        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(currentVPC.getId());

        TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48);
        VPCInstallableItemPlugin.IndEvo_ItemEffect effect = VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(currentVPC.getId());
        effect.addItemDescription(text, currentVPC, InstallableIndustryItemPlugin.InstallableItemDescriptionMode.INDUSTRY_TOOLTIP);
        tooltip.addImageWithText(opad);

        return true;
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
        list.add(new VPCInstallableItemPlugin(this));
        return list;
    }

    @Override
    public void initWithParams(List<String> params) {
        super.initWithParams(params);

        for (String str : params) {
            if (VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.containsKey(str)) {
                setCurrentVPC(new SpecialItemData(str, null));
                break;
            }
        }
    }

    @Override
    public List<SpecialItemData> getVisibleInstalledItems() {
        List<SpecialItemData> result = super.getVisibleInstalledItems();

        if (currentVPC != null) {
            result.add(currentVPC);
        }

        return result;
    }

    @Override
    public boolean hasVPC() {
        return getCurrentVPC() != null;
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