package indevo.industries.changeling.industry.population;

import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class MonasticOrderSubIndustry extends SubIndustry {

    public MonasticOrderSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
    }
}
