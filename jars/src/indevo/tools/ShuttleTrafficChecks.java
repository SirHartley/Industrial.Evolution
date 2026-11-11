package indevo.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleBudget;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleState;
import indevo.ambience.shuttletraffic.ShuttleNetwork;
import indevo.ambience.shuttletraffic.ShuttleTrail;
import indevo.ambience.shuttletraffic.dogfights.DogfightFleet;
import indevo.ambience.shuttletraffic.dogfights.DogfightGroup;
import indevo.ambience.shuttletraffic.dogfights.DogfightHost;
import indevo.ambience.shuttletraffic.dogfights.Dogfighter;
import indevo.ambience.shuttletraffic.dogfights.ShuttleDogfight;
import indevo.ambience.shuttletraffic.dogfights.ShuttleDogfights;
import indevo.ambience.shuttletraffic.dogfights.ShuttleProjectiles;
import indevo.ambience.shuttletraffic.dogfights.WingType;
import indevo.ambience.shuttletraffic.flights.ShuttlePaths;
import indevo.ambience.shuttletraffic.movement.Dodge;
import indevo.ambience.shuttletraffic.movement.SteerMover;
import indevo.ambience.shuttletraffic.movement.ShuttlePath;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;

import static indevo.ambience.shuttletraffic.ShuttleState.CRUISE;
import static indevo.ambience.shuttletraffic.ShuttleState.DISABLED;
import static indevo.ambience.shuttletraffic.ShuttleState.DOWN;
import static indevo.ambience.shuttletraffic.ShuttleState.ESCAPING;
import static indevo.ambience.shuttletraffic.ShuttleState.FLEEING;
import static indevo.ambience.shuttletraffic.ShuttleState.FLEEING_HOLDING;
import static indevo.ambience.shuttletraffic.ShuttleState.HOLDING;
import static indevo.ambience.shuttletraffic.ShuttleState.SCREENING;
import static indevo.ambience.shuttletraffic.ShuttleState.STRANDED;

public final class ShuttleTrafficChecks {

    private static final float[] RATES = {30f, 60f, 144f};
    private static final float SPEED = 24f;
    private static final Map<String, UnaryOperator<ShuttleState>> CHANGES = Map.of(
            "hold", ShuttleState::hold,
            "redirect", ShuttleState::redirect,
            "flee", state -> state.flee(true),
            "escape", state -> state.flee(false),
            "screen", ShuttleState::screen,
            "land", ShuttleState::land,
            "strand", ShuttleState::strand,
            "calm", ShuttleState::calm,
            "disable", ShuttleState::disable,
            "restart", ShuttleState::restart);
    // every change the old flags (holding, fleeing, screening, stranded, down) allowed; any other change throws
    private static final Object[][] TRANSITIONS = {
            {CRUISE, "hold", HOLDING}, {FLEEING, "hold", FLEEING_HOLDING},
            {CRUISE, "redirect", CRUISE}, {HOLDING, "redirect", CRUISE}, {FLEEING, "redirect", FLEEING}, {FLEEING_HOLDING, "redirect", FLEEING},
            {CRUISE, "flee", FLEEING}, {HOLDING, "flee", FLEEING}, {FLEEING, "flee", FLEEING}, {FLEEING_HOLDING, "flee", FLEEING},
            {SCREENING, "flee", FLEEING},
            {CRUISE, "escape", ESCAPING}, {HOLDING, "escape", ESCAPING}, {FLEEING, "escape", ESCAPING}, {FLEEING_HOLDING, "escape", ESCAPING},
            {SCREENING, "escape", ESCAPING},
            {CRUISE, "screen", SCREENING}, {HOLDING, "screen", SCREENING}, {SCREENING, "screen", SCREENING},
            {CRUISE, "land", DOWN}, {FLEEING, "land", DOWN},
            {ESCAPING, "strand", STRANDED}, {STRANDED, "strand", STRANDED},
            {SCREENING, "calm", CRUISE}, {STRANDED, "calm", CRUISE}, {DOWN, "calm", CRUISE},
            {CRUISE, "disable", DISABLED}, {HOLDING, "disable", DISABLED}, {FLEEING, "disable", DISABLED}, {FLEEING_HOLDING, "disable", DISABLED},
            {ESCAPING, "disable", DISABLED}, {STRANDED, "disable", DISABLED}, {SCREENING, "disable", DISABLED}, {DISABLED, "restart", CRUISE}};
    // pace, turn rate mult, fleeing, holding, stranded as the old flags set them
    private static final Object[][] LOOKS = {
            {CRUISE, 1f, 1f, false, false, false}, {HOLDING, 1f, 1f, false, true, false}, {FLEEING, 1.4f, 2f, true, false, false},
            {FLEEING_HOLDING, 1.4f, 2f, true, true, false}, {ESCAPING, 1.4f, 2f, true, false, true}, {STRANDED, 0.6f, 1f, false, false, true},
            {SCREENING, 0.6f, 1f, false, false, false}, {DOWN, 1f, 1f, false, false, false},
            {DISABLED, 1f, 1f, false, false, false}};

