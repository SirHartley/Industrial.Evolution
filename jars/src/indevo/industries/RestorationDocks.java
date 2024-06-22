package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.utils.helper.Misc;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.SubMarketAddOrRemovePlugin;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.util.Misc.getMod;

public class RestorationDocks extends BaseIndustry implements EconomyTickListener {

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
        Global.getLogger(RestorationDocks.class).info(Text);
    }

    public void apply() {
        super.apply(true);
        Global.getSector().getListenerManager().addListener(this, true);

        if (isFunctional()) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.REPSTORAGE, false));
            applyImports();

            if (!Global.getSector().getMemoryWithoutUpdate().contains(MAX_DMOD_MEMORY))
                Global.getSector().getMemoryWithoutUpdate().set(MAX_DMOD_MEMORY, new HashMap<FleetMemberAPI, Integer>());
        }
    }

    private void applyImports() {
        if (market.hasSubmarket(Ids.REPSTORAGE)) {
            int amt = getEligibleShips(market.getSubmarket(Ids.REPSTORAGE)).size();
            if (amt > 0) demand(ItemIds.PARTS, Math.min(amt, 4));
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        demand.clear();
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.REPSTORAGE, true));
    }

    @Override
    public boolean isAvailableToBuild() {
        return Settings.getBoolean(Settings.DRYDOCK);
    }

    @Override
    public boolean showWhenUnavailable() {
        return Settings.getBoolean(Settings.DRYDOCK);
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

            MonthlyReport.FDNode iNode = aiMode ? Misc.createMonthlyReportNode(this, market, getCurrentName(), Ids.ACADEMY, Ids.REPAIRDOCKS, Ids.PET_STORE) : null;
            Map<FleetMemberAPI, Float> fixedShips = removeDMods(aiMode);

            if (fixedShips.size() > 0) {
                float totalAmount = 0f;
                float feeAmount = 0f;

                MessageIntel intel = new MessageIntel(StringHelper.getString(getId(), "repairedAt"),
                        com.fs.starfarer.api.util.Misc.getTextColor(), new String[]{(market.getName())}, Global.getSector().getPlayerFaction().getBrightUIColor());

                for (Map.Entry<FleetMemberAPI, Float> entry : fixedShips.entrySet()) {
                    if (aiMode)
                        feeAmount += getHullSizeRepairFee(entry.getKey().getHullSpec().getHullSize()) * repairedShipsAmountList.get(entry.getKey());

                    String name = entry.getKey().getHullSpec().getHullName();
                    float amount = entry.getValue();
                    totalAmount += amount;

                    intel.addLine(BaseIntelPlugin.BULLET + name + ": %s", com.fs.starfarer.api.util.Misc.getTextColor(), new String[]{(com.fs.starfarer.api.util.Misc.getDGSCredits(amount))}, com.fs.starfarer.api.util.Misc.getHighlightColor());
                }

                if (aiMode) iNode.upkeep += feeAmount;
                String header = aiMode ? StringHelper.getString(getId(), "totalCostWIthFee") : StringHelper.getString(getId(), "totalCost");

                intel.addLine(header, com.fs.starfarer.api.util.Misc.getTextColor(), new String[]{com.fs.starfarer.api.util.Misc.getDGSCredits(totalAmount), com.fs.starfarer.api.util.Misc.getDGSCredits(feeAmount)}, com.fs.starfarer.api.util.Misc.getHighlightColor());
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

        if (market.hasSubmarket(Ids.REPSTORAGE)) {
            SubmarketAPI storage = market.getSubmarket(Ids.REPSTORAGE);
            String aicoreId = Misc.getAiCoreIdNotNull(this);
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
                        Global.getLogger(RestorationDocks.class).warn("Could not find eligible ship with smallest amount of D-Mods even though eligibleShips is not empty!");
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

    private float getHullSizeRepairFee(ShipAPI.HullSize size) {
        float cost;
        switch (size) {
            case FRIGATE:
                cost = Settings.getFloat(Settings.RESTO_FEE_FRIGATE);
                break;
            case DESTROYER:
                cost = Settings.getFloat(Settings.RESTO_FEE_DESTROYER);
                break;
            case CRUISER:
                cost = Settings.getFloat(Settings.RESTO_FEE_CRUISER);
                break;
            case CAPITAL_SHIP:
                cost = Settings.getFloat(Settings.RESTO_FEE_CAPITAL);
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
        String aicoreId = Misc.getAiCoreIdNotNull(this);
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
        if (Misc.getAiCoreIdNotNull(this).equals(Commodities.BETA_CORE))
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
            String s = StringHelper.getAbsPercentString(discount, true);
            Pair<String, Color> repInt = StringHelper.getRepIntTooltipPair(market.getFaction());

            Map<String, String> toReplace = new HashMap<>();
            toReplace.put("$factionName", faction.getDisplayName());
            toReplace.put("$dModAmt", getMaxDModRepairAmt() + " " + StringHelper.getString("dmods"));
            toReplace.put("$discountAmt", s + "%");
            toReplace.put("$repInt", repInt.one);

            if (currTooltipMode == IndustryTooltipMode.NORMAL) {
                if (getAICoreId() == null) tooltip.addPara(StringHelper.getString(getId(), "capTooltip"), opad);
                tooltip.addPara(StringHelper.getStringAndSubstituteTokens(getId(), "currentCap", toReplace),
                        2f,
                        com.fs.starfarer.api.util.Misc.getHighlightColor(),
                        new String[]{toReplace.get("$dModAmt"), StringHelper.getAbsPercentString(baseRepairPrice, true)});
            }

            if (!market.isPlayerOwned()
                    && currTooltipMode == IndustryTooltipMode.NORMAL
                    && (!faction.isHostileTo(Global.getSector().getPlayerFaction()) || faction.getId().equals(Factions.PIRATES))) {

                tooltip.addPara(StringHelper.getStringAndSubstituteTokens(getId(), "addFee", toReplace), opad, com.fs.starfarer.api.util.Misc.getTextColor(),
                        faction.getColor(),
                        faction.getDisplayNameWithArticle());

                Map<String, Float> sizeList = new LinkedHashMap<>();
                sizeList.put(StringHelper.getString("frig"), getHullSizeRepairFee(ShipAPI.HullSize.FRIGATE));
                sizeList.put(StringHelper.getString("dest"), getHullSizeRepairFee(ShipAPI.HullSize.DESTROYER));
                sizeList.put(StringHelper.getString("cruiser"), getHullSizeRepairFee(ShipAPI.HullSize.CRUISER));
                sizeList.put(StringHelper.getString("cap"), getHullSizeRepairFee(ShipAPI.HullSize.CAPITAL_SHIP));

                tooltip.addPara(StringHelper.getString(getId(), "repFee"), opad, repInt.two, new String[]{toReplace.get("$discountAmt"), toReplace.get("$repInt")});
                for (Map.Entry<String, Float> e : sizeList.entrySet()) {
                    tooltip.addPara(StringHelper.getString(getId(), "hsCost"), 2f, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{e.getKey(), com.fs.starfarer.api.util.Misc.getDGSCredits(e.getValue())});
                }
            } else if (!market.isPlayerOwned()
                    && currTooltipMode == IndustryTooltipMode.NORMAL
                    && (faction.isHostileTo(Global.getSector().getPlayerFaction()) || !faction.getId().equals(Factions.PIRATES))) {

                tooltip.addPara(StringHelper.getStringAndSubstituteTokens(getId(), "hostile", toReplace), 2f, com.fs.starfarer.api.util.Misc.getNegativeHighlightColor(),
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

        tooltip.addSectionHeading(StringHelper.getString(getId(), "lastMonthHeader"), color, faction.getDarkUIColor(), Alignment.MID, opad);
        tooltip.addPara(StringHelper.getString(getId(), "lastMonthText"), opad);
        //add repair costs here
        for (Map.Entry<FleetMemberAPI, Float> ship : repairedShipsTooltipList.entrySet()) {
            repairCost += ship.getValue();
            feeAmount += getHullSizeRepairFee(ship.getKey().getHullSpec().getHullSize()) * repairedShipsAmountList.get(ship.getKey());

            Map<String, String> toReplace = new HashMap<>();
            toReplace.put("$hullName", ship.getKey().getHullSpec().getHullName());
            toReplace.put("$dModAmt", repairedShipsAmountList.get(ship.getKey()) + " " + StringHelper.getString("dmods"));
            toReplace.put("$amt", com.fs.starfarer.api.util.Misc.getDGSCredits(ship.getValue()));

            tooltip.addPara(BaseIntelPlugin.BULLET + StringHelper.getStringAndSubstituteTokens(getId(), "repairBullet", toReplace), 2f, com.fs.starfarer.api.util.Misc.getHighlightColor(),
                    new String[]{
                            toReplace.get("$hullName"),
                            toReplace.get("$dModAmt"),
                            toReplace.get("$amt")});
        }

        if (!market.isPlayerOwned()) {
            tooltip.addPara(StringHelper.getString(getId(), "commissionFee"), opad, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{com.fs.starfarer.api.util.Misc.getDGSCredits(feeAmount)});
            tooltip.addPara(StringHelper.getString(getId(), "totalRepairCost"), 2F, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{com.fs.starfarer.api.util.Misc.getDGSCredits(repairCost + feeAmount)});
            tooltip.addPara(StringHelper.getString(getId(), "expensesBilled"), 2F);
            return true;
        }

        tooltip.addPara(StringHelper.getString(getId(), "totalRepairCost"), opad, com.fs.starfarer.api.util.Misc.getHighlightColor(), new String[]{com.fs.starfarer.api.util.Misc.getDGSCredits(repairCost)});
        tooltip.addPara(StringHelper.getString(getId(), "expensesDetracted"), 2F);
        return true;
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();
        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "aCoreAssigned" + suffix);
        String coreHighlights = StringHelper.getString(getId(), "aCoreHighlights");
        String effect = StringHelper.getStringAndSubstituteToken(getId(), "aCoreEffect", "$aCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{Integer.toString(ALPHA_CORE_BONUS_REPAIR), StringHelper.getAbsPercentString(ALPHA_CORE_UPKEEP_RED_MULT, true), coreHighlights};

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
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "bCoreAssigned" + suffix);
        String coreHighlights = StringHelper.getString(getId(), "bCoreHighlights");
        String effect = StringHelper.getStringAndSubstituteToken(getId(), "bCoreEffect", "$bCoreHighlights", coreHighlights);
        String[] highlightString = new String[]{StringHelper.getAbsPercentString(BETA_CORE_COST_RED_MULT, true), coreHighlights};

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
        Color highlight = com.fs.starfarer.api.util.Misc.getHighlightColor();

        String suffix = mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? "Short" : "Long";
        String pre = StringHelper.getString("IndEvo_AICores", "gCoreAssigned" + suffix);
        String coreHighlights = StringHelper.getString(getId(), "gCoreHighlights");
        String effect = StringHelper.getStringAndSubstituteToken(getId(), "gCoreEffect", "$gCoreHighlights", coreHighlights);
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

        switch (Misc.getAiCoreIdNotNull(this)) {
            case Commodities.ALPHA_CORE:
                name = StringHelper.getString("IndEvo_AICores", "aCoreStatModAssigned");
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

