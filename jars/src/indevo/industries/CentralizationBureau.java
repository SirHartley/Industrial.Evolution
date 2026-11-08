package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.items.installable.SpecialItemEffectsRepo;
import indevo.utils.helper.MiscIE;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class CentralizationBureau extends BaseIndustry {

    public static Logger log = Global.getLogger(CentralizationBureau.class);

    private static final int ALPHA_MAX_IND = 3;
    private static final int BETA_MAX_IND = 2;
    private static final int GAMMA_MAX_IND = 1;
    private static final int NONE_MAX_IND = 0;

    private boolean aiCoreHasBeenSet = false;
    private Map<String, Integer> industryExistOnMultipleMap = new HashMap<>();
    private Map<String, Integer> currentModSupply = new HashMap<>();
    private Map<String, Integer> currentModDemand = new HashMap<>();

    @Override
    public void init(String id, MarketAPI market) {
        super.init(id, market);
        this.industryExistOnMultipleMap = getIndustryExistOnMultipleMap();
    }

    public void apply() {
        super.apply(true);

        if (getSpecialItem() != null && getAICoreId() != null) {
            MiscIE.getStorageCargo(market).addCommodity(getAICoreId(), 1);
            setAICoreId(null);
        }

        industryExistOnMultipleMap = getIndustryExistOnMultipleMap();

        if (market.isPlayerOwned() && isFunctional()) setCommoditySupply();
        if (!market.isPlayerOwned()) AImode();
    }

    @Override
    public void unapply() {
        super.unapply();
        unmodifyMutableSupDemStats();
    }

    private Map<String, Integer> getIndustryExistOnMultipleMap() {

        //check other in system colonies for industries and compare to this one
        //make a list with a key that contains every Industry with in system multiples found + amount

        Map<String, Integer> industryCountMap = new HashMap<>();
        Set<String> targetIds = convertToLegalIDSet(market.getIndustries());

        Set<MarketAPI> targetMarkets = new HashSet<>(MiscIE.getMarketsInLocation(market.getStarSystem(), market.getFactionId()));

        if (getSpecialItem() != null) {
            for (MarketAPI m : Misc.getFactionMarkets(market.getFaction())) {
                float dist = Misc.getDistanceLY(m.getLocationInHyperspace(), market.getLocationInHyperspace());
                if (dist <= SpecialItemEffectsRepo.RANGE_LY_TWELVE) {
                    targetMarkets.add(m);
                }
            }
        }

        for (MarketAPI m : targetMarkets) {
            if (m.getId().equals(market.getId())) continue;

            for (String id : targetIds) {
                if (id.equals(Ids.ADMANUF) && m.hasIndustry(Ids.ADMANUF)) {
                    String localData = market.getIndustry(id).getSpecialItem().getId();
                    String foreignData = m.getIndustry(id).getSpecialItem().getId();

                    if (localData.equals(foreignData)) addOrIncrement(industryCountMap, id);

                    //check for the opposite if it's HI/OW
                } else if (id.equals(Industries.HEAVYINDUSTRY) || id.equals(Industries.ORBITALWORKS)) {
                    if (m.hasIndustry(Industries.HEAVYINDUSTRY))
                        addOrIncrement(industryCountMap, Industries.HEAVYINDUSTRY);
                    if (m.hasIndustry(Industries.ORBITALWORKS))
                        addOrIncrement(industryCountMap, Industries.ORBITALWORKS);
                } else if (m.hasIndustry(id)) {
                    addOrIncrement(industryCountMap, id);
                }
            }
        }

        return industryCountMap;
    }

    @Override
    public boolean canInstallAICores() {
        return getSpecialItem() == null;
    }

    private void setCommoditySupply() {
        unmodifyMutableSupDemStats();

        //need two maps with the commodity id and the mod int value to bring to tooltip
        Map<String, Integer> supplyMap = new HashMap<>();
        Map<String, Integer> demandMap = new HashMap<>();

        for (Map.Entry<String, Integer> industryExistMapEntry : industryExistOnMultipleMap.entrySet()) {
            String key = industryExistMapEntry.getKey();
            int val = industryExistMapEntry.getValue();

            if (!market.hasIndustry(key)) continue;
            Industry ind = market.getIndustry(key);

            for (MutableCommodityQuantity sup : ind.getAllSupply()) {
                int maxMod = clampToPermittedVal(val);
                String commodityID = sup.getCommodityId();

                if (!supplyMap.containsKey(commodityID) || supplyMap.get(commodityID) < maxMod)
                    supplyMap.put(commodityID, maxMod);

                sup.getQuantity().modifyFlatAlways(
                        getModId(),
                        maxMod,
                        getNameForModifier());
            }

            for (MutableCommodityQuantity dem : ind.getAllDemand()) {
                int maxMod = clampToPermittedVal(val);
                String commodityID = dem.getCommodityId();

                if (!demandMap.containsKey(commodityID) || demandMap.get(commodityID) < maxMod)
                    demandMap.put(commodityID, maxMod);

                dem.getQuantity().modifyFlatAlways(
                        getModId(),
                        maxMod,
                        getNameForModifier());
            }
        }

        currentModSupply = supplyMap;
        currentModDemand = demandMap;
    }

    private boolean coreIdIsAtWorst(String id) {
        String coreId = MiscIE.getAiCoreIdNotNull(this);

        switch (id) {
            case Commodities.GAMMA_CORE:
                return getAICoreId() != null;
            case Commodities.BETA_CORE:
                return coreId.equals(Commodities.BETA_CORE) || coreId.equals(Commodities.ALPHA_CORE);
            case Commodities.ALPHA_CORE:
                return coreId.equals(Commodities.ALPHA_CORE);
            default:
                return false;
        }
    }

    private boolean coreIsSufficient() {
        if (getSpecialItem() != null) return true;

        int currentMax = getHighestIndAmt();

        switch (currentMax) {
            case NONE_MAX_IND:
                return true;
            case GAMMA_MAX_IND:
                return coreIdIsAtWorst(Commodities.GAMMA_CORE);
            case BETA_MAX_IND:
                return coreIdIsAtWorst(Commodities.BETA_CORE);
            default:
                return coreIdIsAtWorst(Commodities.ALPHA_CORE);
        }
    }

    private int clampToPermittedVal(int amt) {
        int max = getMaxSupportedInd();
        return Math.min(max, amt);
    }

    private int getMaxSupportedInd() {
        if (getSpecialItem() != null) return SpecialItemEffectsRepo.LOG_CORE_MAX_BONUS;

        switch (MiscIE.getAiCoreIdNotNull(this)) {
            case Commodities.GAMMA_CORE:
                return GAMMA_MAX_IND;
            case Commodities.BETA_CORE:
                return BETA_MAX_IND;
            case Commodities.ALPHA_CORE:
                return ALPHA_MAX_IND;
            default:
                return NONE_MAX_IND;
        }
    }

    private void unmodifyMutableSupDemStats() {
        for (Map.Entry<String, Integer> industryExistMapEntry : industryExistOnMultipleMap.entrySet()) {
            String key = industryExistMapEntry.getKey();

            if (!market.hasIndustry(key)) continue;
            Industry ind = market.getIndustry(key);

            for (MutableCommodityQuantity sup : ind.getAllSupply()) {
                sup.getQuantity().unmodify(getModId());
            }

            for (MutableCommodityQuantity dem : ind.getAllDemand()) {
                dem.getQuantity().unmodify(getModId());
            }
        }
    }

    private int getHighestIndAmt() {
        return industryExistOnMultipleMap == null || industryExistOnMultipleMap.isEmpty() ? 0 : Collections.max(industryExistOnMultipleMap.values());
    }

    private Set<String> convertToLegalIDSet(List<Industry> indList) {
        Set<String> s = new HashSet<>();
        Set<String> allowedIds = MiscIE.getCSVSetFromMemory(Ids.BUREAU_LIST);

        for (Industry industry : indList) {
            if (allowedIds.contains(industry.getId())) s.add(industry.getId());
        }

        return s;
    }

    private void addOrIncrement(Map<String, Integer> map, String id) {
        if (!map.containsKey(id)) map.put(id, 1);
        else map.put(id, map.get(id) + 1);
    }

    public void AImode() {
        if (getAICoreId() == null && !aiCoreHasBeenSet) {
            setAICoreId(Commodities.GAMMA_CORE);
        }

        aiCoreHasBeenSet = true;
    }

    @Override
    public boolean isAvailableToBuild() {
        return Setting.ADINFRA.enabled()
                && super.isAvailableToBuild()
                && isOnlyInfraInSystem();
    }

    @Override
    public boolean showWhenUnavailable() {
        return Setting.ADINFRA.enabled();
    }

    private boolean isOnlyInfraInSystem() {
        return MiscIE.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction());
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) {
            return super.getUnavailableReason();
        }

        if (!isOnlyInfraInSystem()) {
            return "There can only be one Centralization Bureau in a star system.";
        } else {
            return super.getUnavailableReason();
        }
    }

    @Override
    public boolean isFunctional() {
        return super.isFunctional() && (getAICoreId() != null || getSpecialItem() != null);
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.QUEUED && !isBuilding() && getAICoreId() == null && getSpecialItem() == null) {
            tooltip.addPara("%s", 10f, Misc.getHighlightColor(), "This structure requires an AI core to function.");
        }

        if (!market.isPlayerOwned()) {
            return;
        }

        if (!isBuilding()) {
            float opad = 10f;
            Color highlight = Misc.getHighlightColor();
            Color bad = Misc.getNegativeHighlightColor();

            if (industryExistOnMultipleMap.isEmpty()) {
                String s = "There are no duplicate industries in this system.";
                Color c = currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY ? highlight : bad;
                tooltip.addPara("%s", opad, c, s);
            }

            if (isFunctional()) {
                if (coreIsSufficient()) {
                    String sufficient = "sufficient";
                    tooltip.addPara(
                            "The AI core installed in this structure is " + sufficient + ".",
                            opad,
                            Misc.getPositiveHighlightColor(),
                            sufficient);

                } else if (!industryExistOnMultipleMap.isEmpty()) {
                    String insufficient = "insufficient";
                    tooltip.addPara(
                            "The AI core installed in this structure is " + insufficient + ". Maximum supported bonus: %s",
                            opad,
                            Misc.getNegativeHighlightColor(),
                            getMaxSupportedInd() + "");
                }
            }
        }
    }

    @Override
    protected void addPostSupplySection(TooltipMakerAPI tooltip, boolean hasSupply, IndustryTooltipMode mode) {
        super.addPostSupplySection(tooltip, hasSupply, mode);
        addBoostSection(tooltip, currentModSupply, "Additional production output", IconRenderMode.GREEN);
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);
        addBoostSection(tooltip, currentModDemand, "Additional demand requirements", IconRenderMode.NORMAL);
    }

    private void addBoostSection(TooltipMakerAPI tooltip, Map<String, Integer> boosts, String heading, IconRenderMode iconMode) {
        if (boosts.isEmpty() || !isFunctional()) return;

        float opad = 10f;
        FactionAPI faction = market.getFaction();

        tooltip.addSectionHeading(heading, faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
        tooltip.beginIconGroup();
        tooltip.setIconSpacingMedium();

        for (Map.Entry<String, Integer> curr : boosts.entrySet()) {
            int qty = curr.getValue();
            if (qty > 0) tooltip.addIcons(market.getCommodityData(curr.getKey()), qty, iconMode);
        }

        tooltip.addIconGroup(32, 3, opad);
    }

//AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Alpha-level AI core. " : "Alpha-level AI core currently assigned. ";
        String effect = "The maximum bonus production this high level intelligence can provide is %s";
        String highlightString = ALPHA_MAX_IND + "";

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

        String pre = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Beta-level AI core. " : "Beta-level AI core currently assigned. ";
        String effect = "The maximum bonus production this intelligence can provide is %s";
        String highlightString = BETA_MAX_IND + "";

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

        String pre = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Gamma-level AI core. " : "Gamma-level AI core currently assigned. ";
        String effect = "The maximum bonus production this low level intelligence can provide is %s";
        String highlightString = GAMMA_MAX_IND + "";

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
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    public static Pair<MarketAPI, Float> getNearestBureauWithItem(Vector2f locInHyper) {
        MarketAPI nearest = null;
        float minDist = Float.MAX_VALUE;

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.hasIndustry(Ids.ADINFRA)) {
                Industry ind = market.getIndustry(Ids.ADINFRA);

                if (ind.isFunctional() && ind.getSpecialItem() != null) {
                    float dist = Misc.getDistanceLY(locInHyper, ind.getMarket().getLocationInHyperspace());
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

    public static Set<MarketAPI> getListOfActiveBureausInRange(Vector2f locInHyper) {
        Set<MarketAPI> marketAPISet = new HashSet<>();

        for (MarketAPI market : Misc.getFactionMarkets("player")) {
            if (market.hasIndustry(Ids.ADINFRA)) {
                Industry ind = market.getIndustry(Ids.ADINFRA);

                if (ind.isFunctional() && ind.getSpecialItem() != null) {
                    float dist = Misc.getDistanceLY(locInHyper, ind.getMarket().getLocationInHyperspace());
                    if (dist < RANGE_LY_TWELVE) {
                        marketAPISet.add(market);
                    }
                }
            }
        }

        return marketAPISet;
    }

    //register this in mod plugin
    public static class BureauFactor implements ColonyOtherFactorsListener {
        public boolean isActiveFactorFor(SectorEntityToken entity) {
            return getNearestBureauWithItem(entity.getLocationInHyperspace()) != null;
        }

        public void printOtherFactors(TooltipMakerAPI text, SectorEntityToken entity) {
            Pair<MarketAPI, Float> p = getNearestBureauWithItem(entity.getLocationInHyperspace());

            if (p != null) {
                Color h = Misc.getHighlightColor();
                float opad = 10f;

                String dStr = "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two);
                String lights = "light-years";
                if (dStr.equals("1")) lights = "light-year";

                if (p.two > RANGE_LY_TWELVE) {
                    text.addPara("The nearest Centralization Bureau with a Logistics AI core is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away. The maximum " +
                                    "range in which resource management is possible is %s light-years.",
                            opad, h,
                            "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "" + (int) RANGE_LY_TWELVE);
                } else {
                    Set<MarketAPI> s = getListOfActiveBureausInRange(entity.getLocationInHyperspace());


                    text.addPara("The following Centralization Bureaus with Logistics AI cores will benefit from industries built on this planet: ",
                            opad);
                    for (MarketAPI m : s) {
                        String str = m.getName();

                        text.addPara(BaseIntelPlugin.BULLET + str + " in the " + m.getStarSystem().getName(), 3f, h, str);
                    }
                }
            }
        }
    }
}
