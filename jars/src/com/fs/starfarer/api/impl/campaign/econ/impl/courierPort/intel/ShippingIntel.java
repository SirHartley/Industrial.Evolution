package com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingContract;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingCostCalculator;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingTargetHelper;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingTooltipHelper;
import com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.listeners.Shipment;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.econ.impl.courierPort.ShippingCostCalculator.CONTRACT_BASE_FEE;

public class ShippingIntel extends BaseIntelPlugin {

    public static enum ShippingStatus{
        NOT_INITIALIZED,
        ASSEMBLING,
        TRANSFER,
        UNLOADING,
        DONE_SUCCESS,
        DONE_ALTERNATE
    }

    protected final Shipment shipment;
    protected ShippingStatus status = ShippingStatus.NOT_INITIALIZED;

    public ShippingIntel(Shipment shipment) {
        this.shipment = shipment;
    }

    public void setStatus(ShippingStatus status){
        this.status = status;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if(status == ShippingStatus.DONE_SUCCESS || status == ShippingStatus.DONE_ALTERNATE){
            endAfterDelay();
        }
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;

        bullet(info);
        info.addPara(shipment.contract.name, pad);
        if(!status.toString().contains("done")) info.addPara("ETA: %s", pad, h, getETA() + " " + getDaysString(getETA()));
        info.addPara("Total cost: %s" , pad, h, Misc.getDGSCredits(shipment.cost));
        unindent(info);
    }

    // sidebar text description
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color hl = Misc.getHighlightColor();

        ShippingContract contract = shipment.contract;
        FactionAPI faction = contract.getFromMarket().getFaction();
        FactionAPI otherFaction = contract.getToMarket().getFaction();

        info.addImages(width, 128, opad, opad, faction.getCrest(), otherFaction.getCrest());

        info.addSectionHeading("Current Status", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);

        switch (status) {
            case ASSEMBLING:
                info.addPara("The courier fleet is currently loading cargo at %s.", opad, faction.getBaseUIColor(), contract.getFromMarket().getName());
                break;
            case TRANSFER:
                info.addPara("The courier fleet is currently travelling to %s.", opad, otherFaction.getBaseUIColor(), contract.getToMarket().getName());
                if (getETA() >= 1)
                    info.addPara("It will arrive in approximately %s.", opad, hl, getETA() + " " + getDaysString(getETA()));
                break;
            case UNLOADING:
                info.addPara("The courier fleet is currently unloading cargo at %s.", opad, otherFaction.getBaseUIColor(), contract.getToMarket().getName());
                break;
            case DONE_SUCCESS:
                info.addPara("The delivery has finished and the fleet has disbanded.", opad);
                info.addPara("The cargo was delivered to " + contract.getToMarket().getName() + ", " + contract.getToSubmarket().getNameOneLine(), opad);
                break;
            case DONE_ALTERNATE:
            default:
                info.addPara("The delivery has finished with unexpected difficulties and the fleet has disbanded.", opad);
                info.addPara("The cargo was delivered to " + contract.getToMarket().getName() + ", " + contract.getToSubmarket().getNameOneLine(), opad);
        }
        
