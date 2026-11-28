package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.scripts.EyeIndicatorScript;
import indevo.industries.artillery.terrain.DroneConeTerrain;
import indevo.industries.artillery.terrain.SearchlightTerrain;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FleetLightRenderer implements LunaCampaignRenderingPlugin {

    // same layer as the searchlight spots, below the fleets
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_7);

    public static final Color BASE_COLOR = SearchlightTerrain.ORANGE;
    public static final float WARN_DISTANCE = 900f;
    public static final float ARC_SPAN = 300f;
    public static final float ARC_MIN_ALPHA = 0.15f;
    public static final float ARC_MAX_ALPHA = 0.7f;
    public static final float INSIDE_EDGE_ALPHA = 0.15f;
    public static final float INSIDE_NEAR_ALPHA = 0.5f;
    public static final float FLASH_ALPHA = 0.8f;
    public static final float LINE_WIDTH = 1.5f;
    public static final float GLOW_WIDTH = 6f;
    public static final float GLOW_ALPHA = 0.35f;
    public static final int SEGMENTS = 180;

    // render runs on one thread
    private static final float[] ALPHAS = new float[SEGMENTS + 1];
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    public static class Light {
        public final CampaignFleetAPI fleet;
        public float radius;
        public float target;
        public float rate;
        public float brightness;
        public float inside;
        public float locking;
        public float flash = SearchlightTerrain.RING_SECONDS;
        public boolean shown;
        public boolean sees;
        public boolean locked;

        public Light(CampaignFleetAPI fleet, float radius) {
            this.fleet = fleet;
            this.radius = radius;
            this.target = radius;
        }
    }

    private final Map<CampaignFleetAPI, Light> lights = new LinkedHashMap<>();
    private LocationAPI location;
    private float sinceCheck = DroneNetScript.CHECK_SECONDS;

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(FleetLightRenderer.class)) return;
        LunaCampaignRenderer.addTransientRenderer(new FleetLightRenderer());
    }

    // same gate as the drone net, never with plain watchtowers
    public static boolean isActive(LocationAPI loc) {
        return Setting.ARTILLERY_FLEET_LIGHTS.enabled()
                && loc != null
                && !loc.isHyperspace()
                && loc.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)
                && !loc.getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)
                && !ArtilleryStationPlacer.usesWatchtowers(loc)
                && ArtilleryStationEntityPlugin.needsVisionAgainstPlayer(loc);
    }

    // the fleets EyeIndicatorScript fills the eye for, only while the player sees at least a sensor contact
    public static boolean isWatcher(CampaignFleetAPI fleet, CampaignFleetAPI player, Set<String> artilleryFactions) {
        return fleet.isAlive()
                && EyeIndicatorScript.canReport(fleet, player, artilleryFactions)
                && fleet.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE;
    }

    // Misc.getVisibleFleets without sensor contacts, the check EyeIndicatorScript runs
    public static boolean seesPlayer(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        VisibilityLevel level = player.getVisibilityLevelTo(fleet);
        return level == VisibilityLevel.COMPOSITION_DETAILS || level == VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) return;

        LocationAPI current = Global.getSector().getCurrentLocation();
        if (current != location) {
            lights.clear();
            location = current;
            sinceCheck = DroneNetScript.CHECK_SECONDS;
        }

        sinceCheck += amount;

        // no callback for fleets entering the location or for a change in what a fleet sees
        if (sinceCheck >= DroneNetScript.CHECK_SECONDS) {
            sinceCheck = 0f;
            check();
        }

        Iterator<Light> iterator = lights.values().iterator();
        while (iterator.hasNext()) {
            Light light = iterator.next();
            advanceLight(light, amount);
            if (!light.shown && light.brightness <= 0f) iterator.remove();
        }
    }

    private void check() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean active = player != null && player.getContainingLocation() == location && isActive(location);

        for (Light light : lights.values()) light.shown = false;
        if (!active) return;

        Set<String> artilleryFactions = ArtilleryStationEntityPlugin.getArtilleryFactionIds(location);
        boolean spotted = EyeIndicatorScript.isPlayerSpotted();

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (!isWatcher(fleet, player, artilleryFactions)) continue;

            float target = EyeIndicatorScript.getDetectionRadius(fleet, player);
            Light light = lights.get(fleet);
            if (light == null) {
                light = new Light(fleet, target);
                lights.put(fleet, light);
            } else if (target != light.target) {
                light.target = target;
                light.rate = Math.abs(target - light.radius) / DroneNetScript.CONE_RESIZE_SECONDS;
            }

            // red once the eye is full while this fleet sees the player, like a locked searchlight spot
            boolean sees = seesPlayer(fleet, player);
            boolean locked = sees && spotted;
            if (locked && !light.locked) light.flash = 0f;

            light.sees = sees;
            light.locked = locked;
            light.shown = true;
        }
    }

    // radius eased like ProfileScale, so a transponder switch grows the edge instead of jumping
    private void advanceLight(Light light, float amount) {
        float step = light.rate * amount;
        light.radius = Math.abs(light.target - light.radius) <= step ? light.target : light.radius + Math.signum(light.target - light.radius) * step;

        light.brightness = clamp(light.brightness + (light.shown ? amount : -amount) / SearchlightTerrain.FADE_SECONDS);
        light.inside = clamp(light.inside + (light.shown && light.sees ? amount : -amount) / SearchlightTerrain.LOCK_FADE_SECONDS);
        light.locking = clamp(light.locking + (light.shown && light.locked ? amount : -amount) / SearchlightTerrain.LOCK_FADE_SECONDS);
        if (light.flash < SearchlightTerrain.RING_SECONDS) light.flash += amount;
    }

    // the edge of each fleet's sight: outside only the stretch facing the player, brighter as the player closes in; inside the whole edge, brightest toward the way out
    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (lights.isEmpty() || location != Global.getSector().getCurrentLocation()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != location) return;

        for (Light light : lights.values()) {
            if (light.brightness <= 0f || !light.fleet.isInCurrentLocation()) continue;

            Vector2f loc = light.fleet.getLocation();
            if (!viewport.isNearViewport(loc, light.radius * 1.1f)) continue;

            float gap = Misc.getDistance(loc, player.getLocation()) - light.radius;
            float closeness = SearchlightRenderer.smooth(1f - gap / WARN_DISTANCE);
            float inside = SearchlightRenderer.smooth(light.inside);
            float approach = (ARC_MIN_ALPHA + (ARC_MAX_ALPHA - ARC_MIN_ALPHA) * closeness) * (1f - inside);
            float flash = light.flash < SearchlightTerrain.RING_SECONDS ? FLASH_ALPHA * (1f - light.flash / SearchlightTerrain.RING_SECONDS) : 0f;
            float facing = (float) Math.toRadians(Misc.getAngleInDegrees(loc, player.getLocation()));
            Color color = Misc.interpolateColor(BASE_COLOR, DroneConeTerrain.LOCK_COLOR, SearchlightRenderer.smooth(light.locking));

            drawEdge(loc, light.radius, facing, color, viewport.getAlphaMult() * light.brightness, approach, inside, flash, viewport.getViewMult());
        }
    }

    private static void drawEdge(Vector2f loc, float radius, float facing, Color color, float fade,
                                 float approach, float inside, float flash, float unitsPerPixel) {
        float max = 0f;
        for (int i = 0; i <= SEGMENTS; i++) {
            double offset = 2.0 * Math.PI * i / SEGMENTS - Math.PI;
            float along = (float) offset * radius / ARC_SPAN;
            float near = (float) Math.exp(-along * along);

            ALPHAS[i] = Math.min(1f, fade * (approach * near + inside * (INSIDE_EDGE_ALPHA + INSIDE_NEAR_ALPHA * near) + flash));
            COS[i] = (float) Math.cos(facing + offset);
            SIN[i] = (float) Math.sin(facing + offset);
            max = Math.max(max, ALPHAS[i]);
        }

        if (max < 0.005f) return;

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float half = GLOW_WIDTH * 0.5f * unitsPerPixel;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (int side = -1; side <= 1; side += 2) {
            float outer = radius + side * half;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= SEGMENTS; i++) {
                GL11.glColor4f(r, g, b, ALPHAS[i] * GLOW_ALPHA);
                GL11.glVertex2f(loc.x + COS[i] * radius, loc.y + SIN[i] * radius);
                GL11.glColor4f(r, g, b, 0f);
                GL11.glVertex2f(loc.x + COS[i] * outer, loc.y + SIN[i] * outer);
            }
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(LINE_WIDTH * Global.getSettings().getScreenScaleMult());
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= SEGMENTS; i++) {
            GL11.glColor4f(r, g, b, ALPHAS[i]);
            GL11.glVertex2f(loc.x + COS[i] * radius, loc.y + SIN[i] * radius);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
