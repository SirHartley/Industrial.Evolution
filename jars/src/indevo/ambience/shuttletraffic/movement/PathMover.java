package indevo.ambience.shuttletraffic.movement;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleFleetScan;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.ShuttleTrail;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class PathMover {

    private static final float AVOID_MARGIN = 40f;
    private static final float AVOID_EASE = 20f;
    private static final float DOCK_CHECK_DISTANCE = 40f;

    private final Shuttle shuttle;

    private ShuttlePath path;
    private Shuttle.End launchEnd;
    private Shuttle.End landEnd;
    private float jitter;
    private final ShuttlePath.Cursor cursor = new ShuttlePath.Cursor();
    private float traveled = 0f;
    private float rebuildTimer = 0f;

    private float avoidOffset = 0f;
    private float lastLateral = 0f;
    private float sideBias = 0f;

    // entity the craft mostly moves with, join paths start relative to it
    private SectorEntityToken frame;
    private ShuttlePath.Segment frameSegment;
    private final Vector2f frameStartLoc = new Vector2f();
    private final Vector2f frameEndLoc = new Vector2f();

    private final Vector2f base = new Vector2f();
    private final Vector2f tangent = new Vector2f();
    private final Vector2f tmp = new Vector2f();

    public PathMover(Shuttle shuttle, ShuttlePath path, Shuttle.End launchEnd, Shuttle.End landEnd, float jitter) {
        this.shuttle = shuttle;
        this.path = path;
        this.launchEnd = launchEnd;
        this.landEnd = landEnd;
        this.jitter = jitter;
        if (path != null) path.rebuild();
    }

    public void setPath(ShuttlePath newPath, Shuttle.End newLaunch, Shuttle.End newLand) {
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

    // a follower takes over the leader's path at its own slot and spreads out before dodging on its own
    public void join(PathMover lead, SectorEntityToken leadFrame, float slotBack, float slotSide, Random random) {
        path = lead.path;
        launchEnd = lead.launchEnd;
        landEnd = lead.landEnd;
        frame = leadFrame;
        frameSegment = null;
        path.place(cursor, Math.max(0f, path.getTraveled(lead.cursor) - slotBack));
        traveled = path.getTraveled(cursor);
        rebuildTimer = random.nextFloat();
        avoidOffset = slotSide;
        lastLateral = slotSide;
        sideBias = slotSide * 4f;
    }

    public void warmStart(float fraction) {
        if (path == null) return;

        ShuttleTrail trail = shuttle.getTrail();
        traveled = path.getLength() * fraction;
        path.place(cursor, traveled);
        path.getPoint(cursor, base);
        path.getTangent(cursor, tangent);
        shuttle.pos.set(base);
        shuttle.setFacing(Misc.getAngleInDegrees(tangent));
        moveFrame();

        for (int i = ShuttleTrail.HISTORY_POINTS; i >= 1; i--) {
            path.getPoint(Math.max(0f, traveled - i * ShuttleTrail.HISTORY_SPACING), tmp);
            trail.pushHistory(tmp.x, tmp.y);
        }

        float speed = shuttle.getSpeed(1f) * path.getSpeedMult(cursor);
        float seconds = trail.getSeconds();
        for (int i = ShuttleTrail.TRAIL_POINTS - 1; i >= 0; i--) {
            float age = seconds * i / (ShuttleTrail.TRAIL_POINTS - 1);
            path.getPoint(Math.max(0f, traveled - speed * age), tmp);
            trail.pushTrail(tmp.x, tmp.y, age);
        }
    }

    public void advance(float amount, ShuttleTraffic traffic) {
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

    public SectorEntityToken getFrame() {
        return frame;
    }

    public Shuttle.End getLaunchEnd() {
        return launchEnd;
    }

    public Shuttle.End getLandEnd() {
        return landEnd;
    }

    public void restartLap() {
        cursor.reset();
        traveled = 0f;
    }

    // retry runs the same frame on a path that just replaced the old one, so the craft never stalls a frame
    private void advancePath(float amount, ShuttleTraffic traffic, boolean retry) {
        float total = path.getLength();
        traveled = path.getTraveled(cursor);
        float launchLength = getEndLength(launchEnd, shuttle.getFrom(), total);
        float landLength = getEndLength(landEnd, shuttle.getTo(), total);
        float remaining = total - traveled;
        boolean docking = landEnd == Shuttle.End.STATION && shuttle.getTo() != null && remaining <= landLength + DOCK_CHECK_DISTANCE;

        if (docking && shuttle.holdForPad(traffic)) {
            if (retry) advancePath(amount, traffic, false);
            return;
        }

        float ease = 1f;
        if (launchLength > 0f && traveled < launchLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(traveled / launchLength));
        if (landLength > 0f && remaining < landLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(remaining / landLength));

        float speed = shuttle.getSpeed(ease) * path.getSpeedMult(cursor);
        shuttle.setThrottle(speed);
        if (path.move(cursor, speed * amount)) {
            shuttle.onPathEnd(traffic);
            if (retry && !shuttle.isDone() && !shuttle.isDown()) advancePath(amount, traffic, false);
            return;
        }

        traveled = path.getTraveled(cursor);

        updatePosition(amount, traffic, total, launchLength, landLength, speed);
    }

    private void updatePosition(float amount, ShuttleTraffic traffic, float total, float launchLength, float landLength, float speed) {
        path.getPoint(cursor, base);
        path.getTangent(cursor, tangent);

        float fraction = total > 0f ? traveled / total : 1f;
        float envelope = (float) Math.sin(Math.PI * fraction);
        float avoidEnvelope = Math.max(0f, Math.min(1f, Math.min(fraction, 1f - fraction) / 0.15f));
        float avoid = shuttle.avoids() ? getAvoidanceTarget(traffic.getFleetScan()) : 0f;
        if (avoid != 0f && !shuttle.getFollowers().isEmpty()) shuttle.disperse(traffic.getRandom());
        float target = (avoid + sideBias) * avoidEnvelope;

        float step = AVOID_EASE * amount;
        if (avoidOffset < target) avoidOffset = Math.min(target, avoidOffset + step);
        else avoidOffset = Math.max(target, avoidOffset - step);

        float lateral = jitter * envelope + avoidOffset + shuttle.getFleeWobble();
        float lateralRate = amount > 0f ? (lateral - lastLateral) / amount : 0f;
        lastLateral = lateral;
        shuttle.pos.set(base.x - tangent.y * lateral, base.y + tangent.x * lateral);

        // face along the path, not the world step, so orbiting anchors do not swing the sprite and trail
        shuttle.updateFacing(amount, (float) Math.toDegrees(Math.atan2(tangent.y * speed + tangent.x * lateralRate, tangent.x * speed - tangent.y * lateralRate)));
        shuttle.updateLook(amount, path.getScaleMult(cursor), traveled, total - traveled, launchLength, landLength);
        moveFrame();
        shuttle.getTrail().pushHistoryIfMoved(shuttle.pos.x, shuttle.pos.y);
        shuttle.pushTrailAtRear();
    }

    private float getAvoidanceTarget(ShuttleFleetScan scan) {
        float nx = -tangent.y;
        float ny = tangent.x;
        float best = 0f;
        boolean fleeing = shuttle.isFleeing();

        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            if (!entry.obstacle && !(fleeing && entry.isHostileThreat(shuttle.faction))) continue;

            float dx = base.x - entry.x;
            float dy = base.y - entry.y;
            float radius = entry.radius + AVOID_MARGIN;
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
        shuttle.getTrail().shift(dx, dy);
    }

    private static float getEndLength(Shuttle.End end, ShuttleAnchor anchor, float total) {
        if (end == Shuttle.End.NONE || anchor == null) return 0f;
        return Math.min(anchor.getLandingLength(), total * 0.4f);
    }

    private static float smooth(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }
}
