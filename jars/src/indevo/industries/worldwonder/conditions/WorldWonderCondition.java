package indevo.industries.worldwonder.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import indevo.industries.worldwonder.industry.WorldWonder;
import com.fs.starfarer.api.util.Misc;

import static indevo.industries.worldwonder.industry.WorldWonder.STABILITY_BONUS;

public class WorldWonderCondition extends BaseMarketConditionPlugin {

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    public void apply(String id) {
        Industry wonder = null;

        for (MarketAPI m : Misc.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())){
           for (Industry ind : m.getIndustries()){
               if (ind instanceof WorldWonder && ind.isFunctional()){
                   wonder = ind;
                   break;
               }
           }
        }

        if (wonder != null) market.getStability().modifyFlat("WorldWonder", STABILITY_BONUS, wonder.getMarket().getName() + " " + wonder.getNameForModifier());
    }

    public void unapply(String id) {
        market.getStability().unmodify("WorldWonder");
    }

    @Override
    public boolean showIcon() {
        return false;
    }
}