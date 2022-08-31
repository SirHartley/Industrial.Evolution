package com.fs.starfarer.api.impl.campaign.econ.conditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_WorldWonder;
import com.fs.starfarer.api.util.Misc;

import static com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_WorldWonder.STABILITY_BONUS;

public class IndEvo_WorldWonderCondition extends BaseMarketConditionPlugin {

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    public void apply(String id) {
        Industry wonder = null;

        for (MarketAPI m : Misc.getMarketsInLocation(market.getContainingLocation(), market.getFactionId())){
           for (Industry ind : m.getIndustries()){
               if (ind instanceof IndEvo_WorldWonder && ind.isFunctional()){
                   wonder = ind;
                   break;
               }
           }
        }

        if (wonder != null) market.getStability().modifyFlat("IndEvo_WorldWonder", STABILITY_BONUS, wonder.getMarket().getName() + " " + wonder.getNameForModifier());
    }

    public void unapply(String id) {
        market.getStability().unmodify("IndEvo_WorldWonder");
    }

    @Override
    public boolean showIcon() {
        return false;
    }
}