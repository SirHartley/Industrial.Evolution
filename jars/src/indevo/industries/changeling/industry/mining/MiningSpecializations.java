package indevo.industries.changeling.industry.mining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import indevo.industries.changeling.industry.Specialization;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.LinkedList;
import java.util.List;

public class MiningSpecializations {

    public static final String BASE_ID = "base_mining";

    public static final List<SubIndustryData> LIST = new LinkedList<SubIndustryData>() {{
        add(new SubIndustryData(BASE_ID, "Mining", "graphics/icons/industry/mining.png", "IndEvo_base_mining", 10000) {
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

        add(new SubIndustryData("ore_mining", "Ore Mining", Global.getSettings().getSpriteName("IndEvo", "ore_mining"), "IndEvo_ore_mining", 10000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applyOutputDeltas(industry, 40, -40, -40, -40);
                    }
                };
            }
        }.gain("Ore output %s", "+40%").cost("Other mining output %s", "-40%"));

        add(new SubIndustryData("rare_mining", "Transplutonics Mining", Global.getSettings().getSpriteName("IndEvo", "rare_mining"), "IndEvo_rare_mining", 25000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applyOutputDeltas(industry, -50, 40, -50, -50);
                    }
                };
            }
        }.gain("Transplutonic ore output %s", "+40%").cost("Other mining output %s", "-50%"));

        add(new SubIndustryData("volatile_mining", "Volatile Extraction", Global.getSettings().getSpriteName("IndEvo", "volatile_mining"), "IndEvo_volatile_mining", 50000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applyOutputDeltas(industry, -50, -50, -50, 40);
                    }
                };
            }
        }.gain("Volatiles output %s", "+40%").cost("Other mining output %s", "-50%"));

        add(new SubIndustryData("organics_mining", "Organics Extraction", Global.getSettings().getSpriteName("IndEvo", "organics_mining"), "IndEvo_organics_mining", 10000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applyOutputDeltas(industry, -50, -50, 40, -50);
                    }
                };
            }
        }.gain("Organics output %s", "+40%").cost("Other mining output %s", "-50%"));
    }};

    // percent of each line, close to the old flat +2 and -2 or -3 on a size 6 colony
    public static void applyOutputDeltas(Industry industry, float oreMod, float rareOreMod, float organicsMod, float volatileMod) {
        Specialization.addSupply(industry, Commodities.ORE, oreMod);
        Specialization.addSupply(industry, Commodities.RARE_ORE, rareOreMod);
        Specialization.addSupply(industry, Commodities.ORGANICS, organicsMod);
        Specialization.addSupply(industry, Commodities.VOLATILES, volatileMod);
    }
}
