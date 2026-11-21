package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.LinkedList;
import java.util.List;

public class Governments {

    public static final String BASE_ID = "base_population_and_infrastructure";

    public static final List<SubIndustryData> LIST = new LinkedList<SubIndustryData>() {
        {
            add(new SubIndustryData(BASE_ID, "Population & Infrastructure", "graphics/icons/industry/population.png", "IndEvo_pop_default", 10000) {
                @Override
                public SubIndustry newInstance() {
                    return new SubIndustry(this) {
                        @Override
                        public void apply() {
                        }

                        @Override
                        public String getImageName(MarketAPI market) {
                            float size = market.getSize();
                            if (size <= BaseIndustry.SIZE_FOR_SMALL_IMAGE) {
                                return Global.getSettings().getSpriteName("industry", "pop_low");
                            }
                            if (size >= BaseIndustry.SIZE_FOR_LARGE_IMAGE) {
                                return Global.getSettings().getSpriteName("industry", "pop_high");
                            }

                            return imageName;
                        }

                        @Override
                        public boolean isBase() {
                            return true;
                        }
                    };
                }
            });

            add(new SubIndustryData("underworld", "Underworld Governance", Global.getSettings().getSpriteName("IndEvo", "pop_underworld"), "IndEvo_pop_uw", 45000) {
                @Override
                public SubIndustry newInstance() {
                    return new UnderworldSubIndustry(this);
                }
            });

            add(new SubIndustryData("rural", "Rural Polity", Global.getSettings().getSpriteName("IndEvo", "pop_rural"), "IndEvo_pop_rural", 80000) {
                @Override
                public SubIndustry newInstance() {
                    return new RuralPolitySubIndustry(this);
                }
            });

            add(new SubIndustryData("hidden", "Hidden Arcology", Global.getSettings().getSpriteName("IndEvo", "pop_hidden"), "IndEvo_pop_hidden", 125000) {
                @Override
                public SubIndustry newInstance() {
                    return new HiddenArcologiesSubIndustry(this);
                }
            });

            add(new SubIndustryData("outpost", "Anchorage", Global.getSettings().getSpriteName("IndEvo", "pop_outpost"), "IndEvo_pop_outpost", 30000) {
                @Override
                public SubIndustry newInstance() {
                    return new OutpostSubIndustry(this);
                }
            });

            add(new SubIndustryData("helldivers", "Managed Democracy", Global.getSettings().getSpriteName("IndEvo", "pop_helldivers"), "IndEvo_pop_helldivers", 100000) {
                @Override
                public SubIndustry newInstance() {
                    return new HelldiversSubIndustry(this);
                }
            });

            add(new SubIndustryData("warhammer", "Forge World", Global.getSettings().getSpriteName("IndEvo", "pop_warhammer"), "IndEvo_pop_warhammer", 150000) {
                @Override
                public SubIndustry newInstance() {
                    return new WarhammerSubIndustry(this);
                }
            });

            add(new SubIndustryData("corpos", "Corporate Governance", Global.getSettings().getSpriteName("IndEvo", "pop_corpos"), "IndEvo_pop_corpos", 50000) {
                @Override
                public SubIndustry newInstance() {
                    return new CorporateGovernanceSubIndustry(this);
                }
            });
        }
    };

    public static SubIndustryData get(String id) {
        for (SubIndustryData data : LIST) if (data.id.equals(id)) return data;
        return null;
    }
}
