package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.impl.campaign.ids.Stats;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

import static com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.getMaxIndustries;
import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;

public class OutpostSubIndustry extends SubIndustry {

    public static final float MAX_MARKET_SIZE = 3f;
    public static final float UPKEEP_RED_MULT = 0.7f;


    public OutpostSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
        if (market.getSize() >= MAX_MARKET_SIZE) market.getPopulation().setWeight(getWeightForMarketSizeStatic(market.getSize()));
        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat(getId(), -getMaxIndustries(market.getSize()), getName());
        market.getUpkeepMult().modifyMult(getId(), UPKEEP_RED_MULT, getName());
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodify(getId());
        market.getUpkeepMult().unmodify(getId());
    }

    @Override
    public String getUnavailableReason() {
        if (market.getSize() > MAX_MARKET_SIZE) return "This planet is too populated";
        return super.getUnavailableReason();
    }
}
