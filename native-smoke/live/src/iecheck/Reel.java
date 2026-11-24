package iecheck;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

// test only: plays one sizzle reel take in real time. heading, burn, eased zoom and hidden ui, timed cues and event marks;
// REEL lines give the take's seconds for the edit, OBS records from rolling to cut when -Die.reel.obs is set
public final class Reel implements EveryFrameScript {
    private static final Logger log = Global.getLogger(Reel.class);
    private static final String BURN = "ie_reel_burn";

    public interface Cue {
        void run(Reel r) throws Exception;
    }

    private record Timed(float at, Cue cue) {
    }

    private record Event(String mark, BooleanSupplier when, Cue cue) {
    }

    final String name;
    private final float max;
    private final List<Timed> timed = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final Map<String, Float> marks = new LinkedHashMap<>();
    private final List<Runnable> frames = new ArrayList<>();
    private boolean hideUi = true;
    private boolean started = false;
    boolean done = false;
    float t = 0f;
    float burn = 7f;
    // null holds the fleet where it is, NaN leaves it alone
    Float heading = Float.NaN;
    private float zoomFrom = -1f;
    private float zoomTo;
    private float zoomAt;
    private float zoomFor;
    private float cutAt = -1f;
    private float burnFlat = 0f;

    public Reel(String name, float maxSeconds) {
        this.name = name;
        this.max = maxSeconds;
    }

    public Reel ui(boolean hide) {
        hideUi = hide;
        return this;
    }

    public Reel at(float seconds, Cue cue) {
        timed.add(new Timed(seconds, cue));
        return this;
    }

    // logs the mark once the condition first holds, then runs the cue
    public Reel when(String mark, BooleanSupplier when, Cue cue) {
        events.add(new Event(mark, when, cue));
        return this;
    }

    public Reel frame(Runnable run) {
        frames.add(run);
        return this;
    }

    public float mark(String mark) {
        Float at = marks.get(mark);
        return at == null ? -1f : at;
    }

    public void zoom(float zoom) {
        zoomFrom = -1f;
        setZoom(zoom);
    }

    public void ease(float to, float seconds) {
        zoomFrom = Global.getSector().getCampaignUI().getZoomFactor();
        zoomTo = to;
        zoomAt = t;
        zoomFor = Math.max(0.01f, seconds);
    }

    public void cut(float after) {
        if (cutAt < 0f) cutAt = t + after;
    }

    public void note(String text) {
        log.info("LIVE_CHECK note: REEL " + name + " " + String.format("%.2f", t) + " s: " + text);
    }

    // the take as one step: rolls, waits for the cut, checks that it played
    public static void play(Ctx c, Reel reel) {
        ArtilleryChecks.realTime();
        Global.getSector().addTransientScript(reel);
        c.until(() -> reel.done, reel.max / 10f + 30f, "REEL " + reel.name + " played to its cut");
    }

    static void setZoom(float zoom) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (zoom < ui.getMinZoomFactor()) ui.setMinZoomFactor(zoom);
        if (zoom > ui.getMaxZoomFactor()) ui.setMaxZoomFactor(zoom);
        ui.setZoomFactor(zoom);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;
        if (!started) start();
        t += Math.min(amount, 0.1f);

        steer();
        if (zoomFrom >= 0f) {
            float k = Math.min(1f, (t - zoomAt) / zoomFor);
            k = k * k * (3f - 2f * k);
            setZoom(zoomFrom + (zoomTo - zoomFrom) * k);
            if (k >= 1f) zoomFrom = -1f;
        }

        try {
            for (Timed cue : new ArrayList<>(timed)) {
                if (t < cue.at()) continue;
                timed.remove(cue);
                cue.cue().run(this);
            }
            for (Event e : new ArrayList<>(events)) {
                if (!e.when().getAsBoolean()) continue;
                events.remove(e);
                marks.put(e.mark(), t);
                log.info("LIVE_CHECK note: REEL mark " + name + " " + e.mark() + " " + String.format("%.2f", t));
                if (e.cue() != null) e.cue().run(this);
            }
        } catch (Throwable x) {
            log.error("LIVE_CHECK note: REEL " + name + " cue threw " + x, x);
            cut(0f);
        }
        ReelMouse.advance(Math.min(amount, 0.1f));
        for (Runnable run : frames) run.run();

        if ((cutAt >= 0f && t >= cutAt) || t >= max) finish();
    }

    private void start() {
        started = true;
        if (hideUi) Global.getSector().getCampaignUI().setHideUI(true);
        ReelObs.request("StartRecord");
        log.info("LIVE_CHECK note: REEL rolling " + name);
    }

    private void finish() {
        done = true;
        ReelObs.request("StopRecord");
        log.info("LIVE_CHECK note: REEL cut " + name + " " + String.format("%.2f", t) + (t >= max && cutAt < 0f ? " (max length)" : "") + " marks " + marks);
        Global.getSector().getCampaignUI().setHideUI(false);
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) fleet.getStats().getFleetwideMaxBurnMod().unmodify(BURN);
        ReelMouse.reset();
    }

    // the fleet flies at exactly the reel burn, so every take and both sides of a cut match
    private void steer() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;
        float base = fleet.getFleetData().getMinBurnLevel() - burnFlat;
        burnFlat = burn - base;
        fleet.getStats().getFleetwideMaxBurnMod().modifyFlat(BURN, burnFlat);

        if (heading == null) {
            fleet.setMoveDestinationOverride(fleet.getLocation().x, fleet.getLocation().y);
        } else if (!heading.isNaN()) {
            Vector2f far = Misc.getUnitVectorAtDegreeAngle(heading);
            far.scale(100000f);
            fleet.setMoveDestinationOverride(fleet.getLocation().x + far.x, fleet.getLocation().y + far.y);
        }
    }
}
