package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.combat.CombatEngine;
import indevo.industries.petshop.hullmods.PetHullmod;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

// registered in data/config/settings.json, only active in the petsstatus group: walks the piloted pet ship through the states of its status line
public class PetsStatusPlugin extends BaseEveryFrameCombatPlugin {
    private static final Logger log = Global.getLogger(PetsStatusPlugin.class);
    static final String KEY = "IndEvo_petStatus";
    // read by PetsStatusChecks after the battle: "ok|what" or "fail|what"
    static final List<String> results = new ArrayList<>();
    static volatile boolean done = false;

    private CombatEngineAPI engine;
    private boolean active;
    private int frame = 0;
    private ShipAPI a, b, plain;
    private final List<ShipAPI> enemies = new ArrayList<>();
    private boolean enemiesNear = false;
    // a shot pauses the battle for 3 s of real time: the runner captures a second or two after the log line, and status timers count battle time
    private long shotUntil = 0L;
    private float t0 = -1f;
    private int phase = -1;
    private float phaseStart;
    private final List<Phase> phases = new ArrayList<>();

    private static final class Phase {
        final String name;
        final float dur;
        final Consumer<Float> each;
        final float[] at;
        final String[] want;
        final boolean[] seen;
        final float shotAt;

        Phase(String name, float dur, Consumer<Float> each, float shotAt, float[] at, String... want) {
            this.name = name;
            this.dur = dur;
            this.each = each;
            this.at = at;
            this.want = want;
            this.seen = new boolean[at.length];
            this.shotAt = shotAt;
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        active = Arrays.asList(System.getProperty("ie.check", "").split(",")).contains("petsstatus") && !engine.isSimulation() && engine.isInCampaign();
        if (!active) return;
        ((CombatEngine) engine).setShowDeploymentDialog(false);
        results.clear();
        done = false;
        PetsStatusChecks.fought = true;
        log.info("LIVE_CHECK note: pets status plugin active");
    }

    private void deploy(int owner, float y, float facing) {
        CombatFleetManagerAPI fm = engine.getFleetManager(owner);
        int i = 0;
        for (FleetMemberAPI m : fm.getReservesCopy()) fm.spawnFleetMember(m, new Vector2f(i++ * 600f - 600f, y), facing, 0f);
    }

    private static Object status(ShipAPI ship) {
        return ship == null ? null : ship.getCustomData().get(KEY);
    }

