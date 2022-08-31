package com.fs.starfarer.api.splinterFleet.plugins.dialogue;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.splinterFleet.plugins.FleetUtils;
import com.fs.starfarer.api.splinterFleet.plugins.fleetManagement.Behaviour;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;

public class FleetStatus {

    public String fleetName;

    public int currentCargo;
    public int currentFuel;
    public int currentCrew;
    public int currentCrewAndMarines;
    public int currentSupplies;

    public int totalCargoCap = 0;
    public int totalFuelCap = 0;
    public int totalCrewCap = 0;
    public int requiredCrew = 0;
    public int requiredSuppliesPerMonth = 0;
    public int requiredFuelPerLY = 0;
    public int suppliesToRecover = 0;

    public CargoAPI cargo;
    public List<FleetMemberAPI> fleetMemberAPIList;

    public String operatingTimeString;
    public String fuelRangeLYString;

    public FleetStatus recalculate() {
        return getFleetStatus(fleetName, cargo, fleetMemberAPIList);
    }

    public static FleetStatus getFleetStatus(CampaignFleetAPI fleet) {
        return getFleetStatus(fleet.getName(), fleet.getCargo(), fleet.getFleetData().getMembersListCopy());
    }

    public static FleetStatus getFleetStatus(String fleetName, CargoAPI cargo, List<FleetMemberAPI> fleetMemberAPIList) {
        FleetStatus status = new FleetStatus();

        CampaignFleetAPI fleet = FleetUtils.createFakeFleet(fleetName, fleetMemberAPIList, cargo);

        status.cargo = cargo;
        status.fleetMemberAPIList = fleetMemberAPIList;

        status.fleetName = fleetName;
        status.currentCargo = Math.round(cargo.getSpaceUsed());
        status.currentFuel = Math.round(cargo.getFuel());
        status.currentCrew = Math.round(cargo.getCrew());
        status.currentCrewAndMarines = Math.round(cargo.getCrew() + cargo.getMarines());
        status.currentSupplies = Math.round(cargo.getSupplies());

        status.requiredFuelPerLY = Math.round(fleet.getLogistics().getFuelCostPerLightYear());
        status.requiredSuppliesPerMonth = Math.round(fleet.getLogistics().getShipMaintenanceSupplyCost());
        status.suppliesToRecover = Math.round(fleet.getLogistics().getTotalRepairAndRecoverySupplyCost());

        //fleet.getLogistics().getTotalSuppliesPerDay();

        //tally, skip for selected members
        for (FleetMemberAPI m : fleetMemberAPIList) {
            status.totalCrewCap += m.getMaxCrew();
            status.requiredCrew += m.getMinCrew();
            status.totalCargoCap += m.getCargoCapacity();
            status.totalFuelCap += m.getFuelCapacity();
        }

        float operatingTimeMonths = ((status.currentSupplies * 1f) - status.suppliesToRecover) / (status.requiredSuppliesPerMonth);

        String operatingTimeString;
        if (operatingTimeMonths < 1) operatingTimeString = "less than 1 month";
        else if (operatingTimeMonths < 2) operatingTimeString = "1 month";
        else operatingTimeString = (int) Math.floor(operatingTimeMonths) + " months";

        status.operatingTimeString = operatingTimeString;

        status.fuelRangeLYString =  Math.round((status.currentFuel * 1f) / status.requiredFuelPerLY) + " LY";


        return status;
    }

    public static void addFleetStatusTooltip(TextPanelAPI text, CampaignFleetAPI fleet) {
        float opad = 10f;
        float spad = 3f;

        TooltipMakerAPI tooltip = text.beginTooltip();
        tooltip.addSectionHeading("Detachment Status", Alignment.MID, opad);

        //tooltip.setParaSmallInsignia();
        tooltip.setParaSmallInsignia();

        Behaviour.FleetBehaviour behaviour = Behaviour.getFleetBehaviour(fleet, true);
        String s = Misc.ucFirst(behaviour.toString());
        tooltip.addPara("Current Mode: %s", opad, Behaviour.getColourForBehaviour(behaviour), s);

        FleetStatus status = FleetStatus.getFleetStatus(fleet);

        s = status.currentCargo + " / " + status.totalCargoCap;
        Color color = status.currentCargo > status.totalCargoCap ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
        tooltip.addPara("Current Cargo: %s", opad, color, s);

        s = status.currentFuel + " / " + status.totalFuelCap;
        color = status.currentFuel > status.totalFuelCap ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
        tooltip.addPara("Current Fuel: %s", spad, color, s);

        s = status.currentCrewAndMarines + " / " + status.totalCrewCap;
        color = status.currentCrewAndMarines > status.totalCrewCap ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
        tooltip.addPara("Current Personnel: %s", spad, color, s);

        if(status.suppliesToRecover > 1) tooltip.addPara("Supplies needed to finish repairs: %s", opad, Misc.getHighlightColor(), status.suppliesToRecover + " units");
        tooltip.addPara("Estimated operating time: %s", opad, Misc.getHighlightColor(), status.operatingTimeString);
        tooltip.addPara("Estimated range: %s", spad, Misc.getHighlightColor(), status.fuelRangeLYString);

        tooltip.setParaFontDefault();
        text.addTooltip();
    }
}
