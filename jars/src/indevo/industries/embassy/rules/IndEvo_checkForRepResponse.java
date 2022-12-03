package indevo.industries.embassy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class IndEvo_checkForRepResponse extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        PersonAPI person = AmbassadorPersonManager.getAmbassador(market);
        FactionAPI ambFaction = person.getFaction();

        String optionId = params.get(0).getString(memoryMap);

        return GetIsCorrect(market.getFaction().getRelationship(ambFaction.getId()), optionId);
    }

    private boolean isBetween(float check, float lower, float higher) {
        return (check >= lower && check <= higher);
    }

    private boolean GetIsCorrect(float rel, String repLevel) {
        if (isBetween(rel, -1.00F, -0.26F) && repLevel.equals("INHOSPITABLE")) {
            return true;
        }
        if (isBetween(rel, -0.25F, -0.09F) && repLevel.equals("SUSPICIOUS")) {
            return true;
        }
        if (isBetween(rel, -0.10F, 0.10F) && repLevel.equals("NEUTRAL")) {
            return true;
        }
        if (isBetween(rel, 0.11F, 0.25F) && repLevel.equals("FAVORABLE")) {
            return true;
        }
        if (isBetween(rel, 0.26F, 0.50F) && repLevel.equals("WELCOMING")) {
            return true;
        }
        return (isBetween(rel, 0.51F, 1F) && repLevel.equals("FRIENDLY"));


    }
}