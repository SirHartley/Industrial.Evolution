package indevo.skillshot.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import indevo.skillshot.GuideLineStyle;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SkillshotUtils {

    protected static final float JOIN_TOLERANCE = 0.01f;

    public static Vector2f getCursorWorldPosition() {
        return new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));
    }

    public static void drawLines(List<Vector2f> vertices, Color colour, float alpha, float widthPx) {
        drawLines(vertices, colour, alpha, widthPx, GuideLineStyle.SOLID);
    }

    public static void drawLines(List<Vector2f> vertices, Color colour, float alpha, float widthPx, GuideLineStyle style) {
        if (vertices == null || vertices.size() < 2) return;

        drawCut(cutDashes(vertices, style), colour, alpha, widthPx);
    }

    public static void drawDashedLines(List<Vector2f> vertices, Color colour, float alpha, float widthPx,
                                       float dashWorld, float gapWorld) {
        if (vertices == null || vertices.size() < 2) return;

        drawCut(cutDashes(vertices, dashWorld, gapWorld), colour, alpha, widthPx);
    }

    protected static void drawCut(List<Vector2f> toDraw, Color colour, float alpha, float widthPx) {
        if (toDraw == null || toDraw.size() < 2) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(widthPx);

        GL11.glColor4f(colour.getRed() / 255f, colour.getGreen() / 255f, colour.getBlue() / 255f,
                alpha * (colour.getAlpha() / 255f));

        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i + 1 < toDraw.size(); i += 2) {
            Vector2f from = toDraw.get(i);
            Vector2f to = toDraw.get(i + 1);

            GL11.glVertex2f(from.x, from.y);
            GL11.glVertex2f(to.x, to.y);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    protected static List<Vector2f> cutDashes(List<Vector2f> vertices, GuideLineStyle style) {
        if (style == null || style == GuideLineStyle.SOLID) return vertices;

        ViewportAPI viewport = Global.getSector().getViewport();
        if (viewport == null) return vertices;

        return cutDashes(vertices,
                viewport.convertScreenWidthToWorldWidth(style.getSegmentPx()),
                viewport.convertScreenWidthToWorldWidth(style.getGapPx()));
    }

    protected static List<Vector2f> cutDashes(List<Vector2f> vertices, float dash, float gap) {
        if (dash <= 0f || gap <= 0f) return vertices;

        float period = dash + gap;

        List<Vector2f> dashes = new ArrayList<>();
        Vector2f previousEnd = null;
        float phase = 0f;

        for (int i = 0; i + 1 < vertices.size(); i += 2) {
            Vector2f from = vertices.get(i);
            Vector2f to = vertices.get(i + 1);

            // a break in the path resets the phase, connected segments keep it so dashes stay even
            if (previousEnd == null || !isSamePoint(previousEnd, from)) phase = 0f;

            phase = cutSegment(dashes, from, to, dash, period, phase);
            previousEnd = to;
        }

        return dashes;
    }

    protected static float cutSegment(List<Vector2f> dashes, Vector2f from, Vector2f to,
                                      float dash, float period, float phase) {
        Vector2f along = Vector2f.sub(to, from, null);
        float length = along.length();
        if (length <= 0f) return phase;

        along.scale(1f / length);

        float end = phase + length;

        for (float start = phase - phase % period; start < end; start += period) {
            float dashFrom = Math.max(phase, start);
            float dashTo = Math.min(end, start + dash);
            if (dashTo <= dashFrom) continue;

            dashes.add(pointAlong(from, along, dashFrom - phase));
            dashes.add(pointAlong(from, along, dashTo - phase));
        }

        // kept inside one period so a long path keeps float precision
        return end % period;
    }

    protected static Vector2f pointAlong(Vector2f from, Vector2f direction, float distance) {
        return new Vector2f(from.x + direction.x * distance, from.y + direction.y * distance);
    }

    protected static boolean isSamePoint(Vector2f a, Vector2f b) {
        return Math.abs(a.x - b.x) <= JOIN_TOLERANCE && Math.abs(a.y - b.y) <= JOIN_TOLERANCE;
    }
}
