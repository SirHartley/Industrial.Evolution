package indevo.industries.changeling.industry.refining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.LinkedList;
import java.util.List;

public class RefiningSpecializations {

    public static final String BASE_ID = "base_refining";

    public static final List<SubIndustryData> LIST = new LinkedList<SubIndustryData>() {{
        add(new SubIndustryData(BASE_ID, "Refining", "graphics/icons/industry/refining.png", "IndEvo_base_refining", 10000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
                    }

                    @Override
                    public boolean isBase() {
                        return true;
                    }
                };
            }
        });

        add(new SubIndustryData("ore_refining", "Ore Refinery", Global.getSettings().getSpriteName("IndEvo", "ore_refining"), "IndEvo_ore_refining", 15000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
                        applyOutputDeltas(industry, -40, 40);
                    }
                };
            }
        });

        add(new SubIndustryData("rare_refining", "Transplutonics Refinery", Global.getSettings().getSpriteName("IndEvo", "rare_ore_refining"), "IndEvo_rare_refining", 25000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
                        applyOutputDeltas(industry, 40, -40);
                    }
                };
            }
        });
    }};

    // percent of each line, ore demand moves by 3/4 of its output so a size 6 colony matches the old flat +-2
    public static void applyOutputDeltas(Industry industry, float transplutonicsMod, float metalMod) {
        Specialization.addSupply(industry, Commodities.RARE_METALS, transplutonicsMod);
        Specialization.addSupply(industry, Commodities.METALS, metalMod);
        Specialization.addDemand(industry, Commodities.RARE_ORE, transplutonicsMod * 0.75f);
        Specialization.addDemand(industry, Commodities.ORE, metalMod * 0.75f);
    }
}
