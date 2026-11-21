package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.Global;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Arrays;
import java.util.Random;

public class SearchlightRenderer {

    public static final float RIM_PEAK_RADIUS = 0.9f;
    public static final float RIM_INNER_WIDTH = 0.36f;
    public static final float RIM_OUTER_WIDTH = 0.22f;
    public static final float RIM_HALF_ARC = 1.15f;
    public static final float SPIKE_RING_RADIUS = 46f;
    public static final float SPIKE_BAND = 26f;
    public static final float SPIKE_DIST_SHARE = 0.025f;
    public static final float SPIKE_MIN_LENGTH = 15f;
    public static final float SPIKE_MAX_LENGTH = 80f;
    public static final float SPIKE_MIN_ARC = 10f;
    public static final float SPIKE_MAX_ARC = 50f;
    public static final float SPIKE_BASE_ALPHA = 0.05f;
    public static final float SPIKE_LIFT_ALPHA = 0.3f;
    public static final float SPIKE_LIFT_LENGTH = 30f;
    public static final float SPIKE_MAX_LIFT = 0.75f;
    public static final float SPIKE_PULSE = 4f;
    public static final float SPIKE_TINT_GAIN = 2f;
    public static final Color SPIKE_BASE = new Color(205, 215, 225);
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
    private static final int SPIKE_STEPS = 360;
    private static final float[] SPIKE_LENGTH = new float[SPIKE_STEPS];
    private static final float[] SPIKE_RED = new float[SPIKE_STEPS];
    private static final float[] SPIKE_GREEN = new float[SPIKE_STEPS];
    private static final float[] SPIKE_BLUE = new float[SPIKE_STEPS];
    private static final float[] SPIKE_WEIGHT = new float[SPIKE_STEPS];
    private static final float[] SPIKE_STREAK = new float[SPIKE_STEPS * 2];

    // no kink in either falloff, a kink shows as a line once a spot fills the screen
    static {
        for (int i = 0; i < GLOW_RADII.length; i++) GLOW_ALPHAS[i] = (1f - GLOW_RADII[i]) * (1f - GLOW_RADII[i]);

        Random random = new Random(7);
        for (int i = 0; i < SPIKE_STREAK.length; i++) SPIKE_STREAK[i] = random.nextFloat() * 6.2832f;

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

    // neutrino detector style band around the tower, pushed out toward each light added since clearSpikes
    public static void clearSpikes() {
        Arrays.fill(SPIKE_LENGTH, 0f);
        Arrays.fill(SPIKE_RED, 0f);
        Arrays.fill(SPIKE_GREEN, 0f);
        Arrays.fill(SPIKE_BLUE, 0f);
        Arrays.fill(SPIKE_WEIGHT, 0f);
    }

    // one bump per light like GraviticScanData, (1 - |offset / half|)^2 wide, longer for a farther light; angle in radians
    public static void addSpike(float angle, float dist, float spotRadius, Color color, float weight) {
        if (weight <= 0f || dist <= 0f) return;

        float span = (float) Math.toDegrees(Math.asin(Math.min(1f, spotRadius / dist)));
        int half = (int) Math.ceil(Math.max(SPIKE_MIN_ARC, Math.min(SPIKE_MAX_ARC, span)) / 2f);
        float length = Math.max(SPIKE_MIN_LENGTH, Math.min(SPIKE_MAX_LENGTH, dist * SPIKE_DIST_SHARE));
        int mid = Math.round((float) Math.toDegrees(angle));

        for (int i = -half; i <= half; i++) {
            float k = 1f - Math.abs(i / (float) half);
            float v = k * k * weight;
            int index = Math.floorMod(mid + i, SPIKE_STEPS);
            SPIKE_LENGTH[index] += length * v;
            SPIKE_RED[index] += color.getRed() / 255f * v;
            SPIKE_GREEN[index] += color.getGreen() / 255f * v;
            SPIKE_BLUE[index] += color.getBlue() / 255f * v;
            SPIKE_WEIGHT[index] += v;
        }
    }

    public static void drawSpikes(Vector2f center, float time, float alpha) {
        if (alpha <= 0f) return;

        begin();
        for (int pass = 0; pass < 2; pass++) {
            float phase = pass == 0 ? time * 1.7f : -time * 1.3f;
            float mult = pass == 0 ? 29f : 17f;
            float passAlpha = pass == 0 ? 1f : 0.7f;

            for (int part = 0; part < 2; part++) {
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for (int step = 0; step <= SPIKE_STEPS; step++) {
                    int i = step % SPIKE_STEPS;
                    double theta = Math.toRadians(i);
                    float cos = (float) Math.cos(theta);
                    float sin = (float) Math.sin(theta);
                    float pulse = (float) Math.sin(theta * mult + phase) * SPIKE_PULSE;
                    float streakPhase = SPIKE_STREAK[pass * SPIKE_STEPS + i];
                    float streak = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(i * 0.9f + streakPhase + time * (0.4f + streakPhase / 6.2832f)));

                    float length = SPIKE_LENGTH[i];
                    float inner = SPIKE_RING_RADIUS - pulse * 0.1f;
                    float outer = SPIKE_RING_RADIUS + SPIKE_BAND - pulse + length;
                    float peak = inner + (outer - inner) * Math.min(0.5f, SPIKE_BAND * 0.5f / (SPIKE_BAND + length));
                    float lift = Math.min(length / SPIKE_LIFT_LENGTH, SPIKE_MAX_LIFT);
                    float a = Math.min(1f, alpha * (SPIKE_BASE_ALPHA + SPIKE_LIFT_ALPHA * lift) * streak * passAlpha);

                    float weight = SPIKE_WEIGHT[i];
                    float mix = Math.min(1f, weight * SPIKE_TINT_GAIN);
                    float r = SPIKE_BASE.getRed() / 255f, g = SPIKE_BASE.getGreen() / 255f, b = SPIKE_BASE.getBlue() / 255f;
                    if (weight > 0f) {
                        r += (SPIKE_RED[i] / weight - r) * mix;
                        g += (SPIKE_GREEN[i] / weight - g) * mix;
                        b += (SPIKE_BLUE[i] / weight - b) * mix;
                    }

                    float from = part == 0 ? inner : peak;
                    float to = part == 0 ? peak : outer;
                    GL11.glColor4f(r, g, b, part == 0 ? 0f : a);
                    GL11.glVertex2f(center.x + cos * from, center.y + sin * from);
                    GL11.glColor4f(r, g, b, part == 0 ? a : 0f);
                    GL11.glVertex2f(center.x + cos * to, center.y + sin * to);
                }
                GL11.glEnd();
            }
        }
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
