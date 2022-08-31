package com.fs.starfarer.api.impl.campaign.intel.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_IndustryHelper;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_PrivatePort;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingFleetAssignmentAI;
import com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager.getFraudCounter;
import static com.fs.starfarer.api.impl.campaign.fleets.IndEvo_ShippingManager.incrementFraudCounter;

@Deprecated
public class IndEvo_ShippingFleetIntel extends BaseIntelPlugin implements FleetEventListener {

    public static final Object LAUNCHED_UPDATE = new Object();
    public static final Object ENDED_UPDATE = new Object();

    public static final String FRAUD_TIME_KEY = "$IndEvo_FraudCommitted";
    public static final String FRAUD_INSTANCES_KEY = "$IndEvo_FraudCommitted";
    public static final int FRAUD_TIME_DAYS = 3 * 31;

    public static final Logger log = Global.getLogger(IndEvo_ShippingFleetIntel.class);
    public boolean debug = false;

    protected String factionId;
    protected String targetFactionId;
    protected final MarketAPI origin;
    protected final MarketAPI target;

    protected final SubmarketAPI originSub;
    protected final SubmarketAPI targetSub;

    protected boolean insured;

    protected IndEvo_ShippingFleetIntel.EndReason endReason;
    protected boolean assembling = true;
    protected boolean hasLostCargo = false;

    protected float daysToLaunch;
    protected final RouteManager.RouteData route;
    protected CampaignFleetAPI fleet = null;

    protected CargoAPI fleetCargo;
    protected CargoAPI targetCargo;

    protected final IndEvo_PrivatePort.ShippingContainer container;
    protected final IndEvo_PrivatePort.Contract contract;

    private int originalFP = 0;
    private float lastPC = 1f;
    boolean isCargoInit = false;

    public IndEvo_PrivatePort.ShippingContainer getContainer() {
        return container;
    }

    public IndEvo_ShippingFleetIntel(IndEvo_PrivatePort.ShippingContainer container, RouteManager.RouteData route) {
        log.info("New Shipping intel registered");

        this.origin = container.getOriginMarket();
        this.target = container.getTargetMarket();

        this.originSub = origin.getSubmarket(container.getOriginSubmarketId());
        this.targetSub = origin.getSubmarket(container.getTargetSubmarketId());

        factionId = origin.getFactionId();
        targetFactionId = target.getFactionId();
        insured = container.isInsured();

        IndEvo_ShippingFleetAssignmentAI.EconomyRouteData data = (IndEvo_ShippingFleetAssignmentAI.EconomyRouteData) route.getCustom();
        CargoAPI baseCargo = data.cargo;
        this.fleetCargo = baseCargo;
        this.targetCargo = IndEvo_IndustryHelper.getCargoCopy(baseCargo);

        this.contract = new IndEvo_PrivatePort.Contract(baseCargo, container);
        this.container = container;
        this.route = route;
    }

    private void changeFleetFactionIfNeeded() {
        if (fleet != null) {
            //set to independent if the factions are hostile
            if (fleet.getFaction().isHostileTo(targetFactionId)) {
                if (!fleet.getFaction().isHostileTo(Factions.INDEPENDENT) &&
                        !target.getFaction().isHostileTo(Factions.INDEPENDENT)) {
                    factionId = Factions.INDEPENDENT;
                }
            }
        }
    }

    @Override
    public void advanceImpl(float amount) {
        debug = Global.getSettings().isDevMode();

        if (isEnding() || isEnded()) return;

        if (assembling) {
            daysToLaunch -= Global.getSector().getClock().convertToDays(amount);
            if (daysToLaunch <= 0) assembling = false;
        }

        if (route.getCurrentSegmentId() == 4) {
            endEvent(EndReason.COMPLETE);
            return;
        }

        checkAbort();

        if (isEnding() || isEnded()) return;

        if (fleet == null && route.getActiveFleet() != null) {
            setFleet();
            originalFP = route.getActiveFleet().getFleetPoints();
        } else if (fleet != null && !fleet.isAlive() && route.getActiveFleet() != null && route.getActiveFleet().isAlive()) {
            setFleet();
        } else if (fleet != null && !fleet.isAlive()) {
            endEvent(EndReason.DEFEATED);
            return;
        }

        setFleetCargo();
        changeFleetFactionIfNeeded();
        targetFactionId = target.getFactionId();
    }

