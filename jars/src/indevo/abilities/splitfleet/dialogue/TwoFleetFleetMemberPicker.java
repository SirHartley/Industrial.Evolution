package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.abilities.splitfleet.FleetUtils;
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
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (!FleetUtils.staysWithPlayer(m)) fleetMemberList.add(m);
        }

        boolean take = loadout.takeMode;
        String title = take ? "Select the ships you take with you" : "Select ships for the detachment";

        int rows = fleetMemberList.size() > 8 ? (int) Math.ceil(fleetMemberList.size() / 8f) : 1;
        int cols = Math.min(fleetMemberList.size(), 8);
        cols = Math.max(cols, 4);

        dialoguePlugin.dialog.showFleetMemberPickerDialog(title, "Confirm", "Cancel", rows,
                cols, 88f, true, true, fleetMemberList, new FleetMemberPickerListener() {

                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        //take mode accepts an empty pick: you take only the ships that always stay with you
                        if (members != null && (take || !members.isEmpty())) {

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
