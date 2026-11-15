package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.Global;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SearchlightRenderer {

    public static final float RIM_PEAK_RADIUS = 0.9f;
    public static final float RIM_INNER_WIDTH = 0.36f;
    public static final float RIM_OUTER_WIDTH = 0.22f;
    public static final float RIM_HALF_ARC = 1.15f;
    public static final float V_LENGTH = 330f;
    public static final float V_LENGTH_SHARE = 0.6f;
    public static final float V_START = 30f;
    public static final float V_WIDTH = 40f;
    public static final float V_MIN_HALF_ARC = 0.1f;
    public static final float V_MAX_HALF_ARC = 0.38f;
    public static final float V_LINE_ALPHA = 0.42f;
    public static final float V_PEAK_SHARE = 0.25f;
    public static final float V_FILL_ALPHA = 0.13f;
    public static final float V_FILL_SHARE = 0.8f;
    public static final float RING_START_SHARE = 0.15f;
    public static final float RING_GROW_SHARE = 0.95f;
    public static final float RING_ALPHA = 0.7f;
    public static final float RING_WIDTH = 2f;

    private static final int GLOW_SEGMENTS = 48;
    private static final int RIM_SEGMENTS = 32;
    private static final int FILL_SEGMENTS = 8;
    private static final int RING_SEGMENTS = 64;
    private static final float[] GLOW_RADII = {0f, 0.05f, 0.1f, 0.17f, 0.25f, 0.35f, 0.47f, 0.6f, 0.75f, 0.88f, 1f};
    private static final float[] GLOW_ALPHAS = new float[GLOW_RADII.length];
    private static final float[] RIM_SHARES = {-1f, -0.8f, -0.62f, -0.46f, -0.32f, -0.2f, -0.1f, 0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f};
    private static final float[] RIM_RADII = new float[RIM_SHARES.length];
    private static final float[] RIM_ALPHAS = new float[RIM_SHARES.length];
    private static final float[] V_ALONG = {0f, 0.08f, 0.16f, V_PEAK_SHARE, 0.4f, 0.55f, 0.7f, 0.85f, 1f};
    private static final float[] V_ALONG_ALPHA = new float[V_ALONG.length];
    private static final float[] V_ACROSS = {-1f, -0.75f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 0.75f, 1f};
    private static final float[] V_ACROSS_ALPHA = new float[V_ACROSS.length];

    // no kink in either falloff, a kink shows as a line once a spot fills the screen
    static {
        for (int i = 0; i < GLOW_RADII.length; i++) GLOW_ALPHAS[i] = (1f - GLOW_RADII[i]) * (1f - GLOW_RADII[i]);

        for (int i = 0; i < V_ALONG.length; i++) {
            float share = V_ALONG[i];
            V_ALONG_ALPHA[i] = share < V_PEAK_SHARE ? smooth(share / V_PEAK_SHARE) : 1f - smooth((share - V_PEAK_SHARE) / (1f - V_PEAK_SHARE));
        }

        for (int i = 0; i < V_ACROSS.length; i++) V_ACROSS_ALPHA[i] = 1f - smooth(Math.abs(V_ACROSS[i]));

        for (int i = 0; i < RIM_SHARES.length; i++) {
            float share = RIM_SHARES[i];
            float rise = smooth(1f + share);
            RIM_RADII[i] = RIM_PEAK_RADIUS + share * (share < 0f ? RIM_INNER_WIDTH : RIM_OUTER_WIDTH);
            RIM_ALPHAS[i] = share < 0f ? rise * rise * rise : 1f - smooth(share);
        }
    }

    // additive round glow, alpha at the centre, falls off to the radius
    public static void drawGlow(Vector2f loc, float radius, Color color, float alpha) {
        if (alpha <= 0f) return;

        begin();
        drawBand(loc, radius, GLOW_RADII, GLOW_ALPHAS, 0f, (float) Math.PI, false, GLOW_SEGMENTS, color, alpha);
        end();
    }

    // soft band on the edge of a spot, centred on angle (radians) and fading out toward both ends of its arc
    public static void drawRim(Vector2f loc, float radius, float angle, Color color, float alpha) {
        if (alpha <= 0f) return;

        begin();
        drawBand(loc, radius, RIM_RADII, RIM_ALPHAS, angle, RIM_HALF_ARC, true, RIM_SEGMENTS, color, alpha);
        end();
    }

    // two soft lines from the lamp toward the sides of a spot at dist with a faint fill between them, angle in radians
    public static void drawEmitter(Vector2f lamp, float angle, float dist, float spotRadius, Color color, float alpha) {
        if (alpha <= 0f || dist <= V_START) return;

        float half = (float) Math.asin(Math.min(1f, spotRadius / dist));
        half = Math.max(V_MIN_HALF_ARC, Math.min(V_MAX_HALF_ARC, half));
        float length = Math.min(V_LENGTH, dist * V_LENGTH_SHARE);
        if (length <= V_START) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        begin();

        for (int side = -1; side <= 1; side += 2) {
            float dirX = (float) Math.cos(angle + side * half);
            float dirY = (float) Math.sin(angle + side * half);

            for (int i = 0; i < V_ALONG.length - 1; i++) {
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for (int j = 0; j < V_ACROSS.length; j++) {
                    for (int end = i; end <= i + 1; end++) {
                        float along = V_START + (length - V_START) * V_ALONG[end];
                        float across = V_ACROSS[j] * V_WIDTH / 2f;
                        GL11.glColor4f(r, g, b, Math.min(1f, alpha * V_LINE_ALPHA * V_ALONG_ALPHA[end] * V_ACROSS_ALPHA[j]));
                        GL11.glVertex2f(lamp.x + dirX * along - dirY * across, lamp.y + dirY * along + dirX * across);
                    }
                }
                GL11.glEnd();
            }
        }

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, Math.min(1f, alpha * V_FILL_ALPHA));
        GL11.glVertex2f(lamp.x, lamp.y);
        GL11.glColor4f(r, g, b, 0f);
        for (int i = 0; i <= FILL_SEGMENTS; i++) {
            double rad = angle - half + 2f * half * i / FILL_SEGMENTS;
            GL11.glVertex2f(lamp.x + (float) Math.cos(rad) * length * V_FILL_SHARE, lamp.y + (float) Math.sin(rad) * length * V_FILL_SHARE);
        }
        GL11.glEnd();

        end();
    }

    // thin ring that runs out from the middle of a spot, progress 0 to 1; the line keeps its pixel width at every zoom
    public static void drawRing(Vector2f loc, float radius, float progress, Color color, float alpha) {
        float ringAlpha = alpha * RING_ALPHA * (1f - progress);
        if (ringAlpha <= 0f) return;

        float ringRadius = radius * (RING_START_SHARE + RING_GROW_SHARE * smooth(progress));

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(RING_WIDTH * Global.getSettings().getScreenScaleMult());

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, Math.min(1f, ringAlpha));
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double rad = 2f * Math.PI * i / RING_SEGMENTS;
            GL11.glVertex2f(loc.x + (float) Math.cos(rad) * ringRadius, loc.y + (float) Math.sin(rad) * ringRadius);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    public static float smooth(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    private static void begin() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private static void end() {
        GL11.glPopAttrib();
    }

    private static void drawBand(Vector2f loc, float radius, float[] radii, float[] alphas, float angle, float halfArc,
                                 boolean fadeEnds, int segments, Color color, float alpha) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        for (int i = 0; i < radii.length - 1; i++) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= segments; j++) {
                float share = 2f * j / segments - 1f;
                double rad = angle + halfArc * share;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);
                float fade = fadeEnds ? (float) Math.cos(share * Math.PI / 2f) : 1f;
                float weight = alpha * fade * fade;

                GL11.glColor4f(r, g, b, Math.min(1f, weight * alphas[i]));
                GL11.glVertex2f(loc.x + cos * radius * radii[i], loc.y + sin * radius * radii[i]);
                GL11.glColor4f(r, g, b, Math.min(1f, weight * alphas[i + 1]));
                GL11.glVertex2f(loc.x + cos * radius * radii[i + 1], loc.y + sin * radius * radii[i + 1]);
            }
            GL11.glEnd();
        }
    }
}
