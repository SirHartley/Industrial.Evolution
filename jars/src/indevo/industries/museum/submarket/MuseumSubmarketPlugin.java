package indevo.industries.museum.submarket;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.industries.museum.data.MuseumConstants;
import indevo.submarkets.BaseRemovableStorageSubmarketPlugin;

public class MuseumSubmarketPlugin extends BaseRemovableStorageSubmarketPlugin {

    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.submarket.setFaction(Global.getSector().getFaction("museumColour"));
    }

    @Override
    public boolean showInCargoScreen() {
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        return true;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action != TransferAction.PLAYER_BUY) return false;

        return member.getVariant().hasTag(MuseumConstants.ON_PARADE_TAG) || super.isIllegalOnSubmarket(member, action);
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Out on parade";
    }
}
