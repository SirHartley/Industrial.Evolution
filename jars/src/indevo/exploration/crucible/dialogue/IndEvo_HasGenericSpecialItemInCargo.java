package indevo.exploration.crucible.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_HasGenericSpecialItemInCargo extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        SectorEntityToken crucible = dialog.getInteractionTarget();

        String itemId = params.get(0).getString(memoryMap);

        if (crucible.getMarket() != null && !crucible.getMarket().isPlanetConditionMarketOnly()){
            MarketAPI m = crucible.getMarket();

            for (SubmarketAPI s : m.getSubmarketsCopy()){
                if (s.getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(itemId, null)) > 0) return true;
            }
        }

        return Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(itemId, null)) > 0;
    }
}