        addContractTooltip(info);
        addCargoTooltip(info);
    }

    protected int getETA() {
        ShippingContract contract = shipment.contract;
        float dist = Misc.getDistanceLY(contract.getFromMarket().getPrimaryEntity(), contract.getToMarket().getPrimaryEntity());
        float lyPerDay = Misc.getLYPerDayAtSpeed(shipment.fleet, shipment.fleet.getTravelSpeed());
        float days = dist / lyPerDay;
        float extra = status == ShippingStatus.ASSEMBLING ? 4 : status == ShippingStatus.UNLOADING ? 0 : 2;
        
        return Math.round(days + extra);
    }

    private void addContractTooltip(TooltipMakerAPI tooltip) {
        float opad = 10f;
        float pad = 5f;
        float spad = 3f;

        ShippingContract contract = shipment.contract;
        CargoAPI cargo = shipment.cargo;

        tooltip.addSectionHeading("Contract Summary", Alignment.MID, opad);

        MarketAPI fromMarket = contract.getFromMarket();
        MarketAPI toMarket = contract.getToMarket();
        SubmarketAPI fromSubmarket = contract.getFromSubmarket();
        SubmarketAPI toSubmarket = contract.getToSubmarket();

        String cadenceString = ShippingTooltipHelper.getCadenceString(contract.getRecurrentDays());
        float lyMultVal = ShippingCostCalculator.getLYMult(contract);
        String lyMultStr = Misc.getRoundedValueMaxOneAfterDecimal(lyMultVal);

        float shipCost = ShippingCostCalculator.getContractShipCost(cargo, contract);
        float cargoCost = ShippingCostCalculator.getContractCargoCost(cargo, contract);
        float total = shipCost + cargoCost + CONTRACT_BASE_FEE;

        tooltip.setParaFont(Fonts.DEFAULT_SMALL);

        if (fromMarket != null) tooltip.addPara("From: %s" + (fromSubmarket != null ? ", %s" : ""),
                pad,
                contract.getFromMarket().getTextColorForFactionOrPlanet(),
                fromMarket.getName(),
                fromSubmarket != null ? fromSubmarket.getNameOneLine() : "");

        if (toMarket != null) tooltip.addPara("To: %s" + (toSubmarket != null ? ", %s" : ""),
                pad,
                contract.getToMarket().getTextColorForFactionOrPlanet(),
                toMarket.getName(),
                toSubmarket != null ? toSubmarket.getNameOneLine() : "");

        if(fromMarket != null && toMarket != null && fromSubmarket != null && toSubmarket != null){
            boolean shipsScope = contract.scope != ShippingContract.Scope.ALL_CARGO && contract.scope != ShippingContract.Scope.SPECIFIC_CARGO;
            boolean cargoScope = contract.scope != ShippingContract.Scope.ALL_SHIPS && contract.scope != ShippingContract.Scope.SPECIFIC_SHIPS;
            String and = shipsScope && cargoScope ? " and %s" : "";
            String shipStr = ShippingTooltipHelper.getShipAmtString(contract);
            String cargoStr = ShippingTooltipHelper.getCargoAmtString(contract);

            String firstHL = shipsScope ? Misc.ucFirst(shipStr) : Misc.ucFirst(cargoStr);
            String secondHL = shipsScope && cargoScope ? Misc.ucFirst(cargoStr) : "";

            tooltip.addPara("%s" + and, spad, Misc.getHighlightColor(), firstHL, secondHL);
        }

        tooltip.addPara("Cadence: " + (contract.getRecurrentDays() > 0 ? "every %s" : "%s"), pad, Misc.getHighlightColor(), cadenceString);

        if (fromMarket != null && toMarket != null) {
            String alphaCoreStr = ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.ALPHA_CORE) ? " [-" + IndEvo_StringHelper.getAbsPercentString(ShippingCostCalculator.TOTAL_FEE_REDUCTION, true) + ", Alpha Core]" : "";

            if(shipment.isDone())  {
                tooltip.addPara("Cost: %s", opad, Misc.getHighlightColor(), Misc.getDGSCredits(shipment.cost));
                return;
            }

            tooltip.addPara("Cost:", opad);
            tooltip.beginGridFlipped(300, 1, 100f, 3f);
            tooltip.addToGrid(0, 0, "Base fee", Misc.getDGSCredits(CONTRACT_BASE_FEE));

            if(cargoCost > 1) tooltip.addToGrid(0,
                        1,
                        "Cargo transport",
                        Misc.getDGSCredits(cargoCost) + alphaCoreStr);

            if(shipCost > 1) tooltip.addToGrid(0,
                        2,
                        "Ships transport",
                        Misc.getDGSCredits(shipCost) + alphaCoreStr);

            String betaCoreStr = ShippingTargetHelper.getMemoryAICoreId().equals(Commodities.BETA_CORE) ? " [-" + IndEvo_StringHelper.getAbsPercentString(ShippingCostCalculator.DISTANCE_MULT_REDUCTION, true) + ", Beta Core]" : "";
            tooltip.addToGrid(0, 3, "Distance multiplier", "x" + lyMultStr + betaCoreStr);

            if (fromSubmarket != null && fromSubmarket.getSpecId().equals(Submarkets.LOCAL_RESOURCES)) {
                float stockpileCost = 0f;

                for (CargoStackAPI stack : cargo.getStacksCopy()) {
                    stockpileCost += stack.getBaseValuePerUnit() * stack.getSize();
                }

                tooltip.addToGrid(0, 4, "Stockpile item cost", Misc.getDGSCredits(stockpileCost));
                total += stockpileCost;
            }

            tooltip.addToGrid(0, 5, "Total", Misc.getDGSCredits(total));

            tooltip.addGrid(pad);
            tooltip.setParaFontDefault();
        }
    }

    private void addCargoTooltip(TooltipMakerAPI tooltip) {
        if(shipment.isDone()) return;

        CargoAPI cargo = shipment.cargo;
        SubmarketAPI targetsubmarket = shipment.contract.getToSubmarket();

        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());

        boolean showInFleetScreen = targetsubmarket.getPlugin().showInFleetScreen();
        boolean showInCargoScreen = targetsubmarket.getPlugin().showInCargoScreen();
        float pad = 5f;
        float opad = 10f;

        tooltip.addSectionHeading("Shipment information:", Alignment.MID, opad);
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

    @Override
    public FactionAPI getFactionForUIColors() {
        return super.getFactionForUIColors();
    }

    @Override
    protected float getBaseDaysAfterEnd() {
        return 7;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return super.shouldRemoveIntel();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return shipment.contract.getFromMarket() != null ? shipment.contract.getFromMarket().getPrimaryEntity() : null;
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        List<ArrowData> result = new ArrayList<ArrowData>();
        ArrowData arrow = new ArrowData(shipment.contract.getFromMarket().getPrimaryEntity(), shipment.contract.getToMarket().getPrimaryEntity());
        arrow.color = Global.getSector().getPlayerFaction().getColor();
        result.add(arrow);

        return result;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("player");
        tags.add(Tags.INTEL_FLEET_DEPARTURES);
        return tags;
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "tradeFleet_other");
    }

    public String getName() {
        return "Courier Contract";
    }
}
