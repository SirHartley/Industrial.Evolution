package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Syntax: HasItemAmountAnywhere itemId itemType amount
 *
 * itemTypes:
 * RESOURCES = Commodities
 * WEAPONS
 * FIGHTER_CHIP
 * SPECIAL = Special Items like Nanoforges
 */

public class HasItemAmountAnywhere extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        String itemId = params.get(0).getString(memoryMap);
        CargoAPI.CargoItemType itemType = CargoAPI.CargoItemType.valueOf(params.get(1).getString(memoryMap));
        int amount = params.get(2).getInt(memoryMap);

        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) if (m.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) {
            if (Misc.getStorageCargo(m).getQuantity(itemType, itemId) >= amount) return true;
        }

        return Global.getSector().getPlayerFleet().getCargo().getQuantity(itemType, itemId) >= amount;
    }
}
