package industrial_evolution.splinterFleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import industrial_evolution.splinterFleet.FleetUtils;
import industrial_evolution.splinterFleet.fleetManagement.LoadoutMemory;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static industrial_evolution.splinterFleet.FleetUtils.transferCommodity;

public class TwoFleetCargoPicker {
    protected SplinterFleetDialoguePluginAPI dialoguePlugin;
    protected CampaignFleetAPI fromFleet;
    protected CampaignFleetAPI toFleet;
    protected boolean removeToFleetShipsFromFromFleetForCalc;
    protected boolean autoLoadSupplyFuelCrew;

    protected boolean removeFromFromFleetCargo;
    CargoAPI fromFleetCargoCopy; //need to make a cargo that is exactly like FromFleetCargo but with the stuff removed to mirror it the way it would be if it had actually been removed, since we will always get a "pristine" copy of that fromCargo if it's true

    protected LoadoutMemory.Loadout loadout;

    public TwoFleetCargoPicker(SplinterFleetDialoguePluginAPI dialoguePlugin, CampaignFleetAPI fromFleet, CampaignFleetAPI toFleet, boolean removeToFleetShipsFromFromFleetForCalc, boolean autoLoadSupplyFuelCrew, boolean removeFromFromFleetCargo) {
        this.dialoguePlugin = dialoguePlugin;
        this.fromFleet = fromFleet;
        this.toFleet = toFleet;
        this.removeToFleetShipsFromFromFleetForCalc = removeToFleetShipsFromFromFleetForCalc;
        this.autoLoadSupplyFuelCrew = autoLoadSupplyFuelCrew;
        this.removeFromFromFleetCargo = removeFromFromFleetCargo;
    }

    public TwoFleetCargoPicker(SplinterFleetDialoguePluginAPI dialoguePlugin, CampaignFleetAPI fromFleet, LoadoutMemory.Loadout loadout, boolean autoLoadSupplyFuelCrew) {
        this.dialoguePlugin = dialoguePlugin;
        this.fromFleet = fromFleet;
        this.loadout = loadout;
        this.toFleet = FleetUtils.createFakeFleet(loadout);
        this.removeToFleetShipsFromFromFleetForCalc = true;
        this.autoLoadSupplyFuelCrew = autoLoadSupplyFuelCrew;
        this.removeFromFromFleetCargo = false;
    }

    private void prepFromCargoCopy() {
        fromFleetCargoCopy = fromFleet.getCargo().createCopy();
        fromFleetCargoCopy.removeAll(toFleet.getCargo());
    }

