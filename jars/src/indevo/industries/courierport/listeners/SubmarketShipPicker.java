package indevo.industries.courierport.listeners;

import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.dialogue.ContractSidePanelCreator;

import java.util.ArrayList;
import java.util.List;

public class SubmarketShipPicker {
    ShippingContract contract;
    SubmarketAPI from;
    SubmarketAPI to;

    public SubmarketShipPicker(final InteractionDialogAPI dialogue, final ShippingContract contract) {
        if (contract.fromMarketId == null || contract.toMarketId == null || contract.toSubmarketId == null || contract.fromSubmarketId == null)
            return;

        this.from = contract.getFromSubmarket();
        this.to = contract.getToSubmarket();
        this.contract = contract;
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

                        if (members.isEmpty()) {
                            if (contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS) {
                                contract.scope = ShippingContract.Scope.EVERYTHING;

                            } else if (contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING) {
                                contract.scope = ShippingContract.Scope.SPECIFIC_CARGO;
                            }
                        }

                        new ContractSidePanelCreator().showPanel(dialogue, contract);
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        contract.clearTargetShips();

                        if (contract.scope == ShippingContract.Scope.SPECIFIC_SHIPS) {
                            contract.scope = ShippingContract.Scope.EVERYTHING;

                        } else if (contract.scope == ShippingContract.Scope.SPECIFIC_EVERYTHING) {
                            contract.scope = ShippingContract.Scope.SPECIFIC_CARGO;
                        }

                        new ContractSidePanelCreator().showPanel(dialogue, contract);
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
