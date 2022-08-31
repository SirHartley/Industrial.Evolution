package com.fs.starfarer.api.impl.campaign.econ.impl;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.plugins.addOrRemovePlugins.IndEvo_subMarketAddOrRemovePlugin;

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
