package indevo.ambience.shuttletraffic.dogfights;

import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// fighters of one watched NPC battle: groups from both sides, targeting every 0.25 s, steering every frame
public class ShuttleDogfight {

    public static final int MAX_PER_SIDE = 12;
    public static final float LEASH = 300f;
    private static final float TICK = 0.25f;
    private static final float LAUNCH_SPREAD = 3f;
    private static final float REPLACE_MIN = 4f;
    private static final float REPLACE_MAX = 8f;
    private static final float ESCORT_ZONE = 400f;
    private static final float SUPPORT_ZONE = 150f;
    private static final float TAIL_RANGE = 200f;
    private static final float STRAFE_STANDOFF = 250f;
    private static final float STRAFE_AIM = 0.6f;
    private static final float STRAFE_BREAK = 100f;
    private static final float SLOT_BACK = 12f;
    private static final float SLOT_SIDE = 10f;
    private static final float LAUNCH_TIME = 1.5f;
    private static final float LOOP_TIME = 2.5f;
    private static final float GUN_RANGE = 300f;
    private static final float GUN_CONE = 20f;
    private static final float GUN_SPREAD = 0.5f;
    private static final float GUN_GAP = 0.15f;
    private static final float LASER_RANGE = 250f;
    private static final float LASER_CONE = 8f;
    private static final float LASER_SPREAD = 1f;
    private static final float LASER_GAP = 0.12f;
    private static final float STRAFE_FIRE_RANGE = 300f;
    private static final float TORPEDO_RANGE = 250f;
    private static final float COOLDOWN_MIN = 2f;
    private static final float COOLDOWN_MAX = 3.5f;
    private static final float HIT_SIZE = 10f;
    private static final float FLEET_HIT_SIZE = 14f;
    private static final float TORPEDO_HIT_SIZE = 30f;

    private final DogfightHost host;
    private final Random random;
    @SuppressWarnings("unchecked")
    private final List<DogfightFleet>[] sides = new List[]{new ArrayList<>(), new ArrayList<>()};
    private final List<Dogfighter> fighters = new ArrayList<>();
    private final List<DogfightGroup> groups = new ArrayList<>();
    private final ShuttleProjectiles projectiles = new ShuttleProjectiles();
    private final Vector2f center = new Vector2f();
    private float radius = 0f;
    private final int[] wantedGroups = new int[2];
    private final int[] launchedGroups = new int[2];
    private final float[] launchTimer = new float[2];
    private boolean launching = true;
    private boolean ended = false;
    private float tickTimer = 0f;

    // check program counters
    private final int[] passes = new int[2];
    private final int[] kills = new int[2];
    private int maxLeashOvershoot = 0;

    public ShuttleDogfight(DogfightHost host, Random random) {
        this.host = host;
        this.random = random;
    }

    public void addFleet(DogfightFleet fleet) {
        sides[fleet.side].add(fleet);
    }

    // groups per side clamp(ships / 3, 1, 4), launched over LAUNCH_SPREAD s
    public void setWantedGroups(int side, int groupCount) {
        if (wantedGroups[side] == 0 && groupCount > 0) launchTimer[side] = random.nextFloat() * LAUNCH_SPREAD / groupCount;
        wantedGroups[side] = groupCount;
    }

    // false stops new groups and replacements, fighters already out keep fighting
    public void setLaunching(boolean launching) {
        this.launching = launching;
    }

    public void setCenter(float x, float y, float radius) {
        center.set(x, y);
        this.radius = radius;
    }

    public void advance(float amount) {
        if (!ended && launching) {
            for (int side = 0; side < 2; side++) launch(side, amount);
        }

        tickTimer -= amount;
        if (tickTimer <= 0f) {
            tickTimer = TICK;
            for (Dogfighter fighter : fighters) think(fighter);
        }

        for (Dogfighter fighter : fighters) {
            if (fighter.dieDelay >= 0f) {
                fighter.dieDelay -= amount;
                if (fighter.dieDelay < 0f) kill(fighter);
            }
            steer(fighter, amount);
            fighter.fly(amount);
            weapons(fighter, amount);
            trackLeash(fighter);
        }

        projectiles.advance(amount, this);

        fighters.removeIf(Dogfighter::isDone);
        groups.removeIf(group -> group.members.stream().allMatch(Dogfighter::isDone));
    }

