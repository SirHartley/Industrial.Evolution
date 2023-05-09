package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.industries.artillery.conditions.ArtilleryStationCondition;

import java.util.ArrayList;

public class ArtilleryStationReplacer implements EveryFrameScript {

    private boolean done = false;

    public static void register(){
        Global.getSector().addTransientScript(new ArtilleryStationReplacer());
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (isDone()) return;

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()){
            if (m.hasCondition(ArtilleryStationCondition.ID)){
                for (Industry ind : new ArrayList<>(m.getIndustries())){
                    if (ind.getId().contains("artillery")){
                        m.removeIndustry(ind.getId(), null, false);
                        m.addIndustry(ind.getId());

                        Industry newArty = m.getIndustry(ind.getId());
                        newArty.setAICoreId(ind.getAICoreId());
                    }
                }
            }
        }

        done = true;
    }
}
