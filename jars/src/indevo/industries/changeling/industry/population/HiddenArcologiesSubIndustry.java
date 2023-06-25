package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class HiddenArcologiesSubIndustry extends SubIndustry {

    public HiddenArcologiesSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
    }

    @Override
    public String getImageName(MarketAPI market) {
        PlanetSpecAPI planetSpec = market.getPrimaryEntity() instanceof PlanetAPI ? ((PlanetAPI) market.getPrimaryEntity()).getSpec() : null;

        if (planetSpec != null){
            if (planetSpec.getName().contains("water")) return Global.getSettings().getSpriteName("IndEvo", "pop_hidden_water");
        }

        return imageName;
    }
}
