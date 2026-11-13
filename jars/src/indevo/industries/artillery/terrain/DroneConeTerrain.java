package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.scripts.DroneNetScript;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

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
    private static final int ARC_SEGMENTS = 12;

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7A);
        return this;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        drawCones(1f, viewport.getAlphaMult() * WORLD_ALPHA, viewport);
    }

    // map space is world space times factor, same as the vanilla terrain map icons
    @Override
    public void renderOnMap(float factor, float alphaMult) {
        drawCones(factor, alphaMult * MAP_ALPHA, null);
    }

    private void drawCones(float factor, float alpha, ViewportAPI viewport) {
        DroneNetScript net = DroneNetScript.get(entity.getContainingLocation());
        if (net == null) return;

        alpha *= net.getVisibility();
        if (alpha <= 0f) return;

        float length = net.getConeLength();
        float arc = DroneNetScript.CONE_ARC;
        float pulse = 1f - LOCK_PULSE * Global.getSector().getCampaignUI().getSharedFader().getBrightness();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (DroneNetScript.Spotter spotter : net.getSpotters()) {
            if (spotter.fleet == null) continue;

            Vector2f loc = spotter.fleet.getLocation();
            if (viewport != null && !viewport.isNearViewport(loc, length)) continue;

            Color color = spotter.locked ? LOCK_COLOR
                    : Misc.interpolateColor(PATROL_COLOR, LOCK_COLOR, Math.min(1f, spotter.seen / DroneNetScript.GRACE_SECONDS));
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;
            float a = Math.min(1f, spotter.locked ? alpha * 2f * pulse : alpha);
            float start = spotter.coneAngle - arc / 2f;

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex2f(loc.x * factor, loc.y * factor);
            GL11.glColor4f(r, g, b, a * FAR_ALPHA_MULT);
            for (int i = 0; i <= ARC_SEGMENTS; i++) arcVertex(loc, start + arc * i / ARC_SEGMENTS, length, factor);
            GL11.glEnd();

            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glColor4f(r, g, b, Math.min(1f, a * OUTLINE_ALPHA_MULT));
            GL11.glVertex2f(loc.x * factor, loc.y * factor);
            for (int i = 0; i <= ARC_SEGMENTS; i++) arcVertex(loc, start + arc * i / ARC_SEGMENTS, length, factor);
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    private void arcVertex(Vector2f loc, float angle, float length, float factor) {
        double rad = Math.toRadians(angle);
        GL11.glVertex2f((loc.x + (float) Math.cos(rad) * length) * factor, (loc.y + (float) Math.sin(rad) * length) * factor);
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
