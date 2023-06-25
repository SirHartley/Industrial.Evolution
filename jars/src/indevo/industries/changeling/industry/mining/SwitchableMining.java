package indevo.industries.changeling.industry.mining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.Mining;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
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
        add(new SubIndustryData("base_mining", "Mining", "graphics/icons/industry/mining.png", "IndEvo_base_mining") {
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

        add(new SubIndustryData("ore_mining", "Ore Mining", Global.getSettings().getSpriteName("IndEvo", "ore_mining"), "IndEvo_ore_mining", 10000, 31) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, 2, -3, -3, -3);
                    }
                };
            }
        });

        add(new SubIndustryData("rare_mining", "Transplutonics Mining", Global.getSettings().getSpriteName("IndEvo", "rare_mining"), "IndEvo_rare_mining", 25000, 31) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, -3, 2, -3, -3);
                    }
                };
            }
        });

        add(new SubIndustryData("volatile_mining", "Volatile Extraction", Global.getSettings().getSpriteName("IndEvo", "volatile_mining"), "IndEvo_volatile_mining", 50000, 31) {
            @Override
            public SubIndustry newInstance() {
                return new SubIndustry(this) {
                    public void apply() {
                        applySupplyAndStandardDemandWithModifiers(industry, -3, -3, -3, 2);
                    }
                };
            }
        });

        add(new SubIndustryData("organics_mining", "Organics Extraction", Global.getSettings().getSpriteName("IndEvo", "organics_mining"), "IndEvo_organics_mining", 10000, 31) {
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
        BaseIndustry ind = (BaseIndustry) industry;
        int size = ind.getMarket().getSize();
        int mod = 0;
        MarketConditionAPI condition = null;

        for (MarketConditionAPI cond : ind.getMarket().getConditions()) {
            String id = cond.getSpec().getId();
            if (id.startsWith(commodityId)) {
                mod = ResourceDepositsCondition.MODIFIER.get(id);
                condition = cond;
                break;
            }
        }

        if (condition == null) return;

        int baseMod = ResourceDepositsCondition.BASE_MODIFIER.get(commodityId);

        if (ResourceDepositsCondition.BASE_ZERO.contains(commodityId)) {
            size = 0;
        }

        int base = size + baseMod;

        ind.supply(ind.getId() + "_0", commodityId, base + bonus, BaseIndustry.BASE_VALUE_TEXT);
        ind.supply(ind.getId() + "_1", commodityId, mod + bonus, Misc.ucFirst(condition.getName().toLowerCase()));
    }

    @Override
    public List<SubIndustryData> getIndustryList() {
        return industryList;
    }

    private SubIndustryAPI current = null;

    public void setCurrent(SubIndustryAPI current) {
        setCurrent(current, false);
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
            this.current = current;
            current.init(this);
            if (reapply) reapply();
        } else
            throw new IllegalArgumentException("Switchable Industry List of " + getClass().getName() + " does not contain " + current.getName());
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    @Override
    public void apply() {
        supply.clear();
        demand.clear();

        if (!current.isInit()) current.init(this);
        current.apply();

        super.apply(true); //since super does not override the baseIndustry overloaded apply we can call it here

        if (!isFunctional()) {
            supply.clear();
        }
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
        return current.getName();
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