    private final List<String> problems = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private int checks;

    private ShuttleTrafficChecks() {
    }

    public static void main(String[] args) {
        if (Global.getSector() != null || Global.getSettings() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }

        ShuttleTrafficChecks run = new ShuttleTrafficChecks();
        try {
            // Misc reads settings in its static initialiser
            Global.setSettings(proxy(SettingsAPI.class));
            run.checkConstantSpeed();
            run.checkRefresh();
            run.checkMoveAcross();
            run.checkStarPass();
            run.checkStates();
            run.checkCruise();
            run.checkBudget();
            run.checkTrail();
            run.checkFleetHop();
            run.checkDodge();
            run.checkSteer();
            run.checkRoundRate();
            run.checkSwarmRange();
            run.checkDogfight();
        } finally {
            Global.setSettings(null);
        }

        if (!run.problems.isEmpty()) {
            for (String problem : run.problems) System.out.println("FAIL " + problem);
            System.out.println("Shuttle traffic: " + run.problems.size() + " of " + run.checks + " checks failed");
            System.exit(1);
        }

        for (String note : run.notes) System.out.println("NOTE " + note);
        System.out.println("Shuttle traffic: " + run.checks + " checks passed");
    }


    // trail length is speed * trailSeconds within one sample, follows a speed change within trailSeconds, and is 0 after a stop
    private void checkTrail() {
        float military = 75f;
        for (float seconds : new float[]{ShuttleLook.CRAFT_TRAIL_SECONDS, ShuttleLook.POD_TRAIL_SECONDS}) {
            float interval = seconds / (ShuttleTrail.TRAIL_POINTS - 1);
            for (float rate : RATES) {
                float dt = 1f / rate;
                float[] x = {0f};
                ShuttleTrail trail = new ShuttleTrail(seconds);
                String at = rate + " Hz, " + seconds + " s: ";

                float grown = runTrail(trail, x, SPEED, seconds * 0.4f, dt);
                check(grown <= SPEED * seconds * 0.4f + 0.01f && grown >= SPEED * (seconds * 0.4f - interval - dt),
                        at + "trail after launch is " + grown + ", expected about " + SPEED * seconds * 0.4f);

                float full = runTrail(trail, x, SPEED, seconds * 2f, dt);
                check(Math.abs(full - SPEED * seconds) <= SPEED * interval, at + "trail at constant speed is " + full + ", expected " + SPEED * seconds);

                float fast = runTrail(trail, x, military, seconds + interval + dt, dt);
                check(Math.abs(fast - military * seconds) <= military * interval, at + "military trail is " + fast + ", expected " + military * seconds);

                float half = runTrail(trail, x, military * 0.5f, seconds + interval + dt, dt);
                check(Math.abs(half - military * 0.5f * seconds) <= military * interval,
                        at + "trail after halving speed is " + half + ", expected " + military * 0.5f * seconds);

                float stopped = runTrail(trail, x, 0f, seconds + interval + dt, dt);
                check(stopped < 0.01f, at + "trail after a stop is " + stopped + ", expected 0");
            }
        }
    }

    // a service hop from a pad on an orbiting planet to a fleet circling it arrives on the fleet; a fleet that leaves mid flight sends the craft back to the pad
    private void checkFleetHop() {
        float speed = 30f * 1.8f;
        float fleetSpeed = 100f;
        for (float rate : RATES) {
            float dt = 1f / rate;
            for (boolean leave : new boolean[]{false, true}) {
                Body planet = new Body(0f, 0f, 150f);
                Body fleet = new Body(0f, 0f, 60f);
                orbit(planet, 2000f, 30f, 0f);
                fleetOrbit(fleet, planet, 0f);
                ShuttleAnchor pad = anchor(planet);
                ShuttlePaths paths = new ShuttlePaths(new ShuttleNetwork(), new Random(7));
                ShuttlePath path = paths.servicePath(pad, fleet.token, true, false);
                path.rebuild();

                float launch = Math.min(pad.getLandingLength(), path.getLength() * 0.4f);
                float land = Math.min(ShuttleAnchor.FLEET_END_LENGTH, path.getLength() * 0.4f);
                ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
                Vector2f point = new Vector2f();
                Vector2f leftAt = null;
                float time = 0f;
                float clear = 0f;
                boolean done = false;
                boolean redirected = false;
                while (!done && time < 200f) {
                    time += dt;
                    orbit(planet, 2000f, 30f, time);
                    if (leftAt == null) fleetOrbit(fleet, planet, time);
                    else fleet.loc.x += fleetSpeed * dt;

                    path.refresh(cursor);
                    done = path.move(cursor, speed * dt);
                    path.getPoint(cursor, point);
                    float traveled = path.getTraveled(cursor);
                    if (!redirected && traveled > launch && path.getLength() - traveled > land) clear += dt;

                    if (leave && leftAt == null && traveled > path.getLength() * 0.5f) {
                        leftAt = new Vector2f(fleet.loc);
                        ShuttlePath back = paths.redirectPath(null, point, Misc.getAngleInDegrees(new Vector2f(fleet.loc.x - point.x, fleet.loc.y - point.y)), pad);
                        back.rebuild();
                        path = back;
                        cursor = new ShuttlePath.Cursor();
                        redirected = true;
                    }
                }

                String name = rate + " Hz" + (leave ? ", fleet leaves" : "");
                check(done, name + ": service hop never ended");
                if (!leave) {
                    float off = Vector2f.sub(point, fleet.loc, null).length();
                    check(off <= fleet.radius * 0.5f + 1f, name + ": hop ended " + off + " from the fleet");
                    check(clear >= 1f, name + ": only " + clear + " s in clear view");
                    if (rate == 60f) notes.add("service hop to a fleet 120 su off a 150 su planet: " + String.format("%.1f", time) + " s, "
                            + String.format("%.1f", clear) + " s in clear view at " + speed + " su/s");
                } else {
                    float off = Vector2f.sub(point, planet.loc, null).length();
                    check(off <= planet.radius, name + ": turned back craft ended " + off + " from the planet");
                }
            }
        }
    }

