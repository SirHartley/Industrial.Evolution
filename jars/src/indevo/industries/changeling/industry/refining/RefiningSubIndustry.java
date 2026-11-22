package indevo.industries.changeling.industry.refining;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class RefiningSubIndustry extends SubIndustry {

    private final float transplutonics;
    private final float metals;

    public RefiningSubIndustry(SubIndustryData data, float transplutonics, float metals) {
        super(data);
        this.transplutonics = transplutonics;
        this.metals = metals;
    }

    // ore demand moves by a share of its output so a size 6 colony matches the old flat +-2
    @Override
    public void apply() {
        Specialization.addSupply(industry, Commodities.RARE_METALS, transplutonics);
        Specialization.addSupply(industry, Commodities.METALS, metals);
        Specialization.addDemand(industry, Commodities.RARE_ORE, transplutonics * RefiningSpecializations.DEMAND_SHARE);
        Specialization.addDemand(industry, Commodities.ORE, metals * RefiningSpecializations.DEMAND_SHARE);
    }
}
