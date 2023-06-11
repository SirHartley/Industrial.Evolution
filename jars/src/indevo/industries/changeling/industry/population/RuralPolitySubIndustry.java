package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.campaign.econ.Industry;
import indevo.industries.changeling.industry.SubIndustry;

public class RuralPolitySubIndustry extends SubIndustry {

    public RuralPolitySubIndustry(String id, String name, String imageName, String descriptionID) {
        super(id, imageName, name, descriptionID);
    }

    @Override
    public void apply(Industry industry) {
        ((SwitchablePopulation) industry).superApply();
    }

    //todo add image switch button like wonder
}
