package industrial_evolution.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import industrial_evolution.campaign.ids.IndEvo_ids;
import industrial_evolution.plugins.addOrRemovePlugins.IndEvo_subMarketAddOrRemovePlugin;

public abstract class IndEvo_SharedSubmarketUser extends BaseIndustry implements IndEvo_SharedSubmarketUserAPI {
    public void addSharedSubmarket() {
        if (market.isPlayerOwned() && isFunctional()) {
            Global.getSector().addScript(new IndEvo_subMarketAddOrRemovePlugin(market, IndEvo_ids.SHAREDSTORAGE, false));
        }
    }

    public void removeSharedSubmarket() {
        Global.getSector().addScript(new IndEvo_subMarketAddOrRemovePlugin(market, IndEvo_ids.SHAREDSTORAGE, true));
    }
}
