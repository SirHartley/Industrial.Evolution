package indevo.ambience.shuttletraffic;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// trail and history points are stored relative to a virtual frame that moves like the path point under the craft
// the trail is where the rear was during the last seconds, so its length follows speed; the history is by distance for formation slots
public class ShuttleTrail {

    public static final int TRAIL_POINTS = 16;
    public static final int HISTORY_POINTS = 16;
    public static final float HISTORY_SPACING = 4f;
    // one point past the cut so the tail can be cut between two samples
    private static final int TRAIL_SLOTS = TRAIL_POINTS + 1;

    private final float seconds;
    private final float interval;
    private float sincePush = 0f;

    private float frameX = 0f;
    private float frameY = 0f;
    private float frameDX = 0f;
    private float frameDY = 0f;

    private final float[] trailX = new float[TRAIL_SLOTS];
    private final float[] trailY = new float[TRAIL_SLOTS];
    private final float[] trailAge = new float[TRAIL_SLOTS];
    private final float[] drawX = new float[TRAIL_SLOTS + 1];
    private final float[] drawY = new float[TRAIL_SLOTS + 1];
    private final float[] drawAge = new float[TRAIL_SLOTS + 1];
    private int trailHead = 0;
    private int trailCount = 0;

    private final float[] historyX = new float[HISTORY_POINTS];
    private final float[] historyY = new float[HISTORY_POINTS];
    private int historyHead = 0;
    private int historyCount = 0;

    public ShuttleTrail(float seconds) {
        this.seconds = seconds;
        this.interval = seconds / (TRAIL_POINTS - 1);
    }

    public void shift(float dx, float dy) {
        frameDX = dx;
        frameDY = dy;
        frameX += dx;
        frameY += dy;
    }

    public float getShiftX() {
        return frameDX;
    }

    public float getShiftY() {
        return frameDY;
    }

    public float getSeconds() {
        return seconds;
    }

    public void clear() {
        trailCount = 0;
        historyCount = 0;
        sincePush = 0f;
    }

    // ages every point, only called unpaused, so a paused trail holds still
    public void advance(float amount) {
        sincePush += amount;
        for (int i = 0; i < TRAIL_SLOTS; i++) trailAge[i] += amount;

        // the oldest point stays while the next one is younger, it ends the tail; a lone point goes at seconds too
        while (trailCount >= 1) {
            int next = (trailHead - trailCount + 1 + TRAIL_SLOTS) % TRAIL_SLOTS;
            int check = trailCount >= 2 ? next : (trailHead - 1 + TRAIL_SLOTS) % TRAIL_SLOTS;
            if (trailAge[check] < seconds) break;
            trailCount--;
        }
    }

    public void pushHistoryIfMoved(float x, float y) {
        if (historyCount == 0) {
            pushHistory(x, y);
            return;
        }

        int last = (historyHead - 1 + HISTORY_POINTS) % HISTORY_POINTS;
        float dx = x - (historyX[last] + frameX);
        float dy = y - (historyY[last] + frameY);
        if (dx * dx + dy * dy >= HISTORY_SPACING * HISTORY_SPACING) pushHistory(x, y);
    }

    public void pushHistory(float x, float y) {
        historyX[historyHead] = x - frameX;
        historyY[historyHead] = y - frameY;
        historyHead = (historyHead + 1) % HISTORY_POINTS;
        historyCount = Math.min(HISTORY_POINTS, historyCount + 1);
    }

    public void pushTrailIfDue(float x, float y) {
        if (trailCount > 0 && sincePush < interval) return;

        sincePush = trailCount == 0 ? 0f : Math.min(interval, sincePush - interval);
        pushTrail(x, y, 0f);
    }

    // oldest first: ages must not grow toward the head
    public void pushTrail(float x, float y, float age) {
        trailX[trailHead] = x - frameX;
        trailY[trailHead] = y - frameY;
        trailAge[trailHead] = age;
        trailHead = (trailHead + 1) % TRAIL_SLOTS;
        trailCount = Math.min(TRAIL_SLOTS, trailCount + 1);
    }

