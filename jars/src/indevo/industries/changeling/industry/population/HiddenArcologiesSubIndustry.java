package indevo.industries.changeling.industry.population;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.Farming;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import indevo.ids.Ids;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.utils.helper.MiscIE;

import java.util.Set;

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

        Set<String> supressedConditionIDs = MiscIE.getCSVSetFromMemory(Ids.ARCOLOGIES_COND_LIST);
        for (String s : supressedConditionIDs){
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

        Set<String> supressedConditionIDs = MiscIE.getCSVSetFromMemory(Ids.ARCOLOGIES_COND_LIST);
        for (String s : supressedConditionIDs){
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
        if (market.getPrimaryEntity() instanceof PlanetAPI
                && Farming.AQUA_PLANETS.contains(((PlanetAPI) market.getPrimaryEntity()).getTypeId())) return Global.getSettings().getSpriteName("IndEvo", "pop_hidden_water");
        return imageName;
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyPercent(getId(), -IMMIGRATION_PENALTY, getName() + " - lower immigration");
    }

    @Override
    public boolean isAvailableToBuild() {
        Set<String> lavaPlanetIDs = MiscIE.getCSVSetFromMemory(Ids.ARCOLOGIES_LIST);
        return super.isAvailableToBuild()
                && market.getPrimaryEntity() instanceof PlanetAPI
                && !market.getPrimaryEntity().hasTag(Tags.GAS_GIANT)
                && !lavaPlanetIDs.contains(((PlanetAPI) market.getPrimaryEntity()).getTypeId());
    }

    @Override
    public String getUnavailableReason() {
        Set<String> lavaPlanetIDs = MiscIE.getCSVSetFromMemory(Ids.ARCOLOGIES_LIST);
        if (!(market.getPrimaryEntity() instanceof PlanetAPI)) return "Unavailable on stations";
        if (market.getPrimaryEntity().hasTag(Tags.GAS_GIANT)) return "Unavailable on gas giants";
        if (lavaPlanetIDs.contains(((PlanetAPI) market.getPrimaryEntity()).getTypeId())) return "Can not build a bunker in lava";
        return super.getUnavailableReason();
    }
}
