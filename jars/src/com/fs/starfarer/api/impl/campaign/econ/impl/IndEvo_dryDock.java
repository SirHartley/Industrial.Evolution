package com.fs.starfarer.api.impl.campaign.econ.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.plugins.addOrRemovePlugins.IndEvo_subMarketAddOrRemovePlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.util.Misc.getMod;

public class IndEvo_dryDock extends BaseIndustry implements EconomyTickListener {

    private static final float ALPHA_CORE_UPKEEP_RED_MULT = 0.2F;
    private static final int ALPHA_CORE_BONUS_REPAIR = 3;
    private static final float BETA_CORE_COST_RED_MULT = 0.85F;
    private static final float BASE_COST_RED_MULT = 1F;
    private static final int GAMMA_CORE_MAX_REPAIR = 1;
    private static final String MAX_DMOD_MEMORY = "$IndEvo_maxDModMemory";
    private static final String REPAIR_IDENT = "IndEvo_repairIdent";

    private float aiCoreBonusMult = 1f;
    private Map<FleetMemberAPI, Float> repairedShipsTooltipList = new HashMap<>();
    private Map<FleetMemberAPI, Integer> repairedShipsAmountList = new HashMap<>();

    private static void log(String Text) {
        Global.getLogger(IndEvo_dryDock.class).info(Text);
    }

    public void apply() {
        super.apply(true);
        Global.getSector().getListenerManager().addListener(this, true);

        if (isFunctional()) {
            Global.getSector().addScript(new IndEvo_subMarketAddOrRemovePlugin(market, IndEvo_ids.REPSTORAGE, false));
            applyImports();

            if (!Global.getSector().getMemoryWithoutUpdate().contains(MAX_DMOD_MEMORY))
                Global.getSector().getMemoryWithoutUpdate().set(MAX_DMOD_MEMORY, new HashMap<FleetMemberAPI, Integer>());
        }
    }

    private void applyImports() {
        if (market.hasSubmarket(IndEvo_ids.REPSTORAGE)) {
            int amt = getEligibleShips(market.getSubmarket(IndEvo_ids.REPSTORAGE)).size();
            if (amt > 0) demand(IndEvo_Items.PARTS, Math.min(amt, 4));
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        demand.clear();
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().addScript(new IndEvo_subMarketAddOrRemovePlugin(market, IndEvo_ids.REPSTORAGE, true));
    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSettings().getBoolean("dryDock");
    }

    @Override
    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("dryDock");
    }

    public void reportEconomyTick(int iterIndex) {
        //can't do on month end because monthly report

        int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != lastIterInMonth) return;
        getUpkeep().unmodify(REPAIR_IDENT);

