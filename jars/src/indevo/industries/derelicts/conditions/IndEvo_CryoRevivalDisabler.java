package indevo.industries.derelicts.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import indevo.utils.timers.IndEvo_newDayListener;

import static com.fs.starfarer.api.impl.campaign.econ.impl.Cryorevival.getDistancePopulationMult;

public class IndEvo_CryoRevivalDisabler extends BaseMarketConditionPlugin implements IndEvo_newDayListener {

    //this class used to be a newDayListener, changed to manual time in 3.0.d due to a condition duplication issue
    //implement is the same to avoid breaking saves, but the listener is never added anywhere
    @Override
    public void onNewDay() {
        boolean hasCryo = getDistancePopulationMult(market.getLocationInHyperspace()) > 0;
        if (!hasCryo && market.hasIndustry("cryorevival")) {
            market.getIndustry("cryorevival").setDisrupted(1);
        }
    }

    private int currentDay = 0;
    private float day = 0;

    @Override
    public void advance(float amount) {
        super.advance(amount);

        day += Global.getSector().getClock().convertToDays(amount);
        if (Math.ceil(day) > currentDay) {
            currentDay++;
            onNewDay();
        }
    }

    public void apply(String id) {

    }

    public void unapply(String id) {

    }

    @Override
    public boolean showIcon() {
        return false;
    }
}