    //first cargo set-up with clearing the ships from the fleet cargo, then cycle them
    private void setFleetCargo() {
        if (fleet != null && fleet.isAlive()) {
            if (!isCargoInit) {
                translateMothballedFleetMembersFromCargo();
                isCargoInit = true;
            } else {
                translateMothballedFleetMembersToCargo();
                translateMothballedFleetMembersFromCargo();
            }
        }
    }

    // bullet points
    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.addPara(getName(), c, 0f);
        bullet(info);

        FactionAPI targetFaction = getTargetFaction();
        boolean over = isEnding() || isEnded();

        float initPad = 3, pad = 0;
        Color tc = getBulletColorForMode(mode);
        String name = Misc.ucFirst(targetFaction.getDisplayName());
        info.addPara(name, initPad, tc, targetFaction.getBaseUIColor(), name);

        if (over) {
            String str = "";
            switch (endReason) {
                case FAILED_TO_SPAWN:
                    str = "Fleet failed to assemble";
                    break;
                case TARGET_DESTROYED:
                    str = "Cancelled: Destination not reachable";
                    break;
                case HOSTILE:
                    str = "Cancelled: Hostile";
                    break;
                case DEFEATED:
                    str = "Fleet defeated";
                    break;
                case COMPLETE:
                    str = "Delivery Successful";
                    break;
            }

            info.addPara(str, pad, tc);
        } else if (listInfoParam == LAUNCHED_UPDATE) {
            info.addPara("Fleet launched from %s", pad, getFaction().getBaseUIColor(), origin.getName());
        } else if (assembling) {
            LabelAPI label = info.addPara("Launching from %s in %s", pad, tc, new String[]{origin.getName(), getDays(daysToLaunch) + " " + getDaysString(daysToLaunch)});
            label.setHighlight(origin.getName(), getDays(daysToLaunch) + " " + getDaysString(daysToLaunch));
            label.setHighlightColors(getFaction().getBaseUIColor(), Misc.getHighlightColor());
        }