    // a fleet that would hit a craft head on, from the side or from behind: the craft stays outside the fleet radius below burn 14 (run over)
    private void checkDodge() {
        float runOver = 280f;
        float[] burns = {2f, 9f, 20f};
        float[] angles = {90f, 45f, 135f, 180f, 0f};
        for (float nerve : new float[]{1f, 0.5f, 1.5f}) {
            for (float craftSpeed : new float[]{SPEED, 75f}) {
                for (float radius : new float[]{60f, 150f}) {
                    for (float burn : burns) {
                        float fleetSpeed = burn * 20f;
                        float worst = Float.MAX_VALUE;
                        for (float angle : angles) {
                            for (float aim : new float[]{0f, 0.5f, -0.5f}) {
                                for (float rate : RATES) {
                                    float min = runDodge(craftSpeed, radius, fleetSpeed, angle, aim * radius, 1f / rate, nerve);
                                    worst = Math.min(worst, min);
                                    if (fleetSpeed < runOver) {
                                        check(min >= radius, "nerve " + nerve + ", craft at " + craftSpeed + " su/s, fleet radius " + radius + " at burn " + burn + " from " + angle
                                                + " deg, aim " + aim + ", " + rate + " Hz: came within " + min);
                                    }
                                }
                            }
                        }
                        if (nerve == 1f && craftSpeed == SPEED && radius == 60f) notes.add("dodge at burn " + (int) burn + ": closest " + String.format("%.0f", worst) + " su from a 60 su fleet");
                    }
                }
            }
        }
    }

    // the fleet reaches the craft's unshifted spot well after the lookahead; returns the closest distance between the craft and the fleet centre
    private static float runDodge(float craftSpeed, float radius, float fleetSpeed, float angle, float aim, float dt, float nerve) {
        Dodge dodge = new Dodge();
        dodge.setNerve(nerve);
        float fvx = fleetSpeed * (float) Math.cos(Math.toRadians(angle));
        float fvy = fleetSpeed * (float) Math.sin(Math.toRadians(angle));
        float closing = (float) Math.sqrt((craftSpeed - fvx) * (craftSpeed - fvx) + fvy * fvy);
        float hitTime = Math.max(6f, (radius + Dodge.MARGIN / nerve + 50f) / closing + Dodge.LOOKAHEAD * nerve);
        float hitX = craftSpeed * hitTime + aim;
        float bx = 0f;
        float min = Float.MAX_VALUE;

        for (float time = 0f; time < hitTime * 2f; time += dt) {
            float speed = craftSpeed * dodge.getPace();
            bx += speed * dt;
            float fx = hitX + fvx * (time + dt - hitTime);
            float fy = fvy * (time + dt - hitTime);

            dodge.begin();
            dodge.add(bx, 0f, speed, 0f, fx, fy, fvx, fvy, radius);
            dodge.step(dt, 1f);

            float dx = bx + dodge.getShiftX() - fx;
            float dy = dodge.getShiftY() - fy;
            min = Math.min(min, (float) Math.sqrt(dx * dx + dy * dy));
        }

        return min;
    }

