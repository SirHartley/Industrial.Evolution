package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMarines;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private boolean isTakeMode() {
        return loadout != null && loadout.takeMode;
    }

    private List<FleetMemberAPI> getFromFleetMembers() {
        List<FleetMemberAPI> fromFleetList = new ArrayList<>(fromFleet.getFleetData().getMembersListCopy());
        if (!removeToFleetShipsFromFromFleetForCalc) return fromFleetList;

        List<FleetMemberAPI> toRemove = new ArrayList<>();
        for (FleetMemberAPI toFleetMember : toFleet.getFleetData().getMembersListCopy()) {
            for (FleetMemberAPI fromFleetMember : fromFleetList) {
                if (fromFleetMember.getVariant().getHullVariantId().equals(toFleetMember.getVariant().getHullVariantId()) && !toRemove.contains(fromFleetMember)) {
                    toRemove.add(fromFleetMember);
                    break;
                }
            }
        }

        fromFleetList.removeAll(toRemove);
        return fromFleetList;
    }

    public void init() {
        final float width = 310;
        final boolean take = isTakeMode();
        prepFromCargoCopy();

        //note- passing the cargo here means that any cargo selected is immediately removed from the original fleet cargo
        //this also means we have to return anything we picked before to the player fleet cargo, check recreateTextPanel
        final CargoAPI fromFleetCargo = removeFromFromFleetCargo ? fromFleet.getCargo() : fromFleetCargoCopy;

        class Listener implements CargoPickerListener, DetachmentCargoLoad.Picker {
            private boolean moveCargoFirstTick = true;
            protected CargoAPI tempSelectedCargo = Global.getFactory().createCargo(true);
            private CargoAPI selected;

            public void pickedCargo(CargoAPI cargo) {
                //in take mode the picked side is the player's own, the detachment is what stays behind in the source copy
                FleetStatus newStatus = take
                        ? FleetStatus.getFleetStatus("Left behind", fromFleetCargoCopy, getFromFleetMembers())
                        : FleetStatus.getFleetStatus(toFleet.getName(), cargo, toFleet.getFleetData().getMembersListCopy());

                if (newStatus.isOverCapacity()) {
                    if (removeFromFromFleetCargo) {
                        CargoAPI fromCargo = fromFleet.getCargo();
                        fromCargo.addAll(cargo);
                        fromCargo.removeAll(tempSelectedCargo);
                        toFleet.getCargo().addAll(tempSelectedCargo);
                    }

                    String over = Misc.getAndJoined(newStatus.getOverCapacityList());
                    String who = take ? "The ships left behind" : "The detachment";
                    dialoguePlugin.notice(who + " would be over " + over + " capacity. Nothing was moved.");
                    dialoguePlugin.showBaseOptions();
                    return;
                }

                if (!cargo.isEmpty()) {
                    if (removeFromFromFleetCargo) {
                        //the picker already moved the player's marines, counts before the move are rebuilt from the delta
                        int before = tempSelectedCargo.getMarines();
                        int delta = cargo.getMarines() - before;
                        DetachmentMarines.move(toFleet, before, fromFleet.getCargo().getMarines() + delta, delta);
                    }

                    CargoAPI toFleetCargo = toFleet.getCargo();
                    toFleetCargo.clear();
                    toFleetCargo.addAll(cargo.createCopy(), false);
                    PlayerFleetPersonnelTracker.getInstance().update();

                    if (loadout != null) {
                        loadout.targetCargo.clear();
                        loadout.targetCargo.addAll(cargo);
                    }
                }

                dialoguePlugin.showBaseOptions();
            }

            public void cancelledCargoSelection() {
                //load min and max move cargo outside the picker's transaction, so its cancel does not put it back
                if (removeFromFromFleetCargo && selected != null) {
                    CargoAPI fromCargo = fromFleet.getCargo();
                    fromCargo.addAll(selected);
                    fromCargo.removeAll(tempSelectedCargo);
                }

                toFleet.getCargo().addAll(tempSelectedCargo);
                dialoguePlugin.showBaseOptions();
            }

            public void load(DetachmentCargoLoad.Mode mode) {
                if (selected == null) return;

                FleetStatus from = FleetStatus.getFleetStatus(take ? "Left behind" : "Main Fleet", fromFleetCargo, getFromFleetMembers(), !take);
                FleetStatus to = FleetStatus.getFleetStatus(take ? "You take" : "Detachment", selected, toFleet.getFleetData().getMembersListCopy(), take);
                DetachmentCargoLoad.apply(mode, take ? from : to, take ? to : from);
            }

            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
                selected = cargo;
                float opad = 10f;
                float spad = 3f;
                Color negativeHighlightColor = Misc.getNegativeHighlightColor();
                Color highlightColor = Misc.getHighlightColor();
                Color positiveHighlightColour = Misc.getPositiveHighlightColor();

                List<FleetMemberAPI> fromFleetList = getFromFleetMembers();
                List<FleetMemberAPI> toFleetList = new ArrayList<>(toFleet.getFleetData().getMembersListCopy());

                CargoAPI fromFleetCargo = removeFromFromFleetCargo ? fromFleet.getCargo() : fromFleetCargoCopy;
                FleetStatus fromFleetStatus = FleetStatus.getFleetStatus(take ? "Left behind" : "Main Fleet", fromFleetCargo, fromFleetList, !take);
                FleetStatus toFleetStatus = FleetStatus.getFleetStatus(take ? "You take" : "Detachment", cargo, toFleetList, take);

                //Small hack to save the player some time and have the cargo they selected show up in the "selected" panel
                //an empty pick loads the detachment's minimum in send mode, its maximum in take mode (you take your minimum)
                if (moveCargoFirstTick) {
                    CargoAPI toFleetCargo = toFleet.getCargo();
                    cargo.addAll(toFleetCargo);
                    tempSelectedCargo.addAll(toFleetCargo);
                    toFleetCargo.clear();

                    if (autoLoadSupplyFuelCrew && cargo.isEmpty()) {
                        DetachmentCargoLoad.apply(take ? DetachmentCargoLoad.Mode.MAX : DetachmentCargoLoad.Mode.MIN, take ? fromFleetStatus : toFleetStatus, take ? toFleetStatus : fromFleetStatus);
                        fromFleetStatus = fromFleetStatus.recalculate();
                        toFleetStatus = toFleetStatus.recalculate();
                    }

                    moveCargoFirstTick = false;
                }

                //toFleetStatus.currentCrew += (pickedUp != null && pickedUp.isCrewStack() ? pickedUp.getCargo().getCrew() : 0); //adjust for picked up stack - crew is the only one that needs handling, for some reason.

                panel.addPara(take ? "Select the cargo you take with you. Everything else stays with the detachment." : "Select the cargo you would like to load onto the detachment.", opad);

                for (FleetStatus status : Arrays.asList(fromFleetStatus, toFleetStatus)) {
                    panel.addSectionHeading(status.fleetName, Alignment.MID, opad);
                    panel.addPara("Cargo capacity: %s", spad, status.currentCargo > status.totalCargoCap ? negativeHighlightColor : highlightColor, status.currentCargo + " / " + status.totalCargoCap);
                    panel.addPara("Fuel capacity: %s", spad, status.currentFuel > status.totalFuelCap ? negativeHighlightColor : highlightColor, status.currentFuel + " / " + status.totalFuelCap);
                    panel.addPara("Crew capacity: %s", spad, status.currentCrewAndMarines > status.totalCrewCap ? negativeHighlightColor : highlightColor, status.currentCrewAndMarines + " / " + status.totalCrewCap);
                    panel.addPara("", spad);
                    panel.addPara("Minimum crew requirement: %s", spad, status.currentCrew < status.requiredCrew ? negativeHighlightColor : positiveHighlightColour, status.currentCrew + " / " + status.requiredCrew);
                    panel.addPara("Fleet requires %s supplies / month", spad, highlightColor, status.requiredSuppliesPerMonth + "");

                    if (status.suppliesToRecover > 1)
                        panel.addPara("Supplies needed to finish repairs: %s", opad, Misc.getHighlightColor(), status.suppliesToRecover + " units");
                    panel.addPara("Estimated operating time: %s", spad, highlightColor, status.operatingTimeString);
                    panel.addPara("Estimated range: %s", spad, highlightColor, status.fuelRangeLYString);
                }

                panel.addSectionHeading("Notes", Alignment.MID, opad);
                panel.addPara("Detachment will become derelict if out of supplies. Estimations do not account for combat.", opad);
                if (take && fromFleetStatus.isOverCapacity())
                    panel.addPara("The ships left behind are over %s capacity, take more with you.", opad, negativeHighlightColor, Misc.getAndJoined(fromFleetStatus.getOverCapacityList()));
                else if (!take && toFleetStatus.isOverCapacity())
                    panel.addPara("The detachment is over %s capacity and cannot take this cargo.", opad, negativeHighlightColor, Misc.getAndJoined(toFleetStatus.getOverCapacityList()));

            }
        }

        dialoguePlugin.getDialog().showCargoPickerDialog(take ? "Select the cargo you take with you" : "Select cargo for the detachment", "Confirm", "Cancel", false, width, fromFleetCargo, new Listener());
    }
}
