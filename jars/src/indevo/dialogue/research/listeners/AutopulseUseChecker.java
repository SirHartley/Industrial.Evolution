package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.dialogue.research.intel.GalatiaNewProjectsIntel;
import indevo.ids.Ids;

public class AutopulseUseChecker implements EconomyTickListener {

    public static void register(){
        ListenerManagerAPI manager =Global.getSector().getListenerManager();
        if (!manager.hasListenerOfClass(AutopulseUseChecker.class)) manager.addListener(new AutopulseUseChecker());
    }

    private static final String HAS_FOUND_GUN_KEY = "$IndEvo_HasFoundAutopulse";

    public static boolean isGunFound() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(HAS_FOUND_GUN_KEY);
    }

    private void setGunFound() {
        Global.getSector().getMemoryWithoutUpdate().set(HAS_FOUND_GUN_KEY, true);
        GalatiaNewProjectsIntel.notifyNewProjects(Ids.PROJ_PULSE);
    }


    @Override
    public void reportEconomyTick(int iterIndex) {
        if (isGunFound()) return;


        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            ShipVariantAPI var = m.getVariant();
            for (String s : var.getFittedWeaponSlots()) {
                if (var.getWeaponSpec(s).getWeaponId().equals("autopulse")) {
                    setGunFound();
                    return;
                }
            }
        }
    }

    @Override
    public void reportEconomyMonthEnd() {
    }
}
