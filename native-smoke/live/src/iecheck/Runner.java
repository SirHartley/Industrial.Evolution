package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import org.apache.log4j.Logger;

import java.util.List;

public class Runner implements EveryFrameScript {
    private static final Logger log = Global.getLogger(Runner.class);
    private static final String PHASE = "ie_check_phase";

    private final List<Step> steps;
    private final Ctx ctx = new Ctx();
    private int index;
    private int frames = 0;
    private int reloadState = 0;
    private boolean stopped = false;

    public Runner(String group) {
        steps = Groups.get(group);
        Object saved = Global.getSector().getPersistentData().get(PHASE);
        index = saved == null ? 0 : (Integer) saved;
        Global.getSettings().setDevMode(false);
    }

    @Override
    public boolean isDone() {
        return stopped;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (stopped) return;
        if (++frames < 60) return;

        SectorAPI sector = Global.getSector();
        sector.getPlayerFleet().setNoEngaging(100f);
        InteractionDialogAPI dialog = sector.getCampaignUI().getCurrentInteractionDialog();
        if (dialog != null && !ctx.reload && !ctx.keepDialog) {
            log.info("LIVE_CHECK note: closing dialog " + dialog.getPlugin());
            dialog.dismiss();
            return;
        }

        if (ctx.reload) {
            reloadStep(sector);
            return;
        }
        if (ctx.until != null) {
            sector.setPaused(false);
            Time.fast(true);
            if (ctx.onFrame != null) ctx.onFrame.run();
            float elapsed = sector.getClock().getElapsedDaysSince(ctx.untilStart);
            boolean met = ctx.until.getAsBoolean();
            if (!met && elapsed < ctx.untilMax) return;
            ctx.expect(met, ctx.untilId + " (after " + elapsed + " days)");
            ctx.until = null;
            ctx.onFrame = null;
            Time.fast(false);
            sector.setPaused(true);
        }
        if (System.currentTimeMillis() < ctx.waitUntil) return;
        if (ctx.waitFrames > 0) {
            ctx.waitFrames--;
            return;
        }
        if (ctx.waitDays > 0f) {
            sector.setPaused(false);
            Time.fast(true);
            if (sector.getClock().getElapsedDaysSince(ctx.waitStart) < ctx.waitDays) return;
            ctx.waitDays = 0f;
            Time.fast(false);
            sector.setPaused(true);
        }

        if (index >= steps.size()) {
            stopped = true;
            log.info("LIVE_CHECK COMPLETE passed=" + Ctx.passed + " failed=" + Ctx.failed);
            System.exit(Ctx.failed == 0 ? 0 : 3);
            return;
        }

        Step step = steps.get(index++);
        try {
            log.info("LIVE_CHECK step " + index + "/" + steps.size());
            step.run(ctx);
        } catch (Throwable t) {
            Ctx.failed++;
            log.error("LIVE_CHECK FAIL: step " + index + " threw " + t, t);
        }
    }

    private void reloadStep(SectorAPI sector) {
        if (reloadState == 0) {
            sector.getPersistentData().put(PHASE, index);
            sector.setPaused(true);
            log.info("LIVE_CHECK saving, cmdSave=" + sector.getCampaignUI().cmdSave());
            reloadState = 1;
            frames = 0;
        } else if (reloadState == 1 && frames > 30) {
            reloadState = 2;
            stopped = true;
            log.info("LIVE_CHECK quick load");
            sector.getCampaignUI().quickLoad();
        }
    }
}