    // the point distance back along the recorded path from pos, and the direction toward pos there
    public void getHistoryPoint(float distance, Vector2f pos, float facing, Vector2f out, Vector2f dirOut) {
        float px = pos.x;
        float py = pos.y;
        float dirX = (float) Math.cos(Math.toRadians(facing));
        float dirY = (float) Math.sin(Math.toRadians(facing));
        float remaining = distance;

        for (int i = 1; i <= historyCount; i++) {
            int index = (historyHead - i + HISTORY_POINTS) % HISTORY_POINTS;
            float dx = px - (historyX[index] + frameX);
            float dy = py - (historyY[index] + frameY);
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
            px = historyX[index] + frameX;
            py = historyY[index] + frameY;
        }

        out.set(px - dirX * remaining, py - dirY * remaining);
        dirOut.set(dirX, dirY);
    }

    public float getLength(float rearX, float rearY) {
        return getLength(rearX, rearY, true);
    }

    public float getLength(float rearX, float rearY, boolean emitting) {
        int count = build(rearX, rearY, emitting);
        float length = 0f;
        for (int i = 1; i < count; i++) {
            float dx = drawX[i] - drawX[i - 1];
            float dy = drawY[i] - drawY[i - 1];
            length += (float) Math.sqrt(dx * dx + dy * dy);
        }
        return length;
    }

    // every render call sits between beginRender and endRender
    public static void beginRender() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
    }

    public static void endRender() {
        GL11.glPopAttrib();
    }

    // emitting false: engines off, the trail no longer reaches the craft and ages out where it was left
    public void render(float rearX, float rearY, float width, Color color, boolean additive, float alphaMult, boolean emitting) {
        if (trailCount < 1 || alphaMult <= 0.01f) return;

        int count = build(rearX, rearY, emitting);
        if (count < 2) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float baseAlpha = color.getAlpha() / 255f * alphaMult;

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);

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

                // a growing trail still tapers to nothing at its oldest point
                float fade = i == count - 1 ? 0f : 1f - drawAge[i] / seconds;
                float half = width * 0.5f * fade;
                float px = -dy / len * half * side;
                float py = dx / len * half * side;

                GL11.glColor4f(r, g, b, baseAlpha * fade);
                GL11.glVertex2f(drawX[i], drawY[i]);
                GL11.glColor4f(r, g, b, 0f);
                GL11.glVertex2f(drawX[i] + px, drawY[i] + py);
            }
            GL11.glEnd();
        }
    }

    // the live rear point when emitting, then the stored points newest first, the tail cut at exactly seconds
    private int build(float rearX, float rearY, boolean emitting) {
        int first = 1;
        if (emitting) {
            drawX[0] = rearX;
            drawY[0] = rearY;
            drawAge[0] = 0f;
        } else {
            int newest = (trailHead - 1 + TRAIL_SLOTS) % TRAIL_SLOTS;
            if (trailCount < 1 || trailAge[newest] >= seconds) return 0;
            drawX[0] = trailX[newest] + frameX;
            drawY[0] = trailY[newest] + frameY;
            drawAge[0] = trailAge[newest];
            first = 2;
        }
        int count = 1;

        for (int i = first; i <= trailCount; i++) {
            int index = (trailHead - i + TRAIL_SLOTS) % TRAIL_SLOTS;
            float x = trailX[index] + frameX;
            float y = trailY[index] + frameY;
            float age = trailAge[index];

            if (age >= seconds) {
                float prevAge = drawAge[count - 1];
                float t = age - prevAge > 0.0001f ? (seconds - prevAge) / (age - prevAge) : 1f;
                drawX[count] = drawX[count - 1] + (x - drawX[count - 1]) * t;
                drawY[count] = drawY[count - 1] + (y - drawY[count - 1]) * t;
                drawAge[count] = seconds;
                return count + 1;
            }

            drawX[count] = x;
            drawY[count] = y;
            drawAge[count] = age;
            count++;
        }

        return count;
    }
}