    // a craft launched 400 su to the side reaches a slot 45 su ahead and 30 su beside a fleet, then holds it, straight and turning
    private void checkSteer() {
        for (float burn : new float[]{1f, 5f, 9f}) {
            float fleetSpeed = burn * 20f;
            for (float turnRadius : new float[]{0f, 2000f}) {
                for (float rate : RATES) {
                    float dt = 1f / rate;
                    Vector2f fleet = new Vector2f(0f, 0f);
                    float heading = 0f;
                    SteerMover steer = new SteerMover(new Vector2f(0f, 400f), new Vector2f());
                    Vector2f slot = new Vector2f();
                    Vector2f slotVel = new Vector2f();
                    float reached = -1f;
                    float worst = 0f;
                    float time = 0f;

                    while (time < 60f) {
                        time += dt;
                        float turn = turnRadius > 0f ? fleetSpeed / turnRadius : 0f;
                        heading += turn * dt;
                        float hx = (float) Math.cos(heading);
                        float hy = (float) Math.sin(heading);
                        fleet.x += hx * fleetSpeed * dt;
                        fleet.y += hy * fleetSpeed * dt;

                        float ahead = 45f;
                        float side = 30f;
                        slot.set(fleet.x + hx * ahead - hy * side, fleet.y + hy * ahead + hx * side);
                        // slot velocity: fleet velocity plus the turn of the offset
                        slotVel.set(hx * fleetSpeed - turn * (hy * ahead + hx * side), hy * fleetSpeed + turn * (hx * ahead - hy * side));
                        steer.steer(dt, slot.x, slot.y, slotVel.x, slotVel.y, SteerMover.getDashSpeed(fleetSpeed));

                        float off = Vector2f.sub(steer.pos, slot, null).length();
                        // one frame of a burn 9 fleet is 6 su at 30 Hz
                        if (reached < 0f && off <= 10f) reached = time;
                        else if (reached >= 0f) worst = Math.max(worst, off);
                    }

                    String name = "steer at burn " + (int) burn + (turnRadius > 0f ? ", turning" : "") + ", " + rate + " Hz";
                    check(reached >= 0f, name + ": never reached the slot");
                    check(worst <= 20f, name + ": drifted " + worst + " su off the slot after reaching it");
                    if (rate == 60f && turnRadius == 0f) notes.add(name + ": slot reached after " + String.format("%.1f", reached) + " s, off by at most "
                            + String.format("%.1f", worst) + " su after");
                }
            }
        }

        Vector2f from = new Vector2f(0f, 1000f);
        float t = SteerMover.interceptTime(from, new Vector2f(0f, 0f), new Vector2f(180f, 0f), 240f);
        Vector2f meet = new Vector2f(180f * t, 0f);
        check(!Float.isNaN(t) && Math.abs(Vector2f.sub(meet, from, null).length() - 240f * t) < 1f, "intercept time " + t + " does not meet the target");
        check(Float.isNaN(SteerMover.interceptTime(from, new Vector2f(0f, 0f), new Vector2f(300f, 0f), 240f)) || SteerMover.interceptTime(from, new Vector2f(0f, 0f), new Vector2f(300f, 0f), 240f) > 0f,
                "a target faster than the craft gave a negative intercept time");
    }

    // a fleet in orbit 120 su off the surface, a slow lap
    private static void fleetOrbit(Body fleet, Body planet, float time) {
        double angle = Math.toRadians(200f + time * 4f);
        float radius = planet.radius + 120f + fleet.radius;
        fleet.loc.set(planet.loc.x + (float) Math.cos(angle) * radius, planet.loc.y + (float) Math.sin(angle) * radius);
    }

    private static float runTrail(ShuttleTrail trail, float[] x, float speed, float time, float dt) {
        for (float t = 0f; t < time; t += dt) {
            trail.advance(dt);
            x[0] += speed * dt;
            trail.pushTrailIfDue(x[0], 0f);
        }
        return trail.getLength(x[0], 0f);
    }
    // Battle.advance: max / (2 x min) fleet points clamped 1 to 5, x0.25 with a station; fighters launch at 1 round a day or slower
    private void checkRoundRate() {
        float[][] cases = {{100f, 100f, 0f, 1f}, {200f, 100f, 0f, 1f}, {300f, 100f, 0f, 1.5f}, {1000f, 10f, 0f, 5f},
                {800f, 100f, 1f, 1f}, {900f, 100f, 1f, 1.125f}, {0f, 100f, 0f, 1f}, {100f, 100f, 1f, 0.25f}};
        for (float[] c : cases) {
            float rate = ShuttleDogfights.getRoundRate(c[0], c[1], c[2] > 0f);
            check(Math.abs(rate - c[3]) < 0.001f, "round rate " + c[0] + " vs " + c[1] + (c[2] > 0f ? " with station" : "") + ": " + rate + ", expected " + c[3]);
        }
    }

    // segment distance for the meteor swarm closure, both ends and the middle
    private void checkSwarmRange() {
        Vector2f a = new Vector2f(0f, 0f);
        Vector2f b = new Vector2f(1000f, 0f);
        float[][] cases = {{500f, 300f, 300f}, {-400f, 300f, 500f}, {1300f, -400f, 500f}, {0f, 0f, 0f}};
        for (float[] c : cases) {
            float dist = ShuttleNetwork.getSegmentDistance(new Vector2f(c[0], c[1]), a, b);
            check(Math.abs(dist - c[2]) < 0.01f, "swarm range at " + c[0] + ", " + c[1] + ": " + dist + ", expected " + c[2]);
        }
        check(Math.abs(ShuttleNetwork.getSegmentDistance(new Vector2f(30f, 40f), a, a) - 50f) < 0.01f, "swarm range to a point");
    }