    // fighters whose fleet lives fly back to it, the winners after one loop; the rest look for a port or explode
    public void end(int winner) {
        if (ended) return;
        ended = true;

        for (Dogfighter fighter : fighters) {
            if (fighter.state == Dogfighter.State.DYING) continue;

            if (fighter.home != null && fighter.home.alive) {
                fighter.loopDir = random.nextBoolean() ? 1f : -1f;
                fighter.setState(fighter.side == winner ? Dogfighter.State.LOOP : Dogfighter.State.RETURN);
                continue;
            }

            fighter.refuge = host.findRefuge(fighter);
            if (fighter.refuge != null) fighter.setState(Dogfighter.State.REFUGE);
            else fighter.dieDelay = random.nextFloat() * 1.5f;
        }
    }

    public boolean isOver() {
        return ended && fighters.isEmpty() && projectiles.isEmpty();
    }

    public boolean isEnded() {
        return ended;
    }

    public List<Dogfighter> getFighters() {
        return fighters;
    }

    public ShuttleProjectiles getProjectiles() {
        return projectiles;
    }

    // fighters of this side shot down
    public int getKills(int side) {
        return kills[side];
    }

    public List<DogfightFleet> getSide(int side) {
        return sides[side];
    }

    public int getPasses(int side) {
        return passes[side];
    }

    public int getMaxLeashOvershoot() {
        return maxLeashOvershoot;
    }

    public int countStrikeGroups(int side) {
        int count = 0;
        for (DogfightGroup group : groups) if (group.side == side && group.role == WingType.Role.STRIKE) count++;
        return count;
    }

    private void launch(int side, float amount) {
        launchTimer[side] -= amount;
        if (launchTimer[side] > 0f || wantedGroups[side] <= 0) return;

        int alive = 0;
        int craft = 0;
        for (DogfightGroup group : groups) if (group.side == side && group.isAlive()) alive++;
        for (Dogfighter fighter : fighters) if (fighter.side == side && !fighter.isDone()) craft++;
        if (alive >= wantedGroups[side] || craft >= MAX_PER_SIDE) return;

        launchTimer[side] = LAUNCH_SPREAD / wantedGroups[side] * (0.5f + random.nextFloat());
        DogfightFleet fleet = pickLaunchFleet(side);
        if (fleet == null) return;

        WingType wing = fleet.wings.get(random.nextInt(fleet.wings.size()));
        WingType.Role role = wing.role();
        if (role != WingType.Role.STRIKE && !hasBombers(side) && launchedGroups[side] % 3 == 2) role = WingType.Role.STRIKE;
        launchedGroups[side]++;

        float roll = random.nextFloat();
        int size = Math.min(MAX_PER_SIDE - craft, roll < 0.4f ? 1 : roll < 0.75f ? 2 : 3);
        DogfightGroup group = new DogfightGroup(side, role);
        float heading = Dogfighter.angleTo(fleet.loc, getEnemyCenter(side).x, getEnemyCenter(side).y) + (random.nextFloat() - 0.5f) * 60f;
        Vector2f start = new Vector2f(fleet.loc.x + (random.nextFloat() - 0.5f) * fleet.radius, fleet.loc.y + (random.nextFloat() - 0.5f) * fleet.radius);

        for (int i = 0; i < size; i++) {
            Dogfighter fighter = new Dogfighter(wing, side, group, i, fleet, 2 + random.nextInt(2), random.nextFloat());
            fighter.place(new Vector2f(start.x - (float) Math.cos(Math.toRadians(heading)) * SLOT_BACK * i,
                    start.y - (float) Math.sin(Math.toRadians(heading)) * SLOT_BACK * i), heading);
            group.members.add(fighter);
            fighters.add(fighter);
        }

        groups.add(group);
    }

