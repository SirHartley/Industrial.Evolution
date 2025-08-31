package indevo.industries.warehouses.submarket;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.ids.Ids;
import indevo.industries.warehouses.industry.Warehouses;
import indevo.industries.warehouses.data.WarehouseSubmarketData;
import indevo.submarkets.BaseRemovableStorageSubmarketPlugin;

public class WarehouseSubmarketPlugin extends BaseRemovableStorageSubmarketPlugin {
    //functionality depends on WarehouseSubmarketData

    public WarehouseSubmarketData data;

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        data = ((Warehouses) market.getIndustry(Ids.WAREHOUSES)).getData(this);
    }

    @Override
    public String getName() {
        return data.submarketName;
    }

    @Override
    public boolean showInCargoScreen() {
        return data.showInCargoScreen;
    }

    @Override
    public boolean showInFleetScreen() {
        return data.showInFleetScreen;
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        return !data.showInCargoScreen;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        return !data.showInFleetScreen;
    }

    @Override
    public boolean isFreeTransfer() {
        return true;
    }
}
