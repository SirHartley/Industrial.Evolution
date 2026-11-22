package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.industries.changeling.industry.BaseSubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class BaseGovernmentSubIndustry extends BaseSubIndustry {

    public BaseGovernmentSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public String getImageName(MarketAPI market) {
        float size = market.getSize();
        if (size <= BaseIndustry.SIZE_FOR_SMALL_IMAGE) return Global.getSettings().getSpriteName("industry", "pop_low");
        if (size >= BaseIndustry.SIZE_FOR_LARGE_IMAGE) return Global.getSettings().getSpriteName("industry", "pop_high");
        return imageName;
    }
}