    // a round the side lost on fleet points: one of its groups goes down within half a second
    public void loseGroup(int side) {
        DogfightGroup picked = null;
        int seen = 0;
        for (DogfightGroup group : groups) {
            if (group.side != side || !group.isAlive()) continue;
            seen++;
            if (random.nextInt(seen) == 0) picked = group;
        }
        if (picked == null) return;

        for (Dogfighter member : picked.members) {
            if (member.canBeHit() && member.dieDelay < 0f) member.dieDelay = random.nextFloat() * 0.5f;
        }
    }

    // a lost group is replaced 4 to 8 s later, not at once
    void onGroupLost(int side) {
        launchTimer[side] = Math.max(launchTimer[side], REPLACE_MIN + random.nextFloat() * (REPLACE_MAX - REPLACE_MIN));
    }

    private DogfightFleet pickLaunchFleet(int side) {
        DogfightFleet picked = null;
        int seen = 0;
        for (DogfightFleet fleet : sides[side]) {
            if (!fleet.alive || fleet.wings.isEmpty()) continue;
            seen++;
            if (random.nextInt(seen) == 0) picked = fleet;
        }
        return picked;
    }

    private boolean hasBombers(int side) {
        for (DogfightFleet fleet : sides[side]) for (WingType wing : fleet.wings) if (wing.role() == WingType.Role.STRIKE) return true;
        return false;
    }

    private Vector2f getEnemyCenter(int side) {
        Vector2f sum = new Vector2f();
        int count = 0;
        for (DogfightFleet fleet : sides[1 - side]) {
            if (!fleet.alive) continue;
            Vector2f.add(sum, fleet.loc, sum);
            count++;
        }
        if (count == 0) return new Vector2f(center);
        sum.scale(1f / count);
        return sum;
    }

    // state changes every 0.25 s; steering every frame
    private void think(Dogfighter fighter) {
        Dogfighter.State state = fighter.state;
        if (!fighter.isFighting() || fighter.dieDelay >= 0f) return;
        if (state == Dogfighter.State.LAUNCH && fighter.stateTime < LAUNCH_TIME) return;

        Dogfighter tail = findTail(fighter);
        if (state == Dogfighter.State.EVADE) {
            if (tail == null) resume(fighter);
            else if (fighter.stateTime > 3f) extend(fighter);
            return;
        }

        if (state == Dogfighter.State.EXTEND) {
            if (fighter.stateTime > fighter.extendTime) resume(fighter);
            return;
        }

        if (tail != null) {
            fighter.setState(Dogfighter.State.EVADE);
            fighter.weaveTimer = 0f;
            return;
        }

        Dogfighter lead = fighter.group.leader();
        if (lead != null && lead != fighter) {
            Dogfighter.State leadState = lead.state;
            if (leadState == Dogfighter.State.STRAFE || leadState == Dogfighter.State.LAUNCH || leadState == Dogfighter.State.SUPPORT) {
                fighter.setState(Dogfighter.State.FORM);
                return;
            }

            if (state == Dogfighter.State.FORM || !isValidTarget(fighter, fighter.target)) {
                Dogfighter onLeadTail = findTail(lead);
                fighter.target = onLeadTail != null ? onLeadTail : isValidTarget(fighter, lead.target) ? lead.target : pickTarget(fighter);
                if (fighter.target != null) fighter.setState(Dogfighter.State.PURSUE);
                else startStrafe(fighter);
            }
            return;
        }

        resume(fighter);
    }

    private void resume(Dogfighter fighter) {
        boolean enemyFighters = hasFighters(1 - fighter.side);
        WingType.Role role = fighter.group.role;

        if (role == WingType.Role.STRIKE || !enemyFighters) {
            if (fighter.state != Dogfighter.State.STRAFE) startStrafe(fighter);
            return;
        }

        if (role == WingType.Role.SUPPORT) {
            Dogfighter intruder = nearestEnemy(fighter, fighter.home.loc, fighter.home.radius + SUPPORT_ZONE, false);
            if (intruder != null) {
                fighter.target = intruder;
                fighter.setState(Dogfighter.State.PURSUE);
            } else {
                fighter.setState(Dogfighter.State.SUPPORT);
            }
            return;
        }

        if (fighter.state == Dogfighter.State.PURSUE && isValidTarget(fighter, fighter.target)) return;

        fighter.target = pickTarget(fighter);
        if (fighter.target != null) fighter.setState(Dogfighter.State.PURSUE);
        else startStrafe(fighter);
    }

