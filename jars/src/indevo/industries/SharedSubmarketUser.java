package indevo.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.ids.Ids;
import indevo.utils.scripts.SubMarketAddOrRemovePlugin;

public abstract class SharedSubmarketUser extends BaseIndustry implements SharedSubmarketUserAPI {
    public void addSharedSubmarket() {
        if (market.isPlayerOwned() && isFunctional() && !market.hasSubmarket(Ids.SHAREDSTORAGE)) {
            Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.SHAREDSTORAGE, false));
        }
    }

    public void removeSharedSubmarket() {
        Global.getSector().addScript(new SubMarketAddOrRemovePlugin(market, Ids.SHAREDSTORAGE, true));
    }
}
