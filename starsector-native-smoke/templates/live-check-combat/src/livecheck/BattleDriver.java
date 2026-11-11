package livecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.combat.CombatEngine;
import com.fs.starfarer.combat.CombatState;
import com.fs.state.AppDriver;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Runs inside the battle that Battle starts (registered under "plugins" in data/config/settings.json). Deploys every
// ship, then switches the code under test on and off in short alternating windows of the same battle and compares the
// game's work per frame. Work = wall time minus time inside Display.update/sync (Timers.DisplayWait), so a driver frame
// cap or vsync does not hide the difference. Alternating inside one battle cancels most of the battle's own drift and
// of other load on the PC; separate battles differ too much to compare.
public class BattleDriver extends BaseEveryFrameCombatPlugin {
    private static final Logger log = Global.getLogger(BattleDriver.class);
    static final int WARMUP = 600;
    static final int MEASURE = 4800;
    // 60-frame windows suit code that switches off at once. Code that leaves work behind when switched off (trails,
    // particles) needs longer windows and frames to settle after each switch, for example 300 and 150.
    static final int WINDOW = 60;
    static final int SETTLE = 5;

    private CombatEngineAPI engine;
    private boolean active;
    private int frame = 0;
    private long last = 0, lastCpu = 0;
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    // index 0 = OFF, 1 = ON
    private final List<List<Long>> work = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<List<Long>> wall = List.of(new ArrayList<>(), new ArrayList<>());
    private final long[] cpu = new long[2], ships = new long[2], projectiles = new long[2];
    private final List<Map<String, long[]>> timers = List.of(new TreeMap<>(), new TreeMap<>());

    static void note(String s) {
        log.info("LIVE_CHECK note: " + s);
    }

    // counted like the campaign checks, so the runner script can compare its count with the COMPLETE line
    static void expect(boolean ok, String id) {
        if (ok) Ctx.passed++;
        else Ctx.failed++;
        log.info("LIVE_CHECK " + (ok ? "ok" : "FAIL") + ": battle / " + id);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        String groups = System.getProperty("live.check", "");
        active = Arrays.asList(groups.split(",")).contains("battle") && !engine.isSimulation() && engine.isInCampaign();
        if (!active) return;
        // setting this before startBattle is not enough: without it here the dialog stays open over the battle
        ((CombatEngine) engine).setShowDeploymentDialog(false);
        note("battle driver active, window " + WINDOW + " frames, settle " + SETTLE);
    }

    private static boolean deploymentDialogOpen() {
        Object state = AppDriver.getInstance().getCurrentState();
        return state instanceof CombatState && ((CombatState) state).isShowingDeploymentDialog();
    }

    private int deploy(int owner, float y, float facing) {
        CombatFleetManagerAPI fm = engine.getFleetManager(owner);
        int i = 0;
        for (FleetMemberAPI m : fm.getReservesCopy()) {
            float row = (i / 20) * 600f;
            fm.spawnFleetMember(m, new Vector2f(((i % 20) - 10f) * 450f, y + (owner == 0 ? -row : row)), facing, 0f);
            i++;
        }
        return i;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!active) return;
        frame++;
        long now = System.nanoTime(), dt = now - last;
        last = now;
        long wait = Perf.displayNanos;
        Perf.displayNanos = 0;
        long cpuNow = threads.getCurrentThreadCpuTime(), dcpu = cpuNow - lastCpu;
        lastCpu = cpuNow;

        if (frame == 2) {
            engine.setDoNotEndCombat(true);
            int a = deploy(0, -3000f, 90f), b = deploy(1, 3000f, 270f);
            expect(a > 0 && b > 0, "deployed " + a + " and " + b + " ships");
        }
        if (frame < 3) return;
        if (engine.isPaused()) engine.setPaused(false);
        if (frame == 3) for (ShipAPI s : engine.getShips()) s.setCurrentCR(Math.max(s.getCurrentCR(), 0.7f));
        ShipAPI player = engine.getPlayerShip();
        if (player != null && player.getShipAI() == null) player.resetDefaultAI();
        if (frame < WARMUP) return;
        // the measurement is only of a plain battle when no dialog sits over it
        if (frame == WARMUP) expect(!deploymentDialogOpen(), "no deployment dialog over the battle");
        if (frame == WARMUP) return;