    private void extend(Dogfighter fighter) {
        fighter.setState(Dogfighter.State.EXTEND);
        fighter.extendTime = 1f + random.nextFloat();
    }

    private void startStrafe(Dogfighter fighter) {
        DogfightFleet target = nearestFleet(fighter, 1 - fighter.side);
        if (target == null) {
            fighter.setState(Dogfighter.State.SUPPORT);
            return;
        }

        fighter.strafeTarget = target;
        fighter.approachAngle = Dogfighter.angleTo(target.loc, fighter.pos.x, fighter.pos.y) + (random.nextFloat() - 0.5f) * 120f;
        fighter.strafe = Dogfighter.Strafe.SETUP;
        fighter.torpedoDropped = false;
        fighter.setState(Dogfighter.State.STRAFE);
    }

    // escorts first take enemy strike craft near an own fleet, then the nearest enemy in front, then the nearest
    private Dogfighter pickTarget(Dogfighter fighter) {
        Dogfighter best = null;
        float bestDist = Float.MAX_VALUE;
        if (fighter.group.role == WingType.Role.ESCORT) {
            for (Dogfighter enemy : fighters) {
                if (!isValidTarget(fighter, enemy) || enemy.group.role != WingType.Role.STRIKE || !isNearOwnFleet(enemy, fighter.side)) continue;
                float dist = Vector2f.sub(enemy.pos, fighter.pos, null).lengthSquared();
                if (dist < bestDist) {
                    bestDist = dist;
                    best = enemy;
                }
            }
            if (best != null) return best;
        }

        Dogfighter front = nearestEnemy(fighter, fighter.pos, Float.MAX_VALUE, true);
        return front != null ? front : nearestEnemy(fighter, fighter.pos, Float.MAX_VALUE, false);
    }

    private boolean isNearOwnFleet(Dogfighter enemy, int side) {
        for (DogfightFleet fleet : sides[side]) {
            if (!fleet.alive) continue;
            float reach = fleet.radius + ESCORT_ZONE;
            if (Vector2f.sub(enemy.pos, fleet.loc, null).lengthSquared() <= reach * reach) return true;
        }
        return false;
    }

    // an enemy already chased by others counts as farther, so the fight splits into duels instead of one pile
    private Dogfighter nearestEnemy(Dogfighter fighter, Vector2f around, float range, boolean frontOnly) {
        Dogfighter best = null;
        float bestDist = Float.MAX_VALUE;
        for (Dogfighter enemy : fighters) {
            if (!isValidTarget(fighter, enemy)) continue;
            if (frontOnly && Math.abs(Dogfighter.wrap(Dogfighter.angleTo(fighter.pos, enemy.pos.x, enemy.pos.y) - fighter.facing)) > 90f) continue;

            float dist = Vector2f.sub(enemy.pos, around, null).lengthSquared();
            if (dist > range * range) continue;

            int chasers = countChasers(enemy, fighter.group);
            dist *= (1 + chasers) * (1 + chasers);
            if (dist < bestDist) {
                bestDist = dist;
                best = enemy;
            }
        }
        return best;
    }

    private int countChasers(Dogfighter enemy, DogfightGroup own) {
        int count = 0;
        for (Dogfighter other : fighters) {
            if (other.target == enemy && other.group != own && other.state == Dogfighter.State.PURSUE) count++;
        }
        return count;
    }

    private DogfightFleet nearestFleet(Dogfighter fighter, int side) {
        DogfightFleet best = null;
        float bestDist = Float.MAX_VALUE;
        for (DogfightFleet fleet : sides[side]) {
            if (!fleet.alive) continue;
            float dist = Vector2f.sub(fleet.loc, fighter.pos, null).lengthSquared();
            if (dist < bestDist) {
                bestDist = dist;
                best = fleet;
            }
        }
        return best;
    }

