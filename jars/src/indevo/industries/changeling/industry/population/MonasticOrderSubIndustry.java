package indevo.industries.changeling.industry.population;

import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class MonasticOrderSubIndustry extends SubIndustry {

    /*
Monastic Orders
•	-50% positive income
•	No luddic cells
•	Stability can’t fall below 3 or go above 7
•	Locked at size 5
•	Military industries -upkeep
•   Marines stored here passively gather experience
•	Randomly builds known frigates and destroyers, rarely cruisers (by hand with a special hullmod)*/

    public MonasticOrderSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
    }
}