    private static Object field(Object s, String name, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(s.getClass(), MethodHandles.lookup()).findVarHandle(s.getClass(), name, type).get(s);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static String line(ShipAPI ship) {
        Object s = status(ship);
        if (s == null) return "none";
        Integer shown = (Integer) field(s, "shown", int.class);
        if (shown == null || shown < 0) return "unset";
        try {
            String[] lines = (String[]) MethodHandles.privateLookupIn(PetHullmod.class, MethodHandles.lookup()).findStaticVarHandle(PetHullmod.class, "STATUS_LINES", String[].class).get();
            return lines[shown];
        } catch (ReflectiveOperationException e) {
            return "lines? " + e;
        }
    }

    static String title(ShipAPI ship) {
        Object s = status(ship);
        return s == null ? "none" : String.valueOf(field(s, "title", String.class));
    }

    private ShipAPI byName(String name) {
        for (ShipAPI ship : engine.getShips()) {
            if (ship.getFleetMember() != null && name.equals(ship.getFleetMember().getShipName()) && !ship.isFighter()) return ship;
        }
        return null;
    }

    private void build() {
        float max = a.getMaxHitpoints();
        float flux = a.getMaxFlux();
        phases.add(new Phase("idle", 3f, t -> {}, 2.6f, new float[]{2.5f}, "keeping watch"));
        phases.add(new Phase("hit", 2.5f, t -> {
            if (t == 0f) a.setHitpoints(max * 0.9f);
        }, 0.35f, new float[]{0.3f, 1.8f}, "startled", "keeping watch"));
        phases.add(new Phase("half", 2.5f, t -> {
            if (t == 0f) a.setHitpoints(max * 0.4f);
        }, 1.9f, new float[]{0.3f, 1.8f}, "startled", "worried"));
        phases.add(new Phase("low", 2f, t -> {
            if (t == 0f) a.setHitpoints(max * 0.2f);
        }, 1.6f, new float[]{0.3f, 1.5f}, "startled", "hiding"));
        // a lower line waits out the 2 s hold
        phases.add(new Phase("healed", 2.5f, t -> {
            if (t == 0f) a.setHitpoints(max);
        }, 0.5f, new float[]{0.4f, 2.2f}, "hiding", "keeping watch"));
        phases.add(new Phase("flux", 2f, t -> a.getFluxTracker().setCurrFlux(flux * 0.95f), 1.2f, new float[]{0.3f}, "nervous"));
        phases.add(new Phase("overload", 2.5f, t -> {
            if (t == 0f) a.getFluxTracker().forceOverload(5f);
        }, 1.2f, new float[]{0.3f}, "panicking"));
        phases.add(new Phase("vent", 3f, t -> {
            if (t == 0f) {
                a.getFluxTracker().stopOverload();
                a.getFluxTracker().setCurrFlux(flux * 0.6f);
            }
            if (t > 0.1f && !a.getFluxTracker().isVenting() && a.getFluxTracker().getCurrFlux() > 0f) a.getFluxTracker().ventFlux();
        }, 2f, new float[]{1.5f}, "waiting it out"));
        phases.add(new Phase("phased", 3.5f, t -> {
            a.getFluxTracker().setCurrFlux(0f);
            a.setPhased(true);
        }, 2.8f, new float[]{2.7f}, "holding still"));
        phases.add(new Phase("enemies", 3.5f, t -> {
            a.setPhased(false);
            enemiesNear = true;
        }, 2.8f, new float[]{2.7f}, "cheering you on"));
        phases.add(new Phase("longname", 3.5f, t -> {
            enemiesNear = false;
            if (t == 0f) engine.setPlayerShipExternal(b);
        }, 2.5f, new float[]{2.4f}, "B:keeping watch"));
        phases.add(new Phase("nopet", 3f, t -> {
            if (t == 0f) engine.setPlayerShipExternal(plain);
        }, 2f, new float[]{1.5f}, "P:none"));
        phases.add(new Phase("retreat", 3.5f, t -> {
            if (t == 0f) {
                engine.setPlayerShipExternal(a);
                a.setRetreating(true, false);
            }
        }, 2.8f, new float[]{2.7f}, "glad to leave"));
        phases.add(new Phase("flameout", 3f, t -> {
            if (t == 0f) a.getEngineController().forceFlameout(true);
        }, 1.5f, new float[]{1f}, "holding on tight"));
        // a full enemy retreat ends the battle after a few seconds, so it comes last, on the long name ship that has neither flag
        phases.add(new Phase("celebrate", 2.62f, t -> {
            if (t == 0f) {
                engine.setPlayerShipExternal(b);
                engine.getFleetManager(1).getTaskManager(false).orderFullRetreat();
            }
        }, 2.55f, new float[]{2.5f}, "B:celebrating"));
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!active || done) return;
        frame++;
        if (System.currentTimeMillis() < shotUntil) {
            engine.setPaused(true);
            return;
        }
        if (engine.isPaused()) engine.setPaused(false);
        if (frame == 2) {
            deploy(0, -2000f, 90f);
            deploy(1, 2000f, 270f);
            log.info("LIVE_CHECK note: pets status battle deployed");
        }
        if (frame < 10) return;
        if (a == null) {
            a = byName("ISS Rex");
            b = byName("ISS Long");
            plain = byName("ISS Plain");
            for (ShipAPI ship : engine.getShips()) if (ship.getOwner() == 1 && !ship.isFighter()) enemies.add(ship);
            if (a == null || b == null || plain == null) {
                results.add("fail|test ships deployed: " + a + ", " + b + ", " + plain);
                done = true;
                return;
            }
            engine.setPlayerShipExternal(a);
            build();
            t0 = engine.getTotalElapsedTime(false) + 1f;
        }

        // the enemy waits out of range unless the phase wants it near
        float far = engine.getMapHeight() / 2f - 600f;
        for (int i = 0; i < enemies.size(); i++) {
            ShipAPI e = enemies.get(i);
            if (e.isHulk() || !e.isAlive()) continue;
            Vector2f to = enemiesNear ? new Vector2f(a.getLocation().x + i * 300f, a.getLocation().y + 700f) : new Vector2f(i * 800f, far);
            e.getLocation().set(to);
            e.getVelocity().set(0f, 0f);
        }
        // the AI of the test ships stays off so nothing changes their state
        for (ShipAPI s : new ShipAPI[]{a, b, plain}) {
            s.getVelocity().set(0f, 0f);
        }

        float now = engine.getTotalElapsedTime(false);
        if (now < t0) return;
        if (phase < 0 || now - phaseStart >= phases.get(phase).dur) {
            if (phase >= 0) finish(phases.get(phase));
            phase++;
            phaseStart = now;
            if (phase >= phases.size()) {
                end();
                return;
            }
            log.info("LIVE_CHECK note: status phase " + phases.get(phase).name);
            phases.get(phase).each.accept(0f);
            return;
        }
        Phase p = phases.get(phase);
        float t = now - phaseStart;
        p.each.accept(t);
        for (int i = 0; i < p.at.length; i++) {
            if (p.seen[i] || t < p.at[i]) continue;
            p.seen[i] = true;
            String want = p.want[i];
            ShipAPI ship = want.startsWith("B:") ? b : want.startsWith("P:") ? plain : a;
            String w = want.replaceFirst("^[BP]:", "");
            String got = line(ship);
            boolean ok = w.equals(got) && engine.getPlayerShip() == ship;
            results.add((ok ? "ok" : "fail") + "|" + p.name + " at " + p.at[i] + " s: line " + got + ", want " + w + (engine.getPlayerShip() == ship ? "" : " (not the player ship)"));
            log.info("LIVE_CHECK note: status " + p.name + " at " + p.at[i] + " s: " + got + " (want " + w + "), hull " + a.getHitpoints() / a.getMaxHitpoints() + ", flux " + a.getFluxTracker().getFluxLevel());
        }
        if (p.shotAt > 0f && t >= p.shotAt && t - amount < p.shotAt) {
            log.info("LIVE_CHECK shot status-" + p.name);
            shotUntil = System.currentTimeMillis() + 3000L;
        }
    }

    private void finish(Phase p) {
        for (int i = 0; i < p.at.length; i++) if (!p.seen[i]) results.add("fail|" + p.name + " at " + p.at[i] + " s: never checked");
    }

    private void end() {
        results.add((title(b).endsWith("...") ? "ok" : "fail") + "|long name title " + title(b));
        results.add(("REX".equals(title(a)) ? "ok" : "fail") + "|title " + title(a));
        results.add((status(plain) == null ? "ok" : "fail") + "|the ship with no pet has no status");
        log.info("LIVE_CHECK note: titles " + title(a) + " / " + title(b));
        done = true;
        PetsStatusChecks.fought = true;
        for (ShipAPI ship : new ArrayList<>(engine.getShips())) {
            if (ship.getOwner() != 1 || ship.isHulk()) continue;
            engine.applyDamage(ship, ship.getLocation(), 1000000f, DamageType.HIGH_EXPLOSIVE, 0f, true, false, null);
        }
        engine.endCombat(2f, FleetSide.PLAYER);
    }
}
