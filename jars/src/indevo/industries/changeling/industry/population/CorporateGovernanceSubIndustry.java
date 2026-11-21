package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.changeling.conditions.GovernmentCondition;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.helper.StringHelper;

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

    public static final String MAX_GROWTH_TAG = "IndEvo_CorpoGov_MaxSize";
    public static final float MIL_UPKEEP_INCREASE = 3f;
    public static final float MAX_DISRUPTION_DAYS = 62f;
    public static final float INCOME_PER_STAB = 20000f;
    public static final float MAX_STAB_BONUS = 10f;
    public static final float BASE_INCOME_MULT = 1.3f;
    public static final float BASE_STAB = 0f;

    public static final float BASE_IMMIGRATION_PENALTY = 20f;
    public static final float INCOME_PER_IMMIGRATION_TIER = 20000;
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
            boolean isCorpo = GovernmentCondition.getGovernment(ind.getMarket()) instanceof CorporateGovernanceSubIndustry;
            return !Setting.GOVERNMENT_LARP_MODE.enabled() && isCorpo;
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

        modifyStability(modId(3));
        market.removeImmigrationModifier(this);
        market.addImmigrationModifier(this);

        resetSupplyProfile();

        if (GovernmentCondition.isLocked(market)) {
            market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat(getId(), getMaxIndustries(10) - getMaxIndustries(market.getSize()), null); //this gets max market industries immediately
        }

        modifyStability2(industry, market, modId(3)); //overmax industry stability, after the max industries bonus

        market.getMemoryWithoutUpdate().set(MemFlags.MARKET_CAN_ALWAYS_INCENTIVIZE_GROWTH, true);

        int maxSize = Misc.getMaxMarketSize(market);
        int size = market.getSize();

        if (size == maxSize){
            market.addTag(MAX_GROWTH_TAG);
            //market.getStats().getDynamic().getMod(Stats.MAX_MARKET_SIZE).modifyFlat(getId(), maxSize + 1, getName());
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        market.removeImmigrationModifier(this);

        market.getIncomeMult().unmodify(modId(1));
        market.getIncomeMult().unmodify(modId(2));
        market.getIncomeMult().unmodify(modId(3));

        market.removeTag(MAX_GROWTH_TAG);
        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodifyFlat(getId());
        market.getMemoryWithoutUpdate().unset(MemFlags.MARKET_CAN_ALWAYS_INCENTIVIZE_GROWTH);
        //market.getStats().getDynamic().getMod(Stats.MAX_MARKET_SIZE).unmodify(getId());
    }

    @Override
    public void applyToOther(Industry ind) {
        if (ind.getSpec().getTags().contains("military")) {
            ind.getUpkeep().modifyMult(getId(), MIL_UPKEEP_INCREASE, getName());
        }

        if (ind.isDisrupted() && ind.getDisruptedDays() > MAX_DISRUPTION_DAYS)
            ind.setDisrupted(MAX_DISRUPTION_DAYS);
    }

    @Override
    public void unapplyFromOther(Industry ind) {
        ind.getUpkeep().unmodify(getId());
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

    // overwrites the host's stability base value and income mult under its own keys
    public void modifyStability(String modId) {
        market.getIncomeMult().unmodifyMult(modId()); //clear the old base mult set by normal pop&infra
        market.getIncomeMult().unmodifyMult(modId); //clear the default stability mult

        market.getIncomeMult().modifyMultAlways(modId(2), BASE_INCOME_MULT, "Base (Corporate Governance)");

        float income = market.getNetIncome();
        double stab = income > 0 ? Math.min(Math.floor(income / INCOME_PER_STAB), MAX_STAB_BONUS) : 0;

        market.getStability().modifyFlat(modId, (float) stab, "Stability (Corporate Governance)");
        market.getIncomeMult().modifyMultAlways(modId, getIncomeStabilityMult(market.getPrevStability()), "Stability (Corporate Governance)");

        //set it again so it _remains_ 0
        market.getStability().modifyFlat("_" + modId + "_ms", BASE_STAB, "Base value");
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getId(), -BASE_IMMIGRATION_PENALTY, getName() + " - Base");
        incoming.getWeight().modifyFlat(getId()+"_2", -Math.max(0, Math.min(MAX_ADDITIONAL_IMMIGRATION_PENALTY, (float) Math.ceil(IMMIGRATION_PENALTY_PER_TIER * (market.getNetIncome() / INCOME_PER_IMMIGRATION_TIER)))), getName() + " - Income");
    }
}
