package indevo.industries.changeling.industry.population;

import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class CorporateGovernanceSubIndustry extends SubIndustry{

    public CorporateGovernanceSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
    }
}
