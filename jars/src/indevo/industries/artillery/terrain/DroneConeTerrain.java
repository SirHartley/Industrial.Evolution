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
    public static final Color LIGHT_COLOR = new Color(255, 232, 200);
    public static final Color LOCK_COLOR = new Color(255, 78, 52);
    public static final Color ZONE_COLOR = new Color(255, 200, 80);
    public static final Color ENGINE_CORE_COLOR = new Color(255, 235, 200);

    public static final LightConeRenderer.Style LIGHT = new LightConeRenderer.Style();
    public static final float LOCK_ALPHA_MULT = 1.25f;
    public static final float LOCK_PULSE = 0.25f;
    public static final float BLOOM_SIZE = 200f;
    public static final float BLOOM_ALPHA = 0.12f;
    public static final float LAMP_SIZE = 22f;
    public static final float LAMP_ALPHA = 0.45f;

    public static final float NEST_GLOW_SIZE = 80f;
    public static final float NEST_GLOW_ALPHA = 0.4f;
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
    private static final float[] CONE_ANGLE = new float[1];

    protected transient SpriteAPI glow;
    protected transient float dustTime;

    static {
        LIGHT.peak = 0.13f;
        LIGHT.edgeStart = 0.35f;
        LIGHT.edgeEnd = 1.2f;
        LIGHT.coreShare = 0.65f;
        LIGHT.coreWidth = 0.2f;
        LIGHT.farShare = 0.4f;
        LIGHT.falloff = 8f;
        LIGHT.endStart = 0.6f;
        LIGHT.nearSoftShare = 0.01f;
        LIGHT.shadow = 0.95f;
        LIGHT.sourceRadius = 50f;
        LIGHT.dustGain = 2.5f;
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7A, CampaignEngineLayers.TERRAIN_8);
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        dustTime += amount;
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
        drawNestLights(net, alpha, viewport);
    }

    // map space is world space times factor, same as the vanilla terrain map icons
    @Override
    public void renderOnMap(float factor, float alphaMult) {
        DroneNetScript net = DroneNetScript.get(entity.getContainingLocation());
        if (net == null) return;

        drawZones(net, factor, alphaMult * net.getVisibility() * ZONE_MAP_ALPHA);
    }

    private void drawCones(DroneNetScript net, float alpha, ViewportAPI viewport) {
        float length = net.getConeLength();
        List<VisionBlockers.Blocker> blockers = net.getBlockers();
        glow.setAdditiveBlend();
        glow.setAngle(0f);
        glow.setSize(BLOOM_SIZE, BLOOM_SIZE);

        for (DroneNetScript.Spotter spotter : net.getSpotters()) {
            if (spotter.entity == null) continue;

            Vector2f loc = spotter.entity.getLocation();
            if (!viewport.isNearViewport(loc, length * LightConeRenderer.DRAWN_LENGTH_MULT)) continue;

            Color color = Misc.interpolateColor(LIGHT_COLOR, LOCK_COLOR, spotter.alert);
            float lock = 1f + (LOCK_ALPHA_MULT - 1f) * spotter.alert;
            LightConeRenderer.draw(loc, spotter.coneAngle, DroneNetScript.CONE_ARC, length, blockers, LIGHT, color, alpha * lock);
            CONE_ANGLE[0] = spotter.coneAngle;
            LightConeRenderer.drawDust(viewport, loc, CONE_ANGLE, DroneNetScript.CONE_ARC, length, blockers, LIGHT, color, alpha * lock, dustTime);

            glow.setColor(color);
            glow.setAlphaMult(alpha * BLOOM_ALPHA);
            glow.renderAtCenter(loc.x, loc.y);
        }
    }

    private void drawNestLights(DroneNetScript net, float alpha, ViewportAPI viewport) {
        List<VisionBlockers.Blocker> blockers = net.getBlockers();
        float length = net.getNestLightLength();

        for (SectorEntityToken nest : net.getNests()) {
            Vector2f loc = nest.getLocation();
            if (nest.isDiscoverable()) continue;
            if (!viewport.isNearViewport(loc, length * LightConeRenderer.DRAWN_LENGTH_MULT)) continue;

            DroneNetScript.NestLights lights = net.getNestLights(nest);
            for (int i = 0; i < DroneNetScript.NEST_LIGHTS; i++) {
                Color color = Misc.interpolateColor(LIGHT_COLOR, LOCK_COLOR, lights.alert[i]);
                float lock = 1f + (LOCK_ALPHA_MULT - 1f) * lights.alert[i];
                CONE_ANGLE[0] = net.getNestLightAngle(nest, i);
                LightConeRenderer.draw(loc, CONE_ANGLE[0], DroneNetScript.NEST_LIGHT_ARC, length, blockers, LIGHT, color, alpha * lock);
                LightConeRenderer.drawDust(viewport, loc, CONE_ANGLE, DroneNetScript.NEST_LIGHT_ARC, length, blockers, LIGHT, color, alpha * lock, dustTime);
            }

            glow.setAngle(0f);
            glow.setSize(NEST_GLOW_SIZE, NEST_GLOW_SIZE);
            glow.setColor(LIGHT_COLOR);
            glow.setAlphaMult(alpha * NEST_GLOW_ALPHA);
            glow.renderAtCenter(loc.x, loc.y);
        }
    }

    private void drawDrones(DroneNetScript net, ViewportAPI viewport) {
        float alpha = viewport.getAlphaMult();
        float lampAlpha = alpha * net.getVisibility() * LAMP_ALPHA;
        float pulse = LOCK_PULSE * Global.getSector().getCampaignUI().getSharedFader().getBrightness();
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
            glow.setAlphaMult(lampAlpha * (1f - pulse * spotter.alert));
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

    private void drawZones(DroneNetScript net, float factor, float alpha) {
        if (alpha <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (SectorEntityToken nest : net.getNests()) {
            Vector2f loc = nest.getLocation();
            if (nest.isDiscoverable()) continue;

            drawZone(loc, net.getNestLightLength(), ZONE_COLOR, alpha, factor);
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
