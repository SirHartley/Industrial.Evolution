package com.fs.starfarer.api.artilleryStation.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class IndEvo_FleetVisibilityScript implements EveryFrameScript {

    public static final String WAS_SEEN_BY_HOSTILE_ENTITY = "$IndEvo_WasSeenByOtherEntity";
    public static final float BASE_KNOWN_DURATION = 5f;
    public IntervalUtil checkInterval = new IntervalUtil(0.5f, 0.5f);

    public static void register() {
        if (Global.getSector().hasTransientScript(IndEvo_FleetVisibilityScript.class)) return;

        IndEvo_FleetVisibilityScript m = new IndEvo_FleetVisibilityScript();
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
        checkInterval.advance(amount);

        if (checkInterval.intervalElapsed()){
            for (CampaignFleetAPI f : loc.getFleets()) {

                //check if visible to other fleet
                for (CampaignFleetAPI otherFLeet : loc.getFleets()) {
                    if (otherFLeet == f) continue;

                    if (otherFLeet.isHostileTo(f)
                            && otherFLeet.getAI() != null
                            && !otherFLeet.isStationMode()
                            && Misc.getVisibleFleets(otherFLeet, false).contains(f)){

                        f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, BASE_KNOWN_DURATION);
                        break;
                    }
                }
            }
        }
    }
}

