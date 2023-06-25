package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.campaign.econ.Industry;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class RuralPolitySubIndustry extends SubIndustry {

    public RuralPolitySubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply(Industry industry) {
        ((SwitchablePopulation) industry).superApply();
    }

    //todo add image switch button like wonder
}
