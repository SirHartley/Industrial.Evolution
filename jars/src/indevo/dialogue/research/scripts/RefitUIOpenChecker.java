package indevo.dialogue.research.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import indevo.utils.ModPlugin;

import java.util.List;

public class RefitUIOpenChecker implements EveryFrameScript {

    boolean open = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        boolean isOpen = CoreUITabId.REFIT.equals(Global.getSector().getCampaignUI().getCurrentCoreTab());

        if (isOpen && !open) {
            ModPlugin.log("Player Refit Open");

            List<RefitTabListener> list = Global.getSector().getListenerManager().getListeners(RefitTabListener.class);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

            for (RefitTabListener x : list) {
                x.reportRefitOpened(fleet);
            }

            open = true;
        } else if (!isOpen && open) {
            ModPlugin.log("Player Refit Closed");

            List<RefitTabListener> list = Global.getSector().getListenerManager().getListeners(RefitTabListener.class);
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

            for (RefitTabListener x : list) {
                x.reportRefitClosed(fleet);
            }

            open = false;
        }
    }

    public static void register() {
        if (!Global.getSector().hasTransientScript(RefitUIOpenChecker.class)) {
            ModPlugin.log("creating RefitUIOpenChecker instance");
            Global.getSector().addTransientScript(new RefitUIOpenChecker());
        }
    }
}
