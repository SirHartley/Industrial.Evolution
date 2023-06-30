package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.Description;

public interface SubIndustryAPI {

    String getId();

    String getImageName(MarketAPI market);

    String getName();

    Description getDescription();

    void init(Industry industry);

    void apply();

    void unapply();

    boolean isBase();

    float getCost();

    float getBuildTime();

    boolean isAvailableToBuild();

    String getUnavailableReason();

    float getPatherInterest(Industry industry);

    void advance(float amt);

    boolean isInit();
}