        int t = frame - WARMUP - 1;
        if (t >= MEASURE) {
            finish();
            return;
        }
        int w = t / WINDOW, f = t % WINDOW, on = w % 2 == 0 ? 1 : 0;
        if (f == 0) {
            collect(1 - on);
            Perf.codeOn = on == 1;
            Perf.timing = false;
        }
        if (f == SETTLE) Perf.timing = true;
        if (f > SETTLE) {
            work.get(on).add(dt - wait);
            wall.get(on).add(dt);
            cpu[on] += dcpu;
            ships[on] += engine.getShips().size();
            projectiles[on] += engine.getProjectiles().size();
        }
        int mid = (MEASURE / WINDOW / 2) & ~1;
        if (w == mid && f == WINDOW - 2) log.info("LIVE_CHECK shot battle-on");
        if (w == mid + 1 && f == WINDOW - 2) log.info("LIVE_CHECK shot battle-off");
    }

    private void collect(int state) {
        for (Map.Entry<String, long[]> e : Perf.totals.entrySet()) {
            long[] acc = timers.get(state).computeIfAbsent(e.getKey(), k -> new long[2]);
            acc[0] += e.getValue()[0];
            acc[1] += e.getValue()[1];
        }
        Perf.totals.clear();
    }

    private static double avg(List<Long> v) {
        long s = 0;
        for (long x : v) s += x;
        return s / (double) v.size() / 1000.0;
    }

    private static double pct(List<Long> v, double p) {
        List<Long> c = new ArrayList<>(v);
        Collections.sort(c);
        return c.get((int) (c.size() * p)) / 1000.0;
    }

    private void finish() {
        collect(((frame - WARMUP - 2) / WINDOW) % 2 == 0 ? 1 : 0);
        Perf.timing = false;
        Perf.codeOn = true;
        for (int s = 1; s >= 0; s--) {
            int n = work.get(s).size();
            String state = s == 1 ? "ON " : "OFF";
            // Thread CPU time ticks in 15.6 ms steps on Windows: only its average over many frames means anything.
            note(String.format("PERF %s: %d frames, work avg %.0f us (median %.0f, p95 %.0f), wall avg %.0f us (median %.0f), "
                            + "thread CPU avg %.0f us, ships %.0f, projectiles %.0f", state, n, avg(work.get(s)),
                    pct(work.get(s), 0.5), pct(work.get(s), 0.95), avg(wall.get(s)), pct(wall.get(s), 0.5),
                    cpu[s] / 1000.0 / n, ships[s] / (double) n, projectiles[s] / (double) n));
            for (Map.Entry<String, long[]> e : timers.get(s).entrySet()) {
                note(String.format("PERF %s   %-60s %8.1f us/frame %6.2f calls/frame", state, e.getKey(),
                        e.getValue()[0] / 1000.0 / n, e.getValue()[1] / (double) n));
            }
        }
        double on = avg(work.get(1)), off = avg(work.get(0));
        note(String.format("PERF summary: work ON %.0f us, OFF %.0f us, difference %.0f us per frame (%.1f%%), medians %.0f vs %.0f",
                on, off, on - off, 100 * (on - off) / on, pct(work.get(1), 0.5), pct(work.get(0), 0.5)));
        // wall far above work, or a wall median pinned at 1000/N ms, means a frame cap or vsync is still on
        note(String.format("PERF frame pacing: wall median %.0f us vs work median %.0f us", pct(wall.get(1), 0.5), pct(work.get(1), 0.5)));
        expect(Perf.displayCalls > 0, "the Display wait timer ran (" + Perf.displayCalls + " calls), so work excludes driver waits");
        log.info("LIVE_CHECK COMPLETE passed=" + Ctx.passed + " failed=" + Ctx.failed);
        System.exit(Ctx.failed == 0 ? 0 : 3);
    }
}
