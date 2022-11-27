package indevo.converters.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;

import static indevo.ids.IndEvo_ids.CONVERTERS_FACTION_ID;

public class ConverterRepRestetter implements EconomyTickListener {

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {
        resetConverterRep();
    }

    public static void resetConverterRep() {
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            f.setRelationship(CONVERTERS_FACTION_ID, -1);
        }

        Global.getSector().getPlayerFaction().setRelationship(CONVERTERS_FACTION_ID, -1);
    }
}
