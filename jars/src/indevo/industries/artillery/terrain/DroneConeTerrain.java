package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.utils.VisionBlockers;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.List;

public class DroneConeTerrain extends BaseRingTerrain {

    public static final String TERRAIN_ID = "IndEvo_drone_cones";
    public static final Color LIGHT_COLOR = new Color(255, 236, 190);
    public static final Color LOCK_COLOR = new Color(255, 110, 70);
    public static final Color ZONE_COLOR = new Color(255, 200, 80);

    public static final float CONE_ALPHA = 0.3f;
    public static final float LOCK_ALPHA_MULT = 1.4f;
    public static final float LOCK_PULSE = 0.25f;
    public static final float DRAWN_ARC_MULT = 1.2f;
    public static final float DRAWN_LENGTH_MULT = 1.1f;
    public static final float EDGE_SOFT_SHARE = 0.45f;
    public static final float END_SOFT_SHARE = 0.25f;
    public static final float NEAR_SOFT_SHARE = 0.04f;
    public static final float DISTANCE_FALLOFF = 0.6f;
    public static final float SCROLL_SPEED = 0.02f;

    public static final float ZONE_ALPHA = 0.25f;
    public static final float ZONE_MAP_ALPHA = 0.4f;
    public static final float ZONE_CENTER_ALPHA_MULT = 0.2f;
    public static final float ZONE_OUTLINE_ALPHA_MULT = 1.5f;

    public static final float RENDER_RANGE = 10000000f;
    private static final float[] RINGS = {0f, 0.04f, 0.12f, 0.26f, 0.42f, 0.6f, 0.75f, 0.88f, 1f};
    private static final int ARC_SEGMENTS = 28;
    private static final int ZONE_SEGMENTS = 48;

