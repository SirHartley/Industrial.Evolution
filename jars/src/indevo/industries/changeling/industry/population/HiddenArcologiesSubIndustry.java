package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.fs.starfarer.api.impl.campaign.ids.Conditions.*;

public class HiddenArcologiesSubIndustry extends SubIndustry implements MarketImmigrationModifier {
    //Hidden Arcologies
    //•	Negate all conditions that affect the surface (temp, meteors…)
    //•	Increase defence
    //•	Decrease all output by 2
    //decrease accessibility by 15%
    //•   Decrease Pop Growth 30%
    //•	Two types for planets:
    //    o	Underwater Arcologies
    //    o	Underground Arcologies

    public static final float IMMIGRATION_PENALTY = 0.3f;
    public static final int SUPPLY_REDUCTION = 2;
    public static final float ACCESS_RED = 0.15f;
    public static final float DEFENCE_BONUS = 1f;
    public static final List<String> SUPRESSED_CONDITIONS = new ArrayList<>(Arrays.asList(COLD, HOT, TOXIC_ATMOSPHERE, DENSE_ATMOSPHERE, THIN_ATMOSPHERE, NO_ATMOSPHERE, EXTREME_WEATHER, IRRADIATED, INIMICAL_BIOSPHERE, METEOR_IMPACTS, POOR_LIGHT, DARK,
            "US_storm"));

    public HiddenArcologiesSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
        applyGlobalOutputReduction(SUPPLY_REDUCTION);

        MarketAPI market = industry.getMarket();

        market.getAccessibilityMod().modifyFlat(getId(), -ACCESS_RED, getName());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(getId(), 1f + DEFENCE_BONUS, getName());

        industry.getMarket().addImmigrationModifier(this);

        for (String s : SUPRESSED_CONDITIONS){
            market.suppressCondition(s);
        }
    }

    @Override
    public void unapply(){
        if (industry == null) return;

        MarketAPI market = industry.getMarket();

        for (Industry ind : market.getIndustries()){
            ind.getSupplyBonusFromOther().unmodify(getId());
            ind.getDemandReductionFromOther().unmodify(getId());
        }

        industry.getMarket().removeImmigrationModifier(this);

        market.getAccessibilityMod().unmodify(getId());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(getId());

        for (String s : SUPRESSED_CONDITIONS){
            market.unsuppressCondition(s);
        }
    }

    public void applyGlobalOutputReduction(int amt){
        for (Industry ind : industry.getMarket().getIndustries()){
            ind.getSupplyBonusFromOther().modifyFlat(getId(), -amt);
            ind.getDemandReductionFromOther().modifyFlat(getId(), amt);
        }
    }

    @Override
    public String getImageName(MarketAPI market) {
        PlanetSpecAPI planetSpec = market.getPrimaryEntity() instanceof PlanetAPI ? ((PlanetAPI) market.getPrimaryEntity()).getSpec() : null;

        if (planetSpec != null){
            if (planetSpec.getName().contains("water")) return Global.getSettings().getSpriteName("IndEvo", "pop_hidden_water");
        }

        return imageName;
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyPercent(getId(), 1 - IMMIGRATION_PENALTY, getName() + " - lower immigration");
    }
}