    // an enemy within 200 su behind me with its nose within 40 degrees of me
    private Dogfighter findTail(Dogfighter fighter) {
        for (Dogfighter enemy : fighters) {
            if (enemy.side == fighter.side || !enemy.isFighting()) continue;

            float dx = fighter.pos.x - enemy.pos.x;
            float dy = fighter.pos.y - enemy.pos.y;
            if (dx * dx + dy * dy > TAIL_RANGE * TAIL_RANGE) continue;

            float toMe = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (Math.abs(Dogfighter.wrap(toMe - enemy.facing)) > 40f) continue;
            if (Math.abs(Dogfighter.wrap(toMe - fighter.facing)) > 60f) continue;
            return enemy;
        }
        return null;
    }

    boolean hasFighters(int side) {
        for (Dogfighter fighter : fighters) if (fighter.side == side && fighter.isFighting()) return true;
        return false;
    }

    // a fighter still emerging from its fleet is not picked yet
    private static boolean isValidTarget(Dogfighter fighter, Dogfighter enemy) {
        return enemy != null && enemy.side != fighter.side && enemy.isFighting() && enemy.state != Dogfighter.State.LAUNCH;
    }

    private void steer(Dogfighter fighter, float amount) {
        switch (fighter.state) {
            case LAUNCH -> {
                Vector2f enemy = getEnemyCenter(fighter.side);
                fighter.desired = Dogfighter.angleTo(fighter.pos, enemy.x, enemy.y);
                fighter.throttle = 1f;
            }
            case FORM -> steerForm(fighter);
            case PURSUE -> steerPursue(fighter);
            case EVADE -> {
                fighter.weaveTimer -= amount;
                if (fighter.weaveTimer <= 0f) {
                    fighter.weaveDir = -fighter.weaveDir;
                    fighter.weaveTimer = 0.5f + random.nextFloat() * 0.5f;
                    fighter.weaveThrottle = 0.7f + random.nextFloat() * 0.5f;
                }
                fighter.desired = fighter.facing + 100f * fighter.weaveDir;
                fighter.throttle = fighter.weaveThrottle;
            }
            case EXTEND -> {
                fighter.desired = fighter.facing;
                fighter.throttle = Dogfighter.MAX_THROTTLE;
            }
            case STRAFE -> steerStrafe(fighter);
            case SUPPORT -> {
                Vector2f home = fighter.home.loc;
                float orbit = fighter.home.radius + SUPPORT_ZONE * 0.7f;
                float out = Vector2f.sub(fighter.pos, home, null).length();
                float radial = Dogfighter.angleTo(home, fighter.pos.x, fighter.pos.y);
                fighter.desired = radial + 90f + Math.max(-60f, Math.min(60f, (out - orbit) * 0.6f));
                fighter.throttle = 0.8f;
            }
            case LOOP -> {
                fighter.desired = fighter.facing + 90f * fighter.loopDir;
                fighter.throttle = 1f;
                if (fighter.stateTime > LOOP_TIME) fighter.setState(Dogfighter.State.RETURN);
            }
            case RETURN -> {
                Vector2f home = fighter.home.loc;
                fighter.desired = Dogfighter.angleTo(fighter.pos, home.x, home.y);
                float dist = Vector2f.sub(home, fighter.pos, null).length();
                fighter.throttle = dist < 80f ? Dogfighter.MIN_THROTTLE : 1f;
                if (!fighter.home.alive) fighter.dieDelay = Math.max(fighter.dieDelay, 0f);
            }
            case REFUGE -> {
                Vector2f port = fighter.refuge.loc();
                fighter.desired = Dogfighter.angleTo(fighter.pos, port.x, port.y);
                fighter.throttle = 1f;
            }
            case DYING -> {
            }
        }

        if (fighter.isFighting() && fighter.state != Dogfighter.State.LAUNCH) {
            float reach = radius + LEASH;
            if (Vector2f.sub(fighter.pos, center, null).lengthSquared() > reach * reach) fighter.desired = Dogfighter.angleTo(fighter.pos, center.x, center.y);
        }
    }

