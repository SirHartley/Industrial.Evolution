package indevo.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager.MeteroidShowerType;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.rogue.RogueAsteroidSpawner;
import indevo.exploration.meteor.rogue.RogueAsteroidSpawner.Placement;
import indevo.exploration.meteor.rogue.RogueFormation;
import indevo.exploration.meteor.rogue.RogueFormation.Rock;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RogueAsteroidChecks {

    private static final int SEEDS = 2000;
    private static final float EPS = 0.01f;
    private static final Map<RogueFormation, Float> WEIGHTS = new EnumMap<>(Map.of(
            RogueFormation.SCATTER, 20f, RogueFormation.CLUSTER, 20f, RogueFormation.TEARDROP, 20f,
            RogueFormation.WALL, 10f, RogueFormation.CHEVRON, 10f, RogueFormation.PAIR, 10f, RogueFormation.ESCORT, 5f));
    private static final float TOTAL_WEIGHT = 95f;

    private final List<String> problems = new ArrayList<>();
    private int checks = 0;

    private RogueAsteroidChecks() {
    }

    public static void main(String[] args) {
        if (Global.getSector() != null || Global.getSettings() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }

        RogueAsteroidChecks run = new RogueAsteroidChecks();
        try {
            // Misc reads settings in its static initialiser
            Global.setSettings(proxy(SettingsAPI.class));
            run.checkWeights();
            run.checkShapes();
            run.checkKinds();
            run.checkPath();
            run.checkPlacement();
            run.checkStartSearch();
            run.checkCourseAim();
            run.checkDodge();
        } finally {
            Global.setSettings(null);
        }

        if (!run.problems.isEmpty()) {
            for (String problem : run.problems) System.out.println("FAIL " + problem);
            System.out.println("Rogue asteroids: " + run.problems.size() + " of " + run.checks + " checks failed");
            System.exit(1);
        }

        System.out.println("Rogue asteroids: " + run.checks + " checks passed");
    }

    private void checkWeights() {
        float total = 0f;
        for (RogueFormation formation : RogueFormation.values()) {
            check(WEIGHTS.containsKey(formation), formation + " has a planned weight");
            check(WEIGHTS.getOrDefault(formation, -1f) == formation.weight, formation + " weight " + formation.weight);
            total += formation.weight;
        }
        check(Math.abs(total - TOTAL_WEIGHT) < EPS, "formation weights sum to " + TOTAL_WEIGHT + ", got " + total);
    }

    private void checkShapes() {
        for (RogueFormation formation : RogueFormation.values()) {
            for (int seed = 0; seed < SEEDS; seed++) {
                List<Rock> rocks = formation.generate(new Random(seed));
                String id = formation + " seed " + seed;

                for (Rock rock : rocks) {
                    check(Float.isFinite(rock.x()) && Float.isFinite(rock.y()) && Float.isFinite(rock.size()), id + " finite rock");
                }

                float footprint = RogueFormation.getFootprintRadius(rocks);
                for (Rock rock : rocks) {
                    if (!rock.isOrbiting()) check(Math.hypot(rock.x(), rock.y()) + rock.size() <= footprint + EPS, id + " rock inside footprint");
                }

                switch (formation) {
                    case SCATTER -> {
                        count(id, rocks, 10, 14);
                        bounds(id, rocks, 450f, 1300f);
                        sizes(id, rocks, 20f, 60f);
                    }
                    case CLUSTER -> {
                        count(id, rocks, 12, 18);
                        Rock core = rocks.get(0);
                        check(core.x() == 0f && core.y() == 0f && in(core.size(), 85f, 100f), id + " core rock at origin, size 85 to 100");
                        for (Rock rock : rocks.subList(1, rocks.size())) {
                            double d = Math.hypot(rock.x(), rock.y());
                            check(d >= 110f - EPS && d <= 380f + EPS, id + " cluster rock 110 to 380 out, got " + d);
                            check(in(rock.size(), 20f, 50f), id + " cluster rock size 20 to 50");
                        }
                    }
                    case TEARDROP -> {
                        count(id, rocks, 17, 24);
                        bounds(id, rocks, 1400f, 282f);
                        sizes(id, rocks, 16f, 100f);
                        Rock lead = rocks.get(0);
                        check(in(lead.size(), 85f, 100f), id + " lead size 85 to 100");
                        float maxSize = 0f;
                        float meanX = 0f;
                        for (Rock rock : rocks) {
                            maxSize = Math.max(maxSize, rock.size());
                            meanX += rock.x() / rocks.size();
                        }
                        check(lead.size() == maxSize, id + " lead is the largest rock");
                        check(lead.x() > meanX + 400f, id + " head leads the tail");
                        check(rocks.get(rocks.size() - 1).x() < lead.x() - 1000f, id + " last tail rock well behind the lead");
                    }
                    case WALL -> {
                        count(id, rocks, 11, 17);
                        bounds(id, rocks, 650f, 1200f);
                        sizes(id, rocks, 20f, 80f);
                        float minY = Float.MAX_VALUE;
                        float maxY = -Float.MAX_VALUE;
                        for (Rock rock : rocks) {
                            minY = Math.min(minY, rock.y());
                            maxY = Math.max(maxY, rock.y());
                        }
                        check(maxY - minY >= 2300f, id + " wall spans the path, got " + (maxY - minY));
                    }
                    case CHEVRON -> {
                        count(id, rocks, 9, 16);
                        bounds(id, rocks, 2300f, 1500f);
                        sizes(id, rocks, 16f, 80f);
                        Rock lead = rocks.get(0);
                        check(lead.x() == 0f && lead.y() == 0f && in(lead.size(), 60f, 80f), id + " lead at origin, size 60 to 80");
                        float meanX = 0f;
                        for (Rock rock : rocks.subList(1, rocks.size())) meanX += rock.x() / (rocks.size() - 1);
                        check(meanX < -100f, id + " arms trail the lead, mean x " + meanX);
                    }
                    case PAIR -> {
                        count(id, rocks, 12, 18);
                        bounds(id, rocks, 510f, 760f);
                        sizes(id, rocks, 20f, 75f);
                        int left = 0;
                        for (Rock rock : rocks) {
                            check(Math.abs(rock.y()) >= 240f - EPS, id + " gap between the groups");
                            if (rock.y() < 0f) left++;
                        }
                        check(left >= 6 && left <= 9 && rocks.size() - left >= 6 && rocks.size() - left <= 9, id + " two groups of 6 to 9");
                    }
                    case ESCORT -> {
                        count(id, rocks, 6, 9);
                        Rock big = rocks.get(0);
                        check(big.solo() && !big.isOrbiting() && in(big.size(), 170f, 200f), id + " big solo rock 170 to 200");
                        check(RogueFormation.getTreasureIndex(rocks) == 0, id + " treasure on the big rock");
                        for (Rock rock : rocks.subList(1, rocks.size())) {
                            check(rock.focus() == 0 && in(rock.orbitRadius(), 260f, 380f), id + " orbit 260 to 380 around the big rock");
                            check(in(rock.size(), 20f, 35f), id + " escort size 20 to 35");
                            check(in(Math.abs(rock.orbitSpin()), 0.35f, 0.6f), id + " orbit spin 0.35 to 0.6");
                        }
                    }
                }

                if (formation != RogueFormation.ESCORT) {
                    check(RogueFormation.getTreasureIndex(rocks) == 0, id + " treasure on the first rock");
                }
            }
        }
    }

    private void checkKinds() {
        int irradiated = 0;
        int total = 0;

        for (int seed = 0; seed < SEEDS; seed++) {
            for (RogueFormation formation : RogueFormation.values()) {
                List<Rock> original = formation.generate(new Random(seed));

                List<Rock> rocks = new ArrayList<>(original);
                List<MeteroidShowerType> kinds = RogueAsteroidSpawner.assignKinds(rocks, MeteroidShowerType.ICEROID, new Random(seed));
                for (int i = 0; i < rocks.size(); i++) {
                    Rock rock = rocks.get(i);
                    if (kinds.get(i) == MeteroidShowerType.ICEROID) {
                        check(!rock.isOrbiting(), formation + " orbiting rocks are never ice");
                        check(Math.abs(rock.size() - Math.min(MeteorEntity.MAX_SIZE, original.get(i).size() * 2f)) < EPS, formation + " ice rock at double size, capped");
                    } else {
                        check(kinds.get(i) == MeteroidShowerType.ASTEROID && rock.size() == original.get(i).size(), formation + " other rocks of an ice rogue are plain asteroids");
                    }
                    check(rock.size() <= MeteorEntity.MAX_SIZE, formation + " size at most " + MeteorEntity.MAX_SIZE);
                }

                rocks = new ArrayList<>(original);
                kinds = RogueAsteroidSpawner.assignKinds(rocks, MeteroidShowerType.IRRADIOID, new Random(seed));
                for (int i = 0; i < rocks.size(); i++) {
                    check(rocks.get(i).size() == original.get(i).size(), formation + " irradiated rogue keeps sizes");
                    if (kinds.get(i) == MeteroidShowerType.IRRADIOID) irradiated++;
                    total++;
                }

                rocks = new ArrayList<>(original);
                kinds = RogueAsteroidSpawner.assignKinds(rocks, MeteroidShowerType.ASTEROID, new Random(seed));
                for (MeteroidShowerType kind : kinds) check(kind == MeteroidShowerType.ASTEROID, formation + " asteroid rogue has only asteroids");
            }
        }

        float share = (float) irradiated / total;
        check(share > 0.27f && share < 0.33f, "irradiated share about 30%, got " + share);
    }

    private void checkPath() {
        Random random = new Random(7);
        for (float grid : new float[]{5000f, 26000f, 60000f}) {
            for (int i = 0; i < 3000; i++) {
                float limit = grid - RogueAsteroidSpawner.GRID_MARGIN;
                Vector2f aim = new Vector2f((random.nextFloat() * 2f - 1f) * limit, (random.nextFloat() * 2f - 1f) * limit);
                float heading = random.nextFloat() * 360f;
                float radius = RogueFormation.lerp(RogueAsteroidSpawner.MIN_ARC_RADIUS, RogueAsteroidSpawner.MAX_ARC_RADIUS, random.nextFloat());
                int dir = random.nextBoolean() ? 1 : -1;
                String id = "path grid " + grid + " #" + i;

                CircularArc path = RogueAsteroidSpawner.createPath(aim, heading, radius, dir, grid);
                if (!check(path != null, id + " exists")) continue;

                check(Math.abs(Misc.getDistance(path.center, aim) - radius) < 1f, id + " runs through the aim point");
                check(path.getAngleTravelDir() == dir, id + " travels in dir " + dir);

                Vector2f start = path.getPointForAngle(path.startAngle);
                Vector2f end = path.getPointForAngle(path.endAngle);
                check(Math.abs(Math.max(Math.abs(start.x), Math.abs(start.y)) - grid) < 2f, id + " starts on the system edge " + start);
                check(Math.abs(Math.max(Math.abs(end.x), Math.abs(end.y)) - grid) < 2f, id + " ends on the system edge " + end);

                float progress = path.getTraversalProgress(aim);
                check(progress > 0f && progress < 1f, id + " aim point between start and end, progress " + progress);

                float aimAngle = path.getAngleForPoint(aim);
                float tangent = aimAngle + 90f * dir;
                check(Math.abs(Misc.getAngleDiff(tangent, heading)) < 0.5f, id + " heading at the aim point");

                float back = RogueAsteroidSpawner.getArcDistance(path, path.startAngle, aimAngle);
                float forward = RogueAsteroidSpawner.getArcDistance(path, aimAngle, path.endAngle);
                check(Math.abs(back + forward - path.getArcCircumference()) < 2f, id + " distances add up to the path length");
                if (grid <= 26000f) check(path.getArcCircumference() <= Misc.getDistance(start, end) * 1.02f + 1f, id + " path is nearly straight");

                Vector2f startByDistance = RogueAsteroidSpawner.getPointBack(path, aimAngle, back);
                check(Misc.getDistance(startByDistance, start) < 2f, id + " walking back the full distance reaches the start");
            }
        }
    }

    private void checkPlacement() {
        Random random = new Random(11);
        for (int i = 0; i < 400; i++) {
            RogueFormation formation = RogueFormation.values()[i % RogueFormation.values().length];
            List<Rock> rocks = formation.generate(new Random(i));
            Vector2f aim = new Vector2f((random.nextFloat() * 2f - 1f) * 20000f, (random.nextFloat() * 2f - 1f) * 20000f);
            float heading = random.nextFloat() * 360f;
            float radius = RogueFormation.lerp(RogueAsteroidSpawner.MIN_ARC_RADIUS, RogueAsteroidSpawner.MAX_ARC_RADIUS, random.nextFloat());
            int dir = random.nextBoolean() ? 1 : -1;
            CircularArc path = RogueAsteroidSpawner.createPath(aim, heading, radius, dir, 26000f);
            if (!check(path != null, "placement path exists")) continue;

            float speed = 300f;
            float refAngle = path.getAngleForPoint(aim);
            Vector2f ref = path.getPointForAngle(refAngle);
            Vector2f along = Misc.getUnitVectorAtDegreeAngle(heading);
            Vector2f left = Misc.getUnitVectorAtDegreeAngle(heading + 90f);
            float angularSpeed = speed / path.radius;

            for (Rock rock : rocks) {
                if (rock.isOrbiting()) continue;
                Placement placement = RogueAsteroidSpawner.getPlacement(path, refAngle, speed, rock);
                Vector2f p = placement.arc().getPointForAngle(placement.angle());
                float dx = p.x - ref.x;
                float dy = p.y - ref.y;
                float x = dx * along.x + dy * along.y;
                float y = dx * left.x + dy * left.y;
                String id = formation + " #" + i + " dir " + dir;

                check(Math.abs(x - rock.x()) < 25f, id + " along offset " + rock.x() + " placed at " + x);
                check(Math.abs(y - rock.y()) < 25f, id + " across offset " + rock.y() + " placed at " + y);
                check(Misc.getDistance(placement.arc().center, path.center) < EPS, id + " shares the path centre");
                check(Math.abs(placement.velocity() / placement.arc().radius - angularSpeed) < 1e-6f, id + " same angular speed keeps the shape");
            }
        }
    }

    private void checkStartSearch() {
        float step = RogueAsteroidSpawner.START_SEARCH_STEP;

        float back = RogueAsteroidSpawner.findStartDistance(3000f, 20000f, 20000f, d -> d >= 1000d && d <= 5000d, step);
        check(back > 5000f && back <= 5000f + step, "start steps back out of view, got " + back);

        back = RogueAsteroidSpawner.findStartDistance(3000f, 20000f, 20000f, d -> false, step);
        check(back == 3000f, "start at the desired distance when out of view, got " + back);

        back = RogueAsteroidSpawner.findStartDistance(50000f, 20000f, 20000f, d -> false, step);
        check(back == 20000f, "start never behind the system edge, got " + back);

        back = RogueAsteroidSpawner.findStartDistance(-500f, 20000f, 20000f, d -> false, step);
        check(back == 0f, "start never past the aim point when out of view, got " + back);

        back = RogueAsteroidSpawner.findStartDistance(3000f, 4000f, 20000f, d -> d >= 1000d && d <= 5000d, step);
        check(back < 1000f && back >= 1000f - step, "edge in view moves the start ahead of the view, got " + back);

        back = RogueAsteroidSpawner.findStartDistance(3000f, 4000f, 2000f, d -> true, step);
        check(Float.isNaN(back), "no start when everything is in view, got " + back);
    }

    private void checkCourseAim() {
        Random random = new Random(3);
        Vector2f fleet = new Vector2f(1000f, -2000f);
        float spread = RogueAsteroidSpawner.AIM_SPREAD;

        for (int i = 0; i < 2000; i++) {
            Vector2f far = new Vector2f(fleet.x + 30000f, fleet.y);
            Vector2f aim = RogueAsteroidSpawner.getCourseAimPoint(fleet, far, random);
            float x = aim.x - fleet.x;
            float y = aim.y - fleet.y;
            check(x >= -spread - EPS && x <= RogueAsteroidSpawner.AIM_AHEAD_MAX + spread + EPS && Math.abs(y) <= spread + EPS, "aim ahead on the course, got " + x + ", " + y);

            Vector2f near = new Vector2f(fleet.x, fleet.y + 3000f);
            aim = RogueAsteroidSpawner.getCourseAimPoint(fleet, near, random);
            check(aim.y - fleet.y <= 3000f + spread + EPS && Math.abs(aim.x - fleet.x) <= spread + EPS, "aim not past the course target");

            aim = RogueAsteroidSpawner.getCourseAimPoint(fleet, fleet, random);
            check(Misc.getDistance(aim, fleet) <= spread + EPS, "parked fleet gets the vicinity");
        }
    }

    private void checkDodge() {
        Vector2f rock = new Vector2f(0f, 0f);
        Vector2f vel = new Vector2f(100f, 0f);

        Vector2f point = MeteorEntity.getClosestApproach(rock, vel, new Vector2f(300f, 50f), 4f, 100f);
        check(point != null && Math.abs(point.x - 300f) < EPS && Math.abs(point.y) < EPS, "closest approach on the lane abeam the fleet");

        check(MeteorEntity.getClosestApproach(rock, vel, new Vector2f(-300f, 0f), 4f, 100f) == null, "no dodge for a fleet behind the rock");
        check(MeteorEntity.getClosestApproach(rock, vel, new Vector2f(500f, 0f), 4f, 100f) == null, "no dodge past the lookahead");
        check(MeteorEntity.getClosestApproach(rock, vel, new Vector2f(300f, 150f), 4f, 100f) == null, "no dodge outside the range");
        check(MeteorEntity.getClosestApproach(rock, new Vector2f(), new Vector2f(10f, 0f), 4f, 100f) == null, "no dodge for a still rock");

        Vector2f diagonal = MeteorEntity.getClosestApproach(new Vector2f(100f, 100f), new Vector2f(-50f, -50f), new Vector2f(0f, 40f), 4f, 100f);
        check(diagonal != null && Math.abs(diagonal.x - 20f) < 0.1f && Math.abs(diagonal.y - 20f) < 0.1f, "diagonal lane closest point " + diagonal);
    }

    private void count(String id, List<Rock> rocks, int min, int max) {
        check(rocks.size() >= min && rocks.size() <= max, id + " count " + min + " to " + max + ", got " + rocks.size());
    }

    private void bounds(String id, List<Rock> rocks, float maxX, float maxY) {
        for (Rock rock : rocks) {
            check(Math.abs(rock.x()) <= maxX + EPS && Math.abs(rock.y()) <= maxY + EPS, id + " rock within " + maxX + " x " + maxY + ", got " + rock.x() + ", " + rock.y());
        }
    }

    private void sizes(String id, List<Rock> rocks, float min, float max) {
        for (Rock rock : rocks) check(in(rock.size(), min, max), id + " size " + min + " to " + max + ", got " + rock.size());
    }

    private static boolean in(float value, float min, float max) {
        return value >= min - EPS && value <= max + EPS;
    }

    private boolean check(boolean ok, String what) {
        checks++;
        if (!ok) problems.add(what);
        return ok;
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
}
