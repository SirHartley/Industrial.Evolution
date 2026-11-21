package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface SubIndustryAPI {

    String getId();

    String getImageName(MarketAPI market);

    String getName();

    Description getDescription();

    void init(Industry industry);

    void apply();

    void unapply();

    void preApplyToOther(Industry ind);

    void applyToOther(Industry ind);

    void unapplyFromOther(Industry ind);

    boolean isBase();

    float getCost();

    float getBuildTime();

    boolean isAvailableToBuild();

    String getUnavailableReason();

    float getPatherInterest(Industry industry);

    void advance(float amt);

    boolean isInit();

    // red line at the top of the government section, null for none
    String getWarning();

    // ongoing effects for the population tooltip; a government that owns a section keeps it when empty
    boolean hasOngoing();

    void addOngoing(TooltipMakerAPI tooltip, float width, boolean expanded);
}
