package indevo.industries.courierport.listeners;

import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.courierport.ShippingContract;

import java.util.ArrayList;
import java.util.List;

public class SubmarketShipPicker {
    ShippingContract contract;
    SubmarketAPI from;
    SubmarketAPI to;

    public SubmarketShipPicker(final InteractionDialogAPI dialogue, final ShippingContract contract, final Runnable onClose) {
        this.from = contract.getFromSubmarket();
        this.to = contract.getToSubmarket();
        this.contract = contract;
        if (from == null || to == null) {
            onClose.run();
            return;
        }

        List<FleetMemberAPI> memberList = getValidMembersList();

        int rows = memberList.size() > 8 ? (int) Math.ceil(memberList.size() / 8f) : 1;
        int cols = Math.min(memberList.size(), 8);
        cols = Math.max(cols, 6);

        dialogue.showFleetMemberPickerDialog("Select ships to transport", "Confirm", "Cancel", rows,
                cols, 88f, true, true, memberList, new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        contract.clearTargetShips();
                        contract.addToShips(members);
                        onClose.run();
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        onClose.run();
                    }
                });
    }

    private List<FleetMemberAPI> getValidMembersList() {
        List<FleetMemberAPI> fleetMemberList = new ArrayList<>();
        from.getCargo().initMothballedShips("player");

        for (FleetMemberAPI member : from.getCargo().getMothballedShips().getMembersListCopy()) {
            if (!to.getPlugin().isIllegalOnSubmarket(member, SubmarketPlugin.TransferAction.PLAYER_SELL)) {
                fleetMemberList.add(member);
            }
        }

        return fleetMemberList;
    }
}
