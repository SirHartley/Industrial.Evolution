package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
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

    public static final float IMMIGRATION_PENALTY = 30f;
    public static final int SUPPLY_REDUCTION = 2;
    public static final float ACCESS_RED = 0.3f;
    public static final float DEFENCE_BONUS = 1f;
    public static final List<String> SUPRESSED_CONDITIONS = new ArrayList<>(Arrays.asList(VERY_COLD, VERY_HOT, COLD, HOT, TOXIC_ATMOSPHERE, DENSE_ATMOSPHERE, THIN_ATMOSPHERE, NO_ATMOSPHERE, EXTREME_WEATHER, IRRADIATED, INIMICAL_BIOSPHERE, METEOR_IMPACTS,
            "US_storm"));

    public HiddenArcologiesSubIndustry(SubIndustryData data) {
        super(data);
    }

    @Override
    public void apply() {
        ((SwitchablePopulation) industry).superApply();
        applyGlobalOutputReduction(SUPPLY_REDUCTION);

        market.getAccessibilityMod().modifyFlat(getId(), -ACCESS_RED, getName());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(getId(), 1f + DEFENCE_BONUS, getName());

        market.addImmigrationModifier(this);

        for (String s : SUPRESSED_CONDITIONS){
            market.suppressCondition(s);
        }
    }

    @Override
    public void unapply(){
        if (industry == null) return;

        for (Industry ind : market.getIndustries()){
            ind.getSupplyBonusFromOther().unmodify(getId());
            ind.getDemandReductionFromOther().unmodify(getId());
        }

        market.removeImmigrationModifier(this);

        market.getAccessibilityMod().unmodify(getId());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(getId());

        for (String s : SUPRESSED_CONDITIONS){
            market.unsuppressCondition(s);
        }
    }

    public void applyGlobalOutputReduction(int amt){
        for (Industry ind : market.getIndustries()){
            ind.getSupplyBonusFromOther().modifyFlat(getId(), -amt);
            ind.getDemandReductionFromOther().modifyFlat(getId(), amt);
        }
    }

    @Override
    public String getImageName(MarketAPI market) {
        if (market.hasCondition(WATER_SURFACE)) return Global.getSettings().getSpriteName("IndEvo", "pop_hidden_water");
        return imageName;
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyPercent(getId(), -IMMIGRATION_PENALTY, getName() + " - lower immigration");
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild()
                && market.getPrimaryEntity() instanceof PlanetAPI
                && !market.getPrimaryEntity().hasTag(Tags.GAS_GIANT)
                && !((PlanetAPI) market.getPrimaryEntity()).getTypeId().contains("lava");
    }

    @Override
    public String getUnavailableReason() {
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Unavailable on stations";
        if (market.getPrimaryEntity().hasTag(Tags.GAS_GIANT)) return "Unavailable on gas giants";
        if (((PlanetAPI) market.getPrimaryEntity()).getTypeId().contains("lava")) return "Can not build a bunker in lava";
        return super.getUnavailableReason();
    }
}
