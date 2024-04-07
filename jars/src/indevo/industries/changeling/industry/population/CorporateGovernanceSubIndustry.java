package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.helper.Settings;
import indevo.utils.helper.StringHelper;

import static com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry.getDeficitText;
import static com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.*;

public class CorporateGovernanceSubIndustry extends SubIndustry implements MarketImmigrationModifier {

    /*
Corporate Governance
- Base income increased
x•	More income means higher stability
x•	Higher stability does not mean more income
x •	2 months max. disruption time
x•	All industry slots immediately available when locked
x •	negative base growth
x•	base stability = 1
not possible x •	Incentives are twice as expensive (or more?)
(check!) x•	Can shrink
x•	increase military upkeep by x3
*/

    public static final float MIL_UPKEEP_INCREASE = 3f;
    public static final float MAX_DISRUPTION_DAYS = 62f;
    public static final float INCOME_PER_STAB = 20000f;
    public static final float MAX_STAB_BONUS = 10f;
    public static final float BASE_INCOME_MULT = 1.3f;
    public static final float BASE_STAB = 0f;

    public static final float BASE_IMMIGRATION_PENALTY = 20f;
    public static final float INCOME_PER_IMMIGRATION_TIER = 10000;
    public static final float IMMIGRATION_PENALTY_PER_TIER = 1f;
    public static final float MAX_ADDITIONAL_IMMIGRATION_PENALTY = 30f;

    public static class CorpoPolityTooltipAdder extends BaseIndustryOptionProvider {
        public static void register() {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            if (!manager.hasListenerOfClass(CorpoPolityTooltipAdder.class))
                manager.addListener(new CorpoPolityTooltipAdder(), true);
        }

        @Override
        public boolean isUnsuitable(Industry ind, boolean allowUnderConstruction) {
            return !isSuitable(ind);
        }

        public boolean isSuitable(Industry ind) {
            Industry pop = ind.getMarket().getIndustry(Industries.POPULATION);
            boolean isCorpo = pop instanceof SwitchablePopulation && ((SwitchablePopulation) pop).getCurrent() instanceof CorporateGovernanceSubIndustry;
            return !Settings.getBoolean(Settings.GOVERNMENT_LARP_MODE) && isCorpo;
        }

        @Override
        public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
            if (isUnsuitable(ind, true)) return;
            float opad = 10f;

            boolean military = ind.getSpec().getTags().contains("military");

            tooltip.addSectionHeading("Governance Effects: Corporate Governance", Alignment.MID, opad);

            if (military) {
                tooltip.addPara("Military Building: %s increased by %s", opad, Misc.getTextColor(), Misc.getNegativeHighlightColor(), "upkeep", StringHelper.getAbsPercentString(MIL_UPKEEP_INCREASE, false));
            } else {
                tooltip.addPara("No effect on this industry.", opad);
            }
        }
    }

    public CorporateGovernanceSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        CorpoPolityTooltipAdder.register();

        MarketAPI market = industry.getMarket();
        SwitchablePopulation population = ((SwitchablePopulation) industry);

        population.superApply(); //applies default pop&Infra

        applyIndustryEffects();
        modifyStability(industry, market, population.getModId(3));
        market.addImmigrationModifier(this);

        resetSupplyProfile();

        if (population.locked) {
            market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat(getId(), getMaxIndustries(10) - getMaxIndustries(market.getSize()), null); //this gets max market industries immediately
        }

        modifyStability2(industry, market, population.getModId(3)); //overmax industry stability
    }

    @Override
    public void unapply() {
        super.unapply();

        market.removeImmigrationModifier(this);

        SwitchablePopulation population = ((SwitchablePopulation) industry);

        market.getIncomeMult().unmodify(population.getModId(1));
        market.getIncomeMult().unmodify(population.getModId(2));
        market.getIncomeMult().unmodify(population.getModId(3));

        for (Industry industry : market.getIndustries()) {
            if (industry.getSpec().getTags().contains("military")) {
                industry.getUpkeep().unmodify(getId());
            }
        }

        industry.getMarket().getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodifyFlat(getId());
    }

    public void applyIndustryEffects(){
        for (Industry industry : market.getIndustries()) {
            if (industry.getSpec().getTags().contains("military")) {
                industry.getUpkeep().modifyMult(getId(), MIL_UPKEEP_INCREASE, getName());
            }

            if (industry.isDisrupted() && industry.getDisruptedDays() > MAX_DISRUPTION_DAYS)
                industry.setDisrupted(MAX_DISRUPTION_DAYS);
        }
    }

    public void resetSupplyProfile(){
        BaseIndustry ind = (BaseIndustry) industry;

        int size = market.getSize();

        //clear
        ind.demand(Commodities.FOOD, 0);
        ind.demand(Commodities.DOMESTIC_GOODS, 0);
        ind.demand(Commodities.LUXURY_GOODS, 0);
        ind.demand(Commodities.DRUGS, 0);
        ind.demand(Commodities.ORGANS, 0);
        ind.demand(Commodities.SUPPLIES, 0);

        ind.supply(Commodities.CREW, 0);
        ind.supply(Commodities.DRUGS, 0);
        ind.supply(Commodities.ORGANS, 0);

        //set
        ind.demand(Commodities.FOOD, size);
        ind.demand(Commodities.DOMESTIC_GOODS, size + 1);
        ind.demand(Commodities.LUXURY_GOODS, size + 1);
        ind.demand(Commodities.DRUGS, size - 1);
        ind.demand(Commodities.ORGANS, size - 2);
        ind.demand(Commodities.SUPPLIES, Math.min(size, 3));

        ind.supply(Commodities.CREW, size - 3);
        ind.supply(Commodities.DRUGS, size - 4);
        ind.supply(Commodities.ORGANS, size - 4);
    }

    public static void modifyStability(Industry industry, MarketAPI market, String modId) {
        SwitchablePopulation population = ((SwitchablePopulation) industry);

        market.getIncomeMult().unmodifyMult(population.getModId()); //clear the old base mult set by normal pop&infra
        market.getIncomeMult().unmodifyMult(modId); //clear the default stability mult

        market.getIncomeMult().modifyMultAlways(population.getModId(2), BASE_INCOME_MULT, "Base (Corporate Governance)");

        float income = market.getNetIncome();
        double stab = income > 0 ? Math.min(Math.floor(income / INCOME_PER_STAB), MAX_STAB_BONUS) : 0;

        market.getStability().modifyFlat(modId, (float) stab, "Stability (Corporate Governance)");
        market.getIncomeMult().modifyMultAlways(modId, getIncomeStabilityMult(market.getPrevStability()), "Stability (Corporate Governance)");

        //clear existing stability base mults so it's 0
        for (MutableStat.StatMod mod : market.getStability().getFlatMods().values()) {
            if (mod.desc.toLowerCase().contains("_ms")) market.getStability().unmodify(mod.source);
        }

        //set it again so it _remains_ 0
        market.getStability().modifyFlat("_" + modId + "_ms", BASE_STAB, "Base value");
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getId(), -BASE_IMMIGRATION_PENALTY, getName() + " - Base");
        incoming.getWeight().modifyFlat(getId()+"_2", -Math.max(0, Math.min(MAX_ADDITIONAL_IMMIGRATION_PENALTY, (float) Math.ceil(IMMIGRATION_PENALTY_PER_TIER * (market.getNetIncome() / INCOME_PER_IMMIGRATION_TIER)))), getName() + " - Income");
    }
}
