package indevo.industries.changeling.industry;

import java.util.List;

public interface SwitchableIndustryAPI {
    List<SubIndustryData> getIndustryList();

    void setCurrent(SubIndustryAPI id, boolean reapply);

    SubIndustryAPI getCurrent();

    boolean canChange();
}