    // 12 against 12 for two minutes over three seeds: hit rates, kills, strafing passes, leash, cap, cost; then both ends
    private void checkDogfight() {
        for (float rate : new float[]{30f, 60f}) {
            String at = rate + " Hz: ";
            int[] totals = new int[6];
            int strikeGroups = 0;
            int maxSide = 0;
            int leash = 0;
            long nanos = 0L;
            int frames = 0;
            float seconds = 120f;
            ShuttleDogfight fight = null;
            List<DogfightFleet> fleets = null;

            for (int seed : new int[]{7, 11, 23}) {
                fleets = new ArrayList<>();
                fight = newFight(new Random(seed), fleets);
                Set<DogfightGroup> strike = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int frame = 0; frame < (int) (seconds * rate); frame++) {
                    long start = System.nanoTime();
                    fight.advance(1f / rate);
                    nanos += System.nanoTime() - start;
                    frames++;

                    int[] count = new int[2];
                    for (Dogfighter fighter : fight.getFighters()) {
                        if (fighter.isDone()) continue;
                        count[fighter.side]++;
                        if (fighter.group.role == WingType.Role.STRIKE) strike.add(fighter.group);
                    }
                    maxSide = Math.max(maxSide, Math.max(count[0], count[1]));
                }

                ShuttleProjectiles shots = fight.getProjectiles();
                for (int side = 0; side < 2; side++) {
                    totals[0] += shots.getFired(side, false);
                    totals[1] += shots.getHits(side, false);
                    totals[2] += shots.getFired(side, true);
                    totals[3] += shots.getHits(side, true);
                    totals[4] += fight.getKills(side);
                    totals[5] += fight.getPasses(side);
                }
                strikeGroups += strike.size();
                leash = Math.max(leash, fight.getMaxLeashOvershoot());
            }

            float fighterRate = totals[0] > 0 ? totals[1] / (float) totals[0] : 0f;
            float fleetRate = totals[2] > 0 ? totals[3] / (float) totals[2] : 0f;
            float killsPerMinute = totals[4] / (seconds * 3f / 60f);
            float passesPerGroup = strikeGroups > 0 ? totals[5] / (float) strikeGroups : 0f;
            float frameMicros = nanos / 1000f / frames;

            check(maxSide <= ShuttleDogfight.MAX_PER_SIDE, at + "a side flew " + maxSide + " fighters, cap " + ShuttleDogfight.MAX_PER_SIDE);
            check(totals[0] > 300, at + "only " + totals[0] + " shots at fighters in three fights");
            check(fighterRate >= 0.15f && fighterRate <= 0.85f, at + "fighter hit rate " + fighterRate + ", expected 0.15 to 0.85");
            check(totals[2] > 60 && fleetRate >= 0.5f, at + "fleet hit rate " + fleetRate + " of " + totals[2] + " shots, expected at least 0.5");
            check(killsPerMinute >= 2f && killsPerMinute <= 60f, at + killsPerMinute + " kills per minute, expected 2 to 60");
            check(passesPerGroup >= 0.5f, at + passesPerGroup + " strafing passes per strike group, expected at least 0.5");
            check(leash <= 120, at + "a fighter got " + leash + " su past the leash");
            check(frameMicros < 2000f, at + frameMicros + " us per frame");
            notes.add(String.format("dogfight at %s Hz: fighter hit rate %.2f, fleet hit rate %.2f, %.1f kills per minute, %.1f passes per strike group, leash overshoot %d su, %.0f us per frame",
                    rate, fighterRate, fleetRate, killsPerMinute, passesPerGroup, leash, frameMicros));

            // side 0 won and lives: back to its fleets; side 1's fleets are gone and no port: they explode
            for (DogfightFleet fleet : fleets) fleet.alive = fleet.side == 0;
            fight.end(0);
            float end = 0f;
            boolean exploded = true;
            while (!fight.isOver() && end < 40f) {
                fight.advance(1f / rate);
                end += 1f / rate;
                for (Dogfighter fighter : fight.getFighters()) {
                    if (fighter.side == 1 && end > 2.2f && fighter.getState() != Dogfighter.State.DYING) exploded = false;
                }
            }
            check(fight.isOver(), at + "battle end not over after 40 s, " + fight.getFighters().size() + " fighters left");
            check(exploded, at + "a fighter of the lost side outlived its 1.5 s");
        }
    }

    // side 0: gun escorts and torpedo bombers, side 1: laser escorts and gun support, two fleets each 220 su apart
    private static ShuttleDogfight newFight(Random random, List<DogfightFleet> fleets) {
        DogfightHost host = new DogfightHost() {
            @Override
            public void particle(Vector2f loc, Vector2f vel, float size, float brightness, float duration, Color color) {
            }

            @Override
            public Refuge findRefuge(Dogfighter fighter) {
                return null;
            }
        };

        ShuttleDogfight fight = new ShuttleDogfight(host, random);
        WingType[][] wings = {
                {wing(WingType.Role.ESCORT, true, false, false), wing(WingType.Role.STRIKE, false, false, true)},
                {wing(WingType.Role.ESCORT, false, true, false), wing(WingType.Role.SUPPORT, true, false, false)}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 2; i++) {
                DogfightFleet fleet = new DogfightFleet(side, List.of(wings[side]));
                fleet.loc.set(side == 0 ? -110f : 110f, i == 0 ? -60f : 60f);
                fleet.radius = 40f;
                fleets.add(fleet);
                fight.addFleet(fleet);
            }
            fight.setWantedGroups(side, 4);
        }
        fight.setCenter(0f, 0f, 190f);
        return fight;
    }

    private static WingType wing(WingType.Role role, boolean guns, boolean lasers, boolean torpedoes) {
        ShuttleHull.Look look = new ShuttleHull.Look("check_" + role, null, Color.ORANGE, Color.ORANGE, true);
        return new WingType(new ShuttleHull(look, true, false, true, false, false, 2, 4f, 6f), role, guns, lasers, torpedoes, Color.RED);
    }

    private void check(boolean passed, String problem) {
        checks++;
        if (!passed) problems.add(problem);
    }

    // a cursor moves at the same speed on beziers and circles, and arrives at the same time at any frame rate
    // 16 sample tables, linear inside a sample: in the first sample after a short bezier handle a step strays by about 20%
    private void checkConstantSpeed() {
        for (float rate : RATES) {
            Body center = new Body(0f, 0f, 100f);
            ShuttlePath path = testPath(center);
            path.rebuild();
            float length = path.getLength();
            float dt = 1f / rate;
            float step = SPEED * dt;

            ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
            Vector2f last = new Vector2f();
            Vector2f point = new Vector2f();
            path.getPoint(cursor, last);
            float worst = 0f;
            float worstAt = 0f;
            int frames = 0;
            boolean done = false;
            while (!done && frames < 100000) {
                path.refresh(cursor);
                done = path.move(cursor, step);
                path.getPoint(cursor, point);
                frames++;
                float error = Math.abs(Vector2f.sub(point, last, null).length() - step) / step;
                if (!done && error > worst) {
                    worst = error;
                    worstAt = path.getTraveled(cursor);
                }
                last.set(point);
            }

            float time = frames * dt;
            check(done, rate + " Hz: cursor never reached the end");
            check(Math.abs(time - length / SPEED) <= dt + length / SPEED * 0.0001f, rate + " Hz: arrived after " + time + " s, expected " + length / SPEED);
            check(worst < 0.25f, rate + " Hz: step length off by " + worst * 100f + "% at " + worstAt + " of " + length);
        }
    }

    // a rebuilt table never moves the craft; a lane that moves as a whole carries the craft with it
    private void checkRefresh() {
        Body a = new Body(-1500f, 200f, 120f);
        Body b = new Body(1800f, -400f, 90f);
        ShuttlePath path = lane(a, b);
        path.rebuild();

        Vector2f before = new Vector2f();
        Vector2f after = new Vector2f();
        ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
        for (int i = 1; i < 10; i++) {
            path.place(cursor, path.getLength() * i / 10f);
            path.getPoint(cursor, before);
            path.refresh(cursor);
            path.rebuild();
            path.getPoint(cursor, after);
            check(before.equals(after), "refresh moved a still craft at " + i + "/10 from " + before + " to " + after);
        }

        for (int i = 1; i < 10; i++) {
            path.place(cursor, path.getLength() * i / 10f);
            path.getPoint(cursor, before);
            a.loc.translate(37f, -21f);
            b.loc.translate(37f, -21f);
            path.refresh(cursor);
            path.getPoint(cursor, after);
            float dx = after.x - before.x - 37f;
            float dy = after.y - before.y + 21f;
            check(Math.abs(dx) + Math.abs(dy) < 0.05f, "refresh did not carry the craft with its lane at " + i + "/10, off by " + dx + ", " + dy);
        }
    }

    // moving over segment ends keeps the whole distance
    private void checkMoveAcross() {
        Body center = new Body(0f, 0f, 100f);
        ShuttlePath path = testPath(center);
        path.rebuild();
        float length = path.getLength();
        ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();

        for (float start = 5f; start < length - 400f; start += length / 23f) {
            for (float distance : new float[]{1f, 50f, 333f}) {
                path.place(cursor, start);
                float before = path.getTraveled(cursor);
                boolean done = path.move(cursor, distance);
                float after = path.getTraveled(cursor);
                check(!done && Math.abs(after - before - distance) < 0.01f, "move " + distance + " from " + before + " went " + (after - before));
            }
        }

        path.place(cursor, length - 10f);
        check(path.move(cursor, 20f), "move past the end did not report the end");
        check(Math.abs(path.getTraveled(cursor) - length) < 0.01f, "move past the end stopped at " + path.getTraveled(cursor) + " of " + length);
    }

    // a lane across a star stays clear of it while both anchors orbit during the flight
    private void checkStarPass() {
        Body star = new Body(0f, 0f, 400f);
        float clearance = star.radius * 1.1f;

        for (float rate : RATES) {
            for (float offset : new float[]{-30f, 30f}) {
                for (float bend : new float[]{-300f, 0f, 300f}) {
                    Body a = new Body(0f, 0f, 150f);
                    Body b = new Body(0f, 0f, 100f);
                    orbit(a, 2200f, 10f, 0f);
                    orbit(b, 2900f, 185f, 0f);

                    ShuttlePath.Waypoint start = new ShuttlePath.PadWaypoint(anchor(a), new ShuttlePath.EntityWaypoint(b.token), 8f, 0f, false);
                    ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(anchor(b), new ShuttlePath.EntityWaypoint(a.token), -8f, 0f, true);
                    ShuttlePath.Waypoint pass = new ShuttlePath.StarPassWaypoint(a.token, b.token, star.token, clearance, bend, offset, false);
                    ShuttlePath path = new ShuttlePath()
                            .add(new ShuttlePath.BezierSegment(start, pass, 230f, 700f, 0f))
                            .add(new ShuttlePath.BezierSegment(pass, end, 700f, 180f, 0f));
                    path.rebuild();

                    float dt = 1f / rate;
                    float time = 0f;
                    float closest = Float.MAX_VALUE;
                    ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
                    Vector2f point = new Vector2f();
                    boolean done = false;
                    while (!done && time < 1000f) {
                        time += dt;
                        orbit(a, 2200f, 10f, time);
                        orbit(b, 2900f, 185f, time);
                        path.refresh(cursor);
                        done = path.move(cursor, SPEED * 4f * dt);
                        path.getPoint(cursor, point);
                        closest = Math.min(closest, point.length());
                    }

                    String name = rate + " Hz, offset " + offset + ", bend " + bend;
                    check(done, name + ": star pass flight never ended");
                    check(closest >= star.radius, name + ": star pass came within " + closest + " of a " + star.radius + " su star");
                }
            }
        }
    }

    private void checkStates() {
        for (ShuttleState from : ShuttleState.values()) {
            for (Map.Entry<String, UnaryOperator<ShuttleState>> change : CHANGES.entrySet()) {
                ShuttleState expected = null;
                for (Object[] row : TRANSITIONS) if (row[0] == from && row[1].equals(change.getKey())) expected = (ShuttleState) row[2];

                ShuttleState result;
                try {
                    result = change.getValue().apply(from);
                } catch (IllegalStateException e) {
                    result = null;
                }

                check(result == expected, change.getKey() + " from " + from + " gave " + result + ", expected " + (expected != null ? expected : "a throw"));
            }
        }

        check(LOOKS.length == ShuttleState.values().length, "state look table misses a state");
        for (Object[] row : LOOKS) {
            ShuttleState state = (ShuttleState) row[0];
            check(state.pace == (float) row[1] && state.turnMult == (float) row[2], state + " pace " + state.pace + " turn " + state.turnMult);
            check(state.isFleeing() == (boolean) row[3] && state.isHolding() == (boolean) row[4] && state.isStranded() == (boolean) row[5],
                    state + " flags differ from the old flags");
        }
    }

    // a cruise reaches the sight, laps it outside its radius at 0.9 speed and lands back at home
    private void checkCruise() {
        for (int seed = 0; seed < 20; seed++) {
            Body home = new Body(0f, 0f, 120f);
            Body sight = new Body(0f, 0f, 200f);
            orbit(home, 1800f, 20f, 0f);
            orbit(sight, 3400f, 60f, 0f);
            ShuttlePaths paths = new ShuttlePaths(new ShuttleNetwork(), new Random(seed));
            ShuttlePath path = paths.cruisePath(anchor(home), sight.token, paths.rollCruise((seed % 5 - 2) * 0.1f));
            path.rebuild();

            float time = 0f;
            float closestSight = Float.MAX_VALUE;
            float lapSpeed = 0f;
            ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
            Vector2f point = new Vector2f();
            boolean done = false;
            while (!done && time < 2000f) {
                time += 1f / 60f;
                orbit(home, 1800f, 20f, time);
                orbit(sight, 3400f, 60f, time);
                path.refresh(cursor);
                done = path.move(cursor, SPEED / 60f);
                path.getPoint(cursor, point);
                closestSight = Math.min(closestSight, Vector2f.sub(point, sight.loc, null).length());
                if (path.getSegment(cursor) instanceof ShuttlePath.CircleSegment) lapSpeed = path.getSpeedMult(cursor);
            }

            String name = "cruise seed " + seed;
            check(done, name + ": never ended");
            check(closestSight >= sight.radius * 1.2f, name + ": came within " + closestSight + " of the sight");
            check(closestSight <= sight.radius * 1.5f + 60f + 1f, name + ": never reached the lap, closest " + closestSight);
            check(lapSpeed == 0.9f, name + ": lap speed " + lapSpeed);
            check(Vector2f.sub(point, home.loc, null).length() <= home.radius, name + ": ended " + Vector2f.sub(point, home.loc, null).length() + " from home");
        }
    }

    // a busy system keeps room for local flights, lanes scale down but keep one flight each, rare flights fit while there is room
    private void checkBudget() {
        List<ShuttleRoute> routes = new ArrayList<>();
        for (int i = 0; i < 6; i++) routes.add(route(ShuttleRoute.Kind.MARKET, 8));
        routes.add(route(ShuttleRoute.Kind.PICKET, 2));
        routes.add(route(ShuttleRoute.Kind.PICKET, 2));
        for (int i = 0; i < 4; i++) routes.add(route(ShuttleRoute.Kind.CRUISE, 1));
        routes.add(route(ShuttleRoute.Kind.MINING, 3));
        routes.add(route(ShuttleRoute.Kind.HUNT, 1));

        ShuttleBudget budget = new ShuttleBudget();
        budget.update(routes, 60);
        int lanes = 0;
        for (ShuttleRoute route : routes) {
            if (route.kind.group != ShuttleBudget.Group.LANES) check(route.max == route.demand, route.kind + " max " + route.max + " differs from demand");
            else lanes += route.max;
        }

        check(lanes <= 45, "lanes take " + lanes + " of 60 with 15 reserved for local flights");
        check(lanes >= 40, "lanes scaled down to " + lanes + ", more than needed");
        budget.add(ShuttleBudget.Group.LANES, lanes);
        check(budget.getRoom(ShuttleBudget.Group.LOCAL) == 60 - lanes, "local room " + budget.getRoom(ShuttleBudget.Group.LOCAL) + " with lanes full");
        check(budget.getRoom(ShuttleBudget.Group.LANES) == 45 - lanes, "lanes have room " + budget.getRoom(ShuttleBudget.Group.LANES) + " inside the local reserve");
        budget.add(ShuttleBudget.Group.LOCAL, 10);
        check(budget.getRoom(ShuttleBudget.Group.RARE) == 50 - lanes, "rare room " + budget.getRoom(ShuttleBudget.Group.RARE));

        budget.update(routes, 6);
        for (ShuttleRoute route : routes) check(route.max >= 1, route.kind + " lost its last flight at cap 6");

        List<ShuttleRoute> quiet = List.of(route(ShuttleRoute.Kind.MARKET, 3), route(ShuttleRoute.Kind.PICKET, 1));
        budget.update(quiet, 60);
        check(quiet.get(0).max == 3, "a quiet system scaled its lane to " + quiet.get(0).max);
        budget.count(List.of());
        check(budget.getRoom(ShuttleBudget.Group.LANES) == 58 && budget.getRoom(ShuttleBudget.Group.LOCAL) == 57,
                "quiet system rooms " + budget.getRoom(ShuttleBudget.Group.LANES) + " and " + budget.getRoom(ShuttleBudget.Group.LOCAL));
    }

    private static ShuttleRoute route(ShuttleRoute.Kind kind, int demand) {
        ShuttleRoute route = new ShuttleRoute(kind.name(), kind, null, null, 0f);
        route.demand = demand;
        return route;
    }

    // one lap per 400 s, far less than real orbits, so the lane turns a lot during one flight
    private static void orbit(Body body, float radius, float startAngle, float time) {
        double angle = Math.toRadians(startAngle + time * 0.9f);
        body.loc.set((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius);
    }

    private static ShuttlePath testPath(Body center) {
        ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(new Vector2f(-900f, -300f), new Vector2f(1f, 0.2f));
        ShuttlePath.CircleSegment circle = new ShuttlePath.CircleSegment(center.token, 0f, 0f, 260f, 190f, 25f, 200f, 400f, 0f);
        ShuttlePath.Waypoint end = new ShuttlePath.FixedWaypoint(new Vector2f(800f, 700f), new Vector2f(0.3f, 1f));
        return new ShuttlePath()
                .add(new ShuttlePath.BezierSegment(start, new ShuttlePath.SegmentWaypoint(circle, 0f), 150f, 150f, 0f))
                .add(circle)
                .add(new ShuttlePath.BezierSegment(new ShuttlePath.SegmentWaypoint(circle, 1f), end, 150f, 200f, 0f))
                .add(new ShuttlePath.BezierSegment(end, new ShuttlePath.FixedWaypoint(new Vector2f(3800f, -200f), new Vector2f(1f, -0.4f)), 400f, 300f, 700f));
    }

    private static ShuttlePath lane(Body a, Body b) {
        ShuttlePath.Waypoint start = new ShuttlePath.PadWaypoint(anchor(a), new ShuttlePath.EntityWaypoint(b.token), -8f, 0f, false);
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(anchor(b), new ShuttlePath.EntityWaypoint(a.token), 8f, 0f, true);
        return new ShuttlePath().add(new ShuttlePath.BezierSegment(start, end, 200f, 170f, 250f));
    }

    private static ShuttleAnchor anchor(Body body) {
        return new ShuttleAnchor(body.token, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.MARKET, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }

    private static final class Body {
        private final Vector2f loc;
        private final float radius;
        private final SectorEntityToken token;

        private Body(float x, float y, float radius) {
            this.loc = new Vector2f(x, y);
            this.radius = radius;
            this.token = (SectorEntityToken) Proxy.newProxyInstance(SectorEntityToken.class.getClassLoader(), new Class<?>[]{SectorEntityToken.class},
                    (self, method, args) -> switch (method.getName()) {
                        case "getLocation" -> loc;
                        case "getRadius" -> this.radius;
                        case "isAlive" -> true;
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }
}
