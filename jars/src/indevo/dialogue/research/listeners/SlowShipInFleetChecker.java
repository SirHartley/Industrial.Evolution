package indevo.dialogue.research.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import indevo.dialogue.research.intel.GalatiaNewProjectsIntel;
import indevo.ids.Ids;
import indevo.utils.ModPlugin;

public class SlowShipInFleetChecker implements EconomyTickListener {

    public static void register(){
        ListenerManagerAPI manager = Global.getSector().getListenerManager();
        if (manager.hasListenerOfClass(SlowShipInFleetChecker.class)) return;
        manager.addListener(new SlowShipInFleetChecker(), true);
    }

    public static final String MEM_KEY = "$IndEvo_SlowShipFleetCheckerDone";
    @Override
    public void reportEconomyTick(int iterIndex) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.getBoolean(MEM_KEY)) return;

        boolean hasSlowShip = false;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {

            ModPlugin.log(member.getHullSpec().getNameWithDesignationWithDashClass() + " - burn " + member.getStats().getMaxBurnLevel().getModifiedInt() + " -- base " + member.getStats().getMaxBurnLevel().getBaseValue());
            if (member.getStats().getMaxBurnLevel().getBaseValue() <= 7f){
                hasSlowShip = true;
                break;
            }
        }

        if (hasSlowShip){
            mem.set(MEM_KEY, true);
            GalatiaNewProjectsIntel.notifyNewProjects(Ids.PROJ_SONIC);
        }
    }

    @Override
    public void reportEconomyMonthEnd() {

    }
}
