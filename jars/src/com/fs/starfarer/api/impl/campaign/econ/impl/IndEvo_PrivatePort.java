package com.fs.starfarer.api.impl.campaign.econ.impl;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

@Deprecated
public class IndEvo_PrivatePort extends BaseIndustry {

    private static void log(String Text) {
        Global.getLogger(IndEvo_PrivatePort.class).info(Text);
    }

    private boolean debug = false;

    public static final float INSURANCE_REDUCTION = 0.7f;
    public static final float DISTANCE_MULT_REDUCTION = 0.8f;
    public static final float TOTAL_FEE_REDUCTION = 0.9f;
    private static final String AI_CORE_ID_STRING = "$IndEvo_CurrentShippingCore";

    public void apply() {
        super.apply(true);
        debug = Global.getSettings().isDevMode();
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public boolean isAvailableToBuild() {
        return Global.getSettings().getBoolean("PrivatePort");
    }

    @Override
    public boolean showWhenUnavailable() {
        return Global.getSettings().getBoolean("PrivatePort");
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        float opad = 10.0F;

        tooltip.addPara("Only the AI core %s in any port is valid for contracts.",
                opad, Misc.getHighlightColor(), new String[]{"last installed"});
    }

    @Override
    protected void addPostUpkeepSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        if (!mode.equals(IndustryTooltipMode.NORMAL)) return;
        addUpcomingShipmentList(tooltip, mode, false);

    }

    public void addUpcomingShipmentList(TooltipMakerAPI tooltip, IndustryTooltipMode mode, boolean expanded) {

        FactionAPI marketFaction = market.getFaction();
        Color color = marketFaction.getBaseUIColor();
        Color dark = marketFaction.getDarkUIColor();

        float opad = 5.0F;

        tooltip.addSectionHeading("Upcoming Shipments", color, dark, Alignment.MID, 10f);

        tooltip.beginTable(marketFaction, 20f, "From", 150f, "To", 150f, "In", 90f);

        int i = 0;
        Map<ShippingContainer, Integer> dayList = getShipmentWithDayList();

        for (Map.Entry<ShippingContainer, Integer> entry : dayList.entrySet()) {
            i++;
            String origin = entry.getKey().getOriginMarket().getName();
            String destination = entry.getKey().getTargetMarket().getName();
            String days = entry.getValue() + " " + BaseIntelPlugin.getDaysString(entry.getValue());

            tooltip.addRow(origin, destination, days);

            if (i > 4 && dayList.size() > 5) break;
        }

        tooltip.addTable("No planned shipments.", 0, opad);

        if (i > 4 && dayList.size() > 5)
            tooltip.addPara("... and " + (dayList.size() - 5) + " more upcoming shipments.",
                    opad, Misc.getHighlightColor(), new String[]{(dayList.size() - 5) + ""});
    }

    private Map<ShippingContainer, Integer> getShipmentWithDayList() {
        List<ShippingContainer> containerList = IndEvo_ShippingManager.getCurrentInstance().getContainerList();
        Map<ShippingContainer, Integer> daymap = new HashMap<>();

        for (ShippingContainer container : containerList) {
            daymap.put(container, container.getDaysUntilDispatch());
        }

        return sort(daymap);
    }