    public void init() {
        final float width = 310;
        prepFromCargoCopy();

        //note- passing the cargo here means that any cargo selected is immediately removed from the original fleet cargo
        //this also means we have to return anything we picked before to the player fleet cargo, check recreateTextPanel
        final CargoAPI fromFleetCargo = removeFromFromFleetCargo ? fromFleet.getCargo() : fromFleetCargoCopy;

        dialoguePlugin.getDialog().showCargoPickerDialog("Select cargo for the detachment", "Confirm", "Cancel", false, width, fromFleetCargo, new CargoPickerListener() {
            private boolean moveCargoFirstTick = true;
            protected CargoAPI tempSelectedCargo = Global.getFactory().createCargo(true);

            public void pickedCargo(CargoAPI cargo) {
                if (!cargo.isEmpty()) {
                    CargoAPI toFleetCargo = toFleet.getCargo();
                    toFleetCargo.clear();
                    toFleetCargo.addAll(cargo.createCopy(), false);

                    if(false) { //removeFromFromFleetCargo, this should have been for marine XP retention, but it doesn't work
                        PlayerMarketTransaction trans = new PlayerMarketTransaction(null, null, CampaignUIAPI.CoreUITradeMode.OPEN);

                        //if from fleet is player fleet and we transfer stuff from it, it is calculated as "sell action" - otherwise, we buy
                        CargoAPI toCargo = fromFleet.isPlayerFleet() ? trans.getSold() : trans.getBought();
                        toCargo.addAll(cargo);
                        PlayerFleetPersonnelTracker.getInstance().processTransaction(trans, toFleet);
                    }

                    if (loadout != null) {
                        loadout.targetCargo.clear();
                        loadout.targetCargo.addAll(cargo);
                    }
                }

                dialoguePlugin.showBaseOptions();
            }

            public void cancelledCargoSelection() {
                toFleet.getCargo().addAll(tempSelectedCargo);
                dialoguePlugin.showBaseOptions();
            }

            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
                float opad = 10f;
                float spad = 3f;
                Color negativeHighlightColor = Misc.getNegativeHighlightColor();
                Color highlightColor = Misc.getHighlightColor();
                Color positiveHighlightColour = Misc.getPositiveHighlightColor();

                //player fleet status
                List<FleetMemberAPI> fromFleetList = new ArrayList<>(fromFleet.getFleetData().getMembersListCopy());
                List<FleetMemberAPI> toFleetList = new ArrayList<>(toFleet.getFleetData().getMembersListCopy());

                if (removeToFleetShipsFromFromFleetForCalc) {
                    List<FleetMemberAPI> toRemove = new ArrayList<>();

                    for (FleetMemberAPI toFleetMember : toFleetList) {
                        for (FleetMemberAPI fromFleetMember : fromFleetList) {
                            if (fromFleetMember.getVariant().getHullVariantId().equals(toFleetMember.getVariant().getHullVariantId())) {
                                toRemove.add(fromFleetMember);
                                break;
                            }
                        }
                    }

                    fromFleetList.removeAll(toRemove);
                }

                CargoAPI fromFleetCargo = removeFromFromFleetCargo ? fromFleet.getCargo() : fromFleetCargoCopy;
                FleetStatus fromFleetStatus = FleetStatus.getFleetStatus("Main Fleet", fromFleetCargo, fromFleetList);
                FleetStatus toFleetStatus = FleetStatus.getFleetStatus("Detachment", cargo, toFleetList);

                //Small hack to save the player some time and have the cargo they selected show up in the "selected" panel
                //also distributes one month of supplies, fuel ect to the detachment if required and cargo is empty
                if (moveCargoFirstTick) {
                    CargoAPI toFleetCargo = toFleet.getCargo();
                    cargo.addAll(toFleetCargo);
                    tempSelectedCargo.addAll(toFleetCargo);
                    toFleetCargo.clear();

                    if (autoLoadSupplyFuelCrew && cargo.isEmpty())
                        autoLoadSupplyFuelCrew(fromFleetStatus, toFleetStatus);

                    moveCargoFirstTick = false;
                }

                //toFleetStatus.currentCrew += (pickedUp != null && pickedUp.isCrewStack() ? pickedUp.getCargo().getCrew() : 0); //adjust for picked up stack - crew is the only one that needs handling, for some reason.

                panel.addPara("Select the cargo you would like to load onto the detachment.", opad);

                for (FleetStatus status : Arrays.asList(fromFleetStatus, toFleetStatus)) {
                    panel.addSectionHeading(status.fleetName, Alignment.MID, opad);
                    panel.addPara("Cargo capacity: %s", spad, status.currentCargo > status.totalCargoCap ? negativeHighlightColor : highlightColor, status.currentCargo + " / " + status.totalCargoCap);
                    panel.addPara("Fuel capacity: %s", spad, status.currentFuel > status.totalFuelCap ? negativeHighlightColor : highlightColor, status.currentFuel + " / " + status.totalFuelCap);
                    panel.addPara("Crew capacity: %s", spad, status.currentCrewAndMarines > status.totalCrewCap ? negativeHighlightColor : highlightColor, status.currentCrewAndMarines + " / " + status.totalCrewCap);
                    panel.addPara("", spad);
                    panel.addPara("Minimum crew requirement: %s", spad, status.currentCrew < status.requiredCrew ? negativeHighlightColor : positiveHighlightColour, status.currentCrew + " / " + status.requiredCrew);
                    panel.addPara("Fleet requires %s supplies / month", spad, highlightColor, status.requiredSuppliesPerMonth + "");

                    if(status.suppliesToRecover > 1) panel.addPara("Supplies needed to finish repairs: %s", opad, Misc.getHighlightColor(), status.suppliesToRecover + " units");
                    panel.addPara("Estimated operating time: %s", spad, highlightColor, status.operatingTimeString);
                    panel.addPara("Estimated range: %s", spad, highlightColor, status.fuelRangeLYString);
                }

                panel.addSectionHeading("Notes", Alignment.MID, opad);
                panel.addPara("Detachment will become derelict if out of supplies. Estimations do not account for overloading and combat.", opad);

            }

            private void autoLoadSupplyFuelCrew(FleetStatus fromFleet, FleetStatus toFleet) {
                CargoAPI fromCargo = fromFleet.cargo;
                CargoAPI toCargo = toFleet.cargo;

                if (toFleet.currentSupplies < toFleet.requiredSuppliesPerMonth) {
                    //take what you need to repair and enough for one month
                    //if not available, take half the stuff player has available up to your cargo max
                    float amt = (toFleet.requiredSuppliesPerMonth + toFleet.suppliesToRecover) - toFleet.currentSupplies;
                    if(amt > fromFleet.currentSupplies && toFleet.currentSupplies < 1) amt = Math.max(toFleet.totalCargoCap, fromFleet.currentSupplies / 2f);

                    transferCommodity(fromCargo, toCargo, Commodities.SUPPLIES, amt);
                }

                if (toFleet.currentFuel < toFleet.totalFuelCap) {
                    //if we'd take more than half the fuel of the main fleet, just take half
                    float amt = toFleet.totalFuelCap - toFleet.currentFuel;
                    if(amt > fromFleet.currentFuel / 2f && toFleet.currentFuel < 1) amt = fromFleet.currentFuel / 2f;

                    transferCommodity(fromCargo, toCargo, Commodities.FUEL, amt);
                }

                if (toFleet.currentCrew < toFleet.requiredCrew) {
                    //load whats needed unless this would mean going below the min crew for the main fleet
                    float amt = toFleet.requiredCrew - toFleet.currentCrew;
                    if(fromFleet.currentCrew - amt < fromFleet.requiredCrew && toFleet.currentCrew < 1) amt = Math.max(0, fromFleet.currentCrew - fromFleet.requiredCrew);
                    if(fromFleet.currentCrew < fromFleet.requiredCrew) amt = 1;

                    transferCommodity(fromCargo, toCargo, Commodities.CREW, amt);
                }

                fromFleet.recalculate();
                toFleet.recalculate();
            }
        });
    }
}
