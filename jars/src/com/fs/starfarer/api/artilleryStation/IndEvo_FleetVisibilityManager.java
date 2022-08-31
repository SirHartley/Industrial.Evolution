package com.fs.starfarer.api.artilleryStation;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;

public class IndEvo_FleetVisibilityManager implements EveryFrameScript {

    public static final String WAS_SEEN_BY_HOSTILE_ENTITY = "$IndEvo_WasSeenByOtherEntity";
    public static final float BASE_KNOWN_DURATION = 5f;

    public static void register() {
        if (Global.getSector().hasTransientScript(IndEvo_FleetVisibilityManager.class)) return;

        IndEvo_FleetVisibilityManager m = new IndEvo_FleetVisibilityManager();
        Global.getSector().addTransientScript(m);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();

        if (loc.isHyperspace()) return;

        for (CampaignFleetAPI f : loc.getFleets()) {
            //check if visible to other fleet
            for (CampaignFleetAPI otherFLeet : loc.getFleets()) {
                if (Misc.getVisibleFleets(otherFLeet, true).contains(f))
                    f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, BASE_KNOWN_DURATION);
            }
        }
    }
}

