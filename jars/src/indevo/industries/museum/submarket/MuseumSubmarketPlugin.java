package indevo.industries.museum.submarket;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
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
}
