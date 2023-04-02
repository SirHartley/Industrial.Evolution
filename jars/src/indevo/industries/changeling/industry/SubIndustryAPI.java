package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.loading.Description;

public interface SubIndustryAPI {
    String getId();
    String getImage();
    String getName();
    Description getDescription();
    void apply(Industry industry);
}
