package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;

import java.awt.*;

public class RelicHelper {

    public static String getName() {
        return Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getName();
    }

    public static int getAvailable(MarketAPI market) {
        return (int) (getInStorage(market) + MiscIE.getPlayerCargo().getCommodityQuantity(ItemIds.RARE_PARTS));
    }

    public static boolean canAfford(MarketAPI market, int amount) {
        return getAvailable(market) >= amount;
    }

    public static void take(MarketAPI market, int amount) {
        if (!canAfford(market, amount)) throw new IllegalStateException("took " + amount + " relics with only " + getAvailable(market) + " available, caller skipped canAfford");

        float remaining = amount;
        float inStorage = getInStorage(market);

        if (inStorage > 0) {
            float take = Math.min(remaining, inStorage);
            Misc.getStorageCargo(market).removeCommodity(ItemIds.RARE_PARTS, take);
            remaining -= take;
        }

        if (remaining > 0) MiscIE.getPlayerCargo().removeCommodity(ItemIds.RARE_PARTS, remaining);
    }

    public static LabelAPI addCostLine(TooltipMakerAPI info, MarketAPI market, int cost, float pad) {
        int available = getAvailable(market);
        Color color = available >= cost ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
        return info.addPara("Cost: %s (Available: " + available + ")", pad, color, cost + " " + getName());
    }

    private static float getInStorage(MarketAPI market) {
        CargoAPI storage = Misc.getStorageCargo(market);
        return storage != null ? storage.getCommodityQuantity(ItemIds.RARE_PARTS) : 0f;
    }
}