        if (isFunctional()) {
            boolean aiMode = !market.isPlayerOwned();
            if (aiMode && (market.getFaction().isHostileTo(Global.getSector().getPlayerFaction()) && !market.getFaction().getId().equals(Factions.PIRATES)))
                return;

            MonthlyReport.FDNode iNode = aiMode ? createMonthlyReportNode() : null;
            Map<FleetMemberAPI, Float> fixedShips = removeDMods(aiMode);

            if (fixedShips.size() > 0) {
                float totalAmount = 0f;
                float feeAmount = 0f;

                MessageIntel intel = new MessageIntel(IndEvo_StringHelper.getString(getId(), "repairedAt"),
                        Misc.getTextColor(), new String[]{(market.getName())}, Global.getSector().getPlayerFaction().getBrightUIColor());

                for (Map.Entry<FleetMemberAPI, Float> entry : fixedShips.entrySet()) {
                    if (aiMode)
                        feeAmount += getHullSizeRepairFee(entry.getKey().getHullSpec().getHullSize()) * repairedShipsAmountList.get(entry.getKey());

                    String name = entry.getKey().getHullSpec().getHullName();
                    float amount = entry.getValue();
                    totalAmount += amount;

                    intel.addLine(BaseIntelPlugin.BULLET + name + ": %s", Misc.getTextColor(), new String[]{(Misc.getDGSCredits(amount))}, Misc.getHighlightColor());
                }

                if (aiMode) iNode.upkeep += feeAmount;
                String header = aiMode ? IndEvo_StringHelper.getString(getId(), "totalCostWIthFee") : IndEvo_StringHelper.getString(getId(), "totalCost");

                intel.addLine(header, Misc.getTextColor(), new String[]{Misc.getDGSCredits(totalAmount), Misc.getDGSCredits(feeAmount)}, Misc.getHighlightColor());
                intel.setIcon(Global.getSettings().getSpriteName("intel", "repairs_finished"));
                intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
                Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.INCOME_TAB, Tags.INCOME_REPORT);
            }
        }
    }

    public void reportEconomyMonthEnd() {
    }

    private Map<FleetMemberAPI, Float> removeDMods(boolean aiMode) {
        Map<FleetMemberAPI, Float> fixedShips = new HashMap<>();
        getUpkeep().unmodify(REPAIR_IDENT);

        repairedShipsAmountList = new HashMap<>();

        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        Map<FleetMemberAPI, Integer> maxDModMemory = (HashMap<FleetMemberAPI, Integer>) memory.get(MAX_DMOD_MEMORY);

        if (market.hasSubmarket(IndEvo_ids.REPSTORAGE)) {
            SubmarketAPI storage = market.getSubmarket(IndEvo_ids.REPSTORAGE);
            String aicoreId = IndEvo_IndustryHelper.getAiCoreIdNotNull(this);
            int removalBudget = getMaxDModRepairAmt();
            ArrayList<FleetMemberAPI> eligibleShips = getEligibleShips(storage);

            //get report node
            String path = aiMode ? "dryDock" : "industries";

            MonthlyReport report = SharedData.getData().getCurrentReport();
            MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
            MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
            MonthlyReport.FDNode indNode = report.getNode(mNode, path);
            MonthlyReport.FDNode iNode = report.getNode(indNode, getId());

            //run the removal
            while (removalBudget >= 0 && !eligibleShips.isEmpty()) {
                FleetMemberAPI ship;
                //if there's a beta core, pick ship with least Dmods
                if (aicoreId.equals(Commodities.BETA_CORE)) {
                    //make a new map containing current D-mod count

                    HashMap<FleetMemberAPI, Integer> eligibleShipsDNum = new HashMap<>();
                    for (FleetMemberAPI s : eligibleShips) {
                        eligibleShipsDNum.put(s, getNumNonBuiltInDMods(s.getVariant()));
                    }

                    //get ship with currently lowest amount of dmods
                    Map.Entry<FleetMemberAPI, Integer> min = null;
                    for (Map.Entry<FleetMemberAPI, Integer> entry : eligibleShipsDNum.entrySet()) {
                        if (min == null || min.getValue() > entry.getValue()) min = entry;
                    }

                    if (min == null) {
                        Global.getLogger(IndEvo_dryDock.class).warn("Could not find eligible ship with smallest amount of D-Mods even though eligibleShips is not empty!");
                        break;
                    }

                    ship = min.getKey();
                } else {
                    //otherwise, random ship
                    int rnd = new Random().nextInt(eligibleShips.size());
                    ship = eligibleShips.get(rnd);
                }

                //pick and a random dmod
                List<HullModSpecAPI> DModList = getListNonBuiltInDMods(ship.getVariant());
                int rnd = new Random().nextInt(DModList.size());

                //cost/dmod memory handing
                int dModAmount = DModList.size();
                if (!maxDModMemory.containsKey(ship) || maxDModMemory.get(ship) < dModAmount)
                    maxDModMemory.put(ship, dModAmount);

                log("maxDModMemory: " + maxDModMemory.get(ship));

                //remove the dmod and add costs
                ship.getVariant().removePermaMod(DModList.get(rnd).getId());

                float cost = getSingleDModRepairCost(ship, maxDModMemory);
                iNode.upkeep += cost;

                log("Repair cost: " + cost);
                log("BetaCoreBonus: " + aiCoreBonusMult);

                float total = fixedShips.containsKey(ship) ? fixedShips.get(ship) + cost : cost;
                fixedShips.put(ship, total);

                if (getListNonBuiltInDMods(ship.getVariant()).size() < 1) {
                    eligibleShips.remove(ship);
                    maxDModMemory.remove(ship);
                }

                //Remove this ship for gamma core functionality
                if (aicoreId.equals(Commodities.GAMMA_CORE)) eligibleShips.remove(ship);

                if (repairedShipsAmountList.containsKey(ship))
                    repairedShipsAmountList.put(ship, repairedShipsAmountList.get(ship) + 1);
                else repairedShipsAmountList.put(ship, 1);

                removalBudget--;
            }
        }

        //restore the hull to base if no dmod remains
        for (Map.Entry<FleetMemberAPI, Float> ship : fixedShips.entrySet()) {
            ShipVariantAPI shipVar = ship.getKey().getVariant();
            if (getNumNonBuiltInDMods(shipVar) == 0) shipVar.setHullSpecAPI(getBaseShipHullSpec(shipVar, false));
        }

        memory.set(MAX_DMOD_MEMORY, maxDModMemory);
        repairedShipsTooltipList = fixedShips;
        return fixedShips;
    }

    private MonthlyReport.FDNode createMonthlyReportNode() {
        MonthlyReport report = SharedData.getData().getCurrentReport();

        MonthlyReport.FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);
        marketsNode.name = IndEvo_StringHelper.getString("colonies");
        marketsNode.custom = MonthlyReport.OUTPOSTS;
        marketsNode.tooltipCreator = report.getMonthlyReportTooltip();

        MonthlyReport.FDNode mNode = report.getNode(marketsNode, market.getId());
        mNode.name = market.getName() + " (" + market.getSize() + ")";
        mNode.custom = market;

        MonthlyReport.FDNode indNode = report.getNode(mNode, "industries");

        for (Industry curr : market.getIndustries()) {
            if (!curr.getId().equals(IndEvo_ids.ACADEMY)) { //Have to exclude academy or player will not be billed training
                MonthlyReport.FDNode iNode = report.getNode(indNode, curr.getId());
                iNode.income = 0;
                iNode.upkeep = 0;
            }
        }

        MonthlyReport.FDNode dryDockNode = report.getNode(mNode, "dryDock");
        dryDockNode.name = IndEvo_StringHelper.getString(getId(), "indNodeTitle");
        dryDockNode.mapEntity = market.getPrimaryEntity();
        dryDockNode.tooltipCreator = report.getMonthlyReportTooltip();
        dryDockNode.icon = getCurrentImage();
        dryDockNode.custom = this;
        dryDockNode.custom2 = this;

        MonthlyReport.FDNode iNode = report.getNode(dryDockNode, this.getId());
        iNode.name = getCurrentName();
        iNode.upkeep = 0;
        iNode.custom = this;
        iNode.mapEntity = market.getPrimaryEntity();

        return iNode;
    }

    private float getHullSizeRepairFee(ShipAPI.HullSize size) {
        float cost;
        switch (size) {
            case FRIGATE:
                cost = Global.getSettings().getFloat("restoFee_FRIGATE");
                break;
            case DESTROYER:
                cost = Global.getSettings().getFloat("restoFee_DESTROYER");
                break;
            case CRUISER:
                cost = Global.getSettings().getFloat("restoFee_CRUISER");
                break;
            case CAPITAL_SHIP:
                cost = Global.getSettings().getFloat("restoFee_CAPITAL");
                break;
            default:
                cost = 10000f;
        }

        float costMod = market.getFaction().getRelationship(Global.getSector().getPlayerFaction().getId());
        costMod = Math.min(costMod, 0.5f);
        costMod = Math.max(costMod, -0.5f);

        return cost * (1 - costMod);
    }

    public ArrayList<FleetMemberAPI> getEligibleShips(SubmarketAPI storage) {
        ArrayList<FleetMemberAPI> eligibleShips = new ArrayList<>();

        if (storage == null
                || storage.getCargoNullOk() == null
                || storage.getCargoNullOk().getMothballedShips() == null) return eligibleShips;

        ArrayList<FleetMemberAPI> shipsInStorage = new ArrayList<>(storage.getCargo().getMothballedShips().getMembersListCopy());
        for (FleetMemberAPI ship : shipsInStorage) {
            if (getNumNonBuiltInDMods(ship.getVariant()) > 0) {
                eligibleShips.add(ship);
            }
        }

        return eligibleShips;
    }

    public int getMaxDModRepairAmt() {
        String aicoreId = IndEvo_IndustryHelper.getAiCoreIdNotNull(this);
        return aicoreId.equals(Commodities.ALPHA_CORE) ? market.getSize() + ALPHA_CORE_BONUS_REPAIR : market.getSize();
    }

    public float getSingleDModRepairCost(FleetMemberAPI ship, Map<FleetMemberAPI, Integer> maxDModMemory) {
        float baseVal = getBaseShipHullSpec(ship.getVariant(), true).getBaseValue();

        //add it to the memory if the ship is not in it, does not account for increases in d-mods as it never saves back
        if (!maxDModMemory.containsKey(ship)) maxDModMemory.put(ship, getNumNonBuiltInDMods(ship.getVariant()));

        return (float) (((baseVal / 2) * (Math.pow(1.2, maxDModMemory.get(ship) + 1))) / maxDModMemory.get(ship)) * aiCoreBonusMult;
    }

    public static ShipHullSpecAPI getBaseShipHullSpec(ShipVariantAPI shipVar, boolean forceBaseHull) {
        ShipHullSpecAPI base = shipVar.getHullSpec().getDParentHull();

        if (forceBaseHull && shipVar.getHullSpec().getBaseHull() != null) return shipVar.getHullSpec().getBaseHull();
        if (!shipVar.getHullSpec().isDefaultDHull() && !shipVar.getHullSpec().isRestoreToBase())
            base = shipVar.getHullSpec();
        if (shipVar.getHullSpec().isRestoreToBase()) base = shipVar.getHullSpec().getBaseHull();

        return base;
    }

    public int getNumNonBuiltInDMods(ShipVariantAPI variant) {
        return DModManager.getNumNonBuiltInDMods(variant);
    }

    public static List<HullModSpecAPI> getListNonBuiltInDMods(ShipVariantAPI variant) {
        List<HullModSpecAPI> list = new ArrayList<>();
        for (String id : variant.getHullMods()) {
            if (variant.getHullSpec().getBuiltInMods().contains(id)) continue;

            if (getMod(id).hasTag(Tags.HULLMOD_DMOD)) {
                list.add(getMod(id));
            }
        }
        return list;
    }

    public float getBaseRepairPrice() {
        float baseRepairPrice = 0.5f;
        if (IndEvo_IndustryHelper.getAiCoreIdNotNull(this).equals(Commodities.BETA_CORE))
            baseRepairPrice /= BETA_CORE_COST_RED_MULT;

        return baseRepairPrice;
    }

    private float getDiscount() {
        float discount = (Math.round(((market.getFaction().getRelationship(Global.getSector().getPlayerFaction().getId()))) * 100f)) / 100f;
        discount = Math.min(discount, 0.5f);
        discount = Math.max(discount, -0.5f);

        return discount;
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        publicAddRightAfterDescriptionSection(tooltip, mode);
    }

    public void publicAddRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        float baseRepairPrice = getBaseRepairPrice();
        FactionAPI faction = market.getFaction();

        if (isFunctional()) {
            float opad = 5.0F;

            float discount = getDiscount();
            String s = IndEvo_StringHelper.getAbsPercentString(discount, true);
            Pair<String, Color> repInt = IndEvo_StringHelper.getRepIntTooltipPair(market.getFaction());

            Map<String, String> toReplace = new HashMap<>();
            toReplace.put("$factionName", faction.getDisplayName());
            toReplace.put("$dModAmt", getMaxDModRepairAmt() + " " + IndEvo_StringHelper.getString("dmods"));
            toReplace.put("$discountAmt", s + "%");
            toReplace.put("$repInt", repInt.one);

            if (currTooltipMode == IndustryTooltipMode.NORMAL) {
                if (getAICoreId() == null) tooltip.addPara(IndEvo_StringHelper.getString(getId(), "capTooltip"), opad);
                tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteTokens(getId(), "currentCap", toReplace),
                        2f,
                        Misc.getHighlightColor(),
                        new String[]{toReplace.get("$dModAmt"), IndEvo_StringHelper.getAbsPercentString(baseRepairPrice, true)});
            }

            if (!market.isPlayerOwned()
                    && currTooltipMode == IndustryTooltipMode.NORMAL
                    && (!faction.isHostileTo(Global.getSector().getPlayerFaction()) || faction.getId().equals(Factions.PIRATES))) {

                tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteTokens(getId(), "addFee", toReplace), opad, Misc.getTextColor(),
                        faction.getColor(),
                        faction.getDisplayNameWithArticle());

                Map<String, Float> sizeList = new LinkedHashMap<>();
                sizeList.put(IndEvo_StringHelper.getString("frig"), getHullSizeRepairFee(ShipAPI.HullSize.FRIGATE));
                sizeList.put(IndEvo_StringHelper.getString("dest"), getHullSizeRepairFee(ShipAPI.HullSize.DESTROYER));
                sizeList.put(IndEvo_StringHelper.getString("cruiser"), getHullSizeRepairFee(ShipAPI.HullSize.CRUISER));
                sizeList.put(IndEvo_StringHelper.getString("cap"), getHullSizeRepairFee(ShipAPI.HullSize.CAPITAL_SHIP));

                tooltip.addPara(IndEvo_StringHelper.getString(getId(), "repFee"), opad, repInt.two, new String[]{toReplace.get("$discountAmt"), toReplace.get("$repInt")});
                for (Map.Entry<String, Float> e : sizeList.entrySet()) {
                    tooltip.addPara(IndEvo_StringHelper.getString(getId(), "hsCost"), 2f, Misc.getHighlightColor(), new String[]{e.getKey(), Misc.getDGSCredits(e.getValue())});
                }
            } else if (!market.isPlayerOwned()
                    && currTooltipMode == IndustryTooltipMode.NORMAL
                    && (faction.isHostileTo(Global.getSector().getPlayerFaction()) || !faction.getId().equals(Factions.PIRATES))) {

                tooltip.addPara(IndEvo_StringHelper.getStringAndSubstituteTokens(getId(), "hostile", toReplace), 2f, Misc.getNegativeHighlightColor(),
                        faction.getColor(),
                        faction.getDisplayName());
            }
        }
    }

    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {

        if (mode != IndustryTooltipMode.NORMAL || repairedShipsTooltipList == null || repairedShipsTooltipList.isEmpty()) {
            return false;
        }
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        float repairCost = 0f;
        float feeAmount = 0f;

        tooltip.addSectionHeading(IndEvo_StringHelper.getString(getId(), "lastMonthHeader"), color, faction.getDarkUIColor(), Alignment.MID, opad);
        tooltip.addPara(IndEvo_StringHelper.getString(getId(), "lastMonthText"), opad);
        //add repair costs here
        for (Map.Entry<FleetMemberAPI, Float> ship : repairedShipsTooltipList.entrySet()) {
            repairCost += ship.getValue();
            feeAmount += getHullSizeRepairFee(ship.getKey().getHullSpec().getHullSize()) * repairedShipsAmountList.get(ship.getKey());

            Map<String, String> toReplace = new HashMap<>();
            toReplace.put("$hullName", ship.getKey().getHullSpec().getHullName());
            toReplace.put("$dModAmt", repairedShipsAmountList.get(ship.getKey()) + " " + IndEvo_StringHelper.getString("dmods"));
            toReplace.put("$amt", Misc.getDGSCredits(ship.getValue()));

            tooltip.addPara(BaseIntelPlugin.BULLET + IndEvo_StringHelper.getStringAndSubstituteTokens(getId(), "repairBullet", toReplace), 2f, Misc.getHighlightColor(),
                    new String[]{
                            toReplace.get("$hullName"),
                            toReplace.get("$dModAmt"),
                            toReplace.get("$amt")});
        }

        if (!market.isPlayerOwned()) {
            tooltip.addPara(IndEvo_StringHelper.getString(getId(), "commissionFee"), opad, Misc.getHighlightColor(), new String[]{Misc.getDGSCredits(feeAmount)});
            tooltip.addPara(IndEvo_StringHelper.getString(getId(), "totalRepairCost"), 2F, Misc.getHighlightColor(), new String[]{Misc.getDGSCredits(repairCost + feeAmount)});
            tooltip.addPara(IndEvo_StringHelper.getString(getId(), "expensesBilled"), 2F);
            return true;
        }

        tooltip.addPara(IndEvo_StringHelper.getString(getId(), "totalRepairCost"), opad, Misc.getHighlightColor(), new String[]{Misc.getDGSCredits(repairCost)});
        tooltip.addPara(IndEvo_StringHelper.getString(getId(), "expensesDetracted"), 2F);
        return true;
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = IndEvo_StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String coreHighlights = IndEvo_StringHelper.getString(getId(), "aCoreHighlights");
        String effect = IndEvo_StringHelper.getStringAndSubstituteToken(getId(), "aCoreEffect", "$aCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{Integer.toString(ALPHA_CORE_BONUS_REPAIR), IndEvo_StringHelper.getAbsPercentString(ALPHA_CORE_UPKEEP_RED_MULT, true), coreHighlights};

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
        String coreHighlights = IndEvo_StringHelper.getString(getId(), "bCoreHighlights");
        String effect = IndEvo_StringHelper.getStringAndSubstituteToken(getId(), "bCoreEffect", "$bCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{IndEvo_StringHelper.getAbsPercentString(BETA_CORE_COST_RED_MULT, true), coreHighlights};

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
        String pre = IndEvo_StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String coreHighlights = IndEvo_StringHelper.getString(getId(), "gCoreHighlights");
        String effect = IndEvo_StringHelper.getStringAndSubstituteToken(getId(), "gCoreEffect", "$gCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{Integer.toString(GAMMA_CORE_MAX_REPAIR), coreHighlights};


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
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    protected void applyAICoreToIncomeAndUpkeep() {
        String name;

        switch (IndEvo_IndustryHelper.getAiCoreIdNotNull(this)) {
            case Commodities.ALPHA_CORE:
                name = IndEvo_StringHelper.getString("IndEvo_AICores", "aCoreStatModAssigned");
                getUpkeep().modifyMult("ind_core", ALPHA_CORE_UPKEEP_RED_MULT, name);
                break;
            default:
                getUpkeep().unmodifyMult("ind_core");
        }
    }

    @Override
    protected void applyBetaCoreModifiers() {
        aiCoreBonusMult = BETA_CORE_COST_RED_MULT;
    }

    @Override
    protected void applyNoAICoreModifiers() {
        aiCoreBonusMult = BASE_COST_RED_MULT;
    }
}

