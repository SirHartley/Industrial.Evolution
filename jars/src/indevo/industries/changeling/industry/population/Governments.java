package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.util.Misc;
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
            }
                    .gain("Income %s per stability point below 5", "x1.2")
                    .gain("Illegal goods are legal, their output %s", "+" + (int) UnderworldSubIndustry.BONUS_ILLEGAL_OUTPUT)
                    .gain("Takes %s of trader cargo in the system", Math.round(UnderworldSubIndustry.EXTORTION_SHARE * 100f) + "%")
                    .cost("Base stability %s (normally 5)", "" + (int) UnderworldSubIndustry.BASE_STABILITY_AMT)
                    .cost("Income falls as stability rises above 5")
                    .cost("No stability from domestic goods"));

            add(new SubIndustryData("rural", "Rural Polity", Global.getSettings().getSpriteName("IndEvo", "pop_rural"), "IndEvo_pop_rural", 80000) {
                @Override
                public SubIndustry newInstance() {
                    return new RuralPolitySubIndustry(this);
                }
            }
                    .gain("Growth %s, Luddic Path interest %s", "+" + RuralPolitySubIndustry.BASE_IMMIGRATION_INCREASE, "-" + (int) RuralPolitySubIndustry.BASE_PATHER_INTEREST_DECREASE)
                    .gain("Rural buildings: upkeep %s, stability %s, growth %s", off(RuralPolitySubIndustry.RURAL_UPKEEP_DECREASE),
                            "+" + RuralPolitySubIndustry.STABILITY_INCREASE_PER_RURAL, "+" + RuralPolitySubIndustry.IMMIGRATION_INCREASE_PER_RURAL)
                    .gain("Farming output %s, also makes organics", "+" + (int) RuralPolitySubIndustry.FARMING_BASE_BONUS)
                    .gain("Light Industry also makes supplies and weapons")
                    .cost("Industrial buildings: upkeep %s, stability %s", x(RuralPolitySubIndustry.INDUSTRIAL_UPKEEP_INCREASE), "-" + RuralPolitySubIndustry.STABILITY_DECREASE_PER_INDUSTRY)
                    .cost("Each AI core or industry item: stability %s", "-" + RuralPolitySubIndustry.INDUSTRY_ITEM_STABILITY_DECREASE)
                    .requires("Rich Farmland or better"));

            add(new SubIndustryData("hidden", "Hidden Arcology", Global.getSettings().getSpriteName("IndEvo", "pop_hidden"), "IndEvo_pop_hidden", 125000) {
                @Override
                public SubIndustry newInstance() {
                    return new HiddenArcologiesSubIndustry(this);
                }
            }
                    .gain("Ignores most surface hazard conditions")
                    .gain("Ground defenses %s", x(1f + HiddenArcologiesSubIndustry.DEFENCE_BONUS))
                    .cost("Industry output %s", "-" + HiddenArcologiesSubIndustry.SUPPLY_REDUCTION)
                    .cost("Accessibility %s", "-" + Math.round(HiddenArcologiesSubIndustry.ACCESS_RED * 100f) + "%")
                    .cost("Growth %s", "-" + Math.round(HiddenArcologiesSubIndustry.IMMIGRATION_PENALTY) + "%")
                    .requires("Planet, no gas giant"));

            add(new SubIndustryData("outpost", "Anchorage", Global.getSettings().getSpriteName("IndEvo", "pop_outpost"), "IndEvo_pop_outpost", 30000) {
                @Override
                public SubIndustry newInstance() {
                    return new OutpostSubIndustry(this);
                }
            }
                    .gain("Upkeep %s", off(OutpostSubIndustry.UPKEEP_RED_MULT))
                    .gain("Government can always be changed")
                    .cost("Colony size limited to %s", "" + OutpostSubIndustry.MAX_MARKET_SIZE)
                    .cost("No industries")
                    .requires("Colony size " + OutpostSubIndustry.MAX_MARKET_SIZE + " or less"));

            add(new SubIndustryData("helldivers", "Managed Democracy", Global.getSettings().getSpriteName("IndEvo", "pop_helldivers"), "IndEvo_pop_helldivers", 100000) {
                @Override
                public SubIndustry newInstance() {
                    return new HelldiversSubIndustry(this);
                }
            }
                    .gain("Stability never falls below %s", "" + HelldiversSubIndustry.MIN_STAB)
                    .gain("No Luddic Path activity")
                    .gain("Military upkeep %s", off(HelldiversSubIndustry.HELLDIVERS_UPKEEP_RED))
                    .gain("Extra patrols, more with colony size")
                    .gain("Stored marines gain experience")
                    .gain("Stored destroyers get Hellpods after %s days", "" + HelldiversSubIndustry.DAYS_TO_HELLPOD_REFIT)
                    .gain("Raids and bombardments raise income for months")
                    .cost("Income %s", "-" + Math.round(HelldiversSubIndustry.INCOME_RED) + "%")
                    .cost("Stability never rises above %s", "" + HelldiversSubIndustry.MAX_STAB)
                    .cost("Colony size limited to %s", "" + HelldiversSubIndustry.MAX_MARKET_SIZE)
                    .requires("Planet, colony size " + HelldiversSubIndustry.MAX_MARKET_SIZE + " or less"));

            add(new SubIndustryData("warhammer", "Forge World", Global.getSettings().getSpriteName("IndEvo", "pop_warhammer"), "IndEvo_pop_warhammer", 150000) {
                @Override
                public SubIndustry newInstance() {
                    return new WarhammerSubIndustry(this);
                }
            }
                    .gain("Industrial buildings ignore hazard upkeep while supplied")
                    .gain("Income %s per ship hull unit", "+" + Misc.getDGSCredits(WarhammerSubIndustry.FLAT_INCOME_PER_HULL))
                    .gain("Production budget %s per ship hull unit", "+" + Misc.getDGSCredits(WarhammerSubIndustry.FLAT_PRODUCTION_BUDGET_PER_HULL))
                    .gain("%s chance per month of a hand built ship", Math.round(WarhammerSubIndustry.BUILD_CHANCE_PER_MONTH * 100f) + "%")
                    .gain("Stability %s while metals are supplied", "+1")
                    .cost("Demands metals and transplutonics")
                    .cost("Rural buildings: upkeep %s", x(WarhammerSubIndustry.RURAL_BUILDING_UPKEEP_MULT))
                    .cost("Any shortage builds unrest")
                    .cost("Adds Pollution once permanent")
                    .requires("Extreme heat, no gas giant"));

            add(new SubIndustryData("corpos", "Corporate Governance", Global.getSettings().getSpriteName("IndEvo", "pop_corpos"), "IndEvo_pop_corpos", 50000) {
                @Override
                public SubIndustry newInstance() {
                    return new CorporateGovernanceSubIndustry(this);
                }
            }
                    .gain("Income %s", x(CorporateGovernanceSubIndustry.BASE_INCOME_MULT))
                    .gain("Stability %s per " + Misc.getDGSCredits(CorporateGovernanceSubIndustry.INCOME_PER_STAB) + " net income, up to %s", "+1", "" + (int) CorporateGovernanceSubIndustry.MAX_STAB_BONUS)
                    .gain("Disruptions last %s days at most", "" + (int) CorporateGovernanceSubIndustry.MAX_DISRUPTION_DAYS)
                    .gain("All industry slots once permanent")
                    .cost("Base stability %s (normally 5)", "" + (int) CorporateGovernanceSubIndustry.BASE_STAB)
                    .cost("Military upkeep %s", x(CorporateGovernanceSubIndustry.MIL_UPKEEP_INCREASE))
                    .cost("Growth %s, lower with more income", "-" + (int) CorporateGovernanceSubIndustry.BASE_IMMIGRATION_PENALTY)
                    .cost("The colony can shrink"));
        }
    };

    private static String x(float mult) {
        return "x" + Misc.getRoundedValueMaxOneAfterDecimal(mult);
    }

    private static String off(float mult) {
        return "-" + Math.round((1f - mult) * 100f) + "%";
    }

    public static SubIndustryData get(String id) {
        for (SubIndustryData data : LIST) if (data.id.equals(id)) return data;
        return null;
    }
}
