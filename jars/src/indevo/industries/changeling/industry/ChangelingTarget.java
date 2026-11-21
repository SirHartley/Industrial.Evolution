package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.List;

public interface ChangelingTarget {
    List<SubIndustryData> getOptions();

    String getCurrentId(MarketAPI market);

    void setCurrent(MarketAPI market, SubIndustryData data);
}
