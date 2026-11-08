package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
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
    private static final float FLEE_PACE = 1.4f;
    private static final float CALM_PACE = 0.6f;
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
    public float turnRate = TURN_RATE;
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
    public CampaignEngineLayers layer = CampaignEngineLayers.TERRAIN_7;
    public float lastThreatNearDay = -100f;

    private final ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
    private float traveled = 0f;
    private float avoidOffset = 0f;
    private float lastLateral = 0f;
    private float darken = 1f;
    private float glowMult = 1f;
    private float rebuildTimer = 0f;
    private float age = 0f;
    private float spin = 0f;
    private float turnSpeed = 0f;
    private float extraLag = 0f;
    private float pace = 1f;
    private float fleeStart = 0f;
    private float calmTurnRate = TURN_RATE;
    private float resumeDay = 0f;
    private float screenX = 0f;
    private float screenY = 0f;
    private float appearRamp = 1f;
    private float appearAlpha = 1f;
    private float appearScale = 1f;
    private boolean facingSet = false;
    private boolean cleared = false;
    private boolean holding = false;
    private boolean fleeing = false;
    private boolean screening = false;
    private boolean stranded = false;
    private boolean down = false;
    private boolean waitForSafeHome = false;
    private final float phase;

    private final Vector2f base = new Vector2f();
    private final Vector2f tangent = new Vector2f();
    private final Vector2f tmp = new Vector2f();
    // trail and history points are stored relative to frame, so they ride along with an orbiting anchor
    private SectorEntityToken frame;
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
        setFrame(path.getFrame(cursor));

        for (int i = HISTORY_POINTS; i >= 1; i--) {
            path.getPoint(Math.max(0f, traveled - i * HISTORY_SPACING), tmp);
            pushHistory(tmp.x, tmp.y);
        }

        for (int i = TRAIL_POINTS; i >= 1; i--) {
            path.getPoint(Math.max(0f, traveled - i * TRAIL_SPACING), tmp);
            pushTrail(tmp.x, tmp.y);
        }
    }

    public void advance(float amount, ShuttleTrafficRenderer renderer) {
        if (delay > 0f) {
            delay -= amount;
            return;
        }

        age += amount;
        spin += spinRate * amount;

        if (down) {
            advanceDown(renderer);
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

        advancePath(amount, renderer, true);
    }

    // retry runs the same frame on a path that just replaced the old one, so the craft never stalls a frame
    private void advancePath(float amount, ShuttleTrafficRenderer renderer, boolean retry) {
        float total = path.getLength();
        traveled = path.getTraveled(cursor);
        float launchLength = getEndLength(launchEnd, from, total);
        float landLength = getEndLength(landEnd, to, total);
        float remaining = total - traveled;
        boolean docking = landEnd == End.STATION && to != null && remaining <= landLength + DOCK_CHECK_DISTANCE;

        if (docking && queues && !cleared) {
            if (to.isPadBusy(renderer.getTime())) {
                startHolding(renderer);
                if (retry) advancePath(amount, renderer, false);
                return;
            }

            cleared = true;
        }

        if (docking) to.padBusyUntil = renderer.getTime() + 1f;

        float ease = 1f;
        if (launchLength > 0f && traveled < launchLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(traveled / launchLength));
        if (landLength > 0f && remaining < landLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(remaining / landLength));

        float speed = hull.getCruiseSpeed() * speedMult * pace * ease;
        if (boostTime > 0f && age < boostTime) speed += boost * (1f - age / boostTime);
        if (path.move(cursor, speed * amount)) {
            onPathEnd(renderer);
            if (retry && !done && !down) advancePath(amount, renderer, false);
            return;
        }

        traveled = path.getTraveled(cursor);

        updatePosition(amount, renderer, total, launchLength, landLength, speed);
    }

    public void redirect(ShuttleAnchor newTarget, ShuttleTrafficRenderer renderer) {
        to = newTarget;
        cleared = false;
        holding = false;
        setPath(renderer.getFlights().redirectPath(frame, pos, facing, newTarget), End.NONE, ShuttleTrafficRenderer.getEnd(newTarget));
    }

    public void flee(ShuttleAnchor refuge, float threatX, float threatY, ShuttleTrafficRenderer renderer) {
        Random random = renderer.getRandom();
        leader = null;
        followers.clear();
        setFleeing(true);
        holding = false;
        screening = false;
        stranded = false;
        cleared = false;
        picket = false;
        stayDays = 0f;
        fleeStart = age;

        float heading = facing + (random.nextFloat() - 0.5f) * 140f;
        boolean panic = random.nextFloat() < 0.2f;

        if (refuge != null) {
            to = refuge;
            waitForSafeHome = home != null && refuge != home;
            setPath(renderer.getFlights().fleePath(frame, pos, heading, refuge, panic), End.NONE, ShuttleTrafficRenderer.getEnd(refuge));
            return;
        }

        stranded = true;
        lastThreatNearDay = renderer.getDays();
        setPath(renderer.getFlights().awayPath(frame, pos, heading, threatX, threatY, panic), End.NONE, End.NONE);
    }

    public void screen(float threatX, float threatY, ShuttleTrafficRenderer renderer) {
        screenX = threatX;
        screenY = threatY;
        if (screening) return;

        screening = true;
        holding = false;
        cleared = false;
        pace = CALM_PACE;
        setPath(renderer.getFlights().screenPath(frame, pos, facing, home, threatX, threatY), End.NONE, End.NONE);
    }

    public boolean isDown() {
        return down;
    }

    public boolean isFleeing() {
        return fleeing;
    }

    public boolean isStranded() {
        return stranded;
    }

    public boolean isScreening() {
        return screening;
    }

    public boolean isWaiting() {
        return delay > 0f;
    }

    private void onPathEnd(ShuttleTrafficRenderer renderer) {
        if (holding) {
            finishHoldingLap(renderer);
            return;
        }

        if (screening) {
            continueScreening(renderer);
            return;
        }

        if (stranded) {
            continueStranded(renderer);
            return;
        }

        if (to != null && home != null && to != home && landEnd != End.NONE && (stayDays > 0f || waitForSafeHome)) {
            down = true;
            resumeDay = renderer.getDays() + stayDays;
            return;
        }

        done = true;
    }

    private void advanceDown(ShuttleTrafficRenderer renderer) {
        float days = renderer.getDays();
        if (!exists(home) || to == null || !to.entity.isAlive()) {
            done = true;
            return;
        }

        if (days < resumeDay || home.closed || !home.isSafe(days) || to.threatened) return;

        ShuttleAnchor start = to;
        down = false;
        waitForSafeHome = false;
        stayDays = 0f;
        setFleeing(false);
        pace = 1f;
        from = start;
        to = home;
        facingSet = false;
        trailCount = 0;
        historyCount = 0;
        setPath(renderer.getFlights().lanePath(start, home, (renderer.getRandom().nextFloat() - 0.5f) * 0.5f, false),
                ShuttleTrafficRenderer.getEnd(start), ShuttleTrafficRenderer.getEnd(home));
    }

    private void advanceFollower(float amount) {
        Shuttle lead = leader;
        if (lead.done || lead.down) {
            done = true;
            return;
        }

        scale = lead.scale;
        alpha = lead.alpha;
        layer = lead.layer;
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
        setFrame(lead.frame);
        pushTrailAtRear();
    }

    private void finishHoldingLap(ShuttleTrafficRenderer renderer) {
        if (to == null || !to.isUsable(renderer.getLocation())) {
            done = true;
            return;
        }

        if (to.isPadBusy(renderer.getTime())) {
            cursor.reset();
            traveled = 0f;
            return;
        }

        to.padBusyUntil = renderer.getTime() + 1f;
        redirect(to, renderer);
        cleared = true;
    }

    private void continueScreening(ShuttleTrafficRenderer renderer) {
        LocationAPI location = renderer.getLocation();
        if (home != null && home.isUsable(location) && home.threatened && !home.overrun) {
            setPath(renderer.getFlights().screenPath(frame, pos, facing, home, screenX, screenY), End.NONE, End.NONE);
            return;
        }

        screening = false;
        pace = 1f;
        if (home == null || !home.isUsable(location)) {
            done = true;
            return;
        }

        redirect(home, renderer);
    }

    private void continueStranded(ShuttleTrafficRenderer renderer) {
        setFleeing(false);
        pace = CALM_PACE;

        if (!exists(home)) {
            done = true;
            return;
        }

        float days = renderer.getDays();
        if (home.isUsable(renderer.getLocation()) && home.isSafe(days) && days - lastThreatNearDay >= 1f) {
            stranded = false;
            pace = 1f;
            redirect(home, renderer);
            return;
        }

        setPath(renderer.getFlights().holdPath(frame, pos, facing, STRANDED_HOLD_RADIUS), End.NONE, End.NONE);
    }

    private void startHolding(ShuttleTrafficRenderer renderer) {
        holding = true;
        setPath(renderer.getFlights().holdPath(to.entity, pos, facing, ShuttleAnchor.HOLD_RADIUS), End.NONE, End.NONE);
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
    }

    private void setFleeing(boolean value) {
        if (value == fleeing) return;

        fleeing = value;
        if (value) {
            calmTurnRate = turnRate;
            turnRate *= 2f;
            pace = FLEE_PACE;
        } else {
            turnRate = calmTurnRate;
            pace = 1f;
        }
    }

    private void updatePosition(float amount, ShuttleTrafficRenderer renderer, float total, float launchLength, float landLength, float speed) {
        path.getPoint(cursor, base);
        path.getTangent(cursor, tangent);

        float fraction = total > 0f ? traveled / total : 1f;
        float envelope = (float) Math.sin(Math.PI * fraction);
        float avoidEnvelope = Math.max(0f, Math.min(1f, Math.min(fraction, 1f - fraction) / 0.15f));
        float target = avoids ? getAvoidanceTarget(renderer.getFleetScan()) * avoidEnvelope : 0f;

        float step = AVOID_EASE * amount;
        if (avoidOffset < target) avoidOffset = Math.min(target, avoidOffset + step);
        else avoidOffset = Math.max(target, avoidOffset - step);

        float lateral = jitter * envelope + avoidOffset;
        if (fleeing) {
            float since = age - fleeStart;
            lateral += FLEE_WOBBLE * Math.max(0f, 1f - since / FLEE_WOBBLE_TIME) * (float) Math.sin(since * 2f);
        }

        float lateralRate = amount > 0f ? (lateral - lastLateral) / amount : 0f;
        lastLateral = lateral;
        pos.set(base.x - tangent.y * lateral, base.y + tangent.x * lateral);

        // face along the path, not the world step, so orbiting anchors do not swing the sprite and trail
        updateFacing(amount, (float) Math.toDegrees(Math.atan2(tangent.y * speed + tangent.x * lateralRate, tangent.x * speed - tangent.y * lateralRate)));
        updateLook(amount, total, launchLength, landLength);
        setFrame(path.getFrame(cursor));
        pushHistoryIfMoved();
        pushTrailAtRear();
    }

    private void updateFacing(float amount, float desired) {
        if (!facingSet) {
            facing = desired;
            facingSet = true;
            turnSpeed = 0f;
            return;
        }

        float diff = Misc.getAngleDiff(facing, desired);
        float turn = Math.min(diff, turnRate * amount);
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
        layer = CampaignEngineLayers.TERRAIN_7;
        glowMult = (launchLength > 0f && traveled < launchLength + speed * 2f) || (landLength > 0f && remaining < landLength + speed * 2f) ? 1.5f : 1f;

        if (launchLength > 0f && traveled < launchLength) applyEnd(launchEnd, 1f - traveled / launchLength);
        if (landLength > 0f && remaining < landLength) applyEnd(landEnd, 1f - remaining / landLength);

        if (appearRamp < 1f) {
            appearRamp = Math.min(1f, appearRamp + amount);
            alpha *= appearAlpha + (1f - appearAlpha) * appearRamp;
            scale *= appearScale + (1f - appearScale) * appearRamp;
        }
    }

    private void applyEnd(End end, float progress) {
        if (end == End.PLANET) {
            scale *= 1f - 0.65f * progress;
            alpha *= 1f - progress;
            darken = Math.min(darken, 1f - 0.3f * progress);
            layer = CampaignEngineLayers.TERRAIN_3;
        } else if (end == End.STATION) {
            scale *= 1f - 0.3f * progress;
            alpha *= 1f - progress;
            layer = CampaignEngineLayers.BELOW_STATIONS;
        }
    }

    private float getAvoidanceTarget(ShuttleFleetScan scan) {
        float nx = -tangent.y;
        float ny = tangent.x;
        float best = 0f;

        for (int i = 0; i < scan.count; i++) {
            if (!scan.obstacle[i] && !(fleeing && scan.isHostileThreat(i, faction))) continue;

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

    private void setFrame(SectorEntityToken next) {
        if (next == frame) return;

        float dx = getFrameX();
        float dy = getFrameY();
        frame = next;
        dx -= getFrameX();
        dy -= getFrameY();

        for (int i = 0; i < TRAIL_POINTS; i++) {
            trailX[i] += dx;
            trailY[i] += dy;
        }

        for (int i = 0; i < HISTORY_POINTS; i++) {
            historyX[i] += dx;
            historyY[i] += dy;
        }
    }

    private float getFrameX() {
        return frame != null ? frame.getLocation().x : 0f;
    }

    private float getFrameY() {
        return frame != null ? frame.getLocation().y : 0f;
    }

    public void render(float alphaMult, SpriteAPI glow) {
        if (delay > 0f || down) return;

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
