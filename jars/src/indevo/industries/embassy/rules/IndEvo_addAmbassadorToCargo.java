package indevo.industries.embassy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import indevo.ids.ItemIds;
import indevo.items.specialitemdata.AmbassadorItemData;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import indevo.industries.embassy.listeners.IndEvo_ambassadorItemTrackerPlugin;
import indevo.industries.embassy.listeners.IndEvo_ambassadorPersonManager;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_addAmbassadorToCargo extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        PersonAPI ambassadorPerson = IndEvo_ambassadorPersonManager.getAmbassador(market);

        AmbassadorItemData amb = new AmbassadorItemData(ItemIds.AMBASSADOR, null, ambassadorPerson);
        Global.getSector().getPlayerFleet().getCargo().addSpecial(amb, 1);

        Global.getSector().getListenerManager().addListener(new IndEvo_ambassadorItemTrackerPlugin(ambassadorPerson, amb));
        IndEvo_ambassadorPersonManager.removeAmbassadorFromMarket(market);

        return true;
    }
}
