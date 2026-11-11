package indevo.ambience.shuttletraffic;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// trail and history points are stored relative to a virtual frame that moves like the path point under the craft
public class ShuttleTrail {

    public static final int TRAIL_POINTS = 16;
    public static final float TRAIL_SPACING = 8f;
    public static final int HISTORY_POINTS = 16;
    public static final float HISTORY_SPACING = 4f;

    private float frameX = 0f;
    private float frameY = 0f;
    private float frameDX = 0f;
    private float frameDY = 0f;

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

    public void clear() {
        trailCount = 0;
        historyCount = 0;
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

    public void pushTrailIfMoved(float x, float y) {
        if (trailCount == 0) {
            pushTrail(x, y);
            return;
        }

        int last = (trailHead - 1 + TRAIL_POINTS) % TRAIL_POINTS;
        float tx = x - (trailX[last] + frameX);
        float ty = y - (trailY[last] + frameY);
        if (tx * tx + ty * ty >= TRAIL_SPACING * TRAIL_SPACING) pushTrail(x, y);
    }

    public void pushTrail(float x, float y) {
        trailX[trailHead] = x - frameX;
        trailY[trailHead] = y - frameY;
        trailHead = (trailHead + 1) % TRAIL_POINTS;
        trailCount = Math.min(TRAIL_POINTS, trailCount + 1);
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

    public void render(float rearX, float rearY, int maxPoints, float width, Color color, boolean additive, float alphaMult) {
        int points = Math.min(trailCount, maxPoints);
        if (points < 1 || alphaMult <= 0.01f) return;

        drawX[0] = rearX;
        drawY[0] = rearY;
        int count = 1;
        for (int i = 1; i <= points; i++) {
            int index = (trailHead - i + TRAIL_POINTS) % TRAIL_POINTS;
            drawX[count] = trailX[index] + frameX;
            drawY[count] = trailY[index] + frameY;
            count++;
        }

        if (count < 2) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float baseAlpha = color.getAlpha() / 255f * alphaMult;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
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

                float fade = 1f - i / (float) (count - 1);
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

        GL11.glPopAttrib();
    }
}
