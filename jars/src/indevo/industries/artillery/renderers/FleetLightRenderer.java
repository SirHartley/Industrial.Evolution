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
import indevo.industries.artillery.terrain.DroneConeTerrain;
import indevo.industries.artillery.terrain.SearchlightTerrain;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class FleetLightRenderer implements LunaCampaignRenderingPlugin {

    // same layer as the searchlight spots, below the fleets
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_7);
    private static final float[] SPOT_ANGLE = {0f};

    public static final Color BASE_COLOR = SearchlightTerrain.WARM;

    public static class Light {
        public final CampaignFleetAPI fleet;
        public float radius;
        public float target;
        public float rate;
        public float brightness;
        public float seeing;
        public float ring = SearchlightTerrain.RING_SECONDS;
        public boolean shown;
        public boolean sees;

        public Light(CampaignFleetAPI fleet, float radius) {
            this.fleet = fleet;
            this.radius = radius;
            this.target = radius;
        }
    }

    private final Map<CampaignFleetAPI, Light> lights = new LinkedHashMap<>();
    private LocationAPI location;
    private float sinceCheck = DroneNetScript.CHECK_SECONDS;
    private float dustTime;

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

    // the fleets EyeIndicatorScript locks on, only while the player sees at least a sensor contact
    public static boolean isWatcher(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        return fleet != player
                && fleet.isAlive()
                && fleet.getAI() != null
                && !fleet.isStationMode()
                && fleet.isHostileTo(player)
                && fleet.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE;
    }

    // Misc.getVisibleFleets without sensor contacts, the check EyeIndicatorScript runs
    public static boolean seesPlayer(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        VisibilityLevel level = player.getVisibilityLevelTo(fleet);
        return level == VisibilityLevel.COMPOSITION_DETAILS || level == VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS;
    }

    // centre distance at which seesPlayer turns true: vanilla shows composition at the full range with the transponder on, else at detectionRangeDetailsMult of it
    public static float getDetectionRadius(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        float range = fleet.getMaxSensorRangeToDetect(player);

        if (!player.isTransponderOn()) {
            float always = Math.max(range * Global.getSettings().getFloat("detectionRangeDetailsAlwaysMult"),
                    Global.getSettings().getFloat("detectionRangeDetailsAlwaysMin"));
            range = Math.max(range * Global.getSettings().getFloat("detectionRangeDetailsMult"), always);
        }

        return range + fleet.getRadius() + player.getRadius();
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

        dustTime += amount;
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

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (!isWatcher(fleet, player)) continue;

            float target = getDetectionRadius(fleet, player);
            Light light = lights.get(fleet);
            if (light == null) {
                light = new Light(fleet, target);
                lights.put(fleet, light);
            } else if (target != light.target) {
                light.target = target;
                light.rate = Math.abs(target - light.radius) / DroneNetScript.CONE_RESIZE_SECONDS;
            }

            boolean sees = seesPlayer(fleet, player);
            if (sees && !light.sees) light.ring = 0f;

            light.sees = sees;
            light.shown = true;
        }
    }

    // radius eased like ProfileScale, so a transponder switch grows the light instead of jumping
    private void advanceLight(Light light, float amount) {
        float step = light.rate * amount;
        light.radius = Math.abs(light.target - light.radius) <= step ? light.target : light.radius + Math.signum(light.target - light.radius) * step;

        light.brightness = clamp(light.brightness + (light.shown ? amount : -amount) / SearchlightTerrain.FADE_SECONDS);
        light.seeing = clamp(light.seeing + (light.shown && light.sees ? amount : -amount) / SearchlightTerrain.LOCK_FADE_SECONDS);
        if (light.ring < SearchlightTerrain.RING_SECONDS) light.ring += amount;
    }

    // the searchlight spot without rim and tower spikes
    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (lights.isEmpty() || location != Global.getSector().getCurrentLocation()) return;

        for (Light light : lights.values()) {
            if (light.brightness <= 0f || !light.fleet.isInCurrentLocation()) continue;

            Vector2f loc = light.fleet.getLocation();
            float radius = light.radius;
            if (!viewport.isNearViewport(loc, radius * LightConeRenderer.DRAWN_LENGTH_MULT)) continue;

            float fade = viewport.getAlphaMult() * light.brightness;
            float seeing = SearchlightRenderer.smooth(light.seeing);
            float alpha = fade * (1f + SearchlightTerrain.SEEING_BOOST * seeing);
            Color color = Misc.interpolateColor(BASE_COLOR, DroneConeTerrain.LOCK_COLOR, seeing);
            Color tint = Misc.interpolateColor(SearchlightTerrain.WARM, color, SearchlightTerrain.HALO_TINT);

            LightConeRenderer.draw(loc, 0f, 360f, radius, null, SearchlightTerrain.LIGHT, tint, alpha);
            LightConeRenderer.drawDust(viewport, loc, SPOT_ANGLE, 360f, radius, null, SearchlightTerrain.LIGHT,
                    Misc.interpolateColor(SearchlightTerrain.WARM, color, SearchlightTerrain.DUST_TINT), alpha, dustTime);
            SearchlightRenderer.drawGlow(loc, radius * SearchlightTerrain.CORE_RADIUS_SHARE, color, alpha * SearchlightTerrain.CORE_ALPHA);
            SearchlightRenderer.drawGlow(loc, radius * SearchlightTerrain.CENTER_RADIUS_SHARE, Color.WHITE, alpha * SearchlightTerrain.CENTER_ALPHA);

            if (light.ring < SearchlightTerrain.RING_SECONDS)
                SearchlightRenderer.drawRing(loc, radius, light.ring / SearchlightTerrain.RING_SECONDS, DroneConeTerrain.LOCK_COLOR, fade);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
