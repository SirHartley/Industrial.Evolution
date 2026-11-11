package livecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Runner implements EveryFrameScript {
    private static final Logger log = Global.getLogger(Runner.class);
    private static final String PHASE = "live_check_phase";
    // statics survive c.reload(), so a group's time and counts carry across it
    private static int groupAt = -1;
    private static long groupStart = 0L;
    private static int groupPassed = 0;
    private static int groupFailed = 0;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private final Ctx ctx = new Ctx();
    private int index;
    private int frames = 0;
    private int reloadState = 0;
    private boolean stopped = false;

    public Runner(String group) {
        for (String one : group.split(",")) {
            names.add(one);
            starts.add(steps.size());
            steps.addAll(Groups.get(one));
        }
        Object saved = Global.getSector().getPersistentData().get(PHASE);
        index = saved == null ? 0 : (Integer) saved;
        if (groupAt >= 0 && groupAt < names.size()) ctx.group = names.get(groupAt);
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
        if (stopped || ctx.held) return;
        if (++frames < 60) return;

        SectorAPI sector = Global.getSector();
        sector.getPlayerFleet().setNoEngaging(100f);
        // out of supplies vanilla shows an accident report, and that dialog refuses every other dialog and core tab
        if (!ctx.keepDialog && sector.getPlayerFleet().getCargo().getSupplies() < 100f) sector.getPlayerFleet().getCargo().addSupplies(200f);
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
        // c.shot and c.key: the runner script writes saves/common/<ack>.data once it has captured or pressed
        if (ctx.ack != null) {
            if (!Global.getSettings().fileExistsInCommon(ctx.ack)) {
                if (System.currentTimeMillis() < ctx.ackUntil) return;
                ctx.expect(false, "the runner script answered " + ctx.ack + " within 15 s");
            }
            ctx.ack = null;
        }
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
            endGroup();
            stopped = true;
            log.info("LIVE_CHECK COMPLETE passed=" + Ctx.passed + " failed=" + Ctx.failed);
            // KEEP=1 of the runner script: the game stays open for clicks and captures by hand
            if (!Boolean.getBoolean("live.keep")) System.exit(Ctx.failed == 0 ? 0 : 3);
            return;
        }

        int group = 0;
        for (int i = 0; i < starts.size(); i++) {
            if (starts.get(i) <= index) group = i;
        }
        if (group != groupAt) {
            endGroup();
            groupAt = group;
            groupStart = System.currentTimeMillis();
            groupPassed = Ctx.passed;
            groupFailed = Ctx.failed;
        }
        ctx.group = names.get(group);
        int inGroup = index - starts.get(group) + 1;
        Step step = steps.get(index++);
        try {
            log.info("LIVE_CHECK step " + index + "/" + steps.size() + " (" + ctx.group + " " + inGroup + ")");
            step.run(ctx);
        } catch (Throwable t) {
            Ctx.failed++;
            log.error("LIVE_CHECK FAIL: " + ctx.group + " / step " + inGroup + " threw " + t, t);
        }
    }

    private void endGroup() {
        if (groupAt < 0 || groupAt >= names.size()) return;
        log.info("LIVE_CHECK group " + names.get(groupAt) + ": " + (Ctx.passed - groupPassed) + " ok, " + (Ctx.failed - groupFailed)
                + " failed, " + (System.currentTimeMillis() - groupStart) / 1000L + " s");
        groupAt = -1;
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
