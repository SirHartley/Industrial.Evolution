package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

public interface SubIndustryDataAPI {
    SubIndustry newInstance();
    String getImageName(MarketAPI market);
}
