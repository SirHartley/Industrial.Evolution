package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import indevo.dialogue.research.GalatiaNewProjectsIntel;
import indevo.ids.Ids;

public class HasUniqueShipChecker implements EconomyTickListener {

    public static void register(){
                ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(HasUniqueShipChecker.class)) manager.addListener(new HasUniqueShipChecker());
    }

    private static final String TRIGGER_KEY = "$IndEvo_HasUniqueShipChecker";

    public static boolean isDone() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(TRIGGER_KEY);
    }

    private void finish() {
        Global.getSector().getMemoryWithoutUpdate().set(TRIGGER_KEY, true);
        GalatiaNewProjectsIntel.notifyNewProjects(Ids.PROJ_MORGANA);
    }


    @Override
    public void reportEconomyTick(int iterIndex) {
        if (isDone()) return;

        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            ShipVariantAPI var = m.getVariant();
            if (var.hasTag(Tags.SHIP_UNIQUE_SIGNATURE)){
                finish();
                return;
            }
        }
    }

    @Override
    public void reportEconomyMonthEnd() {
    }
}