package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.ids.Ids;
import indevo.utils.helper.IndustryHelper;
import indevo.utils.helper.StringHelper;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.ColonyOtherFactorsListener;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import indevo.items.installable.SpecialItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TEN;

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
            Misc.getStorageCargo(market).addCommodity(getAICoreId(), 1);
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

        Set<MarketAPI> targetMarkets = new HashSet<>(IndustryHelper.getMarketsInLocation(market.getStarSystem(), market.getFactionId()));

        if (getSpecialItem() != null) {
            for (MarketAPI m : Misc.getFactionMarkets(market.getFaction())) {
                float dist = Misc.getDistanceLY(m.getLocationInHyperspace(), market.getLocationInHyperspace());
                if (dist <= SpecialItemEffectsRepo.RANGE_LY_TEN) {
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
        String coreId = IndustryHelper.getAiCoreIdNotNull(this);

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

        switch (IndustryHelper.getAiCoreIdNotNull(this)) {
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
        Set<String> allowedIds = IndustryHelper.getCSVSetFromMemory(Ids.BUREAU_LIST);

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
        return Global.getSettings().getBoolean("CentBureau")
                && super.isAvailableToBuild()
                && isOnlyInfraInSystem();
    }

    @Override
    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("CentBureau");
    }

    private boolean isOnlyInfraInSystem() {
        return IndustryHelper.isOnlyInstanceInSystemExcludeMarket(getId(), market.getStarSystem(), market, market.getFaction());
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) {
            return super.getUnavailableReason();
        }

        if (!isOnlyInfraInSystem()) {
            return StringHelper.getString(getId(), "onlyOneInstanceAllowed");
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
        if (!market.isPlayerOwned()) {
            return;
        }

        if (!isBuilding()) {
            float opad = 10f;
            Color highlight = Misc.getHighlightColor();
            Color bad = Misc.getNegativeHighlightColor();

            if (industryExistOnMultipleMap.isEmpty()) {
                String s = StringHelper.getString(getId(), "noDuplicates");
                Color c = currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY ? highlight : bad;
                tooltip.addPara("%s", opad, c, s);
            }

            if (isFunctional()) {
                if (coreIsSufficient()) {
                    String sufficient = StringHelper.getString(getId(), "sufficient");
                    tooltip.addPara(
                            StringHelper.getStringAndSubstituteToken(getId(), "aiCoreSufficient", "$sufficient", sufficient),
                            opad,
                            Misc.getPositiveHighlightColor(),
                            sufficient);

                } else if (!industryExistOnMultipleMap.isEmpty()) {
                    String insufficient = StringHelper.getString(getId(), "insufficient");
                    tooltip.addPara(
                            StringHelper.getStringAndSubstituteToken(getId(), "aiCoreInsufficient", "$insufficient", insufficient),
                            opad,
                            Misc.getNegativeHighlightColor(),
                            getMaxSupportedInd() + "");
                }
            }
        }
    }

//AI core handling

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "aCoreEffect");
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

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "bCoreEffect");
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

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String effect = StringHelper.getString(getId(), "gCoreEffect");
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