    protected transient SpriteAPI texture;
    protected transient float scroll;

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7A);
        texture = Global.getSettings().getSprite("terrain", "pulsar");
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        scroll = (scroll + amount * SCROLL_SPEED) % 1f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        DroneNetScript net = DroneNetScript.get(entity.getContainingLocation());
        if (net == null) return;

        float alpha = viewport.getAlphaMult() * net.getVisibility();
        if (alpha <= 0f) return;

        drawCones(net, alpha, viewport);
        drawZones(net, 1f, alpha * ZONE_ALPHA, viewport);
    }

    // map space is world space times factor, same as the vanilla terrain map icons
    @Override
    public void renderOnMap(float factor, float alphaMult) {
        DroneNetScript net = DroneNetScript.get(entity.getContainingLocation());
        if (net == null) return;

        drawZones(net, factor, alphaMult * net.getVisibility() * ZONE_MAP_ALPHA, null);
    }

    private void drawCones(DroneNetScript net, float alpha, ViewportAPI viewport) {
        float length = net.getConeLength() * DRAWN_LENGTH_MULT;
        float pulse = 1f - LOCK_PULSE * Global.getSector().getCampaignUI().getSharedFader().getBrightness();
        List<VisionBlockers.Blocker> blockers = net.getBlockers();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        texture.bindTexture();

        for (DroneNetScript.Spotter spotter : net.getSpotters()) {
            if (spotter.entity == null) continue;

            Vector2f loc = spotter.entity.getLocation();
            if (!viewport.isNearViewport(loc, length)) continue;

            Color color = Misc.interpolateColor(LIGHT_COLOR, LOCK_COLOR, spotter.alert);
            float lock = 1f + (LOCK_ALPHA_MULT * pulse - 1f) * spotter.alert;
            drawCone(loc, spotter.coneAngle, length, blockers, color, alpha * CONE_ALPHA * lock);
        }

        GL11.glPopAttrib();
    }

    // a textured mesh that fades to nothing at both sides, at the lamp and at the far end
    // every ray ends at the first planet or chaff cloud, so the light shows the shadow the check uses
    private void drawCone(Vector2f loc, float angle, float length, List<VisionBlockers.Blocker> blockers, Color color, float alpha) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float arc = DroneNetScript.CONE_ARC * DRAWN_ARC_MULT;
        float start = angle - arc / 2f;

        float texWidth = texture.getTextureWidth();
        float texHeight = texture.getTextureHeight();
        float meanWidth = length * (float) Math.toRadians(arc) / 2f;
        float texPerUnit = texWidth * texHeight * texture.getHeight() / (texture.getWidth() * meanWidth);

        float[] cos = new float[ARC_SEGMENTS + 1];
        float[] sin = new float[ARC_SEGMENTS + 1];
        float[] reach = new float[ARC_SEGMENTS + 1];
        float[] edge = new float[ARC_SEGMENTS + 1];

        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            float rayAngle = start + arc * i / ARC_SEGMENTS;
            double rad = Math.toRadians(rayAngle);

            cos[i] = (float) Math.cos(rad);
            sin[i] = (float) Math.sin(rad);
            reach[i] = VisionBlockers.getRayLength(blockers, loc, rayAngle, length);
            edge[i] = smooth(Math.min(i, ARC_SEGMENTS - i) / (ARC_SEGMENTS / 2f * EDGE_SOFT_SHARE));
        }

        for (int ring = 0; ring < RINGS.length - 1; ring++) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                for (int step = 0; step < 2; step++) {
                    float share = RINGS[ring + step];
                    float dist = reach[i] * share;
                    float far = dist / length;
                    float light = smooth(far / NEAR_SOFT_SHARE) * (1f - DISTANCE_FALLOFF * far) * smooth((1f - share) / END_SOFT_SHARE);

                    GL11.glColor4f(r, g, b, alpha * edge[i] * light);
                    GL11.glTexCoord2f(dist * texPerUnit - scroll * texWidth, texHeight * i / ARC_SEGMENTS);
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

    private void drawZones(DroneNetScript net, float factor, float alpha, ViewportAPI viewport) {
        if (alpha <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (SectorEntityToken nest : net.getNests()) {
            Vector2f loc = nest.getLocation();
            if (nest.isDiscoverable()) continue;
            if (viewport != null && !viewport.isNearViewport(loc, DroneNetScript.NEST_ZONE_RADIUS)) continue;

            drawZone(loc, DroneNetScript.NEST_ZONE_RADIUS, ZONE_COLOR, alpha, factor);
        }

        GL11.glPopAttrib();
    }

    private void drawZone(Vector2f loc, float radius, Color color, float alpha, float factor) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, alpha * ZONE_CENTER_ALPHA_MULT);
        GL11.glVertex2f(loc.x * factor, loc.y * factor);
        GL11.glColor4f(r, g, b, alpha);
        for (int i = 0; i <= ZONE_SEGMENTS; i++) zoneVertex(loc, radius, i, factor);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, Math.min(1f, alpha * ZONE_OUTLINE_ALPHA_MULT));
        for (int i = 0; i < ZONE_SEGMENTS; i++) zoneVertex(loc, radius, i, factor);
        GL11.glEnd();
    }

    private void zoneVertex(Vector2f loc, float radius, int index, float factor) {
        double rad = 2f * Math.PI * index / ZONE_SEGMENTS;
        GL11.glVertex2f((loc.x + (float) Math.cos(rad) * radius) * factor, (loc.y + (float) Math.sin(rad) * radius) * factor);
    }

    @Override
    public float getRenderRange() {
        return RENDER_RANGE;
    }

    @Override
    public boolean containsEntity(SectorEntityToken other) {
        return false;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        return false;
    }

    @Override
    protected boolean shouldCheckFleetsToApplyEffect() {
        return false;
    }

    @Override
    public boolean hasTooltip() {
        return false;
    }

    @Override
    public String getTerrainName() {
        return "Spotter Cones";
    }

    @Override
    public String getEffectCategory() {
        return TERRAIN_ID;
    }
}
