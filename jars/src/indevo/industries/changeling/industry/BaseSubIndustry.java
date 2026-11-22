package indevo.industries.changeling.industry;

public class BaseSubIndustry extends SubIndustry {

    public BaseSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
    }

    @Override
    public boolean isBase() {
        return true;
    }
}
