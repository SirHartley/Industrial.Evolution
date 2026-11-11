package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Shuttle {

    public enum End {
        NONE,
        PLANET,
        STATION
    }

    private static final int TRAIL_POINTS = 16;
    private static final float TRAIL_SPACING = 8f;
    private static final int HISTORY_POINTS = 16;
    private static final float HISTORY_SPACING = 4f;
    private static final float TURN_RATE = 90f;
    private static final float AVOID_MARGIN = 40f;
    private static final float AVOID_EASE = 20f;
    private static final float DOCK_CHECK_DISTANCE = 40f;
    private static final float FLEE_WOBBLE = 10f;
    private static final float FLEE_WOBBLE_TIME = 3f;
    private static final float STRANDED_HOLD_RADIUS = 150f;

    public final ShuttleHull hull;
    public final FactionAPI faction;
    public ShuttleRoute route;
    public ShuttleAnchor from;
    public ShuttleAnchor to;
    public ShuttleAnchor home;
    public ShuttlePath path;
    public End launchEnd;
    public End landEnd;
    public float speedMult;
    public float jitter;
    public boolean done = false;

    public boolean engineGlow = true;
    public boolean avoids = true;
    public boolean queues = true;
    public boolean flees = true;
    public boolean picket = false;
    public float trailAlpha = 1f;
    public float trailWidth = 3f;
    public int trailMax = TRAIL_POINTS;
    public float flicker = 0.2f;
    public float spinRate = 0f;
    public float stayDays = 0f;
    public float boost = 0f;
    public float boostTime = 0f;
    public float delay = 0f;

    public Shuttle leader;
    public final List<Shuttle> followers = new ArrayList<>(2);
    public float slotBack = 0f;
    public float slotSide = 0f;
    public float noise = 0f;
    public boolean straggler = false;

    public final Vector2f pos = new Vector2f();
    public float facing = 0f;
    public float scale = 1f;
    public float alpha = 1f;
    public float lastThreatNearDay = -100f;

    private final ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
    private float traveled = 0f;
    private float avoidOffset = 0f;
    private float lastLateral = 0f;
    private float sideBias = 0f;
    private float darken = 1f;
    private float glowMult = 1f;
    private float rebuildTimer = 0f;
    private float age = 0f;
    private float spin = 0f;
    private float turnSpeed = 0f;
    private float extraLag = 0f;
    private float fleeStart = 0f;
    private float resumeDay = 0f;
    private float screenX = 0f;
    private float screenY = 0f;
    private float appearRamp = 1f;
    private float appearAlpha = 1f;
    private float appearScale = 1f;
    private boolean facingSet = false;
    private ShuttleState state = ShuttleState.CRUISE;
    // pad clearance and the wait at a refuge hold across states: a fleeing craft queues at a station like any other
    private boolean cleared = false;
    private boolean waitForSafeHome = false;
    private final float phase;

    private final Vector2f base = new Vector2f();
    private final Vector2f tangent = new Vector2f();
    private final Vector2f tmp = new Vector2f();
    // entity the craft mostly moves with, join paths start relative to it
    private SectorEntityToken frame;
    // trail and history points are stored relative to a virtual frame that moves like the path point under the craft
    private float frameX = 0f;
    private float frameY = 0f;
    private float frameDX = 0f;
    private float frameDY = 0f;
    private ShuttlePath.Segment frameSegment;
    private final Vector2f frameStartLoc = new Vector2f();
    private final Vector2f frameEndLoc = new Vector2f();
    private final float[] trailX = new float[TRAIL_POINTS];
    private final float[] trailY = new float[TRAIL_POINTS];
    private final float[] drawX = new float[TRAIL_POINTS + 1];
    private final float[] drawY = new float[TRAIL_POINTS + 1];
    private int trailHead = 0;
    private int trailCount = 0;
    private final float[] historyX = new float[HISTORY_POINTS];
    private final float[] historyY = new float[HISTORY_POINTS];
    private int historyHead = 0;
    private int historyCount = 0;

    public Shuttle(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path,
                   End launchEnd, End landEnd, float speedMult, float jitter, float phase) {
        this.hull = hull;
        this.route = route;
        this.faction = faction;
        this.from = from;
        this.to = to;
        this.home = from;
        this.path = path;
        this.launchEnd = launchEnd;
        this.landEnd = landEnd;
        this.speedMult = speedMult;
        this.jitter = jitter;
        this.phase = phase;
        if (path != null) path.rebuild();
    }

    public void warmStart(float fraction) {
        if (path == null) return;

        traveled = path.getLength() * fraction;
        path.place(cursor, traveled);
        path.getPoint(cursor, base);
        path.getTangent(cursor, tangent);
        pos.set(base);
        facing = Misc.getAngleInDegrees(tangent);
        facingSet = true;
        moveFrame();

        for (int i = HISTORY_POINTS; i >= 1; i--) {
            path.getPoint(Math.max(0f, traveled - i * HISTORY_SPACING), tmp);
            pushHistory(tmp.x, tmp.y);
        }

        for (int i = TRAIL_POINTS; i >= 1; i--) {
            path.getPoint(Math.max(0f, traveled - i * TRAIL_SPACING), tmp);
            pushTrail(tmp.x, tmp.y);
        }
    }

    public void advance(float amount, ShuttleTraffic traffic) {
        if (delay > 0f) {
            delay -= amount;
            return;
        }

        age += amount;
        spin += spinRate * amount;

        if (state == ShuttleState.DOWN) {
            advanceDown(traffic);
            return;
        }

        if (leader != null) {
            advanceFollower(amount);
            return;
        }

        // the current segment is rebuilt every frame, the rest only feeds the total for easing
        rebuildTimer -= amount;
        if (rebuildTimer <= 0f) {
            path.rebuild();
            rebuildTimer = 1f;
        } else {
            path.refresh(cursor);
        }

        advancePath(amount, traffic, true);
    }

    // retry runs the same frame on a path that just replaced the old one, so the craft never stalls a frame
    private void advancePath(float amount, ShuttleTraffic traffic, boolean retry) {
        float total = path.getLength();
        traveled = path.getTraveled(cursor);
        float launchLength = getEndLength(launchEnd, from, total);
        float landLength = getEndLength(landEnd, to, total);
        float remaining = total - traveled;
        boolean docking = landEnd == End.STATION && to != null && remaining <= landLength + DOCK_CHECK_DISTANCE;

        if (docking && queues && !cleared) {
            if (to.isPadBusy(traffic.getTime())) {
                startHolding(traffic);
                if (retry) advancePath(amount, traffic, false);
                return;
            }

            cleared = true;
        }

        if (docking) to.padBusyUntil = traffic.getTime() + 1f;

        float ease = 1f;
        if (launchLength > 0f && traveled < launchLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(traveled / launchLength));
        if (landLength > 0f && remaining < landLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(remaining / landLength));

        float speed = hull.getCruiseSpeed() * speedMult * state.pace * ease;
        if (boostTime > 0f && age < boostTime) speed += boost * (1f - age / boostTime);
        if (path.move(cursor, speed * amount)) {
            onPathEnd(traffic);
            if (retry && !done && state != ShuttleState.DOWN) advancePath(amount, traffic, false);
            return;
        }

        traveled = path.getTraveled(cursor);

        updatePosition(amount, traffic, total, launchLength, landLength, speed);
    }

    public void redirect(ShuttleAnchor newTarget, ShuttleTraffic traffic) {
        to = newTarget;
        cleared = false;
        setState(state.redirect());
        setPath(traffic.getFlights().redirectPath(frame, pos, facing, newTarget), End.NONE, newTarget.getEnd());
    }

    public void flee(ShuttleAnchor refuge, float threatX, float threatY, ShuttleTraffic traffic) {
        Random random = traffic.getRandom();
        leader = null;
        followers.clear();
        setState(state.flee(refuge != null));
        cleared = false;
        picket = false;
        stayDays = 0f;
        fleeStart = age;

        float heading = facing + (random.nextFloat() - 0.5f) * 140f;
        boolean panic = random.nextFloat() < 0.2f;

        if (refuge != null) {
            to = refuge;
            waitForSafeHome = home != null && refuge != home;
            setPath(traffic.getFlights().fleePath(frame, pos, heading, refuge, panic), End.NONE, refuge.getEnd());
            return;
        }

        lastThreatNearDay = traffic.getDays();
        setPath(traffic.getFlights().awayPath(frame, pos, heading, threatX, threatY, panic), End.NONE, End.NONE);
    }

    public void screen(float threatX, float threatY, ShuttleTraffic traffic) {
        screenX = threatX;
        screenY = threatY;
        if (state == ShuttleState.SCREENING) return;

        setState(state.screen());
        cleared = false;
        setPath(traffic.getFlights().screenPath(frame, pos, facing, home, threatX, threatY), End.NONE, End.NONE);
    }

    public ShuttleState getState() {
        return state;
    }

    public boolean isDown() {
        return state == ShuttleState.DOWN;
    }

    public boolean isFleeing() {
        return state.isFleeing();
    }

    public boolean isStranded() {
        return state.isStranded();
    }

    public boolean isScreening() {
        return state == ShuttleState.SCREENING;
    }

    public boolean isWaiting() {
        return delay > 0f;
    }

    private void onPathEnd(ShuttleTraffic traffic) {
        if (state.isHolding()) {
            finishHoldingLap(traffic);
            return;
        }

        if (state == ShuttleState.SCREENING) {
            continueScreening(traffic);
            return;
        }

        if (state.isStranded()) {
            continueStranded(traffic);
            return;
        }

        if (to != null && home != null && to != home && landEnd != End.NONE && (stayDays > 0f || waitForSafeHome)) {
            setState(state.land());
            resumeDay = traffic.getDays() + stayDays;
            return;
        }

        done = true;
    }

    private void advanceDown(ShuttleTraffic traffic) {
        float days = traffic.getDays();
        if (!exists(home) || to == null || !to.entity.isAlive()) {
            done = true;
            return;
        }

        if (days < resumeDay || home.closed || !home.isSafe(days) || to.threatened) return;

        ShuttleAnchor start = to;
        setState(state.calm());
        waitForSafeHome = false;
        stayDays = 0f;
        from = start;
        to = home;
        facingSet = false;
        trailCount = 0;
        historyCount = 0;
        setPath(traffic.getFlights().lanePath(start, home, (traffic.getRandom().nextFloat() - 0.5f) * 0.5f, false),
                start.getEnd(), home.getEnd());
    }

    private void advanceFollower(float amount) {
        Shuttle lead = leader;
        if (lead.done || lead.isDown()) {
            done = true;
            return;
        }

        scale = lead.scale;
        alpha = lead.alpha;
        darken = lead.darken;
        glowMult = lead.glowMult;

        if (straggler) {
            float target = Math.abs(lead.turnSpeed) > 25f ? 30f : 0f;
            if (extraLag < target) extraLag = Math.min(target, extraLag + 15f * amount);
            else extraLag = Math.max(target, extraLag - 10f * amount);
        }

        float offset = phase * 6.28f;
        float side = slotSide + noise * (float) Math.sin(age * 0.7f + offset);
        float back = slotBack + extraLag + noise * 0.5f * (float) Math.sin(age * 0.5f + offset * 2f);
        lead.getHistoryPoint(back, base, tangent);

        pos.set(base.x - tangent.y * side, base.y + tangent.x * side);
        updateFacing(amount, Misc.getAngleInDegrees(tangent));
        frame = lead.frame;
        frameSegment = null;
        shiftFrame(lead.frameDX, lead.frameDY);
        pushTrailAtRear();
    }

    private void finishHoldingLap(ShuttleTraffic traffic) {
        if (to == null || !to.isUsable(traffic.getLocation())) {
            done = true;
            return;
        }

        if (to.isPadBusy(traffic.getTime())) {
            cursor.reset();
            traveled = 0f;
            return;
        }

        to.padBusyUntil = traffic.getTime() + 1f;
        redirect(to, traffic);
        cleared = true;
    }

    private void continueScreening(ShuttleTraffic traffic) {
        LocationAPI location = traffic.getLocation();
        if (home != null && home.isUsable(location) && home.threatened && !home.overrun) {
            setPath(traffic.getFlights().screenPath(frame, pos, facing, home, screenX, screenY), End.NONE, End.NONE);
            return;
        }

        setState(state.calm());
        if (home == null || !home.isUsable(location)) {
            done = true;
            return;
        }

        redirect(home, traffic);
    }

    private void continueStranded(ShuttleTraffic traffic) {
        setState(state.strand());

        if (!exists(home)) {
            done = true;
            return;
        }

        float days = traffic.getDays();
        if (home.isUsable(traffic.getLocation()) && home.isSafe(days) && days - lastThreatNearDay >= 1f) {
            setState(state.calm());
            redirect(home, traffic);
            return;
        }

        setPath(traffic.getFlights().holdPath(frame, pos, facing, STRANDED_HOLD_RADIUS), End.NONE, End.NONE);
    }

    private void startHolding(ShuttleTraffic traffic) {
        setState(state.hold());
        setPath(traffic.getFlights().holdPath(to.entity, pos, facing, ShuttleAnchor.HOLD_RADIUS), End.NONE, End.NONE);
    }

    private void setPath(ShuttlePath newPath, End newLaunch, End newLand) {
        appearRamp = 1f;
        if (newLaunch == End.NONE && (alpha < 1f || scale < 1f)) {
            appearRamp = 0f;
            appearAlpha = alpha;
            appearScale = scale;
        }

        path = newPath;
        path.rebuild();
        rebuildTimer = 1f;
        cursor.reset();
        traveled = 0f;
        launchEnd = newLaunch;
        landEnd = newLand;
        jitter = 0f;
        avoidOffset = 0f;
        lastLateral = 0f;
        sideBias = 0f;
    }

    // pace and turn rate come from the state alone
    private void setState(ShuttleState next) {
        state = next;
    }

    private void updatePosition(float amount, ShuttleTraffic traffic, float total, float launchLength, float landLength, float speed) {
        path.getPoint(cursor, base);
        path.getTangent(cursor, tangent);

        float fraction = total > 0f ? traveled / total : 1f;
        float envelope = (float) Math.sin(Math.PI * fraction);
        float avoidEnvelope = Math.max(0f, Math.min(1f, Math.min(fraction, 1f - fraction) / 0.15f));
        float avoid = avoids ? getAvoidanceTarget(traffic.getFleetScan()) : 0f;
        if (avoid != 0f && !followers.isEmpty()) disperse(traffic.getRandom());
        float target = (avoid + sideBias) * avoidEnvelope;

        float step = AVOID_EASE * amount;
        if (avoidOffset < target) avoidOffset = Math.min(target, avoidOffset + step);
        else avoidOffset = Math.max(target, avoidOffset - step);

        float lateral = jitter * envelope + avoidOffset;
        if (state.isFleeing()) {
            float since = age - fleeStart;
            lateral += FLEE_WOBBLE * Math.max(0f, 1f - since / FLEE_WOBBLE_TIME) * (float) Math.sin(since * 2f);
        }

        float lateralRate = amount > 0f ? (lateral - lastLateral) / amount : 0f;
        lastLateral = lateral;
        pos.set(base.x - tangent.y * lateral, base.y + tangent.x * lateral);

        // face along the path, not the world step, so orbiting anchors do not swing the sprite and trail
        updateFacing(amount, (float) Math.toDegrees(Math.atan2(tangent.y * speed + tangent.x * lateralRate, tangent.x * speed - tangent.y * lateralRate)));
        updateLook(amount, total, launchLength, landLength);
        moveFrame();
        pushHistoryIfMoved();
        pushTrailAtRear();
    }

    private void disperse(Random random) {
        for (Shuttle follower : followers) follower.breakFrom(this, random);
        followers.clear();
    }

    // a follower takes over the leader's path at its own slot and spreads out before dodging on its own
    private void breakFrom(Shuttle lead, Random random) {
        leader = null;
        path = lead.path;
        from = lead.from;
        to = lead.to;
        launchEnd = lead.launchEnd;
        landEnd = lead.landEnd;
        setState(lead.state);
        cleared = lead.cleared;
        waitForSafeHome = lead.waitForSafeHome;
        screenX = lead.screenX;
        screenY = lead.screenY;
        speedMult = lead.speedMult * (0.9f + 0.2f * random.nextFloat());
        path.place(cursor, Math.max(0f, path.getTraveled(lead.cursor) - slotBack));
        traveled = path.getTraveled(cursor);
        rebuildTimer = random.nextFloat();
        avoidOffset = slotSide;
        lastLateral = slotSide;
        sideBias = slotSide * 4f;
    }

    private void updateFacing(float amount, float desired) {
        if (!facingSet) {
            facing = desired;
            facingSet = true;
            turnSpeed = 0f;
            return;
        }

        float diff = Misc.getAngleDiff(facing, desired);
        float turn = Math.min(diff, TURN_RATE * state.turnMult * amount);
        float direction = Misc.getClosestTurnDirection(facing, desired);
        facing = Misc.normalizeAngle(facing + turn * direction);
        turnSpeed = amount > 0f ? turn * direction / amount : 0f;
    }

    private void updateLook(float amount, float total, float launchLength, float landLength) {
        float remaining = total - traveled;
        float speed = hull.getCruiseSpeed() * speedMult;

        scale = path.getScaleMult(cursor);
        alpha = 1f;
        darken = 1f;
        glowMult = (launchLength > 0f && traveled < launchLength + speed * 2f) || (landLength > 0f && remaining < landLength + speed * 2f) ? 1.5f : 1f;

        if (launchLength > 0f && traveled < launchLength) applyEnd(launchEnd, from, 1f - traveled / launchLength);
        if (landLength > 0f && remaining < landLength) applyEnd(landEnd, to, 1f - remaining / landLength);

        if (appearRamp < 1f) {
            appearRamp = Math.min(1f, appearRamp + amount);
            alpha *= appearAlpha + (1f - appearAlpha) * appearRamp;
            scale *= appearScale + (1f - appearScale) * appearRamp;
        }
    }

    // drawn above the planet: the craft shrinks on approach and fades only once it is over the surface
    private void applyEnd(End end, ShuttleAnchor anchor, float progress) {
        if (end == End.PLANET) {
            scale *= 1f - 0.5f * progress;
            darken = Math.min(darken, 1f - 0.3f * progress);
            if (anchor != null) {
                float radius = anchor.getRadius();
                float pad = radius * anchor.getPadDepth();
                float dist = Misc.getDistance(pos, anchor.getLocation());
                alpha *= Math.max(0f, Math.min(1f, (dist - pad) / Math.max(1f, radius - pad)));
            } else {
                alpha *= 1f - progress;
            }
        } else if (end == End.STATION) {
            scale *= 1f - 0.3f * progress;
            alpha *= 1f - progress;
        }
    }

    private float getAvoidanceTarget(ShuttleFleetScan scan) {
        float nx = -tangent.y;
        float ny = tangent.x;
        float best = 0f;

        for (int i = 0; i < scan.count; i++) {
            if (!scan.obstacle[i] && !(state.isFleeing() && scan.isHostileThreat(i, faction))) continue;

            float dx = base.x - scan.x[i];
            float dy = base.y - scan.y[i];
            float radius = scan.radius[i] + AVOID_MARGIN;
            float along = dx * tangent.x + dy * tangent.y;
            if (Math.abs(along) >= radius) continue;

            float across = dx * nx + dy * ny;
            float side = Math.signum(across + avoidOffset);
            if (side == 0f) side = 1f;

            float need = (float) Math.sqrt(radius * radius - along * along);
            float offset = side * need - across;
            if (side * offset <= 0f) continue;

            offset = Math.max(-radius * 1.5f, Math.min(radius * 1.5f, offset));
            if (Math.abs(offset) > Math.abs(best)) best = offset;
        }

        return best;
    }

    private void getHistoryPoint(float distance, Vector2f out, Vector2f dirOut) {
        float fx = getFrameX();
        float fy = getFrameY();
        float px = pos.x;
        float py = pos.y;
        float dirX = (float) Math.cos(Math.toRadians(facing));
        float dirY = (float) Math.sin(Math.toRadians(facing));
        float remaining = distance;

        for (int i = 1; i <= historyCount; i++) {
            int index = (historyHead - i + HISTORY_POINTS) % HISTORY_POINTS;
            float dx = px - (historyX[index] + fx);
            float dy = py - (historyY[index] + fy);
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.0001f) continue;

            dirX = dx / length;
            dirY = dy / length;
            if (length >= remaining) {
                out.set(px - dirX * remaining, py - dirY * remaining);
                dirOut.set(dirX, dirY);
                return;
            }

            remaining -= length;
            px = historyX[index] + fx;
            py = historyY[index] + fy;
        }

        out.set(px - dirX * remaining, py - dirY * remaining);
        dirOut.set(dirX, dirY);
    }

    private void pushHistoryIfMoved() {
        if (historyCount == 0) {
            pushHistory(pos.x, pos.y);
            return;
        }

        int last = (historyHead - 1 + HISTORY_POINTS) % HISTORY_POINTS;
        float dx = pos.x - (historyX[last] + getFrameX());
        float dy = pos.y - (historyY[last] + getFrameY());
        if (dx * dx + dy * dy >= HISTORY_SPACING * HISTORY_SPACING) pushHistory(pos.x, pos.y);
    }

    private void pushHistory(float x, float y) {
        historyX[historyHead] = x - getFrameX();
        historyY[historyHead] = y - getFrameY();
        historyHead = (historyHead + 1) % HISTORY_POINTS;
        historyCount = Math.min(HISTORY_POINTS, historyCount + 1);
    }

    private void pushTrailAtRear() {
        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        float rearX = pos.x - hx * hull.length * scale * 0.5f;
        float rearY = pos.y - hy * hull.length * scale * 0.5f;
        if (trailCount == 0) {
            pushTrail(rearX, rearY);
            return;
        }

        int last = (trailHead - 1 + TRAIL_POINTS) % TRAIL_POINTS;
        float tx = rearX - (trailX[last] + getFrameX());
        float ty = rearY - (trailY[last] + getFrameY());
        if (tx * tx + ty * ty >= TRAIL_SPACING * TRAIL_SPACING) pushTrail(rearX, rearY);
    }

    private void pushTrail(float x, float y) {
        trailX[trailHead] = x - getFrameX();
        trailY[trailHead] = y - getFrameY();
        trailHead = (trailHead + 1) % TRAIL_POINTS;
        trailCount = Math.min(TRAIL_POINTS, trailCount + 1);
    }

    // the segment ends move with their anchors, pads also turn, so blend how far both ends moved since the last frame
    private void moveFrame() {
        ShuttlePath.Segment segment = path.getSegment(cursor);
        float weight = path.getEndWeight(cursor);
        frame = weight < 0.5f ? segment.getStartFrame() : segment.getEndFrame();

        if (segment != frameSegment) {
            // a segment that starts where the last one ended keeps moving from there
            segment.evaluate(0f, tmp);
            boolean joined = frameSegment != null && Math.abs(tmp.x - frameEndLoc.x) + Math.abs(tmp.y - frameEndLoc.y) < 10f;
            if (!joined) frameStartLoc.set(tmp);
            else frameStartLoc.set(frameEndLoc);
            segment.evaluate(1f, frameEndLoc);
            frameSegment = segment;
        }

        segment.evaluate(0f, tmp);
        float dx = (tmp.x - frameStartLoc.x) * (1f - weight);
        float dy = (tmp.y - frameStartLoc.y) * (1f - weight);
        frameStartLoc.set(tmp);
        segment.evaluate(1f, tmp);
        dx += (tmp.x - frameEndLoc.x) * weight;
        dy += (tmp.y - frameEndLoc.y) * weight;
        frameEndLoc.set(tmp);
        shiftFrame(dx, dy);
    }

    private void shiftFrame(float dx, float dy) {
        frameDX = dx;
        frameDY = dy;
        frameX += dx;
        frameY += dy;
    }

    private float getFrameX() {
        return frameX;
    }

    private float getFrameY() {
        return frameY;
    }

    public void render(float alphaMult, SpriteAPI glow) {
        if (delay > 0f || state == ShuttleState.DOWN) return;

        float a = alpha * alphaMult;
        if (a <= 0.01f) return;

        renderTrail(a * trailAlpha);

        SpriteAPI sprite = hull.sprite;
        sprite.setSize(hull.width * scale, hull.length * scale);
        sprite.setAngle(spinRate != 0f ? spin : facing - 90f);
        sprite.setColor(darken >= 1f ? Color.WHITE : new Color((int) (255 * darken), (int) (255 * darken), (int) (255 * darken)));
        sprite.setAlphaMult(a);
        sprite.setNormalBlend();
        sprite.renderAtCenter(pos.x, pos.y);

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));

        if (engineGlow) {
            float glowSize = Math.max(4f, hull.width * scale * 0.9f) * glowMult;
            glow.setSize(glowSize, glowSize);
            glow.setAngle(0f);
            glow.setColor(hull.glowColor);
            glow.setAdditiveBlend();
            glow.setAlphaMult(a * (1f - flicker + flicker * (float) Math.random()));
            glow.renderAtCenter(pos.x - hx * hull.length * scale * 0.5f, pos.y - hy * hull.length * scale * 0.5f);
        }
    }

    private void renderTrail(float alphaMult) {
        int points = Math.min(trailCount, trailMax);
        if (points < 1 || alphaMult <= 0.01f) return;

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        drawX[0] = pos.x - hx * hull.length * scale * 0.5f;
        drawY[0] = pos.y - hy * hull.length * scale * 0.5f;
        float fx = getFrameX();
        float fy = getFrameY();
        int count = 1;
        for (int i = 1; i <= points; i++) {
            int index = (trailHead - i + TRAIL_POINTS) % TRAIL_POINTS;
            drawX[count] = trailX[index] + fx;
            drawY[count] = trailY[index] + fy;
            count++;
        }

        if (count < 2) return;

        Color color = hull.trailColor;
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float baseAlpha = color.getAlpha() / 255f * alphaMult;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, hull.additiveTrail ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int side = -1; side <= 1; side += 2) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i < count; i++) {
                int prev = Math.max(0, i - 1);
                int next = Math.min(count - 1, i + 1);
                float dx = drawX[prev] - drawX[next];
                float dy = drawY[prev] - drawY[next];
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 0.0001f) {
                    dx = 1f;
                    dy = 0f;
                    len = 1f;
                }

                float fade = 1f - i / (float) (count - 1);
                float width = trailWidth * 0.5f * fade;
                float px = -dy / len * width * side;
                float py = dx / len * width * side;

                GL11.glColor4f(r, g, b, baseAlpha * fade);
                GL11.glVertex2f(drawX[i], drawY[i]);
                GL11.glColor4f(r, g, b, 0f);
                GL11.glVertex2f(drawX[i] + px, drawY[i] + py);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    private static boolean exists(ShuttleAnchor anchor) {
        return anchor != null && anchor.seen && anchor.entity.isAlive();
    }

    private static float getEndLength(End end, ShuttleAnchor anchor, float total) {
        if (end == End.NONE || anchor == null) return 0f;
        return Math.min(anchor.getLandingLength(), total * 0.4f);
    }

    private static float smooth(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }
}
