package indevo.industries.changeling.industry.population;

import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class RuralPolitySubIndustry extends SubIndustry {

    public RuralPolitySubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
    }

    //todo add image switch button like wonder
}
