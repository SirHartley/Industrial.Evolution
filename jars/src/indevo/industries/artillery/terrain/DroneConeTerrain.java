package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.utils.VisionBlockers;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.List;

public class DroneConeTerrain extends BaseRingTerrain {

    public static final String TERRAIN_ID = "IndEvo_drone_cones";
    public static final Color PATROL_COLOR = new Color(255, 200, 80);
    public static final Color LOCK_COLOR = new Color(255, 50, 30);
    public static final float WORLD_ALPHA = 0.25f;
    public static final float MAP_ALPHA = 0.4f;
    public static final float FAR_ALPHA_MULT = 0.2f;
    public static final float OUTLINE_ALPHA_MULT = 1.5f;
    public static final float LOCK_PULSE = 0.4f;
    public static final float RENDER_RANGE = 10000000f;
    private static final int ARC_SEGMENTS = 24;
    private static final int ZONE_SEGMENTS = 48;

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7A);
        return this;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        draw(1f, viewport.getAlphaMult() * WORLD_ALPHA, viewport);
    }

    // map space is world space times factor, same as the vanilla terrain map icons
    @Override
    public void renderOnMap(float factor, float alphaMult) {
        draw(factor, alphaMult * MAP_ALPHA, null);
    }

    private void draw(float factor, float alpha, ViewportAPI viewport) {
        DroneNetScript net = DroneNetScript.get(entity.getContainingLocation());
        if (net == null) return;

        alpha *= net.getVisibility();
        if (alpha <= 0f) return;

        float length = net.getConeLength();
        float pulse = 1f - LOCK_PULSE * Global.getSector().getCampaignUI().getSharedFader().getBrightness();
        List<VisionBlockers.Blocker> blockers = net.getBlockers();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (DroneNetScript.Spotter spotter : net.getSpotters()) {
            if (spotter.entity == null) continue;

            Vector2f loc = spotter.entity.getLocation();
            if (viewport != null && !viewport.isNearViewport(loc, length)) continue;

            Color color = spotter.seesPlayer ? LOCK_COLOR : PATROL_COLOR;
            float a = Math.min(1f, spotter.seesPlayer ? alpha * 2f * pulse : alpha);
            drawCone(loc, spotter.coneAngle, length, blockers, color, a, factor);
        }

        for (SectorEntityToken nest : net.getNests()) {
            Vector2f loc = nest.getLocation();
            if (nest.isDiscoverable()) continue;
            if (viewport != null && !viewport.isNearViewport(loc, DroneNetScript.NEST_ZONE_RADIUS)) continue;

            drawZone(loc, DroneNetScript.NEST_ZONE_RADIUS, PATROL_COLOR, alpha, factor);
        }

        GL11.glPopAttrib();
    }

    // every ray stops at the first planet or chaff cloud, so the fan shows the shadow the check uses
    private void drawCone(Vector2f loc, float angle, float length, List<VisionBlockers.Blocker> blockers, Color color, float alpha, float factor) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float start = angle - DroneNetScript.CONE_ARC / 2f;

        float[] x = new float[ARC_SEGMENTS + 1];
        float[] y = new float[ARC_SEGMENTS + 1];
        float[] reach = new float[ARC_SEGMENTS + 1];

        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            float rayAngle = start + DroneNetScript.CONE_ARC * i / ARC_SEGMENTS;
            float rayLength = VisionBlockers.getRayLength(blockers, loc, rayAngle, length);
            double rad = Math.toRadians(rayAngle);

            x[i] = (loc.x + (float) Math.cos(rad) * rayLength) * factor;
            y[i] = (loc.y + (float) Math.sin(rad) * rayLength) * factor;
            reach[i] = rayLength / length;
        }

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glVertex2f(loc.x * factor, loc.y * factor);
        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            GL11.glColor4f(r, g, b, alpha * (1f - (1f - FAR_ALPHA_MULT) * reach[i]));
            GL11.glVertex2f(x[i], y[i]);
        }
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, Math.min(1f, alpha * OUTLINE_ALPHA_MULT));
        GL11.glVertex2f(loc.x * factor, loc.y * factor);
        for (int i = 0; i <= ARC_SEGMENTS; i++) GL11.glVertex2f(x[i], y[i]);
        GL11.glEnd();
    }

    private void drawZone(Vector2f loc, float radius, Color color, float alpha, float factor) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, alpha * FAR_ALPHA_MULT);
        GL11.glVertex2f(loc.x * factor, loc.y * factor);
        GL11.glColor4f(r, g, b, alpha);
        for (int i = 0; i <= ZONE_SEGMENTS; i++) zoneVertex(loc, radius, i, factor);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, Math.min(1f, alpha * OUTLINE_ALPHA_MULT));
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
