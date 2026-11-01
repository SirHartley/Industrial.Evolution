package indevo.industries.embassy.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;

import java.util.List;
import java.util.Map;

public class IndEvo_checkForRepResponse extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(memoryMap.get(MemKeys.MARKET).getString("$id"));
        PersonAPI person = AmbassadorPersonManager.getAmbassador(market);
        FactionAPI ambFaction = person.getFaction();

        String optionId = params.get(0).getString(memoryMap);

        return getRepBand(market.getFaction().getRelationship(ambFaction.getId())).equals(optionId);
    }

    // contiguous bands, relations are raw floats and can sit between hundredths
    public static String getRepBand(float rel) {
        if (rel < -0.25f) return "INHOSPITABLE";
        if (rel < -0.10f) return "SUSPICIOUS";
        if (rel <= 0.10f) return "NEUTRAL";
        if (rel <= 0.25f) return "FAVORABLE";
        if (rel <= 0.50f) return "WELCOMING";
        return "FRIENDLY";
    }
}