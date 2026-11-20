package indevo.exploration.meteor.rogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.exploration.meteor.MeteorSwarmManager.MeteroidShowerType;
import indevo.exploration.meteor.entities.IceTreasureoidEntity;
import indevo.exploration.meteor.entities.IcyRockEntity;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.entities.TreasuroidEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcOnOrbitLossMovement;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.exploration.meteor.movement.MeteorMovementModuleAPI;
import indevo.exploration.meteor.rogue.RogueFormation.Rock;
import indevo.utils.ModPlugin;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoublePredicate;

public final class RogueAsteroidSpawner {

    public static final String TAG_ROGUE_ROCK = "IndEvo_RogueRock";
    public static final float AIM_AT_PLAYER_CHANCE = 0.3f;
    public static final float TREASURE_CHANCE = 0.2f;

    public static final float MIN_ARC_RADIUS = 150000f;
    public static final float MAX_ARC_RADIUS = 400000f;
    public static final float AIM_AHEAD_MAX = 8000f;
    public static final float AIM_SPREAD = 1500f;
    public static final float AIM_RANDOM_RADIUS = 20000f;
    public static final float GRID_MARGIN = 500f;

    public static final float MIN_SPEED = 150f;
    public static final float MAX_SPEED = 250f;
    public static final float MIN_FLEET_SPEED = 10f;
    public static final float VIEW_CLEARANCE = 2000f;
    public static final float START_SEARCH_STEP = 250f;

    public static final float ICE_SIZE_MULT = 2f;
    public static final float ICE_CHANCE = 0.5f;
    public static final float IRRADIATED_CHANCE = 0.3f;
    public static final float ICE_SPLINTER_MIN_SIZE = 100f;

    public record Placement(CircularArc arc, float angle, float velocity) {
    }

    private RogueAsteroidSpawner() {
    }

    public static boolean spawn(StarSystemAPI system, RogueFormation formation, MeteroidShowerType type, Random random) {
        boolean aimAtPlayer = random.nextFloat() < AIM_AT_PLAYER_CHANCE;
        boolean treasure = random.nextFloat() < TREASURE_CHANCE;
        return spawn(system, formation, type, aimAtPlayer, treasure, random);
    }

    public static boolean spawn(StarSystemAPI system, RogueFormation formation, MeteroidShowerType type, boolean aimAtPlayer, boolean treasure, Random random) {
        float gridLimit = Setting.METEOR_GRID_LIMIT.value();
        if (gridLimit <= GRID_MARGIN * 2f) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean playerHere = player != null && player.getContainingLocation() == system;
        boolean aimed = aimAtPlayer && playerHere;

        Vector2f aim = aimed
                ? getCourseAimPoint(player.getLocation(), getHeadingTarget(player, system), random)
                : getRandomPointInCircle(new Vector2f(), AIM_RANDOM_RADIUS, random);
        clampToGrid(aim, gridLimit - GRID_MARGIN);

        float heading = random.nextFloat() * 360f;
        float radius = RogueFormation.lerp(MIN_ARC_RADIUS, MAX_ARC_RADIUS, random.nextFloat());
        int dir = random.nextBoolean() ? 1 : -1;

        CircularArc path = createPath(aim, heading, radius, dir, gridLimit);
        if (path == null) return false;

        List<Rock> rocks = formation.generate(random);
        List<MeteroidShowerType> kinds = assignKinds(rocks, type, random);
        float footprint = RogueFormation.getFootprintRadius(rocks);
        float speed = RogueFormation.lerp(MIN_SPEED, MAX_SPEED, random.nextFloat());

        float aimAngle = path.getAngleForPoint(aim);
        float maxBack = getArcDistance(path, path.startAngle, aimAngle);
        float maxForward = getArcDistance(path, aimAngle, path.endAngle);

        float desired = maxBack;
        if (aimed) {
            float fleetSpeed = player.getVelocity().length();
            float fleetTime = fleetSpeed < MIN_FLEET_SPEED ? 0f : Misc.getDistance(player.getLocation(), aim) / fleetSpeed;
            desired = speed * fleetTime;
        }

        DoublePredicate inView = d -> false;
        if (Global.getSector().getCurrentLocation() == system) {
            ViewportAPI view = Global.getSector().getViewport();
            float llx = view.getLLX(), lly = view.getLLY(), w = view.getVisibleWidth(), h = view.getVisibleHeight();
            float margin = VIEW_CLEARANCE + footprint;
            inView = d -> isInRect(getPointBack(path, aimAngle, (float) d), llx, lly, w, h, margin);
        }

        float back = findStartDistance(desired, maxBack, maxForward, inView, START_SEARCH_STEP);
        if (Float.isNaN(back)) {
            ModPlugin.log("Rogue asteroids: no start out of view, skipped");
            return false;
        }

        float refAngle = getAngleBack(path, aimAngle, back);
        int treasureIndex = treasure ? RogueFormation.getTreasureIndex(rocks) : -1;
        List<SectorEntityToken> spawned = new ArrayList<>();

        for (int i = 0; i < rocks.size(); i++) {
            Rock rock = rocks.get(i);
            MeteroidShowerType kind = kinds.get(i);
            float size = Math.max(MeteorEntity.MIN_SIZE, rock.size());

            MeteorMovementModuleAPI movement;
            Placement placement = null;
            if (rock.isOrbiting()) {
                MeteorMovementModuleAPI focusMovement = ((MeteorEntity) spawned.get(rock.focus()).getCustomPlugin()).movement;
                movement = new ArcOnOrbitLossMovement(focusMovement.getArc(), focusMovement.getVelocity());
            } else {
                placement = getPlacement(path, refAngle, speed, rock);
                movement = new ArcingMovementModule(placement.arc(), placement.velocity(), placement.angle());
            }

            MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, movement);
            SectorEntityToken entity;
            if (i == treasureIndex) {
                entity = type == MeteroidShowerType.ICEROID
                        ? IceTreasureoidEntity.spawn(system, data, new Random(random.nextLong()))
                        : TreasuroidEntity.spawn(system, data);
            } else {
                entity = MeteorFactory.spawn(system, data, kind);
                if (kind == MeteroidShowerType.ICEROID) entity.setFaction(Factions.TRITACHYON);
                if (kind == MeteroidShowerType.IRRADIOID) entity.setFaction(Factions.LUDDIC_PATH);
            }

            if (entity.getCustomPlugin() instanceof IcyRockEntity && size < ICE_SPLINTER_MIN_SIZE) entity.addTag(IcyRockEntity.TAG_NO_SPLINTERS);
            entity.addTag(TAG_ROGUE_ROCK);

            if (placement != null) {
                Vector2f loc = placement.arc().getPointForAngle(placement.angle());
                entity.setLocation(loc.x, loc.y);
            } else {
                SectorEntityToken focus = spawned.get(rock.focus());
                float periodDays = RogueFormation.TAU / Math.abs(rock.orbitSpin()) / Global.getSector().getClock().getSecondsPerDay();
                entity.setCircularOrbit(focus, (float) Math.toDegrees(rock.orbitAngle()), rock.orbitRadius(), Math.signum(rock.orbitSpin()) * periodDays);
            }

            spawned.add(entity);
        }