//Tooltip and descriptions:

    @Override
    public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        currTooltipMode = mode;
        String cat = "IndEvo_BaseIndustryTooltips";

        float pad = 3f;
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();

        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();


        MarketAPI copy = market.clone();
        MarketAPI orig = market;

        market = copy;
        boolean needToAddIndustry = !market.hasIndustry(getId());
        //addDialogMode = true;
        if (needToAddIndustry) market.getIndustries().add(this);

        if (mode != IndustryTooltipMode.NORMAL) {
            market.clearCommodities();
            for (CommodityOnMarketAPI curr : market.getAllCommodities()) {
                curr.getAvailableStat().setBaseValue(100);
            }
        }

        market.reapplyConditions();
        reapply();

        String type = "";
        if (isIndustry()) type = StringHelper.getString(cat, "t1");
        if (isStructure()) type = StringHelper.getString(cat, "t2");

        tooltip.addTitle(getCurrentName() + type, color);

        String desc = spec.getDesc();
        String override = getDescriptionOverride();
        if (override != null) {
            desc = override;
        }
        desc = Global.getSector().getRules().performTokenReplacement(null, desc, market.getPrimaryEntity(), null);

        tooltip.addPara(desc, opad);

        if (isIndustry() && (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                mode == IndustryTooltipMode.UPGRADE ||
                mode == IndustryTooltipMode.DOWNGRADE)
        ) {
            int num = Misc.getNumIndustries(market);
            int max = Misc.getMaxIndustries(market);

            // during the creation of the tooltip, the market has both the current industry
            // and the upgrade/downgrade. So if this upgrade/downgrade counts as an industry, it'd count double if
            // the current one is also an industry. Thus reduce num by 1 if that's the case.
            if (isIndustry()) {
                if (mode == IndustryTooltipMode.UPGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getUpgrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                    for (Industry curr : market.getIndustries()) {
                        if (getSpec().getId().equals(curr.getSpec().getDowngrade())) {
                            if (curr.isIndustry()) {
                                num--;
                            }
                            break;
                        }
                    }
                }
            }

            Color c = gray;
            c = Misc.getTextColor();
            Color h1 = highlight;
            if (num > max) {// || (num >= max && mode == IndustryTooltipMode.ADD_INDUSTRY)) {
                //c = bad;
                h1 = bad;
                num--;

                tooltip.addPara(StringHelper.getString(cat, "t3"), bad, opad);
            }
        }

        addRightAfterDescriptionSection(tooltip, mode);

        if (isDisrupted()) {
            int left = (int) getDisruptedDays();
            if (left < 1) left = 1;

            tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t4", "$days", StringHelper.getDayOrDays(left)),
                    opad, Misc.getNegativeHighlightColor(), highlight, "" + left);
        }

        if (DebugFlags.COLONY_DEBUG || market.isPlayerOwned()) {
            if (mode == IndustryTooltipMode.NORMAL) {
                if (getSpec().getUpgrade() != null && !isBuilding()) {
                    tooltip.addPara(StringHelper.getString(cat, "t5"), Misc.getPositiveHighlightColor(), opad);
                } else {
                    tooltip.addPara(StringHelper.getString(cat, "t6"), Misc.getPositiveHighlightColor(), opad);
                }
                //tooltip.addPara("Click to manage", market.getFaction().getBrightUIColor(), opad);
            }
        }

        if (mode == IndustryTooltipMode.QUEUED) {
            tooltip.addPara(StringHelper.getString(cat, "t7"), Misc.getPositiveHighlightColor(), opad);
            tooltip.addPara(StringHelper.getString(cat, "t8"), opad);

            int left = (int) (getSpec().getBuildTime());
            if (left < 1) left = 1;
            tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t9", "$days", StringHelper.getDayOrDays(left)), opad, highlight, "" + left);

            //return;
        } else if (!isFunctional() && mode == IndustryTooltipMode.NORMAL && isBuilding()) {
            tooltip.addPara(StringHelper.getString(cat, "t10"), opad);

            int left = (int) (buildTime - buildProgress);
            if (left < 1) left = 1;
            tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t11", "$days", StringHelper.getDayOrDays(left)), opad, highlight, "" + left);
        } else if (!isFunctional() && getAICoreId() == null) {
            tooltip.addPara("%s", opad, highlight, StringHelper.getString(getId(), "noAICore"));
        }

        if (!isAvailableToBuild() &&
                (mode == IndustryTooltipMode.ADD_INDUSTRY ||
                        mode == IndustryTooltipMode.UPGRADE ||
                        mode == IndustryTooltipMode.DOWNGRADE)) {
            String reason = getUnavailableReason();
            if (reason != null) {
                tooltip.addPara(reason, bad, opad);
            }
        }

        boolean category = getSpec().hasTag(Industries.TAG_PARENT);

        if (!category) {
            int credits = (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
            String creditsStr = Misc.getDGSCredits(credits);
            if (mode == IndustryTooltipMode.UPGRADE || mode == IndustryTooltipMode.ADD_INDUSTRY) {
                int cost = (int) getBuildCost();
                String costStr = Misc.getDGSCredits(cost);

                int days = (int) getBuildTime();

                LabelAPI label = null;
                if (mode == IndustryTooltipMode.UPGRADE) {
                    label = tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t12", "$days", StringHelper.getDayOrDays(days)), opad,
                            highlight, costStr, "" + days, creditsStr);
                } else {
                    label = tooltip.addPara(StringHelper.getStringAndSubstituteToken(cat, "t13", "$days", StringHelper.getDayOrDays(days)), opad,
                            highlight, costStr, "" + days, creditsStr);
                }
                label.setHighlight(costStr, "" + days, creditsStr);
                if (credits >= cost) {
                    label.setHighlightColors(highlight, highlight, highlight);
                } else {
                    label.setHighlightColors(bad, highlight, highlight);
                }
            } else if (mode == IndustryTooltipMode.DOWNGRADE) {
                float refundFraction = Global.getSettings().getFloat("industryRefundFraction");
                int cost = (int) (getBuildCost() * refundFraction);
                String refundStr = Misc.getDGSCredits(cost);

                tooltip.addPara(StringHelper.getString(cat, "t14"), opad, highlight, refundStr);
            }


            addPostDescriptionSection(tooltip, mode);

            if (!getIncome().isUnmodified()) {
                int income = getIncome().getModifiedInt();
                tooltip.addPara(StringHelper.getString(cat, "t15"), opad, highlight, Misc.getDGSCredits(income));
                tooltip.addStatModGrid(250, 65, 10, pad, getIncome(), true, new TooltipMakerAPI.StatModValueGetter() {
                    public String getPercentValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getMultValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public Color getModColor(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getFlatValue(MutableStat.StatMod mod) {
                        return Misc.getWithDGS(mod.value) + Strings.C;
                    }
                });
            }

            if (!getUpkeep().isUnmodified()) {
                int upkeep = getUpkeep().getModifiedInt();
                tooltip.addPara(StringHelper.getString(cat, "t16"), opad, highlight, Misc.getDGSCredits(upkeep));
                tooltip.addStatModGrid(250, 65, 10, pad, getUpkeep(), true, new TooltipMakerAPI.StatModValueGetter() {
                    public String getPercentValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getMultValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    public Color getModColor(MutableStat.StatMod mod) {
                        return null;
                    }

                    public String getFlatValue(MutableStat.StatMod mod) {
                        return Misc.getWithDGS(mod.value) + Strings.C;
                    }
                });
            }

            addPostUpkeepSection(tooltip, mode);

            boolean hasSupply = !currentModSupply.isEmpty();
            boolean hasDemand = !currentModDemand.isEmpty();

            float maxIconsPerRow = 10f;

            if (hasSupply && isFunctional()) {
                tooltip.addSectionHeading(StringHelper.getString(getId(), "additionalOutputs"), color, dark, Alignment.MID, opad);
                tooltip.beginIconGroup();
                tooltip.setIconSpacingMedium();

                for (Map.Entry<String, Integer> curr : currentModSupply.entrySet()) {
                    int qty = curr.getValue();
                    if (qty > 0) tooltip.addIcons(market.getCommodityData(curr.getKey()), qty, IconRenderMode.GREEN);
                }

                int rows = 3;
                tooltip.addIconGroup(32, rows, opad);
            }

            addPostSupplySection(tooltip, hasSupply, mode);

            if (hasDemand && isFunctional()) {
                tooltip.addSectionHeading(StringHelper.getString(getId(), "additionalDemands"), color, dark, Alignment.MID, opad);
                tooltip.beginIconGroup();
                tooltip.setIconSpacingMedium();

                for (Map.Entry<String, Integer> curr : currentModDemand.entrySet()) {
                    int qty = curr.getValue();
                    if (qty > 0) tooltip.addIcons(orig.getCommodityData(curr.getKey()), qty, IconRenderMode.NORMAL);
                }

                int rows = 3;
                tooltip.addIconGroup(32, rows, opad);
            }

            addPostDemandSection(tooltip, hasDemand, mode);

            if (!needToAddIndustry) {
                addInstalledItemsSection(mode, tooltip, expanded);
            }

            tooltip.addPara(StringHelper.getString(cat, "t19"), gray, opad);
        }

        if (needToAddIndustry) {
            unapply();
            market.getIndustries().remove(this);
        }
        market = orig;
        if (!needToAddIndustry) {
            reapply();
        }
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
                    if (dist < RANGE_LY_TEN) {
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

                if (p.two > RANGE_LY_TEN) {
                    text.addPara("The nearest Centralization Bureau with a Logistics AI core is located in the " +
                                    p.one.getContainingLocation().getNameWithLowercaseType() + ", %s " + lights + " away. The maximum " +
                                    "range in which resource management is possible is %s light-years.",
                            opad, h,
                            "" + Misc.getRoundedValueMaxOneAfterDecimal(p.two),
                            "" + (int) RANGE_LY_TEN);
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