package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class Shuttle {

    public enum End {
        NONE,
        PLANET,
        STATION
    }

    private static final int TRAIL_POINTS = 16;
    private static final float TRAIL_SPACING = 8f;
    private static final float TRAIL_WIDTH = 3f;
    private static final float TURN_RATE = 90f;
    private static final float AVOID_MARGIN = 40f;
    private static final float AVOID_EASE = 40f;
    private static final float DOCK_CHECK_DISTANCE = 40f;
    private static final Color RED_LIGHT = new Color(255, 60, 50);
    private static final Color GREEN_LIGHT = new Color(60, 255, 90);

    public final ShuttleHull hull;
    public final ShuttleRoute route;
    public final FactionAPI faction;
    public ShuttleAnchor from;
    public ShuttleAnchor to;
    public ShuttlePath path;
    public End launchEnd;
    public End landEnd;
    public float speedMult;
    public float jitter;
    public float turnRate = TURN_RATE;
    public boolean done = false;

    public final Vector2f pos = new Vector2f();
    public float facing = 0f;
    public float scale = 1f;
    public float alpha = 1f;
    public CampaignEngineLayers layer = CampaignEngineLayers.TERRAIN_7;

    private float traveled = 0f;
    private float avoidOffset = 0f;
    private float darken = 1f;
    private float glowMult = 1f;
    private float rebuildTimer = 0f;
    private boolean facingSet = false;
    private boolean cleared = false;
    private boolean holding = false;
    private final float lightPhase;

    private final Vector2f base = new Vector2f();
    private final Vector2f tangent = new Vector2f();
    private final Vector2f prevPos = new Vector2f();
    private final Vector2f tmp = new Vector2f();
    private final float[] trailX = new float[TRAIL_POINTS];
    private final float[] trailY = new float[TRAIL_POINTS];
    private final float[] drawX = new float[TRAIL_POINTS + 1];
    private final float[] drawY = new float[TRAIL_POINTS + 1];
    private int trailHead = 0;
    private int trailCount = 0;

    public Shuttle(ShuttleHull hull, ShuttleRoute route, FactionAPI faction, ShuttleAnchor from, ShuttleAnchor to, ShuttlePath path,
                   End launchEnd, End landEnd, float speedMult, float jitter, float lightPhase) {
        this.hull = hull;
        this.route = route;
        this.faction = faction;
        this.from = from;
        this.to = to;
        this.path = path;
        this.launchEnd = launchEnd;
        this.landEnd = landEnd;
        this.speedMult = speedMult;
        this.jitter = jitter;
        this.lightPhase = lightPhase;
        path.rebuild();
    }

    public void warmStart(float fraction) {
        traveled = path.getLength() * fraction;
        path.getPoint(traveled, base);
        path.getTangent(traveled, tangent);
        pos.set(base);
        prevPos.set(base);
        facing = Misc.getAngleInDegrees(tangent);
        facingSet = true;

        for (int i = TRAIL_POINTS; i >= 1; i--) {
            path.getPoint(Math.max(0f, traveled - i * TRAIL_SPACING), tmp);
            pushTrail(tmp.x, tmp.y);
        }
    }

    public void advance(float amount, ShuttleTrafficRenderer renderer) {
        rebuildTimer -= amount;
        if (rebuildTimer <= 0f) {
            path.rebuild();
            rebuildTimer = 1f;
        }

        float total = path.getLength();
        float launchLength = getEndLength(launchEnd, from, total);
        float landLength = getEndLength(landEnd, to, total);
        float remaining = total - traveled;

        if (landEnd == End.STATION && to != null && !cleared && remaining <= landLength + DOCK_CHECK_DISTANCE) {
            if (to.isPadBusy(renderer.getTime())) {
                startHolding();
                return;
            }

            cleared = true;
        }

        if (landEnd == End.STATION && to != null && remaining <= landLength + DOCK_CHECK_DISTANCE) to.padBusyUntil = renderer.getTime() + 1f;

        float ease = 1f;
        if (launchLength > 0f && traveled < launchLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(traveled / launchLength));
        if (landLength > 0f && remaining < landLength) ease = Math.min(ease, 0.35f + 0.65f * smooth(remaining / landLength));

        traveled += hull.getCruiseSpeed() * speedMult * ease * amount;

        if (traveled >= total) {
            if (holding) {
                finishHoldingLap(renderer);
                return;
            }

            done = true;
            return;
        }

        updatePosition(amount, renderer, total, launchLength, landLength);
    }

    public void redirect(ShuttleAnchor newTarget, ShuttleTrafficRenderer renderer) {
        to = newTarget;
        cleared = false;
        holding = false;

        float angle = Misc.getAngleInDegrees(newTarget.getLocation(), pos);
        ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(pos, Misc.getUnitVectorAtDegreeAngle(facing));
        ShuttlePath.Waypoint end = new ShuttlePath.PadWaypoint(newTarget, angle, true);
        float distance = Misc.getDistance(pos, newTarget.getLocation());
        float handle = Math.min(newTarget.getRadius() + 80f, distance * 0.45f);

        setPath(new ShuttlePath().add(new ShuttlePath.BezierSegment(start, end, Math.min(80f, distance * 0.3f), handle, 0f)),
                End.NONE, ShuttleTrafficRenderer.getEnd(newTarget));
    }

    public float getTraveled() {
        return traveled;
    }

    private void setPath(ShuttlePath newPath, End newLaunch, End newLand) {
        path = newPath;
        path.rebuild();
        rebuildTimer = 1f;
        traveled = 0f;
        launchEnd = newLaunch;
        landEnd = newLand;
        jitter = 0f;
        avoidOffset = 0f;
    }

    private void startHolding() {
        holding = true;

        Vector2f heading = Misc.getUnitVectorAtDegreeAngle(facing);
        float cx = pos.x - heading.y * ShuttleAnchor.HOLD_RADIUS;
        float cy = pos.y + heading.x * ShuttleAnchor.HOLD_RADIUS;
        float startAngle = Misc.getAngleInDegrees(new Vector2f(cx, cy), pos);

        setPath(new ShuttlePath().add(new ShuttlePath.CircleSegment(null, cx, cy, ShuttleAnchor.HOLD_RADIUS, startAngle, 360f, 0f)),
                End.NONE, End.NONE);
    }

    private void finishHoldingLap(ShuttleTrafficRenderer renderer) {
        if (to == null || !to.isUsable(renderer.getLocation())) {
            done = true;
            return;
        }

        if (to.isPadBusy(renderer.getTime())) {
            traveled = 0f;
            return;
        }

        to.padBusyUntil = renderer.getTime() + 1f;
        redirect(to, renderer);
        cleared = true;
    }

    private void updatePosition(float amount, ShuttleTrafficRenderer renderer, float total, float launchLength, float landLength) {
        path.getPoint(traveled, base);
        path.getTangent(traveled, tangent);

        float fraction = total > 0f ? traveled / total : 1f;
        float envelope = (float) Math.sin(Math.PI * fraction);
        float avoidEnvelope = Math.max(0f, Math.min(1f, Math.min(fraction, 1f - fraction) / 0.15f));
        float target = getAvoidanceTarget(renderer.getFleetScan()) * avoidEnvelope;

        float step = AVOID_EASE * amount;
        if (avoidOffset < target) avoidOffset = Math.min(target, avoidOffset + step);
        else avoidOffset = Math.max(target, avoidOffset - step);

        float lateral = jitter * envelope + avoidOffset;
        prevPos.set(pos);
        pos.set(base.x - tangent.y * lateral, base.y + tangent.x * lateral);

        float dx = pos.x - prevPos.x;
        float dy = pos.y - prevPos.y;
        float desired = dx * dx + dy * dy > 0.0001f && facingSet ? (float) Math.toDegrees(Math.atan2(dy, dx)) : Misc.getAngleInDegrees(tangent);
        if (!facingSet) {
            facing = desired;
            facingSet = true;
        } else {
            float diff = Misc.getAngleDiff(facing, desired);
            float turn = Math.min(diff, turnRate * amount);
            facing = Misc.normalizeAngle(facing + turn * Misc.getClosestTurnDirection(facing, desired));
        }

        updateLook(total, launchLength, landLength);

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        float rearX = pos.x - hx * hull.length * scale * 0.5f;
        float rearY = pos.y - hy * hull.length * scale * 0.5f;
        if (trailCount == 0) pushTrail(rearX, rearY);
        else {
            int last = (trailHead - 1 + TRAIL_POINTS) % TRAIL_POINTS;
            float tx = rearX - trailX[last];
            float ty = rearY - trailY[last];
            if (tx * tx + ty * ty >= TRAIL_SPACING * TRAIL_SPACING) pushTrail(rearX, rearY);
        }
    }

    private void updateLook(float total, float launchLength, float landLength) {
        float remaining = total - traveled;
        float speed = hull.getCruiseSpeed() * speedMult;

        scale = path.getScaleMult(traveled);
        alpha = 1f;
        darken = 1f;
        layer = CampaignEngineLayers.TERRAIN_7;
        glowMult = (launchLength > 0f && traveled < launchLength + speed * 2f) || (landLength > 0f && remaining < landLength + speed * 2f) ? 1.5f : 1f;

        if (launchLength > 0f && traveled < launchLength) applyEnd(launchEnd, 1f - traveled / launchLength);
        if (landLength > 0f && remaining < landLength) applyEnd(landEnd, 1f - remaining / landLength);
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

    private void pushTrail(float x, float y) {
        trailX[trailHead] = x;
        trailY[trailHead] = y;
        trailHead = (trailHead + 1) % TRAIL_POINTS;
        trailCount = Math.min(TRAIL_POINTS, trailCount + 1);
    }

    public void render(float alphaMult, SpriteAPI glow, boolean lights, float time) {
        float a = alpha * alphaMult;
        if (a <= 0.01f) return;

        renderTrail(a);

        SpriteAPI sprite = hull.sprite;
        sprite.setSize(hull.width * scale, hull.length * scale);
        sprite.setAngle(facing - 90f);
        sprite.setColor(darken >= 1f ? Color.WHITE : new Color((int) (255 * darken), (int) (255 * darken), (int) (255 * darken)));
        sprite.setAlphaMult(a);
        sprite.setNormalBlend();
        sprite.renderAtCenter(pos.x, pos.y);

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        float glowSize = Math.max(4f, hull.width * scale * 0.9f) * glowMult;
        glow.setSize(glowSize, glowSize);
        glow.setAngle(0f);
        glow.setColor(hull.glowColor);
        glow.setAdditiveBlend();
        glow.setAlphaMult(a * (0.8f + 0.2f * (float) Math.random()));
        glow.renderAtCenter(pos.x - hx * hull.length * scale * 0.5f, pos.y - hy * hull.length * scale * 0.5f);

        if (lights && ((time + lightPhase) % 1f) < 0.15f) {
            float half = hull.width * scale * 0.5f;
            glow.setSize(3f, 3f);
            glow.setAlphaMult(a);
            glow.setColor(RED_LIGHT);
            glow.renderAtCenter(pos.x - hy * half, pos.y + hx * half);
            glow.setColor(GREEN_LIGHT);
            glow.renderAtCenter(pos.x + hy * half, pos.y - hx * half);
        }
    }

    private void renderTrail(float alphaMult) {
        if (trailCount < 1) return;

        float hx = (float) Math.cos(Math.toRadians(facing));
        float hy = (float) Math.sin(Math.toRadians(facing));
        drawX[0] = pos.x - hx * hull.length * scale * 0.5f;
        drawY[0] = pos.y - hy * hull.length * scale * 0.5f;
        int count = 1;
        for (int i = 1; i <= trailCount; i++) {
            int index = (trailHead - i + TRAIL_POINTS) % TRAIL_POINTS;
            drawX[count] = trailX[index];
            drawY[count] = trailY[index];
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
                float width = TRAIL_WIDTH * 0.5f * fade;
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

    private static float getEndLength(End end, ShuttleAnchor anchor, float total) {
        if (end == End.NONE || anchor == null) return 0f;
        return Math.min(anchor.getLandingLength(), total * 0.4f);
    }

    private static float smooth(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }
}
