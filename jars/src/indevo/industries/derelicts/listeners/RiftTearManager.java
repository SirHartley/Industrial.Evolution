package indevo.industries.derelicts.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.derelicts.RiftRelocator;
import indevo.utils.timers.NewDayListener;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class RiftTearManager implements NewDayListener {

    private final List<Tear> tears = new ArrayList<>();

    private static class Tear {
        private final JumpPointAPI rift;
        private final List<JumpPointAPI> entrances;
        private final long opened;
        private final int days;

        private Tear(JumpPointAPI rift, List<JumpPointAPI> entrances, long opened, int days) {
            this.rift = rift;
            this.entrances = entrances;
            this.opened = opened;
            this.days = days;
        }

        private int getDaysLeft() {
            return days - (int) Global.getSector().getClock().getElapsedDaysSince(opened);
        }
    }

    public static RiftTearManager getInstanceOrRegister() {
        for (RiftTearManager manager : Global.getSector().getListenerManager().getListeners(RiftTearManager.class)) {
            return manager;
        }

        RiftTearManager manager = new RiftTearManager();
        Global.getSector().getListenerManager().addListener(manager);
        return manager;
    }

    public void open(JumpPointAPI rift, List<JumpPointAPI> entrances, int days) {
        Tear tear = new Tear(rift, new ArrayList<>(entrances), Global.getSector().getClock().getTimestamp(), Math.max(days, 1));
        tears.add(tear);
        rename(tear);
    }

    public int getDaysLeft(SectorEntityToken rift) {
        for (Tear tear : tears) if (tear.rift == rift) return tear.getDaysLeft();
        return -1;
    }

    @Override
    public void onNewDay() {
        for (Tear tear : new ArrayList<>(tears)) {
            if (tear.getDaysLeft() > 0) {
                rename(tear);
                continue;
            }

            tears.remove(tear);
            close(tear);
        }
    }

    private static void rename(Tear tear) {
        int days = tear.getDaysLeft();
        String name = RiftRelocator.RIFT_NAME + " (closes in " + days + (days == 1 ? " day)" : " days)");
        tear.rift.setName(name);
        for (JumpPointAPI entrance : tear.entrances) entrance.setName(name);
    }

    private static void close(Tear tear) {
        StarSystemAPI system = tear.rift.getContainingLocation() instanceof StarSystemAPI s ? s : null;
        RiftRelocator.closeTear(tear.rift, tear.entrances);
        if (system == null) return;

        String name = system.getNameWithNoType();
        Color hl = Misc.getHighlightColor();
        Global.getSector().getCampaignUI().addMessage("The " + RiftRelocator.RIFT_NAME + " in the " + name + " system has closed.",
                Misc.getTextColor(), name, name, hl, hl);
    }
}
