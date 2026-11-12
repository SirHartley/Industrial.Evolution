package indevo.utils.ui;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.opengl.GL11;

import java.awt.*;

// gl primitives for UiCanvas painters, coordinates are virtual pixels with y up, x and y are the bottom left corner
public final class UiDraw {

    private UiDraw() {
    }

    public static void quad(float x, float y, float w, float h, Color c, float alpha) {
        if (w <= 0f || h <= 0f) return;

        begin(c, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(Math.round(x), Math.round(y));
        GL11.glVertex2f(Math.round(x), Math.round(y + h));
        GL11.glVertex2f(Math.round(x + w), Math.round(y + h));
        GL11.glVertex2f(Math.round(x + w), Math.round(y));
        GL11.glEnd();
        end();
    }

    public static void outline(float x, float y, float w, float h, Color c, float alpha) {
        quad(x, y, w, 1f, c, alpha);
        quad(x, y + h - 1f, w, 1f, c, alpha);
        quad(x, y + 1f, 1f, h - 2f, c, alpha);
        quad(x + w - 1f, y + 1f, 1f, h - 2f, c, alpha);
    }

    public static void dashed(float x, float y, float w, float h, Color c, float alpha) {
        float dash = 3f;
        for (float i = 0f; i < w; i += dash * 2f) {
            float len = Math.min(dash, w - i);
            quad(x + i, y, len, 1f, c, alpha);
            quad(x + i, y + h - 1f, len, 1f, c, alpha);
        }

        for (float i = 0f; i < h; i += dash * 2f) {
            float len = Math.min(dash, h - i);
            quad(x, y + i, 1f, len, c, alpha);
            quad(x + w - 1f, y + i, 1f, len, c, alpha);
        }
    }

    // fill fades from the given alpha at the left end to zero over len
    public static void fadeRight(float x, float y, float len, Color c, float alpha) {
        begin(c, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        color(c, alpha);
        GL11.glVertex2f(Math.round(x), Math.round(y));
        GL11.glVertex2f(Math.round(x), Math.round(y + 1f));
        color(c, 0f);
        GL11.glVertex2f(Math.round(x + len), Math.round(y + 1f));
        GL11.glVertex2f(Math.round(x + len), Math.round(y));
        GL11.glEnd();
        end();
    }

    public static void fadeLeft(float x, float y, float len, Color c, float alpha) {
        begin(c, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        color(c, 0f);
        GL11.glVertex2f(Math.round(x), Math.round(y));
        GL11.glVertex2f(Math.round(x), Math.round(y + 1f));
        color(c, alpha);
        GL11.glVertex2f(Math.round(x + len), Math.round(y + 1f));
        GL11.glVertex2f(Math.round(x + len), Math.round(y));
        GL11.glEnd();
        end();
    }

    // 12 unit tick or cross scaled to size, same shape as the edict requirement marks
    public static void mark(float x, float y, float size, boolean ok, Color c, float alpha) {
        float s = size / 12f;
        float top = y + size;

        begin(c, alpha);
        GL11.glLineWidth(2f);
        if (ok) {
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex2f(x + 1.5f * s, top - 6.5f * s);
            GL11.glVertex2f(x + 4.5f * s, top - 9.5f * s);
            GL11.glVertex2f(x + 10.5f * s, top - 2.5f * s);
        } else {
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(x + 2f * s, top - 2f * s);
            GL11.glVertex2f(x + 10f * s, top - 10f * s);
            GL11.glVertex2f(x + 10f * s, top - 2f * s);
            GL11.glVertex2f(x + 2f * s, top - 10f * s);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        end();
    }

    // contain fit inside the box, centred, at integer pixels
    public static void sprite(SpriteAPI sprite, float srcW, float srcH, float x, float y, float w, float h, Color tint, float alpha) {
        if (sprite == null || srcW <= 0f || srcH <= 0f) return;

        float scale = Math.min(w / srcW, h / srcH);
        float dw = Math.round(srcW * scale);
        float dh = Math.round(srcH * scale);

        sprite.setSize(dw, dh);
        sprite.setColor(tint);
        sprite.setAlphaMult(alpha);
        sprite.setNormalBlend();
        sprite.render(Math.round(x + (w - dw) / 2f), Math.round(y + (h - dh) / 2f));
    }

    public static float left(PositionAPI pos) {
        return Math.round(pos.getX());
    }

    public static float bottom(PositionAPI pos) {
        return Math.round(pos.getY());
    }

    private static void begin(Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        color(c, alpha);
    }

    private static void color(Color c, float alpha) {
        float a = c.getAlpha() / 255f * Math.max(0f, Math.min(1f, alpha));
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
    }

    private static void end() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