        ModPlugin.log("Spawning rogue asteroids: " + formation.getId() + " " + type + " rocks " + rocks.size() + " aimed " + aimed + " treasure " + treasure + " start back " + back + " of " + maxBack);
        return true;
    }

    public static List<MeteroidShowerType> assignKinds(List<Rock> rocks, MeteroidShowerType type, Random random) {
        List<MeteroidShowerType> kinds = new ArrayList<>();
        for (int i = 0; i < rocks.size(); i++) {
            Rock rock = rocks.get(i);
            MeteroidShowerType kind = MeteroidShowerType.ASTEROID;

            if (type == MeteroidShowerType.ICEROID && random.nextFloat() < ICE_CHANCE && !rock.isOrbiting()) {
                kind = MeteroidShowerType.ICEROID;
                rocks.set(i, rock.withSize(Math.min(MeteorEntity.MAX_SIZE, rock.size() * ICE_SIZE_MULT)));
            }

            if (type == MeteroidShowerType.IRRADIOID && random.nextFloat() < IRRADIATED_CHANCE) kind = MeteroidShowerType.IRRADIOID;
            kinds.add(kind);
        }
        return kinds;
    }

    public static Vector2f getHeadingTarget(CampaignFleetAPI fleet, LocationAPI loc) {
        SectorEntityToken course = Global.getSector().getCampaignUI().getCurrentCourseTarget();
        if (course != null && course.getContainingLocation() == loc) return course.getLocation();

        Vector2f destination = fleet.getMoveDestination();
        return destination != null ? destination : fleet.getLocation();
    }

    public static Vector2f getCourseAimPoint(Vector2f fleetLoc, Vector2f target, Random random) {
        float dist = Misc.getDistance(fleetLoc, target);
        float ahead = Math.min(dist, random.nextFloat() * AIM_AHEAD_MAX);

        Vector2f aim = new Vector2f(fleetLoc);
        if (dist > 1f) {
            aim.x += (target.x - fleetLoc.x) / dist * ahead;
            aim.y += (target.y - fleetLoc.y) / dist * ahead;
        }

        return getRandomPointInCircle(aim, AIM_SPREAD, random);
    }

    public static Vector2f getRandomPointInCircle(Vector2f center, float radius, Random random) {
        double angle = random.nextDouble() * Math.PI * 2d;
        double dist = Math.sqrt(random.nextDouble()) * radius;
        return new Vector2f(center.x + (float) (Math.cos(angle) * dist), center.y + (float) (Math.sin(angle) * dist));
    }

    public static void clampToGrid(Vector2f loc, float limit) {
        loc.x = Math.max(-limit, Math.min(limit, loc.x));
        loc.y = Math.max(-limit, Math.min(limit, loc.y));
    }

    // circle through aim with heading as its tangent there, cut where it leaves the square of +-gridLimit; dir 1 runs counterclockwise
    public static CircularArc createPath(Vector2f aim, float heading, float radius, int dir, float gridLimit) {
        double h = Math.toRadians(heading);
        double centreAngle = h + dir * Math.PI / 2d;
        double cx = aim.x + radius * Math.cos(centreAngle);
        double cy = aim.y + radius * Math.sin(centreAngle);
        double aimAngle = Math.atan2(aim.y - cy, aim.x - cx);

        double forward = Double.MAX_VALUE;
        double back = Double.MAX_VALUE;

        for (double[] p : getSquareIntersections(cx, cy, radius, gridLimit)) {
            double delta = wrapPi(Math.atan2(p[1] - cy, p[0] - cx) - aimAngle) * dir;
            if (delta > 0d) forward = Math.min(forward, delta);
            else if (delta < 0d) back = Math.min(back, -delta);
        }

        if (forward == Double.MAX_VALUE || back == Double.MAX_VALUE) return null;

        float start = (float) Math.toDegrees(aimAngle - dir * back);
        float end = (float) Math.toDegrees(aimAngle + dir * forward);
        return new CircularArc(new Vector2f((float) cx, (float) cy), radius, start, end);
    }

    private static List<double[]> getSquareIntersections(double cx, double cy, double r, double limit) {
        List<double[]> points = new ArrayList<>();
        for (double edge : new double[]{-limit, limit}) {
            double dx = edge - cx;
            double discX = r * r - dx * dx;
            if (discX >= 0d) {
                double root = Math.sqrt(discX);
                for (double y : new double[]{cy - root, cy + root}) if (Math.abs(y) <= limit) points.add(new double[]{edge, y});
            }

            double dy = edge - cy;
            double discY = r * r - dy * dy;
            if (discY >= 0d) {
                double root = Math.sqrt(discY);
                for (double x : new double[]{cx - root, cx + root}) if (Math.abs(x) <= limit) points.add(new double[]{x, edge});
            }
        }
        return points;
    }

    private static double wrapPi(double angle) {
        double a = angle % (Math.PI * 2d);
        if (a > Math.PI) a -= Math.PI * 2d;
        if (a <= -Math.PI) a += Math.PI * 2d;
        return a;
    }

    // su along the path from one angle to a later one in travel direction
    public static float getArcDistance(CircularArc path, float fromAngle, float toAngle) {
        float diff = Misc.normalizeAngle((toAngle - fromAngle) * path.getAngleTravelDir());
        return (float) Math.toRadians(diff) * path.radius;
    }

    // angle of the point d su behind the aim point, negative d is ahead of it
    public static float getAngleBack(CircularArc path, float aimAngle, float d) {
        return Misc.normalizeAngle(aimAngle - path.getAngleTravelDir() * (float) Math.toDegrees(d / path.radius));
    }

    public static Vector2f getPointBack(CircularArc path, float aimAngle, float d) {
        return path.getPointForAngle(getAngleBack(path, aimAngle, d));
    }

    // first distance back from desired (up to the path start) that is out of view, else the first one ahead of it
    public static float findStartDistance(float desired, float maxBack, float maxForward, DoublePredicate inView, float step) {
        float d = Math.max(0f, Math.min(maxBack, desired));

        for (float b = d; ; b = Math.min(maxBack, b + step)) {
            if (!inView.test(b)) return b;
            if (b >= maxBack) break;
        }

        for (float f = d - step; f > -maxForward; f -= step) {
            if (!inView.test(f)) return f;
        }

        return Float.NaN;
    }

    public static boolean isInRect(Vector2f p, float llx, float lly, float w, float h, float margin) {
        return p.x >= llx - margin && p.x <= llx + w + margin && p.y >= lly - margin && p.y <= lly + h + margin;
    }

    // y to the left of travel, so it shrinks the radius on a counterclockwise path
    public static Placement getPlacement(CircularArc path, float refAngle, float speed, Rock rock) {
        int dir = path.getAngleTravelDir();
        float rockRadius = path.radius - dir * rock.y();
        float angle = Misc.normalizeAngle(refAngle + dir * (float) Math.toDegrees(rock.x() / path.radius));
        return new Placement(path.getModifiedRadiusArc(rockRadius), angle, speed * rockRadius / path.radius);
    }
}
