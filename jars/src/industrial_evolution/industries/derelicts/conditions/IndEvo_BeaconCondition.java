package industrial_evolution.industries.derelicts.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import industrial_evolution.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class IndEvo_BeaconCondition extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    //Blessed be who walks on the glimmering rays, for salvation is within reach.

    /*
     * Increases pop growth,
     * stability and
     * base colony income (pop&infra)
     * and ground defence
     * Increases the effects of AI-core Administrator skills*/

    public static float STABILITY_PENALTY = 0;
    private float beaconMult = 0f;

    public void applyBeaconMult(Float mult) {
        if (mult > beaconMult) beaconMult = mult;
    }

    public void apply(String id) {
        super.apply(id);

        if (market.hasIndustry(IndEvo_ids.PIRATEHAVEN)) {
            STABILITY_PENALTY = market.getFaction().isHostileTo(Factions.PIRATES) ? 0 : 2;
        } else {
            STABILITY_PENALTY = market.getFaction().isHostileTo(Factions.PIRATES) ? -2 : 0;
        }

        market.getStability().modifyFlat(id, STABILITY_PENALTY, "");
        market.addTransientImmigrationModifier(this);
    }

    public void unapply(String id) {
        super.unapply(id);
        market.getStability().unmodify(id);

        market.removeTransientImmigrationModifier(this);
    }

    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.add(Factions.POOR, 10f);
        incoming.getWeight().modifyFlat(getModId(), getImmigrationBonus(), Misc.ucFirst(condition.getName().toLowerCase()));
    }

    protected float getImmigrationBonus() {
        float immigrationIncrease;

        if (market.hasIndustry(IndEvo_ids.PIRATEHAVEN)) {
            immigrationIncrease = market.getFaction().isHostileTo(Factions.PIRATES) ? 0 : market.getSize();

        } else {
            immigrationIncrease = market.getFaction().isHostileTo(Factions.PIRATES) ? -Math.round(market.getSize()) : -Math.round(market.getSize() / 2f);
        }
        return immigrationIncrease;
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        if (!market.getFaction().isHostileTo(Factions.PIRATES) || !market.hasIndustry(IndEvo_ids.PIRATEHAVEN)) {
            String s = STABILITY_PENALTY > 0 ? "+" + STABILITY_PENALTY : "" + STABILITY_PENALTY;
            String t = getImmigrationBonus() > 0 ? "+" + getImmigrationBonus() : "" + getImmigrationBonus();

            tooltip.addPara("+%s stability.",
                    10f, Misc.getHighlightColor(),
                    s);
            tooltip.addPara("%s population growth (based on market size).",
                    10f, Misc.getHighlightColor(),
                    t);
        } else {
            tooltip.addPara("%s, you do not have any friends amongst the sector underworld.",
                    10f, Misc.getNegativeHighlightColor(),
                    "No effect");
        }
    }
}