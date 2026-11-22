package indevo.utils.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;

// survival mode (extended mode in code) factors from the vanilla ext_ keys, read at call time, neutral in normal mode
public class ExtModeHelper {

    public static float mult(String key) {
        return Misc.isExtended() ? Global.getSettings().getFloat(key) : 1f;
    }

    public static float mod(String key) {
        return Misc.isExtended() ? Global.getSettings().getFloat(key) : 0f;
    }

    // hull getBaseValue() has no mode factor, vanilla applies this at each buy price call site
    public static float shipBuyPriceMult(ShipAPI.HullSize size) {
        return Misc.isExtended() ? Global.getSettings().optFloatFromArray("ext_shipBuyPriceMult", size.ordinal() - 2, 1f) : 1f;
    }

    // same key and default as the commodity spec getBasePrice()
    public static float commodityPriceMult(String commodityId) {
        return Misc.isExtended() ? Global.getSettings().optFloat("ext_" + commodityId + "PriceMult", 1f) : 1f;
    }
}
