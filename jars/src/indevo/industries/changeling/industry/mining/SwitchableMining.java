package indevo.industries.changeling.industry.mining;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.changeling.industry.BaseSwitchableIndustry;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryAPI;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public class SwitchableMining extends BaseSwitchableIndustry implements MarketImmigrationModifier {

    public static final List<SubIndustryAPI> industryList = new LinkedList<SubIndustryAPI>(){{

        add(new SubIndustry("base_mining", "Specialized Mining", "graphics/icons/industry/mining.png", "IndEvo_base_mining") {
            @Override
            public void apply(Industry industry) {

                applyConditionBasedIndustryOutputProfile(industry, Commodities.ORE, 0);
                applyConditionBasedIndustryOutputProfile(industry, Commodities.RARE_ORE, 0);
                applyConditionBasedIndustryOutputProfile(industry, Commodities.ORGANICS, 0);
                applyConditionBasedIndustryOutputProfile(industry, Commodities.VOLATILES, 0);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  ind.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                applyDeficitToProduction(ind, 0, deficit,
                        Commodities.ORE,
                        Commodities.RARE_ORE,
                        Commodities.ORGANICS,
                        Commodities.VOLATILES);
            }
        });

        add(new SubIndustry("ore_mining", "Ore Mining", Global.getSettings().getSpriteName("IndEvo", "ore_mining"), "IndEvo_ore_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.ORE, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                applyDeficitToProduction(industry, 0, deficit, Commodities.ORE);
            }
        });

        add(new SubIndustry("rare_mining", "Transplutonics Mining", Global.getSettings().getSpriteName("IndEvo", "rare_mining"), "IndEvo_rare_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.RARE_ORE, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                applyDeficitToProduction(industry, 0, deficit, Commodities.RARE_ORE);
            }
        });

        add(new SubIndustry("volatile_mining", "Volatile Extraction", Global.getSettings().getSpriteName("IndEvo", "volatile_mining"), "IndEvo_volatile_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.VOLATILES, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                applyDeficitToProduction(industry, 0, deficit, Commodities.VOLATILES);
            }
        });

        add(new SubIndustry("organics_mining", "Organics Extraction", Global.getSettings().getSpriteName("IndEvo", "organics_mining"), "IndEvo_organics_mining") {
            @Override
            public void apply(Industry industry) {
                applyConditionBasedIndustryOutputProfile(industry, Commodities.ORGANICS, 2);

                BaseIndustry ind = (BaseIndustry) industry;
                int size = ind.getMarket().getSize();
                ind.demand(Commodities.HEAVY_MACHINERY, size - 3);
                ind.demand(Commodities.DRUGS, size);

                Pair<String, Integer> deficit =  industry.getMaxDeficit(Commodities.HEAVY_MACHINERY);
                applyDeficitToProduction(industry, 0, deficit, Commodities.ORGANICS);
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

    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        Pair<String, Integer> deficit = getMaxDeficit(Commodities.DRUGS);
        if (deficit.two <= 0) return false;
        //return mode == IndustryTooltipMode.NORMAL && isFunctional();
        return mode != IndustryTooltipMode.NORMAL || isFunctional();
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        //if (mode == IndustryTooltipMode.NORMAL && isFunctional()) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            Color h = Misc.getHighlightColor();
            float opad = 10f;
            float pad = 3f;

            Pair<String, Integer> deficit = getMaxDeficit(Commodities.DRUGS);
            if (deficit.two > 0) {
                tooltip.addPara(getDeficitText(Commodities.DRUGS) + ": %s units. Reduced colony growth.", pad, h, "" + deficit.two);
            }
        }
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        Pair<String, Integer> deficit = getMaxDeficit(Commodities.DRUGS);
        if (deficit.two > 0) {
            incoming.getWeight().modifyFlat(getModId(), -deficit.two, "Mining: drug shortage");
        }
    }

    public float getPatherInterest() {
        return 1f + super.getPatherInterest();
    }

    public void applyVisuals(PlanetAPI planet) {
        if (planet == null) return;
        planet.getSpec().setShieldTexture2(Global.getSettings().getSpriteName("industry", "plasma_net_texture"));
        planet.getSpec().setShieldThickness2(0.15f);
        //planet.getSpec().setShieldColor2(new Color(255,255,255,175));
        planet.getSpec().setShieldColor2(new Color(255,255,255,255));
        planet.applySpecChanges();
        shownPlasmaNetVisuals = true;
    }

    public void unapplyVisuals(PlanetAPI planet) {
        if (planet == null) return;
        planet.getSpec().setShieldTexture2(null);
        planet.getSpec().setShieldThickness2(0f);
        planet.getSpec().setShieldColor2(null);
        planet.applySpecChanges();
        shownPlasmaNetVisuals = false;
    }

    protected boolean shownPlasmaNetVisuals = false;

    @Override
    public void setSpecialItem(SpecialItemData special) {
        super.setSpecialItem(special);

        if (shownPlasmaNetVisuals && (special == null || !special.getId().equals(Items.PLASMA_DYNAMO))) {
            unapplyVisuals(market.getPlanetEntity());
        }

        if (special != null && special.getId().equals(Items.PLASMA_DYNAMO)) {
            applyVisuals(market.getPlanetEntity());
        }
    }
}
