package indevo.industries.changeling.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.List;

public interface ChangelingTarget {
    List<SubIndustryData> getOptions();

    String getCurrentId(MarketAPI market);

    void setCurrent(MarketAPI market, SubIndustryData data);

    String getTitle(Industry industry);

    // text behind the ? icon of the picker, null for no icon
    String getHelp();

    // value of the picker's Permanent row, null for no row
    String getPermanence(SubIndustryData data);

    List<EffectLine> getEffects(SubIndustryData data);
}