    // TODO: 22.09.2020 this should be a method in industryTools with no set obj, really
    private static Map<ShippingContainer, Integer> sort(Map<ShippingContainer, Integer> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<ShippingContainer, Integer>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<ShippingContainer, Integer>>() {
            public int compare(Map.Entry<ShippingContainer, Integer> o1,
                               Map.Entry<ShippingContainer, Integer> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        // put data from sorted list into a hashmap
        Map<ShippingContainer, Integer> temp = new LinkedHashMap<>();
        for (Map.Entry<ShippingContainer, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    /*AI core effect:
Gamma decrease insurance costs by 30%
Beta decrease cargo fees by 20%
Alpha decrease total shipping costs by 10%
*/

    @Override
    protected void applyAICoreModifiers() {
        super.applyAICoreModifiers();
        //removeAICores();

        setAIcoreMemoryKey();
    }

    public static String getMemoryAICoreId() {
        String id = Global.getSector().getMemoryWithoutUpdate().getString(AI_CORE_ID_STRING);

        return id != null ? id : "none";
    }

    private void setAIcoreMemoryKey() {
        String id = getAICoreId();
        id = id != null ? id : "none";

        Global.getSector().getMemoryWithoutUpdate().set(AI_CORE_ID_STRING, id);
    }

    @Override
    public void addInstalledItemsSection(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();

        LabelAPI heading = tooltip.addSectionHeading("Items", color, dark, Alignment.MID, opad);

        boolean addedSomething = false;
        if (!getMemoryAICoreId().equals("none")) {
            AICoreDescriptionMode aiCoreDescMode = AICoreDescriptionMode.INDUSTRY_TOOLTIP;
            addAICoreSection(tooltip, getMemoryAICoreId(), aiCoreDescMode);
            addedSomething = true;
        }

        addedSomething |= addNonAICoreInstalledItems(mode, tooltip, expanded);

        if (!addedSomething) {
            heading.setText("No items installed");
            //tooltip.addPara("None.", opad);
        }
    }

    //AI-Core tooltips
    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Alpha-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the %s by %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"total shipping costs", (int) Math.round((1 - TOTAL_FEE_REDUCTION) * 100) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + pre + "Decreases the %s by %s", opad, Misc.getPositiveHighlightColor(), new String[]{"total shipping costs", (int) Math.round((1 - TOTAL_FEE_REDUCTION) * 100) + "%"});
        }
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Beta-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the %s by %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"distance multiplier", (int) Math.round((1 - DISTANCE_MULT_REDUCTION) * 100) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases the %s by %s", opad, Misc.getPositiveHighlightColor(), new String[]{"distance multiplier", (int) Math.round((1 - DISTANCE_MULT_REDUCTION) * 100) + "%"});
        }
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        String pre = "Gamma-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + "Decreases the %s by %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"insurance costs", (int) Math.round((1 - INSURANCE_REDUCTION) * 100) + "%"});
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + "Decreases the %s by %s", 0f, Misc.getPositiveHighlightColor(), new String[]{"insurance costs", (int) Math.round((1 - INSURANCE_REDUCTION) * 100) + "%"});
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    public static class Contract {
        public final CargoAPI originalCargo;
        public final boolean isInsured;
        public final float totalShippingCost;
        public final float stackCost;
        public final float shipCost;
        public final float insuranceCost;
        public final float lyMult;

        public Contract(CargoAPI cargo, ShippingContainer container) {
            cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

            this.isInsured = container.isInsured;
            this.totalShippingCost = container.getTotalShippingCost(cargo);
            this.stackCost = container.getBaseStackCargoSpaceCost(cargo);
            this.shipCost = container.getBaseAbstractShipSpaceCost(cargo);
            this.insuranceCost = isInsured ? container.getInsuranceCost(cargo) : 0f;
            this.lyMult = container.getLYMult();
            this.originalCargo = IndEvo_IndustryHelper.getCargoCopy(cargo);
        }
    }

    public static class ShippingContainer {
        public static final Logger log = Global.getLogger(ShippingContainer.class);

        private CargoAPI targetCargo;
        private final FactionAPI faction;

        private MarketAPI originMarket = null;
        private MarketAPI targetMarket = null;

        private Vector2f originLoc = null;
        private Vector2f targetLoc = null;

        private String originSubmarketId = null;
        private String targetSubmarketId = null;
        private int recurrentEveryMonths = 0;
        private int delayByDays = 0;
        private boolean isInsured = false;

        private boolean markedForRemoval = false;

        private int daysMax = 0;
        private int daysPassed = Integer.MAX_VALUE;

        private final float COST_PER_SPACE = 5f;
        private final float COST_PER_SHIP_SPACE = 170f;
        private final float COST_PER_LY_MULT = 250f;
        private final float COST_INSURANCE_PART = 0.1f;
        private final float INSURANCE_REFUND_PART = 0.8f;

        public enum ShippingStatus {
            FAILURE_ORIGIN_LOST,
            FAILURE_ORIGIN_NO_SPACEPORT,
            FAILURE_ORIGIN_SUBMARKET_LOST,

            FAILURE_TARGET_LOST,
            FAILURE_TARGET_NO_SPACEPORT,
            FAILURE_TARGET_SUBMARKET_LOST,

            FAILURE_HOSTILE,
            FAILURE_EMPTY,

            SUCCESS_PARTIAL,
            SUCCESS,

            NULL
        }

        public String getId() {
            return getClass().getName() + hashCode();
        }

        public void setMarkedForRemoval(boolean markedForRemoval) {
            this.markedForRemoval = markedForRemoval;
        }

        public boolean isMarkedForRemoval() {
            return markedForRemoval;
        }

        // TODO: 16/09/2020 change this to return a list of status, to account for simultaneous failure of shit
        public ShippingStatus getShippingStatus(boolean limited) {
            MarketAPI originMarket = getOriginMarket();
            MarketAPI targetMarket = getTargetMarket();

            //check the origin market
            if (originMarket == null || !originMarket.isInEconomy() || !originMarket.getPrimaryEntity().isAlive())
                return ShippingStatus.FAILURE_ORIGIN_LOST;
            if (!originMarket.hasSpaceport()) return ShippingStatus.FAILURE_TARGET_NO_SPACEPORT;
            //if (!originMarket.hasIndustry(Industries.SPACEPORT) || !originMarket.getIndustry(Industries.SPACEPORT).isFunctional()) return ShippingStatus.FAILURE_ORIGIN_NO_SPACEPORT;
            if (!originMarket.hasSubmarket(getOriginSubmarketId())) return ShippingStatus.FAILURE_ORIGIN_SUBMARKET_LOST;

            //check the target market
            if (targetMarket == null || !targetMarket.isInEconomy() || !targetMarket.getPrimaryEntity().isAlive())
                return ShippingStatus.FAILURE_TARGET_LOST;
           /* if (!(targetMarket.hasIndustry(Industries.SPACEPORT) || targetMarket.getIndustry(Industries.SPACEPORT).isFunctional())
                || !(targetMarket.hasIndustry(Industries.MEGAPORT) || targetMarket.getIndustry(Industries.MEGAPORT).isFunctional())) return ShippingStatus.FAILURE_TARGET_NO_SPACEPORT;*/

            if (!targetMarket.hasSpaceport()) return ShippingStatus.FAILURE_TARGET_NO_SPACEPORT;
            if (!targetMarket.hasSubmarket(getTargetSubmarketId())) return ShippingStatus.FAILURE_ORIGIN_SUBMARKET_LOST;

            //check if there is anyone that can actually ship things
            if (originMarket.getFaction().isHostileTo(targetMarket.getFaction())) {
                if (originMarket.getFaction().isHostileTo(Factions.INDEPENDENT) || targetMarket.getFaction().isHostileTo(Factions.INDEPENDENT))
                    return ShippingStatus.FAILURE_HOSTILE;
            }

            if (!limited) {
                //check if the submarket has the wares
                CargoAPI available = getAvailableCargoFromOrigin(false);
                if (available.isEmpty() && available.getMothballedShips().getMembersListCopy().isEmpty())
                    return ShippingStatus.FAILURE_EMPTY;
                if (!isCompleteDelivery()) return ShippingStatus.SUCCESS_PARTIAL;
                if (isCompleteDelivery()) return ShippingStatus.SUCCESS;

                return ShippingStatus.NULL;
            }

            return ShippingStatus.SUCCESS;
        }

        public boolean isCompleteDelivery() {
            //compare av. cargo to target cargo, if not the same, return false
            CargoAPI originCargo = getOriginMarket().getSubmarket(getOriginSubmarketId()).getCargo();

            for (CargoStackAPI stack : getTargetCargo().getStacksCopy()) {
                float stackTargetQuantity = stack.getSize();
                float stackActualQuantity = originCargo.getQuantity(stack.getType(), stack.getData());

                if (stackActualQuantity < stackTargetQuantity) {
                    return false;
                }
            }

            List<String> originShipIds = IndEvo_IndustryHelper.convertFleetMemberListToIdList(originCargo.getMothballedShips().getMembersListCopy());
            List<String> targetShipIds = IndEvo_IndustryHelper.convertFleetMemberListToIdList(getTargetCargo().getMothballedShips().getMembersListCopy());

            return originShipIds.containsAll(targetShipIds);
        }

        public CargoAPI getAvailableCargoFromOrigin(boolean removeWhenFound) {
            log.info("Shipping container getting cargo:");

            CargoAPI sharedCargo = Global.getFactory().createCargo(true);
            sharedCargo.initMothballedShips(faction.getId());

            CargoAPI originCargo = getOriginMarket().getSubmarket(getOriginSubmarketId()).getCargo();
            CargoAPI originCargoCopy = IndEvo_IndustryHelper.getCargoCopy(getOriginMarket().getSubmarket(getOriginSubmarketId()).getCargo());

            for (CargoStackAPI stack : getTargetCargo().getStacksCopy()) {
                float stackTargetQuantity = stack.getSize();
                float stackActualQuantity = originCargoCopy.getQuantity(stack.getType(), stack.getData());

                if (stackActualQuantity < stackTargetQuantity) {
                    log.info("Not enough of " + stack.getDisplayName() + " at " + stackActualQuantity + " for target " + stackTargetQuantity);

                    sharedCargo.addItems(stack.getType(), stack.getData(), stackActualQuantity);
                    originCargoCopy.removeItems(stack.getType(), stack.getData(), stackActualQuantity);

                    if (removeWhenFound) originCargo.removeItems(stack.getType(), stack.getData(), stackActualQuantity);
                } else {
                    log.info("Enough of " + stack.getDisplayName() + " at " + stackActualQuantity + " for target " + stackTargetQuantity);

                    sharedCargo.addItems(stack.getType(), stack.getData(), stackTargetQuantity);
                    originCargoCopy.removeItems(stack.getType(), stack.getData(), stackTargetQuantity);

                    if (removeWhenFound) originCargo.removeItems(stack.getType(), stack.getData(), stackTargetQuantity);
                }
            }

            //ships
            for (FleetMemberAPI targetShip : getTargetCargo().getMothballedShips().getMembersListCopy()) {
                String s = "failed";

                String baseSpecHullId = getBaseShipHullSpec(targetShip.getHullSpec()).getHullId();

                for (FleetMemberAPI member : originCargoCopy.getMothballedShips().getMembersListCopy()) {
                    boolean target = member.getHullId().equals(targetShip.getHullId());
                    boolean base = member.getHullId().equals(baseSpecHullId);

                    if (target || base) {
                        sharedCargo.getMothballedShips().addFleetMember(member);
                        originCargoCopy.getMothballedShips().removeFleetMember(member);

                        if (removeWhenFound) removeAnalogueFleetMemberFromCargo(originCargo, member);
                        s = "success";
                        break;
                    }
                }


                log.info("Checking ship : " + targetShip.getHullId() + " - " + s);
            }

            sharedCargo.removeEmptyStacks();
            return sharedCargo;
        }

        private void removeAnalogueFleetMemberFromCargo(CargoAPI cargo, FleetMemberAPI targetMember) {
            String baseSpecHullId = getBaseShipHullSpec(targetMember.getHullSpec()).getHullId();

            for (FleetMemberAPI member : cargo.getMothballedShips().getMembersListCopy()) {
                boolean target = member.getHullId().equals(targetMember.getHullId());
                boolean base = member.getHullId().equals(baseSpecHullId);

                if (target || base) {
                    cargo.getMothballedShips().removeFleetMember(member);
                    break;
                }
            }
        }

        private ShipHullSpecAPI getBaseShipHullSpec(ShipHullSpecAPI spec) {
            ShipHullSpecAPI base = spec.getDParentHull();

            if (!spec.isDefaultDHull() && !spec.isRestoreToBase()) {
                base = spec;
            }

            if (spec.isRestoreToBase()) {
                base = spec.getBaseHull();
            }

            return base;
        }

        public static CargoAPI getMissingCargoCargo(CargoAPI original, CargoAPI current, boolean withFleet) {
            log.info("Shipping container getting missing cargo:");
            CargoAPI missingCargo = Global.getFactory().createCargo(true);

            missingCargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            current.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            original.initMothballedShips(Global.getSector().getPlayerFaction().getId());

            for (CargoStackAPI stack : original.getStacksCopy()) {
                float stackActualQuantity = current.getQuantity(stack.getType(), stack.getData());
                float stackTargetQuantity = stack.getSize();

                if (stackActualQuantity < stackTargetQuantity) {
                    log.info("Not enough of " + stack.getDisplayName() + " at " + stackActualQuantity + " for target " + stackTargetQuantity);

                    missingCargo.addItems(stack.getType(), stack.getData(), stackTargetQuantity - stackActualQuantity);
                }
            }

            if (withFleet) {
                List<String> mothBalledShipList = IndEvo_IndustryHelper.convertFleetMemberListToIdList(current.getMothballedShips().getMembersListCopy());
                for (FleetMemberAPI member : original.getMothballedShips().getMembersListCopy()) {
                    String id = member.getHullId();
                    String result = "success";
                    if (!mothBalledShipList.contains(id)) {
                        missingCargo.getMothballedShips().addFleetMember(IndEvo_IndustryHelper.createFleetMemberClone(member));
                        result = "failed";
                    }

                    log.info("Checking for " + member.getHullId() + " - " + result);
                }
            }

            missingCargo.removeEmptyStacks();
            return missingCargo;
        }

        public float getInsuranceRefundPart() {
            return INSURANCE_REFUND_PART;
        }

        public ShippingContainer() {
            this.faction = Global.getSector().getPlayerFaction();
        }

        public CargoAPI getTargetCargo() {
            if (targetCargo == null) {
                this.targetCargo = Global.getFactory().createCargo(true);
            }

            this.targetCargo.initMothballedShips(faction.getId());
            return targetCargo;
        }

        public CargoAPI loadActualCargoFromOrigin() {
            return getAvailableCargoFromOrigin(true);
        }

        public boolean isReadyForDispatch() {
            return daysPassed >= daysMax && !isDelayed();
        }

        public int getDaysUntilDispatch() {
            return Math.max(daysMax - daysPassed, 0) + delayByDays;

            //1 day passed - delay by 14 - 1 max = 1- (1-14)
        }

        public void notifyDayPassed() {
            if (isDelayed()) {
                delayByDays--;
                return;
            }

            daysPassed++;
        }

        public void setDaysPassed(int days) {
            daysPassed = days;
        }

        public boolean isSingleShipment() {
            return getRecurrentEveryMonths() == 0;
        }

        public void setTargetSubmarketId(String targetSubmarketId) {
            this.targetSubmarketId = targetSubmarketId;
        }

        public void setTargetMarket(MarketAPI targetMarket) {
            this.targetMarket = targetMarket;
            this.targetLoc = targetMarket.getStarSystem().getLocation();
        }

        public MarketAPI getTargetMarket() {
            return targetMarket;
        }

        public String getTargetSubmarketId() {
            return targetSubmarketId;
        }

        public void setOriginMarket(MarketAPI originMarket) {
            this.originMarket = originMarket;
            this.originLoc = originMarket.getStarSystem().getLocation();
        }

        public MarketAPI getOriginMarket() {
            return originMarket;
        }

        public void setOriginSubmarketId(String originSubmarketId) {
            this.originSubmarketId = originSubmarketId;
        }

        public String getOriginSubmarketId() {
            return originSubmarketId;
        }

        public int getRecurrentEveryMonths() {
            return recurrentEveryMonths;
        }

        public void setRecurrentEveryMonths(int recurrentEveryMonths) {
            this.recurrentEveryMonths = recurrentEveryMonths;
            daysMax = 31 * recurrentEveryMonths;
        }

        public void setInsured(boolean insured) {
            isInsured = insured;
        }

        public boolean isInsured() {
            return isInsured;
        }

        public int getDelayByDays() {
            return delayByDays;
        }

        public void setDelayByDays(int delayByDays) {
            this.delayByDays = delayByDays;
        }

        public boolean isDelayed() {
            return delayByDays > 0;
        }

        public float getTotalShippingCost(CargoAPI cargo) {
            float total = 1000f;

            total += getBaseStackCargoSpaceCost(cargo);
            total += getBaseAbstractShipSpaceCost(cargo);

            total *= getLYMult();

            if (isInsured) total += getInsuranceCost(cargo);

            //ai cores
            float red = IndEvo_PrivatePort.getMemoryAICoreId().equals(Commodities.ALPHA_CORE) ? IndEvo_PrivatePort.TOTAL_FEE_REDUCTION : 1f;
            total *= red;

            //round to nearest hundred
            total = Math.round(total / 100) * 100;

            return total;
        }

        public float getLYMult() {
            float ly = getTransportDistance();
            float lyMult = (float) Math.round((1f + ((ly * Math.pow(1000f, 1f + (ly / COST_PER_LY_MULT))) / 10000f)) * 10f) / 10f;

            float red = IndEvo_PrivatePort.getMemoryAICoreId().equals(Commodities.GAMMA_CORE) ? IndEvo_PrivatePort.DISTANCE_MULT_REDUCTION : 1f;
            lyMult *= red;

            return lyMult;
        }

        public int getTransportDistance() {
            StarSystemAPI originSystem = getOriginMarket().getStarSystem();
            StarSystemAPI targetSystem = getTargetMarket().getStarSystem();

            if (originSystem == null || targetSystem == null) {
                return Math.round(Misc.getDistanceLY(originLoc, targetLoc));
            }

            return Math.round(Misc.getDistanceLY(originSystem.getLocation(), targetSystem.getLocation()));
        }

        public float getBaseAbstractShipSpaceCost(CargoAPI cargo) {
            float totalValue = 0f;
            cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

            if (!cargo.getMothballedShips().getMembersListCopy().isEmpty()) {

                for (FleetMemberAPI s : cargo.getMothballedShips().getMembersListCopy()) {
                    totalValue += s.getHullSpec().getFleetPoints();
                }
            }

            return totalValue * COST_PER_SHIP_SPACE;
        }

        public float getBaseStackCargoSpaceCost(CargoAPI cargo) {
            float totalSpace = 0f;

            for (CargoStackAPI s : cargo.getStacksCopy()) {
                totalSpace += getStackSpace(s);
            }

            return totalSpace * COST_PER_SPACE;
        }

        public float getCostForStack(CargoStackAPI stack) {
            return (getStackSpace(stack) * COST_PER_SPACE);
        }

        public float getStackSpace(CargoStackAPI stack) {
            float space = stack.getCargoSpace();

            boolean isCrewOrFuel = stack.isPersonnelStack() || stack.isFuelStack();
            space = isCrewOrFuel ? stack.getSize() * 0.75f : space;

            return space;
        }

        public float getInsuranceCost(CargoAPI cargo) {
            float total = 100f;
            total += getValue(cargo);
            total = Math.round(total / 100) * 100;

            float red = IndEvo_PrivatePort.getMemoryAICoreId().equals(Commodities.GAMMA_CORE) ? IndEvo_PrivatePort.INSURANCE_REDUCTION : 1f;
            total *= IndEvo_ShippingManager.getFraudCounter();

            return total * COST_INSURANCE_PART * red;
        }

        public float getValue(CargoAPI cargo) {
            float total = 0;

            total += getShipValue(cargo);
            total += getStackValue(cargo);

            return total;
        }

        public float getShipValue(CargoAPI cargo) {
            cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
            float totalValue = 0f;

            for (FleetMemberAPI s : cargo.getMothballedShips().getMembersListCopy()) {
                totalValue += s.getBaseSellValue();
            }

            return totalValue;
        }

        public float getStackValue(CargoAPI cargo) {
            float totalValue = 0f;

            for (CargoStackAPI s : cargo.getStacksCopy()) {
                totalValue += s.getSize() * s.getBaseValuePerUnit();
            }

            return totalValue;
        }


    }
}