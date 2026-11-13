package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.industries.artillery.utils.VisionBlockers;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

public class LightConeRenderer {

    public static final float DRAWN_ARC_MULT = 1.2f;
    public static final float DRAWN_LENGTH_MULT = 1.1f;
    public static final float EDGE_SOFT_SHARE = 0.5f;
    public static final float CORE_SHARE = 0.4f;
    public static final float CORE_BRIGHTNESS = 0.45f;
    public static final float END_SOFT_SHARE = 0.25f;
    public static final float SHADOW_SOFT_DISTANCE = 60f;
    public static final float NEAR_SOFT_SHARE = 0.04f;
    public static final float DISTANCE_FALLOFF = 0.6f;
    public static final float TEXTURE_SHARE = 0.35f;

    private static final float[] RINGS = {0f, 0.04f, 0.12f, 0.26f, 0.42f, 0.6f, 0.75f, 0.88f, 0.97f, 1f};
    private static final int ARC_SEGMENTS = 32;

    // additive light with no outline: a wide soft halo, a brighter middle and a faint moving texture on top
    // arc and length are the checked ones, the light is drawn a little past both so the checked edge sits inside the fade
    // every ray ends at the first blocker, so the light shows the shadow the sight check uses
    public static void draw(Vector2f loc, float angle, float arc, float length, List<VisionBlockers.Blocker> blockers,
                            Color color, float alpha, SpriteAPI texture, float scroll) {
        if (alpha <= 0f) return;

        float drawnArc = arc * DRAWN_ARC_MULT;
        float drawnLength = length * DRAWN_LENGTH_MULT;
        float start = angle - drawnArc / 2f;

        float[] cos = new float[ARC_SEGMENTS + 1];
        float[] sin = new float[ARC_SEGMENTS + 1];
        float[] reach = new float[ARC_SEGMENTS + 1];
        float[] across = new float[ARC_SEGMENTS + 1];

        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            float rayAngle = start + drawnArc * i / ARC_SEGMENTS;
            double rad = Math.toRadians(rayAngle);
            float fromMiddle = Math.abs(i - ARC_SEGMENTS / 2f) / (ARC_SEGMENTS / 2f);
            float core = Math.max(0f, 1f - fromMiddle * fromMiddle / (CORE_SHARE * CORE_SHARE));

            cos[i] = (float) Math.cos(rad);
            sin[i] = (float) Math.sin(rad);
            reach[i] = VisionBlockers.getRayLength(blockers, loc, rayAngle, drawnLength);
            across[i] = (1f - CORE_BRIGHTNESS) * smooth((1f - fromMiddle) / EDGE_SOFT_SHARE) + CORE_BRIGHTNESS * core * core;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        drawMesh(loc, drawnLength, cos, sin, reach, across, color, alpha * (1f - TEXTURE_SHARE), 0f, 0f, 0f);

        if (texture != null) {
            float meanWidth = drawnLength * (float) Math.toRadians(drawnArc) / 2f;
            float texPerUnit = texture.getTextureWidth() * texture.getTextureHeight() * texture.getHeight() / (texture.getWidth() * meanWidth);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            texture.bindTexture();
            drawMesh(loc, drawnLength, cos, sin, reach, across, color, alpha * TEXTURE_SHARE * 2f, texPerUnit, scroll * texture.getTextureWidth(), texture.getTextureHeight());
        }

        GL11.glPopAttrib();
    }

    private static void drawMesh(Vector2f loc, float length, float[] cos, float[] sin, float[] reach, float[] across,
                                 Color color, float alpha, float texPerUnit, float texOffset, float texHeight) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        for (int ring = 0; ring < RINGS.length - 1; ring++) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                // an open ray fades out over its last quarter, a shadowed one ends just short of what blocks it
                float soft = reach[i] >= length ? length * END_SOFT_SHARE : SHADOW_SOFT_DISTANCE;

                for (int step = 0; step < 2; step++) {
                    float dist = reach[i] * RINGS[ring + step];
                    float far = dist / length;
                    float light = smooth(far / NEAR_SOFT_SHARE) * (1f - DISTANCE_FALLOFF * far) * smooth((reach[i] - dist) / soft);

                    GL11.glColor4f(r, g, b, Math.min(1f, alpha * across[i] * light));
                    GL11.glTexCoord2f(dist * texPerUnit - texOffset, texHeight * i / ARC_SEGMENTS);
                    GL11.glVertex2f(loc.x + cos[i] * dist, loc.y + sin[i] * dist);
                }
            }
            GL11.glEnd();
        }
    }

    private static float smooth(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }
}
