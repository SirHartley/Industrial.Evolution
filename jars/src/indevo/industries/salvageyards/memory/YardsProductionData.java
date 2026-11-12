package indevo.industries.salvageyards.memory;

import com.fs.starfarer.api.Global;
import indevo.config.Setting;
import indevo.ids.ItemIds;

import java.util.HashMap;
import java.util.Map;

// saved in YardsCustomProductionIntel
public class YardsProductionData {
    public Map<String, Integer> productionList = new HashMap<>();
    public int rarePartsSpent = 0;
    public int partsSpent = 0;
    public float baseCost = 0;
    public WreckRegistry.Order registryOrder = null;

    public static float getPartValue() {
        return Math.round(Global.getSettings().getCommoditySpec(ItemIds.PARTS).getBasePrice() * Setting.SY_PART_VALUE_MULT.value());
    }

    public float getCost() {
        float fitCost = registryOrder == null ? 0f : registryOrder.fitCost;
        return baseCost + fitCost - (partsSpent * getPartValue());
    }

    public int getSalvageUnitCost() {
        return registryOrder == null ? 0 : registryOrder.salvageUnitCost;
    }

    public boolean hasOrder() {
        return !productionList.isEmpty() || registryOrder != null;
    }

    public int getRequiredParts() {
        return getFP() * Setting.RARE_PARTS_AMOUNT_PER_FP.intValue();
    }

    public int getMaxPartsAmt() {
        return (int) Math.round((baseCost / 2) / getPartValue());
    }

    private int getFP() {
        int totalFP = 0;

        for (Map.Entry<String, Integer> e : productionList.entrySet()) {
            totalFP += Global.getSettings().getHullSpec(e.getKey()).getFleetPoints() * e.getValue();
        }

        return totalFP;
    }

    public int getAverageDModAmount() {
        int requiredParts = getRequiredParts();
        if (requiredParts <= 0) return Setting.SY_BASE_DMODS.intValue();
        float percentageFilled = rarePartsSpent / (requiredParts * 1f);

        return (int) Math.ceil(Setting.SY_BASE_DMODS.intValue() - (Setting.SY_BASE_DMODS.intValue() * percentageFilled));
    }
}
