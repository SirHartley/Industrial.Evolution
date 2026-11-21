package indevo.industries.changeling.industry.mining;

import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

public class MiningSubIndustry extends SubIndustry {

    private final String focus;
    private final float malus;

    public MiningSubIndustry(SubIndustryData data, String focus, float malus) {
        super(data);
        this.focus = focus;
        this.malus = malus;
    }

    @Override
    public void apply() {
        for (String id : MiningSpecializations.COMMODITIES) Specialization.addSupply(industry, id, id.equals(focus) ? MiningSpecializations.FOCUS : malus);
    }
}