    private void steerForm(Dogfighter fighter) {
        Dogfighter lead = fighter.group.leader();
        if (lead == null || lead == fighter) {
            fighter.desired = fighter.facing;
            return;
        }

        float lx = lead.dirX();
        float ly = lead.dirY();
        float side = (fighter.rank % 2 == 1 ? 1f : -1f) * SLOT_SIDE * ((fighter.rank + 1) / 2);
        float back = SLOT_BACK * fighter.rank;
        float sx = lead.pos.x - lx * back - ly * side;
        float sy = lead.pos.y - ly * back + lx * side;
        float ahead = (sx - fighter.pos.x) * lx + (sy - fighter.pos.y) * ly;
        fighter.desired = Dogfighter.angleTo(fighter.pos, sx + lx * 20f, sy + ly * 20f);
        fighter.throttle = (lead.speed + ahead * 1.5f) / fighter.maxSpeed;
    }

    // aim at where the target will be, hang on its tail, overshoot when too fast
    private void steerPursue(Dogfighter fighter) {
        Dogfighter target = fighter.target;
        if (!isValidTarget(fighter, target)) {
            fighter.desired = fighter.facing;
            return;
        }

        float dist = Vector2f.sub(target.pos, fighter.pos, null).length();
        float lead = dist / ShuttleProjectiles.Kind.GUN.speed;
        float aimX = target.pos.x + target.vel.x * lead;
        float aimY = target.pos.y + target.vel.y * lead;
        fighter.desired = Dogfighter.angleTo(fighter.pos, aimX, aimY);

        float bearing = Math.abs(Dogfighter.wrap(Dogfighter.angleTo(fighter.pos, target.pos.x, target.pos.y) - fighter.facing));
        if (bearing > 110f && dist < 80f) {
            extend(fighter);
            return;
        }

        fighter.throttle = dist < 150f && bearing < 60f ? target.speed / fighter.maxSpeed : Dogfighter.MAX_THROTTLE;
    }

    // out to the standoff point, turn in, run at the fleet, break past it, come around from a new angle
    private void steerStrafe(Dogfighter fighter) {
        DogfightFleet fleet = fighter.strafeTarget;
        if (fleet == null || !fleet.alive) {
            startStrafe(fighter);
            return;
        }

        switch (fighter.strafe) {
            case SETUP -> {
                float a = (float) Math.toRadians(fighter.approachAngle);
                float reach = fleet.radius + STRAFE_STANDOFF;
                float px = fleet.loc.x + (float) Math.cos(a) * reach;
                float py = fleet.loc.y + (float) Math.sin(a) * reach;
                fighter.desired = Dogfighter.angleTo(fighter.pos, px, py);
                fighter.throttle = 1f;
                if (Vector2f.sub(new Vector2f(px, py), fighter.pos, null).lengthSquared() < 60f * 60f) {
                    float r = fleet.radius * STRAFE_AIM * random.nextFloat();
                    double angle = random.nextFloat() * Math.PI * 2;
                    fighter.runPoint.set((float) Math.cos(angle) * r, (float) Math.sin(angle) * r);
                    fighter.strafe = Dogfighter.Strafe.RUN;
                    passes[fighter.side]++;
                }
            }
            case RUN -> {
                float ax = fleet.loc.x + fighter.runPoint.x;
                float ay = fleet.loc.y + fighter.runPoint.y;
                fighter.desired = Dogfighter.angleTo(fighter.pos, ax, ay);
                fighter.throttle = Dogfighter.MAX_THROTTLE;
                float dx = ax - fighter.pos.x;
                float dy = ay - fighter.pos.y;
                if (dx * dx + dy * dy < STRAFE_BREAK * STRAFE_BREAK) {
                    fighter.strafe = Dogfighter.Strafe.BREAK;
                    fighter.breakTurn = (random.nextBoolean() ? 1f : -1f) * 120f;
                    fighter.extendTime = fighter.stateTime + 1f + random.nextFloat();
                    fighter.desired = fighter.facing + fighter.breakTurn;
                }
            }
            case BREAK -> {
                fighter.throttle = Dogfighter.MAX_THROTTLE;
                if (fighter.stateTime > fighter.extendTime) {
                    fighter.strafe = Dogfighter.Strafe.SETUP;
                    fighter.approachAngle += 60f + random.nextFloat() * 120f;
                    fighter.torpedoDropped = false;
                }
            }
        }
    }

