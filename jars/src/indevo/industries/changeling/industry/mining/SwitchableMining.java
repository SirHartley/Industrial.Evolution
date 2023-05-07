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

    public static final List<SubIndustryAPI> industryList = new LinkedList<SubIndustryAPI>(){{

        add(new SubIndustry("base_mining", "graphics/icons/industry/mining.png", "Specialized Mining", "IndEvo_base_mining") {
            @Override
            public void apply(Industry industry) {
                if (industry instanceof SwitchableMining) ((SwitchableMining) industry).superApply(); //applies default
            }

            @Override
            public boolean isBase() {
                return true;
            }
        });

        add(new SubIndustry("ore_mining", Global.getSettings().getSpriteName("IndEvo", "ore_mining"), "Ore Mining", "IndEvo_ore_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.ORE, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                IndustryHelper.applyDeficitToProduction(industry, 0, deficit, Commodities.ORE);
            }
        });

        add(new SubIndustry("rare_mining", Global.getSettings().getSpriteName("IndEvo", "rare_mining"), "Transplutonics Mining", "IndEvo_rare_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.RARE_ORE, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                IndustryHelper.applyDeficitToProduction(industry, 0, deficit, Commodities.RARE_ORE);
            }
        });

        add(new SubIndustry("volatile_mining", Global.getSettings().getSpriteName("IndEvo", "volatile_mining"), "Volatile Extraction", "IndEvo_volatile_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.VOLATILES, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                IndustryHelper.applyDeficitToProduction(industry, 0, deficit, Commodities.VOLATILES);
            }
        });

        add(new SubIndustry("organics_mining", Global.getSettings().getSpriteName("IndEvo", "organics_mining"), "Organics Extraction", "IndEvo_organics_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.ORGANICS, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                IndustryHelper.applyDeficitToProduction(industry, 0, deficit, Commodities.ORGANICS);
            }
        });
    }};


    public static void applyConditionBasedIndustryOutputProfile(Industry industry, String commodityId, Integer bonus){
        BaseIndustry ind = (BaseIndustry) industry;
        int size = ind.getMarket().getSize();
        int mod = 0;
        MarketConditionAPI condition = null;

        for (MarketConditionAPI cond : ind.getMarket().getConditions()){
            String id = cond.getSpec().getId();
            if (id.startsWith(commodityId)){
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
        if (industryList.contains(current)){
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

    public void superApply(){
        supply.clear();
        demand.clear();

        super.apply();
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

    public boolean canChange(){
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
