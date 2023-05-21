package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.Description;

public interface SubIndustryAPI {
    String getId();

    String getImageName(MarketAPI market);

    String getName();

    Description getDescription();

    void apply(Industry industry);

    boolean isBase();

    float getCost();

    float getBuildTime();
}
