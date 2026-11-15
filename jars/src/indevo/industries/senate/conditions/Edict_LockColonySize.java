package indevo.industries.senate.conditions;

import indevo.industries.senate.EdictEffect;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

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
    public List<EdictEffect> getEffects(MarketAPI market) {
        List<EdictEffect> effects = new ArrayList<>();
        effects.add(EdictEffect.neutral("Population growth", "stopped"));
        return effects;
    }

    @Override
    public String getShortDesc() {
        return "Stops colony growth.";
    }
}
