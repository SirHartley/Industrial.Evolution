package indevo.industries.changeling.industry.mining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.Mining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.utils.helper.IndustryHelper;

import java.util.LinkedList;
import java.util.List;

public class SwitchableMining extends Mining implements SwitchableIndustryAPI {

    public static final List<SubIndustryData> industryList = new LinkedList<SubIndustryData>() {{
        add(new SubIndustryData("base_mining", "Mining", "graphics/icons/industry/mining.png", "IndEvo_base_mining", 10000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    @Override
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, 0, 0, 0, 0);
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
                        applySupplyAndStandardDemandWithModifiers(industry, 2, -2, -2, -2);
                    }
                };
            }
        });

        add(new SubIndustryData("rare_mining", "Transplutonics Mining", Global.getSettings().getSpriteName("IndEvo", "rare_mining"), "IndEvo_rare_mining", 25000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, -3, 2, -3, -3);
                    }
                };
            }
        });

        add(new SubIndustryData("volatile_mining", "Volatile Extraction", Global.getSettings().getSpriteName("IndEvo", "volatile_mining"), "IndEvo_volatile_mining", 50000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, -3, -3, -3, 2);
                    }
                };
            }
        });

        add(new SubIndustryData("organics_mining", "Organics Extraction", Global.getSettings().getSpriteName("IndEvo", "organics_mining"), "IndEvo_organics_mining", 10000) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, -3, -3, 2, -3);
                    }
                };
            }
        });
    }};

    public static void applySupplyAndStandardDemandWithModifiers(Industry industry, int oreMod, int rareOreMod, int organicsMod, int volatileMod) {
        applyConditionBasedIndustryOutputProfile(industry, Commodities.ORE, oreMod);
        applyConditionBasedIndustryOutputProfile(industry, Commodities.RARE_ORE, rareOreMod);
        applyConditionBasedIndustryOutputProfile(industry, Commodities.ORGANICS, organicsMod);
        applyConditionBasedIndustryOutputProfile(industry, Commodities.VOLATILES, volatileMod);

        BaseIndustry ind = (BaseIndustry) industry;
        int size = ind.getMarket().getSize();
        ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
        ind.demand(Commodities.DRUGS, size);

        Pair<String, Integer> deficit = ind.getMaxDeficit(Commodities.HEAVY_MACHINERY);
        IndustryHelper.applyDeficitToProduction(ind, 0, deficit,
                Commodities.ORE,
                Commodities.RARE_ORE,
                Commodities.ORGANICS,
                Commodities.VOLATILES);
    }

    public static void applyConditionBasedIndustryOutputProfile(Industry industry, String commodityId, Integer bonus) {
        industry.supply(industry.getId() + "_IndEvo_specialized", commodityId, bonus, "Specialized Extraction");
    }

    @Override
    public List<SubIndustryData> getIndustryList() {
        return industryList;
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    private SubIndustryAPI current = null;
    private String nameOverride = null;

    @Override
    public void apply() {
        if (!current.isInit()) current.init(this);
        current.apply();
        nameOverride = current.getName();

        super.apply(true); //since super does not override the baseIndustry overloaded apply we can call it here

        if (!isFunctional()) {
            supply.clear();
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    public void setCurrent(SubIndustryAPI current, boolean reapply) {
        String id = current.getId();
        boolean contains = false;

        for (SubIndustryData data : industryList)
            if (data.id.equals(id)) {
                contains = true;
                break;
            }

        if (contains) {
            current.init(this);

            if (this.current != null) this.current.unapply();
            this.current = current;

            if (reapply) reapply();
        } else
            throw new IllegalArgumentException("Switchable Industry List of " + getClass().getName() + " does not contain " + current.getName());
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public String getId() {
        return Industries.MINING;
    }

    @Override
    public String getModId() {
        return super.getModId();
    }

    @Override
    public String getModId(int index) {
        return super.getModId(index);
    }

    @Override
    public String getCurrentName() {
        return nameOverride == null ? super.getCurrentName() : nameOverride;
    }

    @Override
    public void init(String id, MarketAPI market) {
        if (current == null) setCurrent(getIndustryList().get(0).newInstance(), false);
        super.init(id, market);
    }

    @Override
    public String getCurrentImage() {
        return current.getImageName(market);
    }

    public boolean canChange() {
        return true;
    }

    @Override
    protected String getDescriptionOverride() {
        return current == null ? super.getDescriptionOverride() : current.getDescription().getText1();
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public float getPatherInterest() {
        return super.getPatherInterest();
    }
}