    // bursts at a fighter on pursue, at the fleet on a strafing run; wingmen in formation fire with their leader
    private void weapons(Dogfighter fighter, float amount) {
        fighter.cooldown -= amount;
        if (fighter.burstLeft > 0) {
            fighter.burstTimer -= amount;
            if (fighter.burstTimer > 0f) return;

            shoot(fighter);
            fighter.burstLeft--;
            fighter.burstTimer = fighter.burstLaser ? LASER_GAP : GUN_GAP;
            if (fighter.burstLeft == 0) fighter.cooldown = COOLDOWN_MIN + random.nextFloat() * (COOLDOWN_MAX - COOLDOWN_MIN);
            return;
        }
        if (!fighter.isFighting() || fighter.state == Dogfighter.State.LAUNCH || fighter.dieDelay >= 0f) return;

        Dogfighter run = getRun(fighter);
        if (run == null && fighter.state == Dogfighter.State.FORM) fighter.torpedoDropped = false;
        if (fighter.cooldown > 0f) return;

        WingType wing = fighter.wing;
        if (run != null) {
            DogfightFleet fleet = run.strafeTarget;
            float ax = fleet.loc.x + run.runPoint.x;
            float ay = fleet.loc.y + run.runPoint.y;
            float dist = (float) Math.hypot(ax - fighter.pos.x, ay - fighter.pos.y);
            float bearing = Math.abs(Dogfighter.wrap(Dogfighter.angleTo(fighter.pos, ax, ay) - fighter.facing));
            if (bearing > GUN_CONE) return;

            if (wing.torpedoes() && !fighter.torpedoDropped && dist <= TORPEDO_RANGE) {
                fighter.torpedoDropped = true;
                fighter.cooldown = COOLDOWN_MIN;
                projectiles.fire(ShuttleProjectiles.Kind.TORPEDO, fighter.side, noseX(fighter), noseY(fighter), Dogfighter.angleTo(fighter.pos, ax, ay),
                        fighter.vel.x, fighter.vel.y, ShuttleProjectiles.TORPEDO_COLOR, true);
                return;
            }

            if (dist <= STRAFE_FIRE_RANGE && dist >= STRAFE_BREAK && (wing.guns() || wing.lasers())) startBurst(fighter, wing.lasers() && !wing.guns(), true);
            return;
        }

        Dogfighter target = fighter.target;
        if (fighter.state != Dogfighter.State.PURSUE || !isValidTarget(fighter, target)) return;

        float dist = Vector2f.sub(target.pos, fighter.pos, null).length();
        float bearing = Math.abs(Dogfighter.wrap(Dogfighter.angleTo(fighter.pos, target.pos.x, target.pos.y) - fighter.facing));
        if (wing.lasers() && bearing <= LASER_CONE && dist <= LASER_RANGE) startBurst(fighter, true, false);
        else if (wing.guns() && bearing <= GUN_CONE && dist <= GUN_RANGE) startBurst(fighter, false, false);
    }

    // the fighter on a strafing run that this one fires with: itself, or its leader while it flies in formation
    private static Dogfighter getRun(Dogfighter fighter) {
        if (fighter.state == Dogfighter.State.STRAFE) return fighter.strafe == Dogfighter.Strafe.RUN && fighter.strafeTarget != null && fighter.strafeTarget.alive ? fighter : null;
        if (fighter.state != Dogfighter.State.FORM) return null;

        Dogfighter lead = fighter.group.leader();
        if (lead == null || lead == fighter || lead.state != Dogfighter.State.STRAFE || lead.strafe != Dogfighter.Strafe.RUN) return null;
        return lead.strafeTarget != null && lead.strafeTarget.alive ? lead : null;
    }

