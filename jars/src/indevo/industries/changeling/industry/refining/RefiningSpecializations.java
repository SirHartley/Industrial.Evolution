package indevo.industries.changeling.industry.refining;

import com.fs.starfarer.api.Global;
import indevo.industries.changeling.industry.BaseSubIndustry;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.List;

public class RefiningSpecializations {

    public static final String BASE_ID = "base_refining";

    public static final float FOCUS = 40f;
    public static final float MALUS = -40f;
    public static final float DEMAND_SHARE = 0.75f;

    public static final List<SubIndustryData> LIST = List.of(
            new SubIndustryData(BASE_ID, "Refining", "graphics/icons/industry/refining.png", "IndEvo_base_refining", 10000, BaseSubIndustry::new),
            new SubIndustryData("ore_refining", "Ore Refinery", Global.getSettings().getSpriteName("IndEvo", "ore_refining"), "IndEvo_ore_refining", 15000,
                    data -> new RefiningSubIndustry(data, MALUS, FOCUS))
                    .gain("Metals output %s", Specialization.percent(FOCUS))
                    .cost("Transplutonics output %s", Specialization.percent(MALUS)),
            new SubIndustryData("rare_refining", "Transplutonics Refinery", Global.getSettings().getSpriteName("IndEvo", "rare_ore_refining"), "IndEvo_rare_refining", 25000,
                    data -> new RefiningSubIndustry(data, FOCUS, MALUS))
                    .gain("Transplutonics output %s", Specialization.percent(FOCUS))
                    .cost("Metals output %s", Specialization.percent(MALUS)));
}
