package indevo.exploration.meteor.rogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager.MeteroidShowerType;
import indevo.exploration.meteor.entities.IcyRockEntity;
import indevo.exploration.meteor.entities.MeteorEntity;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.movement.ArcingMovementModule;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningPathRenderer;
import indevo.utils.ModPlugin;
import indevo.utils.helper.CircularArc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RogueDrizzleSpawner implements EveryFrameScript {

    public static final String TAG_ROCK = "IndEvo_DrizzleRock";
    public static final float WEIGHT = 10f;
    public static final float MIN_DAYS = 5f;
    public static final float MAX_DAYS = 15f;

    public static final float AREA_PER_ROCK = 500000f;
    public static final float MARGIN = 600f;
    public static final float REMOVE_DISTANCE = 1000f;
    public static final int MAX_ROCKS = 80;

    public static final float MIN_ROCK_SIZE = 20f;
    public static final float MAX_ROCK_SIZE = 48f;
    public static final float MIN_SPEED = 120f;
    public static final float MAX_SPEED = 300f;
    public static final float MAX_LANE_SPREAD = 8f;
    public static final int MIN_LANE_SETS = 2;
    public static final int MAX_LANE_SETS = 3;

    public static final float UPDATE_INTERVAL = 0.25f;
    public static final float FILL_RATE_MULT = 2f;
    public static final int SPAWN_TRIES = 16;

    public final LocationAPI location;
    public final MeteroidShowerType type;
    public final Random random;
    public final long startTimestamp;
    public final float durationDays;
    public final List<Vector2f> laneCentres = new ArrayList<>();
    public final int dir;

    public final List<SectorEntityToken> rocks = new ArrayList<>();
    public float timer = 0f;
    public float spawnBudget = 0f;
    public boolean done = false;

    public RogueDrizzleSpawner(LocationAPI location, MeteroidShowerType type, long seed) {
        this.location = location;
        this.type = type;
        this.random = new Random(seed);
        this.startTimestamp = Global.getSector().getClock().getTimestamp();
        this.durationDays = RogueFormation.lerp(MIN_DAYS, MAX_DAYS, random.nextFloat());
        this.dir = random.nextBoolean() ? 1 : -1;
        laneCentres.addAll(createLaneCentres(random));
    }

    public static RogueDrizzleSpawner start(LocationAPI location, MeteroidShowerType type, Random random) {
        RogueDrizzleSpawner spawner = new RogueDrizzleSpawner(location, type, random.nextLong());
        location.addScript(spawner);
        ModPlugin.log("Starting rogue drizzle: " + type + " for " + spawner.durationDays + " days, lane sets " + spawner.laneCentres.size());
        return spawner;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        if (Global.getSector().getClock().getElapsedDaysSince(startTimestamp) >= durationDays
                || location.getMemoryWithoutUpdate().contains(MeteorSwarmWarningPathRenderer.MEM_INSTANCE)) {
            done = true;
            return;
        }

        // nobody sees the rocks, so they go and the drizzle waits for the player
        if (Global.getSector().getCurrentLocation() != location) {
            if (!rocks.isEmpty()) {
                for (SectorEntityToken rock : rocks) if (rock.isAlive()) location.removeEntity(rock);
                rocks.clear();
            }
            spawnBudget = 0f;
            return;
        }

        timer += amount;
        if (timer < UPDATE_INTERVAL) return;
        float elapsed = timer;
        timer = 0f;

        ViewportAPI view = Global.getSector().getViewport();
        float llx = view.getLLX(), lly = view.getLLY(), w = view.getVisibleWidth(), h = view.getVisibleHeight();

        removeFarRocks(llx, lly, w, h);

        int target = getTargetCount(w, h);
        float crossSeconds = ((w + h) / 2f + 2f * MARGIN) / ((MIN_SPEED + MAX_SPEED) / 2f);
        spawnBudget = Math.min(target, spawnBudget + FILL_RATE_MULT * target / crossSeconds * elapsed);

        while (spawnBudget >= 1f && rocks.size() < target) {
            spawnBudget -= 1f;
            spawnRock(llx, lly, w, h);
        }
    }

    private void removeFarRocks(float llx, float lly, float w, float h) {
        List<SectorEntityToken> gone = new ArrayList<>();
        for (SectorEntityToken rock : rocks) {
            if (!rock.isAlive() || rock.hasTag(Tags.FADING_OUT_AND_EXPIRING)) {
                gone.add(rock);
            } else if (!RogueAsteroidSpawner.isInRect(rock.getLocation(), llx, lly, w, h, MARGIN + REMOVE_DISTANCE)) {
                location.removeEntity(rock);
                gone.add(rock);
            }
        }
        rocks.removeAll(gone);
    }

    private void spawnRock(float llx, float lly, float w, float h) {
        Vector2f centre = laneCentres.get(random.nextInt(laneCentres.size()));
        Vector2f p = pickSpawnPoint(llx, lly, w, h, centre, dir, random);
        if (p == null) return;

        float radius = Misc.getDistance(centre, p);
        float angle = Misc.getAngleInDegreesStrict(centre, p);
        float spanLength = 2f * (w + h + 4f * (MARGIN + REMOVE_DISTANCE));
        float span = (float) Math.toDegrees(spanLength / radius);
        CircularArc arc = new CircularArc(centre, radius, angle, angle + dir * span);

        float size = RogueFormation.lerp(MIN_ROCK_SIZE, MAX_ROCK_SIZE, RogueFormation.pow(random.nextFloat(), 1.4f));
        float speed = RogueFormation.lerp(MIN_SPEED, MAX_SPEED, random.nextFloat());
        MeteroidShowerType kind = pickKind(type, random);

        MeteorEntity.MeteorData data = new MeteorEntity.MeteorData(size, new ArcingMovementModule(arc, speed, arc.startAngle));
        SectorEntityToken rock = MeteorFactory.spawn(location, data, kind);
        if (kind == MeteroidShowerType.ICEROID) rock.setFaction(Factions.TRITACHYON);
        if (kind == MeteroidShowerType.IRRADIOID) rock.setFaction(Factions.LUDDIC_PATH);
        if (rock.getCustomPlugin() instanceof IcyRockEntity && size < RogueAsteroidSpawner.ICE_SPLINTER_MIN_SIZE) rock.addTag(IcyRockEntity.TAG_NO_SPLINTERS);

        rock.addTag(TAG_ROCK);
        rock.addTag(RogueAsteroidSpawner.TAG_ROGUE_ROCK);
        rock.setLocation(p.x, p.y);
        rocks.add(rock);
    }

    public static MeteroidShowerType pickKind(MeteroidShowerType type, Random random) {
        if (type == MeteroidShowerType.ICEROID && random.nextFloat() < RogueAsteroidSpawner.ICE_CHANCE) return MeteroidShowerType.ICEROID;
        if (type == MeteroidShowerType.IRRADIOID && random.nextFloat() < RogueAsteroidSpawner.IRRADIATED_CHANCE) return MeteroidShowerType.IRRADIOID;
        return MeteroidShowerType.ASTEROID;
    }

    // one far centre per lane set, the sets rotated up to MAX_LANE_SPREAD degrees apart around the system centre
    public static List<Vector2f> createLaneCentres(Random random) {
        float dist = RogueFormation.lerp(RogueAsteroidSpawner.MIN_ARC_RADIUS, RogueAsteroidSpawner.MAX_ARC_RADIUS, random.nextFloat());
        float base = random.nextFloat() * 360f;
        int sets = MIN_LANE_SETS + random.nextInt(MAX_LANE_SETS - MIN_LANE_SETS + 1);

        List<Vector2f> centres = new ArrayList<>();
        for (int i = 0; i < sets; i++) {
            float angle = base + (i == 0 ? 0f : random.nextFloat() * MAX_LANE_SPREAD);
            Vector2f centre = Misc.getUnitVectorAtDegreeAngle(angle);
            centre.scale(dist);
            centres.add(centre);
        }
        return centres;
    }

    public static int getTargetCount(float w, float h) {
        float area = (w + 2f * MARGIN) * (h + 2f * MARGIN);
        return Math.min(MAX_ROCKS, (int) (area / AREA_PER_ROCK));
    }

    public static Vector2f pickSpawnPoint(float llx, float lly, float w, float h, Vector2f centre, int dir, Random random) {
        float reach = 2f * (w + h + 4f * MARGIN);

        float sideArea = MARGIN * (h + 2f * MARGIN);
        float capArea = MARGIN * w;

        for (int i = 0; i < SPAWN_TRIES; i++) {
            float pick = random.nextFloat() * 2f * (sideArea + capArea);
            float u = random.nextFloat();
            float v = random.nextFloat();
            Vector2f p;
            if (pick < sideArea) p = new Vector2f(llx - MARGIN + u * MARGIN, lly - MARGIN + v * (h + 2f * MARGIN));
            else if (pick < 2f * sideArea) p = new Vector2f(llx + w + u * MARGIN, lly - MARGIN + v * (h + 2f * MARGIN));
            else if (pick < 2f * sideArea + capArea) p = new Vector2f(llx + u * w, lly - MARGIN + v * MARGIN);
            else p = new Vector2f(llx + u * w, lly + h + v * MARGIN);

            Vector2f heading = getLaneHeading(centre, p, dir);
            if (rayHitsRect(p, heading, reach, llx, lly, w, h)) return p;
        }

        return null;
    }

    public static Vector2f getLaneHeading(Vector2f centre, Vector2f p, int dir) {
        return Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegreesStrict(centre, p) + 90f * dir);
    }

    // slab test of the segment p + heading * [0, reach] against the rect
    public static boolean rayHitsRect(Vector2f p, Vector2f heading, float reach, float llx, float lly, float w, float h) {
        float tMin = 0f;
        float tMax = reach;

        float[] origin = {p.x, p.y};
        float[] d = {heading.x, heading.y};
        float[] min = {llx, lly};
        float[] max = {llx + w, lly + h};

        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(d[axis]) < 1e-6f) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) return false;
                continue;
            }

            float t1 = (min[axis] - origin[axis]) / d[axis];
            float t2 = (max[axis] - origin[axis]) / d[axis];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) return false;
        }

        return true;
    }
}
