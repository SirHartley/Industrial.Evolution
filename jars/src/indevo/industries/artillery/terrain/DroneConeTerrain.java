package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.renderers.LightConeRenderer;
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
    public static final Color ENGINE_CORE_COLOR = new Color(255, 235, 200);

    public static final float CONE_ALPHA = 0.3f;
    public static final float LOCK_ALPHA_MULT = 1.4f;
    public static final float LOCK_PULSE = 0.25f;
    public static final float SCROLL_SPEED = 0.02f;
    public static final float LAMP_SIZE = 22f;
    public static final float LAMP_ALPHA = 0.45f;

    public static final float ZONE_ALPHA = 0.25f;
    public static final float ZONE_MAP_ALPHA = 0.4f;
    public static final float ZONE_CENTER_ALPHA_MULT = 0.2f;
    public static final float ZONE_OUTLINE_ALPHA_MULT = 1.5f;

    public static final float INNER_ENGINE_BACK = 9.2f;
    public static final float INNER_ENGINE_SIDE = 3.6f;
    public static final float ENGINE_GLOW_LENGTH = 11f;
    public static final float ENGINE_GLOW_WIDTH = 5f;
    public static final float INNER_ENGINE_SCALE = 0.6f;
    public static final float IDLE_THROTTLE = 0.35f;

    public static final float RENDER_RANGE = 10000000f;
    private static final int ZONE_SEGMENTS = 48;

    protected transient SpriteAPI texture;
    protected transient SpriteAPI glow;
    protected transient float scroll;

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7A, CampaignEngineLayers.TERRAIN_8);
        texture = Global.getSettings().getSprite("terrain", "pulsar");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        scroll = (scroll + amount * SCROLL_SPEED) % 1f;
    }

    // light below the fleets, lamps and engines above them
    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        DroneNetScript net = DroneNetScript.get(entity.getContainingLocation());
        if (net == null) return;

        if (layer == CampaignEngineLayers.TERRAIN_8) {
            drawDrones(net, viewport);
            return;
        }

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
        float length = net.getConeLength();
        float pulse = 1f - LOCK_PULSE * Global.getSector().getCampaignUI().getSharedFader().getBrightness();
        List<VisionBlockers.Blocker> blockers = net.getBlockers();

        for (DroneNetScript.Spotter spotter : net.getSpotters()) {
            if (spotter.entity == null) continue;

            Vector2f loc = spotter.entity.getLocation();
            if (!viewport.isNearViewport(loc, length * LightConeRenderer.DRAWN_LENGTH_MULT)) continue;

            Color color = Misc.interpolateColor(LIGHT_COLOR, LOCK_COLOR, spotter.alert);
            float lock = 1f + (LOCK_ALPHA_MULT * pulse - 1f) * spotter.alert;
            LightConeRenderer.draw(loc, spotter.coneAngle, DroneNetScript.CONE_ARC, length, blockers, color, alpha * CONE_ALPHA * lock, texture, scroll);
        }
    }

    private void drawDrones(DroneNetScript net, ViewportAPI viewport) {
        float alpha = viewport.getAlphaMult();
        float lampAlpha = alpha * net.getVisibility() * LAMP_ALPHA;
        glow.setAdditiveBlend();

        for (DroneNetScript.Spotter spotter : net.getSpotters()) {
            if (spotter.entity == null) continue;
            if (!viewport.isNearViewport(spotter.entity.getLocation(), 100f)) continue;

            float throttle = IDLE_THROTTLE + (1f - IDLE_THROTTLE) * DroneNetScript.getThrottle(spotter);
            drawEngine(spotter, DroneNetScript.ENGINE_BACK, DroneNetScript.ENGINE_SIDE, 1f, throttle, alpha);
            drawEngine(spotter, DroneNetScript.ENGINE_BACK, -DroneNetScript.ENGINE_SIDE, 1f, throttle, alpha);
            drawEngine(spotter, INNER_ENGINE_BACK, INNER_ENGINE_SIDE, INNER_ENGINE_SCALE, throttle, alpha);
            drawEngine(spotter, INNER_ENGINE_BACK, -INNER_ENGINE_SIDE, INNER_ENGINE_SCALE, throttle, alpha);

            if (lampAlpha <= 0f) continue;

            Vector2f lamp = spotter.entity.getLocation();
            glow.setAngle(0f);
            glow.setSize(LAMP_SIZE, LAMP_SIZE);
            glow.setColor(Misc.interpolateColor(LIGHT_COLOR, LOCK_COLOR, spotter.alert));
            glow.setAlphaMult(lampAlpha);
            glow.renderAtCenter(lamp.x, lamp.y);
        }
    }

    private void drawEngine(DroneNetScript.Spotter spotter, float back, float side, float scale, float throttle, float alpha) {
        float length = ENGINE_GLOW_LENGTH * scale * throttle;
        float width = ENGINE_GLOW_WIDTH * scale;
        Vector2f point = DroneNetScript.getEnginePoint(spotter, back + length * 0.3f, side);

        glow.setAngle(spotter.heading);
        glow.setSize(length, width);
        glow.setColor(DroneNetScript.ENGINE_COLOR);
        glow.setAlphaMult(alpha * (0.5f + 0.5f * throttle));
        glow.renderAtCenter(point.x, point.y);

        glow.setSize(length * 0.5f, width * 0.5f);
        glow.setColor(ENGINE_CORE_COLOR);
        glow.renderAtCenter(point.x, point.y);
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
