package indevo.submarkets;

import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;

public class BaseRemovableStorageSubmarketPlugin extends StoragePlugin {

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        setPlayerPaidToUnlock(true);
    }
}
