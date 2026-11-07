package indevo.industries.senate.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl.getWeightForMarketSizeStatic;

public class Edict_LockColonySize extends BaseEdict {

    private static final String WEIGHT_MEMORY_KEY = "$IndEvo_WeightMemory";

    @Override
    public void apply(String id) {
        super.apply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getMemoryWithoutUpdate().set(WEIGHT_MEMORY_KEY, market.getPopulation().getWeightValue());
        market.getPopulation().setWeight(getWeightForMarketSizeStatic(market.getSize()));
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        if (market.getPrimaryEntity() == null) return;

        market.getPopulation().setWeight(market.getMemoryWithoutUpdate().getFloat(WEIGHT_MEMORY_KEY));
    }

    @Override
    public void addEffectText(TooltipMakerAPI tooltip, MarketAPI market, float pad) {
        tooltip.addPara("Completely stops this colony from %s.", pad, Misc.getHighlightColor(), "growing");
    }

    @Override
    public String getShortDesc() {
        return "Stops colony growth.";
    }
}
