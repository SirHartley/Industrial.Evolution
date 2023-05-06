package indevo.industries.changeling.industry;

import java.util.List;

public interface SwitchableIndustryAPI {
    List<SubIndustryAPI> getIndustryList();
    void setCurrent(SubIndustryAPI id);
    SubIndustryAPI getCurrent();
}
