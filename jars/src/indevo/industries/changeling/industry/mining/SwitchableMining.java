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
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.utils.helper.IndustryHelper;

import java.util.LinkedList;
import java.util.List;

public class SwitchableMining extends Mining implements SwitchableIndustryAPI {

    public static final List<SubIndustryAPI> industryList = new LinkedList<SubIndustryAPI>() {{

        add(new SubIndustry("base_mining", "graphics/icons/industry/mining.png", "Mining", "IndEvo_base_mining") {
            @Override
            public void apply(Industry industry) {
                applySupplyAndStandardDemandWithModifiers(industry, 0, 0, 0, 0);
            }

            @Override
            public boolean isBase() {
                return true;
            }
        });

        add(new SubIndustry("ore_mining", Global.getSettings().getSpriteName("IndEvo", "ore_mining"), "Ore Mining", "IndEvo_ore_mining", 10000, 31) {
            @Override
            public void apply(Industry industry) {
                applySupplyAndStandardDemandWithModifiers(industry, 2, -3, -3, -3);
            }
        });

        add(new SubIndustry("rare_mining", Global.getSettings().getSpriteName("IndEvo", "rare_mining"), "Transplutonics Mining", "IndEvo_rare_mining", 25000, 31) {
            @Override
            public void apply(Industry industry) {
                applySupplyAndStandardDemandWithModifiers(industry, -3, 2, -3, -3);
            }
        });

        add(new SubIndustry("volatile_mining", Global.getSettings().getSpriteName("IndEvo", "volatile_mining"), "Volatile Extraction", "IndEvo_volatile_mining", 50000, 31) {
            @Override
            public void apply(Industry industry) {
                applySupplyAndStandardDemandWithModifiers(industry, -3, -3, -3, 2);
            }
        });

        add(new SubIndustry("organics_mining", Global.getSettings().getSpriteName("IndEvo", "organics_mining"), "Organics Extraction", "IndEvo_organics_mining", 10000, 31) {
            @Override
            public void apply(Industry industry) {
                applySupplyAndStandardDemandWithModifiers(industry, -3, -3, 2, -3);
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
    public List<SubIndustryAPI> getIndustryList() {
        return industryList;
    }

    private SubIndustryAPI current = null;

    public void setCurrent(SubIndustryAPI current) {
        if (industryList.contains(current)) {
            this.current = current;
            reapply();
        }
    }

    @Override
    public SubIndustryAPI getCurrent() {
        return current;
    }

    public void apply() {
        supply.clear();
        demand.clear();

        super.apply(true); //since super does not override the baseIndustry overloaded apply we can call it here

        current.apply(this);

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
        current = getIndustryList().get(0);
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
}
