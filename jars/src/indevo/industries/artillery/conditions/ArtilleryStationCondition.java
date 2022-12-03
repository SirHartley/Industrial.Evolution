package indevo.industries.artillery.conditions;

import indevo.industries.artillery.scripts.DerelictArtilleryStationScript;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class ArtilleryStationCondition extends BaseHazardCondition {

    public static final String ID = "IndEvo_ArtilleryStationCondition";
    public static final String ARTILLERY_KEY = "$ArtilleryStation";
    public static final String ARTILLERY_DESTROYED = "$IndEvo_ArtilleryStation_Destroyed";

    public boolean script = true;

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if(script) {
            DerelictArtilleryStationScript.addDerelictArtyToPlanet(market.getPrimaryEntity(), true);
            script = false;
        }
    }

    public boolean isDestroyed(){
        return market.getMemoryWithoutUpdate().getBoolean(ARTILLERY_DESTROYED);
    }

    public static void setDestroyed(boolean isDestroyed, MarketAPI market){
        market.getMemoryWithoutUpdate().set(ARTILLERY_DESTROYED, isDestroyed);
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        if(isDestroyed()) tooltip.addPara("It has been rendered %s. Restoration might be possible thanks to the robust repair systems, but it will require a functional orbital station.",
                10f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                new String[]{"unusable"});

        else tooltip.addPara("It will defend the planet from any hostile forces until disabled.",
                10f,
                Misc.getTextColor(),
                Misc.getHighlightColor(),
                new String[]{"access to exotic technologies"});

    }


}