        // show ETA
        if (!over) {
            int eta = getETA();
            if (eta >= 1) {
                info.addPara("Estimated %s until arrival", pad, tc, eta + " " + getDaysString(eta));
            }
        }
    }

    // sidebar text description
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        FactionAPI faction = getFaction();
        FactionAPI otherFaction = getTargetFaction();
        Color hl = Misc.getHighlightColor();
        Map<String, String> sub = new HashMap<>();

        info.addImages(width, 128, opad, opad, faction.getCrest(), otherFaction.getCrest());

        info.addSectionHeading("Current Status", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);

        if (isEnding() || isEnded()) {
            switch (endReason) {
                case FAILED_TO_SPAWN:
                    info.addPara("The courier fleet has failed to assemble at %s.", opad, faction.getBaseUIColor(), container.getOriginMarket().getName());
                    break;
                case DEFEATED:
                    info.addPara("The courier fleet has been destroyed or otherwise prevented from reaching its destination.", opad);
                    break;
                case TARGET_DESTROYED:
                    info.addPara("%s, or the target delivery point, can no longer be reached by the courier fleet.", opad, otherFaction.getBaseUIColor(), container.getTargetMarket().getName());
                    break;
                case HOSTILE:
                    LabelAPI label = info.addPara(faction.getDisplayNameWithArticle() + faction.getDisplayNameIsOrAre() + " hostile to " + otherFaction.getDisplayNameWithArticle() + " and the Independent. The courier fleet has been recalled.", opad);
                    label.setHighlight(faction.getDisplayNameWithArticle(), otherFaction.getDisplayNameWithArticle());
                    label.setHighlightColors(faction.getBaseUIColor(), otherFaction.getBaseUIColor());
                    break;
                case COMPLETE:
                    info.addPara("The cargo has successfully been delivered to %s.", opad, otherFaction.getBaseUIColor(), container.getTargetMarket().getName());
                    break;
                case OTHER:
                    info.addPara("The delivery has been called off for unspecified reasons.", opad);
                    break;
            }
        } else {
            if (assembling) {
                String launchStr = getDays(daysToLaunch) + " " + getDaysString(daysToLaunch);
                LabelAPI label = info.addPara("The fleet is currently being prepared and will launch from " + origin.getName() + " in " + launchStr, opad);
                label.setHighlight(origin.getName(), launchStr);
                label.setHighlightColors(faction.getBaseUIColor(), hl);
            } else {
                switch (route.getCurrentSegmentId()) {
                    case 1:
                        info.addPara("The courier fleet is currently loading cargo at %s.", opad, faction.getBaseUIColor(), container.getOriginMarket().getName());
                        break;
                    case 2:
                        info.addPara("The courier fleet is currently travelling to %s.", opad, otherFaction.getBaseUIColor(), container.getTargetMarket().getName());
                        if (getETA() >= 1)
                            info.addPara("It will arrive in approximately %s.", opad, hl, getETA() + " " + getDaysString(getETA()));
                        break;
                    case 3:
                        info.addPara("The courier fleet is currently unloading cargo at %s.", opad, otherFaction.getBaseUIColor(), container.getTargetMarket().getName());
                        break;
                    case 4:
                        info.addPara("The courier fleet is currently disbanding at %s.", opad, otherFaction.getBaseUIColor(), container.getTargetMarket().getName());
                        break;
                    default:
                        info.addPara("The delivery has finished and the fleet has disbanded.", opad);
                        break;
                }
            }
        }

        addContractTooltip(info, container);
        addCargoTooltip(info, container, targetCargo);

        translateMothballedFleetMembersToCargo();

        if (hasLostCargo) addMissingCargoTooltip(info);

        translateMothballedFleetMembersFromCargo();
    }

    private void addContractTooltip(TooltipMakerAPI tooltip, IndEvo_PrivatePort.ShippingContainer container) {
        CargoAPI cargo = contract.originalCargo;

        SubmarketAPI originsubmarket = container.getOriginMarket().getSubmarket(container.getOriginSubmarketId());
        originsubmarket = originsubmarket == null ? originSub : originsubmarket;

        SubmarketAPI targetsubmarket = container.getTargetMarket().getSubmarket(container.getTargetSubmarketId());
        targetsubmarket = targetsubmarket == null ? targetSub : targetsubmarket;

        float pad = 5f;
        float opad = 10f;

        String originNameString = container.getOriginMarket().getName();
        String targetNameString = container.getTargetMarket().getName();

        int recurrentMonths = container.getRecurrentEveryMonths();

        tooltip.addSectionHeading("Contract information:", getFaction().getBaseUIColor(), getFaction().getDarkUIColor(), Alignment.MID, opad);
        tooltip.setParaFont(Fonts.DEFAULT_SMALL);

        tooltip.addPara("From: " + originNameString + ", " + originsubmarket.getNameOneLine(),
                opad,
                container.getOriginMarket().getTextColorForFactionOrPlanet(),
                originNameString);

        tooltip.addPara("To:     " + targetNameString + ", " + targetsubmarket.getNameOneLine() + " (" + container.getTransportDistance() + " LY)",
                2f,
                container.getTargetMarket().getTextColorForFactionOrPlanet(),
                targetNameString);

        String recurrent = recurrentMonths == 0 ? "As a single delivery." : "Once every " + recurrentMonths + " months,";
        tooltip.addPara(recurrent, pad, Misc.getHighlightColor(), recurrentMonths + "");

        String insurance = insured ? "Insured shipment - %s refund on loss (%s)." : "No Insurance.";
        tooltip.addPara(insurance, pad, Misc.getHighlightColor(), new String[]{Misc.getDGSCredits(container.getValue(cargo)), (int) Math.round(container.getInsuranceRefundPart() * 100) + "%"});

        tooltip.addPara("Total cost (rounded): %s", pad, Misc.getPositiveHighlightColor(), Misc.getDGSCredits(contract.totalShippingCost));
        tooltip.beginGridFlipped(300, 1, 60f, 3f);
        tooltip.addToGrid(0, 0, "Base fee", Misc.getDGSCredits(1000));
        tooltip.addToGrid(0, 1, "Base cargo cost", Misc.getDGSCredits(contract.stackCost));
        tooltip.addToGrid(0, 2, "Base ship cost", Misc.getDGSCredits(contract.shipCost));
        tooltip.addToGrid(0, 3, "Distance multiplier", "x" + contract.lyMult);
        if (contract.isInsured) {
            tooltip.addToGrid(0, 4, "Insurance Cost", Misc.getDGSCredits(contract.insuranceCost));
        }
        tooltip.addGrid(pad);

        tooltip.setParaFontDefault();
    }

    private void addCargoTooltip(TooltipMakerAPI tooltip, IndEvo_PrivatePort.ShippingContainer container, CargoAPI cargo) {
        SubmarketAPI targetsubmarket = container.getTargetMarket().getSubmarket(container.getTargetSubmarketId());
        targetsubmarket = targetsubmarket == null ? targetSub : targetsubmarket;

        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        boolean showInFleetScreen = targetsubmarket.getPlugin().showInFleetScreen();
        boolean showInCargoScreen = targetsubmarket.getPlugin().showInCargoScreen();
        float pad = 5f;
        float opad = 10f;

        tooltip.addSectionHeading("Shipment information:", getFaction().getBaseUIColor(), getFaction().getDarkUIColor(), Alignment.MID, opad);
        tooltip.setParaFont(Fonts.DEFAULT_SMALL);

        tooltip.addPara("Cargo to be shipped:", opad);

        if (showInCargoScreen && !cargo.isEmpty()) {
            tooltip.beginGridFlipped(300, 1, 80f, 10f);
            //panel.beginGrid(150f, 1);
            int i = 0;
            cargo.sort();
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                tooltip.addToGrid(0, i, stack.getDisplayName(), (int) stack.getSize() + " units");
                i++;

                if (i > 4) {
                    break;
                }
            }
            tooltip.addGrid(pad);
            if (i > 4 && cargo.getStacksCopy().size() > 5) {
                tooltip.addPara("... and " + (cargo.getStacksCopy().size() - 5) + " additional types of cargo.", 10f);
            }
        } else if (showInCargoScreen) {
            tooltip.addPara("    None - No cargo", 3f);
        } else {
            tooltip.addPara("    None - The destination storage does not allow cargo.", 3f);
        }

        tooltip.addPara("Ship Hulls to be transported:", 10f);

        if (showInFleetScreen && !cargo.getMothballedShips().getMembersListCopy().isEmpty()) {

            tooltip.beginGridFlipped(300, 1, 40f, 3f);
            //panel.beginGrid(150f, 1);
            int i = 0;

            Map<ShipHullSpecAPI, Integer> shipMap = new HashMap<>();
            for (FleetMemberAPI ship : cargo.getMothballedShips().getMembersListCopy()) {
                ShipHullSpecAPI hs = ship.getHullSpec();
                if (shipMap.containsKey(hs)) {
                    shipMap.put(hs, shipMap.get(hs) + 1);
                } else {
                    shipMap.put(hs, 1);
                }
            }

            for (Map.Entry<ShipHullSpecAPI, Integer> entry : shipMap.entrySet()) {
                tooltip.addToGrid(0, i, entry.getKey().getHullNameWithDashClass() + " hull", entry.getValue() + "");
                i++;

                if (i > 4) {
                    break;
                }
            }
            tooltip.addGrid(pad);
            if (i > 4 && shipMap.size() > 5) {
                tooltip.addPara("... and " + (shipMap.size() - 5) + " additional ships.", 3f);
            }
        } else if (showInFleetScreen) {
            tooltip.addPara("    None - No ships.", 3f);
        } else {
            tooltip.addPara("    None - The destination storage does not allow ships.", 3f);
        }

        tooltip.setParaFontDefault();
    }

    private void addMissingCargoTooltip(TooltipMakerAPI tooltip) {
        CargoAPI cargo = IndEvo_PrivatePort.ShippingContainer.getMissingCargoCargo(contract.originalCargo, targetCargo, true);

        float pad = 5f;
        float opad = 10f;

        tooltip.addSectionHeading("Lost cargo", getFaction().getBaseUIColor(), getFaction().getDarkUIColor(), Alignment.MID, opad);
        tooltip.addPara("The courier fleet was attacked and has lost the following:", opad);

        tooltip.setParaFont(Fonts.DEFAULT_SMALL);

        tooltip.addPara("Lost Cargo:", opad);

        if (!cargo.isEmpty()) {
            tooltip.beginGridFlipped(300, 1, 80f, 10f);
            //panel.beginGrid(150f, 1);
            int i = 0;
            cargo.sort();
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                tooltip.addToGrid(0, i, stack.getDisplayName(), (int) stack.getSize() + " units");
                i++;
            }
            tooltip.addGrid(pad);

        } else {
            tooltip.addPara("    None - No cargo lost.", 3f);
        }

        tooltip.addPara("Lost Ship Hulls:", 10f);

        if (!cargo.getMothballedShips().getMembersListCopy().isEmpty()) {

            tooltip.beginGridFlipped(300, 1, 40f, 3f);
            //panel.beginGrid(150f, 1);
            int i = 0;

            Map<ShipHullSpecAPI, Integer> shipMap = new HashMap<>();
            for (FleetMemberAPI ship : cargo.getMothballedShips().getMembersListCopy()) {
                ShipHullSpecAPI hs = ship.getHullSpec();
                if (shipMap.containsKey(hs)) {
                    shipMap.put(hs, shipMap.get(hs) + 1);
                } else {
                    shipMap.put(hs, 1);
                }
            }

            for (Map.Entry<ShipHullSpecAPI, Integer> entry : shipMap.entrySet()) {
                tooltip.addToGrid(0, i, entry.getKey().getHullNameWithDashClass() + " hull", entry.getValue() + "");
                i++;
            }

            tooltip.addGrid(pad);
        } else {
            tooltip.addPara("    None - No ships lost.", 3f);
        }

        tooltip.setParaFontDefault();
    }

    public void checkAbort() {
        IndEvo_PrivatePort.ShippingContainer.ShippingStatus status = container.getShippingStatus(true);

        switch (status) {
            case FAILURE_ORIGIN_LOST:
            case FAILURE_ORIGIN_SUBMARKET_LOST:
                if (assembling) {
                    endEvent(EndReason.FAILED_TO_SPAWN);
                    new Deliver(this, origin).run();
                }
                container.setMarkedForRemoval(true);
                break;
            case FAILURE_ORIGIN_NO_SPACEPORT:
            case FAILURE_EMPTY:
                if (assembling) {
                    new Deliver(this, origin).run();
                }

                endEvent(EndReason.FAILED_TO_SPAWN);
                break;
            case FAILURE_TARGET_LOST:
            case FAILURE_TARGET_SUBMARKET_LOST:
                if (assembling) {
                    new Deliver(this, origin).run();
                } else {
                    returnShipmentTo(origin);
                }

                endEvent(EndReason.TARGET_DESTROYED);
                container.setMarkedForRemoval(true);
                break;
            case FAILURE_TARGET_NO_SPACEPORT:
                if (assembling) {
                    new Deliver(this, origin).run();
                } else {
                    returnShipmentTo(origin);
                }

                endEvent(EndReason.TARGET_DESTROYED);
                returnShipmentTo(origin);
                break;
            case FAILURE_HOSTILE:
                if (assembling) {
                    new Deliver(this, origin).run();
                } else {
                    returnShipmentTo(origin);
                }

                endEvent(EndReason.HOSTILE);
                break;
            case NULL:
                endEvent(EndReason.OTHER);
                break;
        }
    }

    private CargoAPI getMissingCargo() {
        CargoAPI missingCargo = IndEvo_PrivatePort.ShippingContainer.getMissingCargoCargo(targetCargo, fleetCargo, true);

        if (missingCargo.getMothballedShips().getMembersListCopy().isEmpty() && missingCargo.getStacksCopy().isEmpty())
            return null;

        //set new target cargo if it lost shit
        hasLostCargo = true;
        targetCargo = IndEvo_IndustryHelper.getCargoCopy(fleetCargo);

        return missingCargo;
    }

    public void translateMothballedFleetMembersFromCargo() {
        if (fleet == null) return;
        fleetCargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isMothballed()) {
                fleet.getFleetData().removeFleetMember(member);
            }
        }

        for (FleetMemberAPI member : fleetCargo.getMothballedShips().getMembersListCopy()) {
            fleet.getFleetData().addFleetMember(member);
            member.getRepairTracker().setMothballed(true);
        }

        fleet.getFleetData().sort();
    }

    private void translateMothballedFleetMembersToCargo() {
        if (fleet == null) return;

        CargoAPI cargo = fleet.getCargo();
        cargo.initMothballedShips(fleet.getFaction().getId());
        cargo.getMothballedShips().clear();

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isMothballed()) {
                cargo.getMothballedShips().addFleetMember(member);
            }
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

        if (battle.isPlayerInvolved()) {
            //place active mothballed ahips back into fleet cargo
            translateMothballedFleetMembersToCargo();

            //get missing cargo
            CargoAPI missingCargo = getMissingCargo();
            if (missingCargo == null) return;

            float val = container.getValue(missingCargo) * container.getInsuranceRefundPart();

            if (insured) {
                if (checkForFraud(battle, fleet)) {
                    fraudConsequences();
                    return;
                }

                insurancePayout(val);
            }

            cargoLostNotification(val, missingCargo);

            //update fleet members
            translateMothballedFleetMembersFromCargo();
        } else {
            float currentFP = fleet.getFleetPoints();
            log.info("originalFP " + originalFP);
            log.info("currentFP " + currentFP);

            //last PC = 40 (reduction by 60%)
            //Current = 20 (reduction by 20% from total, reduction by 50% from current)
            float percentFromOrig = currentFP / originalFP;

            if (percentFromOrig < lastPC) {
                float cargoRedPerc = percentFromOrig / lastPC;

                log.info("cargoRedPerc " + cargoRedPerc);

                translateMothballedFleetMembersToCargo();
                reduceFleetCargoByPercent(cargoRedPerc, fleet);
                CargoAPI missingCargo = getMissingCargo();

                lastPC = percentFromOrig;

                if (missingCargo == null) return;

                float val = container.getValue(missingCargo) * container.getInsuranceRefundPart();

                log.info("Missing cargo money" + val);

                if (insured) {
                    insurancePayout(val);
                }

                cargoLostNotification(val, missingCargo);

                //update fleet members
                translateMothballedFleetMembersFromCargo();
            }
        }
    }

    private void reduceFleetCargoByPercent(float delta, CampaignFleetAPI fleet) {
        //cargo space * delta
        targetCargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        CargoAPI fleetCargo = fleet.getCargo();
        fleetCargo.initMothballedShips(fleet.getFaction().getId());

        Random random = new Random();

        for (CargoStackAPI stack : fleetCargo.getStacksCopy()) {
            float stackSize = stack.getSize();
            float stackTargetQuantity = stackSize * delta;

            //if it's a single item, completely remove it with a chance depending on reduction amt
            if ((int) stackSize == 1) {
                if (random.nextFloat() > delta) {
                    stackTargetQuantity = 0;
                } else {
                    stackTargetQuantity = 1;
                }
            }

            log.info("reducing " + stack.getDisplayName() + " from " + stackSize + " to " + stackTargetQuantity);

            //reduce stack amt
            fleetCargo.removeStack(stack);

            if (stackTargetQuantity >= 1f) {
                fleetCargo.addItems(stack.getType(), stack.getData(), Math.round(stackTargetQuantity));
            }
        }

        fleetCargo.removeEmptyStacks();
    }

    private void printCargoContentsToLog(CargoAPI cargoAPI) {
        cargoAPI.initMothballedShips("player");

        for (CargoStackAPI stack : cargoAPI.getStacksCopy()) {
            log.info(stack.getDisplayName() + " amt: " + stack.getSize());
        }

        for (FleetMemberAPI member : cargoAPI.getMothballedShips().getMembersListCopy()) {
            log.info(member.getHullId());
        }
    }

    private void cargoLostNotification(Float val, CargoAPI cargo) {
        if (debug) printCargoContentsToLog(cargo);

        String s = IndEvo_ShippingFleetAssignmentAI.EconomyRouteData.getCargoList(cargo);
        String pre = insured ? "An insured" : "A";

        MessageIntel intel = new MessageIntel(pre + " courier fleet has been %s.", Misc.getTextColor(), new String[]{"attacked"}, Misc.getNegativeHighlightColor());
        intel.addLine(s + " have been lost.", Misc.getTextColor(), new String[]{"items", "ships"}, Misc.getHighlightColor());
        if (insured)
            intel.addLine("You have received a payout of %s.", Misc.getTextColor(), new String[]{Misc.getDGSCredits(val)}, Misc.getHighlightColor());
        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "notification"));
        intel.setSound(BaseIntelPlugin.getSoundMajorPosting());
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.INTEL_TAB, this);
    }

    private void insurancePayout(float val) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(val);
    }

    private boolean checkForFraud(BattleAPI battle, CampaignFleetAPI fleet) {
        boolean fraud = false;

        if (battle.isPlayerInvolved()) {
            if (battle.getSideFor(fleet) != battle.getSideFor(Global.getSector().getPlayerFleet())) {
                if (battle.knowsWhoPlayerIs(battle.getNonPlayerSide())) fraud = true;
            }
        }

        return fraud;
    }

    private void fraudConsequences() {
        Global.getSector().getMemoryWithoutUpdate().set(FRAUD_TIME_KEY, FRAUD_TIME_DAYS);
        this.insured = false;

        //wipe insurance from ongoing
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(getClass())) {
            if (intel instanceof IndEvo_ShippingFleetIntel) {
                ((IndEvo_ShippingFleetIntel) intel).setInsured(false);
            }
        }

        //wipe insurance from upcoming
        for (IndEvo_PrivatePort.ShippingContainer container : IndEvo_ShippingManager.getCurrentInstance().getContainerList()) {
            container.setInsured(false);
        }

        //increment fraud counter
        incrementFraudCounter();

        //fuck you
        MessageIntel intel = new MessageIntel("You have been caught committing %s.", Misc.getTextColor(), new String[]{"insurance fraud"}, Misc.getNegativeHighlightColor());
        intel.addLine("All ongoing contracts are %s.", Misc.getTextColor(), new String[]{"no longer insured"}, Misc.getHighlightColor());
        intel.addLine("You are %s from insuring shipments for %s.", Misc.getTextColor(), new String[]{"banned", FRAUD_TIME_DAYS + " months"}, Misc.getHighlightColor());
        intel.addLine("Insurance costs increased by .", Misc.getTextColor(), new String[]{"factor " + getFraudCounter()}, Misc.getHighlightColor());
        intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "warning"));
        Global.getSoundPlayer().playUISound("ui_rep_drop", 1, 1);
        Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.INTEL_TAB, this);
    }

    public RouteManager.RouteData getRoute() {
        return route;
    }

    public MarketAPI getOrigin() {
        return origin;
    }

    public MarketAPI getTarget() {
        return target;
    }

    public enum EndReason {
        FAILED_TO_SPAWN, DEFEATED, TARGET_DESTROYED, HOSTILE, COMPLETE, OTHER
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(factionId);
        tags.add(target.getFactionId());
        tags.add(Tags.INTEL_FLEET_DEPARTURES);
        return tags;
    }

    @Override
    public String getIcon() {
        //return getFaction().getCrest();
        if (target.getSize() > 5)
            return Global.getSettings().getSpriteName("intel", "tradeFleet_valuable");
        return Global.getSettings().getSpriteName("intel", "tradeFleet_other");
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return getFaction();
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        return 7;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return target.getPrimaryEntity();
    }

    @Override
    public java.util.List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<>();
        ArrowData arrow = new ArrowData(origin.getPrimaryEntity(), target.getPrimaryEntity());
        arrow.color = getFaction().getColor();
        result.add(arrow);

        return result;
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {

    }

    private void returnShipmentTo(MarketAPI market) {
        if (fleet == null) return;

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, origin.getPlanetEntity(), 300, "Returning to " + origin.getName(), new Deliver(this, market));
    }

    public void endEvent(EndReason reason) {
        endReason = reason;
        if (debug) log.info("ending shipping intel " + endReason);

        if (fleet == null) {
            route.expire();
        } else if (fleet.getEventListeners().contains(this)) {
            fleet.removeEventListener(this);
        }

        endAfterDelay();
        sendUpdateIfPlayerHasIntel(ENDED_UPDATE, false);
    }

    protected int getETA() {
        float eta = daysToLaunch;
        float distHyper = fleet != null
                ? Misc.getDistanceLY(fleet.getLocationInHyperspace(), target.getLocationInHyperspace())
                : Misc.getDistanceLY(origin.getLocationInHyperspace(), target.getLocationInHyperspace());
        eta += distHyper / 2;

        return Math.round(eta);
    }

    private void setFleet() {
        fleet = route.getActiveFleet();
        fleet.addEventListener(this);
        fleetCargo = fleet.getCargo();
    }

    public void init() {
        Global.getSector().getIntelManager().addIntel(this);
        Global.getSector().addScript(this);
    }

    public void setInsured(boolean insured) {
        this.insured = insured;
    }

    protected FactionAPI getFaction() {
        return Global.getSector().getFaction(factionId);
    }

    protected FactionAPI getTargetFaction() {
        if (target.isInEconomy()) return target.getFaction();
        return Global.getSector().getFaction(targetFactionId);
    }

    public void setDaysToLaunch(float daysToLaunch) {
        this.daysToLaunch = daysToLaunch;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    protected String getName() {
        String str = "Courier Fleet from " + origin.getName();
        if (endReason == IndEvo_ShippingFleetIntel.EndReason.COMPLETE)
            str += " - Delivery successful";
        else if (endReason == IndEvo_ShippingFleetIntel.EndReason.OTHER)
            str += " - Delivery over";
        else if (endReason != null)
            str += " - Delivery failed";
        return str;
    }

    public static class Deliver implements Script {
        public static final Logger log = Global.getLogger(Deliver.class);

        final MarketAPI toMarket;
        final String submarketId;
        final CargoAPI cargo;

        public Deliver(IndEvo_ShippingFleetIntel intel, MarketAPI toMarket) {
            this.toMarket = toMarket;
            this.submarketId = intel.getContainer().getTargetSubmarketId();
            this.cargo = intel.targetCargo;

            log.info("Registered new delivery script for " + toMarket.getName() + " " + submarketId);
        }

        @Override
        public void run() {
            transferCargoToTarget();
        }

        private void transferCargoToTarget() {
            boolean debug = Global.getSettings().isDevMode();

            log.info("Transferring shipment cargo to " + toMarket.getName() + " " + submarketId);

            if (toMarket == null || !toMarket.isInEconomy()) return;

            String submarketId = this.submarketId;
            this.cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

            if (!toMarket.hasSubmarket(submarketId)) submarketId = Submarkets.SUBMARKET_STORAGE;

            SubmarketAPI sub = toMarket.getSubmarket(submarketId);
            CargoAPI cargo = sub.getCargo();

            if (cargo != null) {
                for (CargoStackAPI stack : this.cargo.getStacksCopy()) {
                    cargo.addFromStack(stack);
                }

                for (FleetMemberAPI member : this.cargo.getMothballedShips().getMembersListCopy()) {
                    cargo.getMothballedShips().addFleetMember(member);
                    member.getRepairTracker().setMothballed(false);
                }

                log.info("Transfer complete");
                return;
            }
            log.info("Transfer failed");
        }
    }
}