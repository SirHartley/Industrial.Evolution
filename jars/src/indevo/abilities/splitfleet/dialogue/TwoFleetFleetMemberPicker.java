package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import indevo.abilities.splitfleet.fleetManagement.LoadoutMemory;

import java.util.ArrayList;
import java.util.List;

public class TwoFleetFleetMemberPicker {
    protected AbilityPanelDialoguePlugin dialoguePlugin;
    protected LoadoutMemory.Loadout loadout;

    public TwoFleetFleetMemberPicker(AbilityPanelDialoguePlugin plugin, LoadoutMemory.Loadout loadout) {
        this.dialoguePlugin = plugin;
        this.loadout = loadout;
    }

    public void init() {
        List<FleetMemberAPI> fleetMemberListAll = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();

        //remove quest relevant ships from selectables
        for (FleetMemberAPI m : fleetMemberListAll) {
            if (m.getHullSpec().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE) || m.getVariant().getTags().contains(Tags.SHIP_CAN_NOT_SCUTTLE))
                continue;
            fleetMemberList.add(m);
        }

        int rows = fleetMemberList.size() > 8 ? (int) Math.ceil(fleetMemberList.size() / 8f) : 1;
        int cols = Math.min(fleetMemberList.size(), 8);
        cols = Math.max(cols, 4);

        dialoguePlugin.dialog.showFleetMemberPickerDialog("Select ships for the detachment", "Confirm", "Cancel", rows,
                cols, 88f, true, true, fleetMemberList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null && !members.isEmpty()) {

                            loadout.shipVariantList.clear();
                            for (FleetMemberAPI m : members) loadout.addToMembersList(m);
                            loadout.targetCargo.clear(); //this is to reset the cargo so it does not keep excess items for the cargo capacity of the new fleet
                        }

                        dialoguePlugin.refreshCustomPanel();
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                });
    }
}
