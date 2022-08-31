package com.fs.starfarer.api.plugins.addOrRemovePlugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class IndEvo_entityRemovalScript implements EveryFrameScript {

    protected final MarketAPI market;
    protected final CustomCampaignEntityAPI entity;
    protected final String id;
    protected boolean forceRemoval;

    protected boolean done = false;

    public IndEvo_entityRemovalScript(CustomCampaignEntityAPI entity, MarketAPI market, String prerequisiteIndustryID, boolean forceRemoval) {
        this.market = market;
        this.entity = entity;
        this.id = prerequisiteIndustryID;
        this.forceRemoval = forceRemoval;
    }

    public void advance(float amount) {
        if (!market.hasIndustry(id) || forceRemoval) {

            LocationAPI loc = entity.getContainingLocation();
            loc.removeEntity(entity);
        }

        setDone();
    }

    public void setDone() {
        done = true;
    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return true;
    }


}