    private void startBurst(Dogfighter fighter, boolean laser, boolean atFleet) {
        fighter.burstLaser = laser;
        fighter.burstAtFleet = atFleet;
        fighter.burstLeft = laser ? 2 + random.nextInt(2) : 3 + random.nextInt(3);
        fighter.burstTimer = 0f;
    }

    // guns fire along the nose, so in a turning fight the stream trails the target; lasers aim at the lead point
    private void shoot(Dogfighter fighter) {
        float angle = fighter.facing;
        float spread = fighter.burstLaser ? LASER_SPREAD : GUN_SPREAD;
        if (fighter.burstLaser) {
            Dogfighter run = getRun(fighter);
            Dogfighter target = fighter.target;
            if (fighter.burstAtFleet && run != null) {
                angle = Dogfighter.angleTo(fighter.pos, run.strafeTarget.loc.x + run.runPoint.x, run.strafeTarget.loc.y + run.runPoint.y);
            } else if (!fighter.burstAtFleet && isValidTarget(fighter, target)) {
                float dist = Vector2f.sub(target.pos, fighter.pos, null).length();
                float lead = dist / ShuttleProjectiles.Kind.LASER.speed;
                float aim = Dogfighter.angleTo(fighter.pos, target.pos.x + target.vel.x * lead, target.pos.y + target.vel.y * lead);
                if (Math.abs(Dogfighter.wrap(aim - fighter.facing)) <= LASER_CONE) angle = aim;
            }
        }

        angle += (random.nextFloat() * 2f - 1f) * spread;
        ShuttleProjectiles.Kind kind = fighter.burstLaser ? ShuttleProjectiles.Kind.LASER : ShuttleProjectiles.Kind.GUN;
        Color color = fighter.burstLaser ? fighter.wing.boltColor() : ShuttleProjectiles.GUN_COLOR;
        projectiles.fire(kind, fighter.side, noseX(fighter), noseY(fighter), angle, fighter.vel.x, fighter.vel.y, color, fighter.burstAtFleet);
    }

    private static float noseX(Dogfighter fighter) {
        return fighter.pos.x + fighter.dirX() * fighter.hull.length * 0.5f;
    }

    private static float noseY(Dogfighter fighter) {
        return fighter.pos.y + fighter.dirY() * fighter.hull.length * 0.5f;
    }

    void onFighterHit(Dogfighter fighter, float x, float y, Color color) {
        host.particle(new Vector2f(x, y), fighter.vel, HIT_SIZE, 0.8f, 0.25f, color);
        fighter.hits++;
        if (fighter.hits >= fighter.toughness) kill(fighter);
    }

    void onFleetHit(DogfightFleet fleet, float x, float y, ShuttleProjectiles.Kind kind, Color color) {
        boolean torpedo = kind == ShuttleProjectiles.Kind.TORPEDO;
        host.particle(new Vector2f(x, y), fleet.vel, torpedo ? TORPEDO_HIT_SIZE : FLEET_HIT_SIZE, torpedo ? 1f : 0.8f, torpedo ? 0.5f : 0.25f, color);
    }

    // 3 hit particles and a drifting fade; a group with nobody left is replaced 4 to 8 s later
    void kill(Dogfighter fighter) {
        if (!fighter.canBeHit()) return;

        for (int i = 0; i < 3; i++) {
            host.particle(fighter.pos, fighter.vel, 20f + random.nextFloat() * 15f, 1f, 0.6f, i == 0 ? Color.WHITE : fighter.hull.glowColor);
        }
        fighter.explode();
        kills[fighter.side]++;
        if (!fighter.group.isAlive() && !ended) onGroupLost(fighter.side);
    }

    private void trackLeash(Dogfighter fighter) {
        if (!fighter.isFighting()) return;
        float over = Vector2f.sub(fighter.pos, center, null).length() - radius - LEASH;
        if (over > maxLeashOvershoot) maxLeashOvershoot = (int) over;
    }
}
