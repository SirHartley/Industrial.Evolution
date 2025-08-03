package indevo.submarkets;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.MiscIE;

public class BaseRemovableStorageSubmarketPlugin extends StoragePlugin implements RemovablePlayerSubmarketPluginAPI {

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        setPlayerPaidToUnlock(true);
    }

    @Override
    public void notifyBeingRemoved() {
        moveCargoToStorage();
    }

    public void moveCargoToStorage() {
        if (MiscIE.getStorageCargo(market) != null) {
            CargoAPI toCargo = MiscIE.getStorageCargo(market);
            CargoAPI fromCargo = getCargo();

            fromCargo.initMothballedShips("player");
            toCargo.initMothballedShips("player");

            SubmarketPlugin storage = Misc.getStorage(market);
            if (storage != null) ((StoragePlugin) storage).setPlayerPaidToUnlock(true);

            //transfer ships
            for (FleetMemberAPI ship : fromCargo.getMothballedShips().getMembersListCopy()) {
                toCargo.getMothballedShips().addFleetMember(ship);
            }

            //transfer cargo
            toCargo.addAll(fromCargo);

            //clear it
            fromCargo.clear();
            fromCargo.getMothballedShips().clear();
        }
    }
}
