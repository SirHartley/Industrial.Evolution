package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.scripts.EyeIndicatorScript;
import indevo.industries.artillery.terrain.DroneConeTerrain;
import indevo.industries.artillery.terrain.SearchlightTerrain;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import indevo.industries.artillery.utils.ProfileScale;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DetectionRenderer implements LunaCampaignRenderingPlugin {

    public enum SpotState {
        NONE,
        TRACKING,
        LOCKED
    }

    // same layer as the searchlight spots, below the fleets
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_7);
    private static final float[] SPOT_ANGLE = {0f};
    private static final float[] WANDER_PERIODS = {11f, 7f, 9.5f, 6f};

    public static final Color EDGE_COLOR = SearchlightTerrain.ORANGE;
    public static final Color SPOT_COLOR = new Color(255, 214, 72);
    public static final float WARN_DISTANCE = 900f;
    public static final float ARC_SPAN = 300f;
    public static final float ARC_MAX_ALPHA = 0.7f;
    public static final float INSIDE_NEAR_ALPHA = 0.5f;
    public static final float FLASH_ALPHA = 0.8f;
    public static final float LINE_WIDTH = 1.5f;
    public static final float GLOW_WIDTH = 6f;
    public static final float GLOW_ALPHA = 0.35f;
    public static final float BLOCK_GAP = 50f;
    public static final float BLOCK_FADE = 250f;
    public static final float BLOCK_MIN_RADIUS = 10f;
    public static final float UNION_FADE = 150f;
    public static final float WANDER_SHARE = 0.3f;
    public static final float SNAP_SECONDS = 0.2f;
    public static final int SEGMENTS = 180;

    // render runs on one thread
    private static final float[] ALPHAS = new float[SEGMENTS + 1];
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    public static class Edge {
        public final SectorEntityToken entity;
        public final boolean tower;
        public final List<SectorEntityToken> blockers = new ArrayList<>();
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

        public Edge(SectorEntityToken entity, boolean tower, float radius) {
            this.entity = entity;
            this.tower = tower;
            this.radius = radius;
            this.target = radius;
        }
    }

    private final Map<SectorEntityToken, Edge> edges = new LinkedHashMap<>();
    private final ProfileScale profileScale = new ProfileScale();
    private LocationAPI location;
    private float sinceCheck = DroneNetScript.CHECK_SECONDS;
    private float dustTime;

    private SpotState spotState = SpotState.NONE;
    private float spotBrightness;
    private float spotLocking;
    private float spotLockTime;
    private float spotRing = SearchlightTerrain.RING_SECONDS;
    private float spotSnap;
    private float spotWander;
    private final float[] spotPhase = new float[WANDER_PERIODS.length];

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(DetectionRenderer.class)) return;
        LunaCampaignRenderer.addTransientRenderer(new DetectionRenderer());
    }

    // same gate as the drone net, never with plain watchtowers
    public static boolean isFleetEdgeActive(LocationAPI loc) {
        return Setting.ARTILLERY_FLEET_LIGHTS.enabled()
                && loc != null
                && !loc.isHyperspace()
                && loc.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)
                && !loc.getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)
                && !ArtilleryStationPlacer.usesWatchtowers(loc)
                && ArtilleryStationEntityPlugin.needsVisionAgainstPlayer(loc);
    }

    // same gate as the eye
    public static boolean isTowerEdgeActive(LocationAPI loc, CampaignFleetAPI player) {
        return loc != null
                && !loc.isHyperspace()
                && loc.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)
                && !loc.getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)
                && EyeIndicatorScript.hasHostileArtillery(loc, player);
    }

    // the fleets EyeIndicatorScript fills the eye for, only while the player sees at least a sensor contact
    public static boolean isWatcher(CampaignFleetAPI fleet, CampaignFleetAPI player, Set<String> artilleryFactions) {
        return fleet.isAlive()
                && EyeIndicatorScript.canReport(fleet, player, artilleryFactions)
                && fleet.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE;
    }

    // the towers EyeIndicatorScript fills the eye for by range, never one the player has not found yet
    public static boolean isWatchingTower(SectorEntityToken tower, CampaignFleetAPI player) {
        return tower.getCustomPlugin() instanceof WatchtowerEntityPlugin plugin
                && !tower.isDiscoverable()
                && EyeIndicatorScript.isRangeSource(plugin, player);
    }

    // Misc.getVisibleFleets without sensor contacts, the check EyeIndicatorScript runs
    public static boolean seesPlayer(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        VisibilityLevel level = player.getVisibilityLevelTo(fleet);
        return level == VisibilityLevel.COMPOSITION_DETAILS || level == VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS;
    }

    // what an edge fades out near: planets, stars, jump points and visible objects, no fleets, terrain, asteroids or hidden helpers
    public static boolean isBlocker(SectorEntityToken object) {
        if (object instanceof CampaignFleetAPI || object instanceof CampaignTerrainAPI
                || object instanceof AsteroidAPI || object instanceof RingBandAPI) return false;
        if (object.getRadius() < BLOCK_MIN_RADIUS || object.hasTag(Tags.NON_CLICKABLE)) return false;

        return object instanceof PlanetAPI || object instanceof JumpPointAPI
                || object.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE;
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
            edges.clear();
            location = current;
            sinceCheck = DroneNetScript.CHECK_SECONDS;
            spotState = SpotState.NONE;
            spotBrightness = 0f;
            spotLocking = 0f;
        }

        dustTime += amount;
        sinceCheck += amount;
        profileScale.advance(amount, location);

        // no callback for fleets entering the location or for a change in what a fleet or tower sees
        if (sinceCheck >= DroneNetScript.CHECK_SECONDS) {
            sinceCheck = 0f;
            check();
        }

        Iterator<Edge> iterator = edges.values().iterator();
        while (iterator.hasNext()) {
            Edge edge = iterator.next();
            advanceEdge(edge, amount);
            if (!edge.shown && edge.brightness <= 0f) iterator.remove();
        }

        advanceSpot(amount);
    }

    private void check() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean here = player != null && player.getContainingLocation() == location;

        for (Edge edge : edges.values()) edge.shown = false;

        boolean eyeLocked = EyeIndicatorScript.isEyeLocked();
        boolean insideTower = false;

        if (here && isFleetEdgeActive(location)) {
            Set<String> artilleryFactions = ArtilleryStationEntityPlugin.getArtilleryFactionIds(location);

            for (CampaignFleetAPI fleet : location.getFleets()) {
                if (!isWatcher(fleet, player, artilleryFactions)) continue;

                boolean sees = seesPlayer(fleet, player);
                if (eyeLocked && !sees) continue;
                update(fleet, false, EyeIndicatorScript.getDetectionRadius(fleet, player), sees, eyeLocked);
            }
        }

        if (here && isTowerEdgeActive(location, player)) {
            float range = Setting.ARTILLERY_WATCHTOWER_RANGE.value();

            for (SectorEntityToken tower : location.getEntitiesWithTag(Ids.TAG_WATCHTOWER)) {
                if (!isWatchingTower(tower, player)) continue;

                boolean sees = Misc.getDistance(tower, player) < range;
                insideTower |= sees;
                if (eyeLocked && !sees) continue;
                update(tower, true, range, sees, eyeLocked);
            }
        }

        findBlockers();
        checkSpot(insideTower, eyeLocked);
    }

    // objects move slowly, so the 0.25 s check is enough to know which ones sit near an edge
    private void findBlockers() {
        for (Edge edge : edges.values()) edge.blockers.clear();
        if (edges.isEmpty()) return;

        for (SectorEntityToken object : location.getAllEntities()) {
            if (!isBlocker(object)) continue;

            float reach = object.getRadius() + BLOCK_GAP + BLOCK_FADE;
            for (Edge edge : edges.values()) {
                if (object == edge.entity) continue;

                float dist = Misc.getDistance(edge.entity.getLocation(), object.getLocation());
                if (dist > Math.min(edge.radius, edge.target) - reach && dist < Math.max(edge.radius, edge.target) + reach)
                    edge.blockers.add(object);
            }
        }
    }

    // red while the eye is red and it sees the player, like a locked searchlight spot
    private void update(SectorEntityToken entity, boolean tower, float target, boolean sees, boolean eyeLocked) {
        Edge edge = edges.get(entity);
        if (edge == null) {
            edge = new Edge(entity, tower, target);
            edges.put(entity, edge);
        } else if (target != edge.target) {
            edge.target = target;
            edge.rate = Math.abs(target - edge.radius) / DroneNetScript.CONE_RESIZE_SECONDS;
        }

        boolean locked = sees && eyeLocked;
        if (locked && !edge.locked) edge.flash = 0f;

        edge.sees = sees;
        edge.locked = locked;
        edge.shown = true;
    }

    // a watchtower keeps a spot on the player only while the eye fills, it locks red when the eye turns red and fades out after the ring
    private void checkSpot(boolean insideTower, boolean eyeLocked) {
        switch (spotState) {
            case NONE -> {
                if (insideTower && !eyeLocked) {
                    spotState = SpotState.TRACKING;
                    if (spotBrightness <= 0f) for (int i = 0; i < spotPhase.length; i++) spotPhase[i] = (float) (Math.random() * 2.0 * Math.PI);
                }
            }
            case TRACKING -> {
                if (eyeLocked) {
                    spotState = SpotState.LOCKED;
                    spotLockTime = 0f;
                    spotRing = 0f;
                    Global.getSoundPlayer().playUISound(DroneNetScript.LOCK_SOUND_ID, 1f, DroneNetScript.LOCK_SOUND_VOLUME);
                } else if (!insideTower) spotState = SpotState.NONE;
            }
            case LOCKED -> {
                if (!eyeLocked) spotState = insideTower ? SpotState.TRACKING : SpotState.NONE;
            }
        }
    }

    // radius eased like ProfileScale, so a transponder switch grows the edge instead of jumping
    private void advanceEdge(Edge edge, float amount) {
        float step = edge.rate * amount;
        edge.radius = Math.abs(edge.target - edge.radius) <= step ? edge.target : edge.radius + Math.signum(edge.target - edge.radius) * step;

        edge.brightness = clamp(edge.brightness + (edge.shown ? amount : -amount) / SearchlightTerrain.FADE_SECONDS);
        edge.inside = clamp(edge.inside + (edge.shown && edge.sees ? amount : -amount) / SearchlightTerrain.LOCK_FADE_SECONDS);
        edge.locking = clamp(edge.locking + (edge.shown && edge.locked ? amount : -amount) / SearchlightTerrain.LOCK_FADE_SECONDS);
        if (edge.flash < SearchlightTerrain.RING_SECONDS) edge.flash += amount;
    }

    private void advanceSpot(float amount) {
        boolean locked = spotState == SpotState.LOCKED;
        if (locked) spotLockTime += amount;

        boolean shown = spotState == SpotState.TRACKING || (locked && spotLockTime < SearchlightTerrain.RING_SECONDS);
        spotBrightness = clamp(spotBrightness + (shown ? amount : -amount) / SearchlightTerrain.FADE_SECONDS);
        spotLocking = clamp(spotLocking + (locked ? amount : -amount) / SearchlightTerrain.LOCK_FADE_SECONDS);
        spotSnap = clamp(spotSnap + (locked ? amount : -amount) / SNAP_SECONDS);
        if (spotBrightness > 0f) spotWander += amount;
        if (spotRing < SearchlightTerrain.RING_SECONDS) spotRing += amount;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (location != Global.getSector().getCurrentLocation()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != location) return;

        for (Edge edge : edges.values()) renderEdge(edge, player, viewport);
        renderSpot(player, viewport);
    }

    // only the stretch of the edge facing the player: outside it comes in over the last WARN_DISTANCE, inside it shows the way out
    private void renderEdge(Edge edge, CampaignFleetAPI player, ViewportAPI viewport) {
        if (edge.brightness <= 0f || !edge.entity.isInCurrentLocation()) return;

        Vector2f loc = edge.entity.getLocation();
        if (!viewport.isNearViewport(loc, edge.radius * 1.1f)) return;

        float gap = Misc.getDistance(loc, player.getLocation()) - edge.radius;
        float closeness = SearchlightRenderer.smooth(1f - gap / WARN_DISTANCE);
        float inside = SearchlightRenderer.smooth(edge.inside);
        float approach = ARC_MAX_ALPHA * closeness * (1f - inside);
        float flash = edge.flash < SearchlightTerrain.RING_SECONDS ? FLASH_ALPHA * (1f - edge.flash / SearchlightTerrain.RING_SECONDS) : 0f;
        float facing = (float) Math.toRadians(Misc.getAngleInDegrees(loc, player.getLocation()));
        Color color = Misc.interpolateColor(EDGE_COLOR, DroneConeTerrain.LOCK_COLOR, SearchlightRenderer.smooth(edge.locking));

        drawEdge(edge, facing, color, viewport.getAlphaMult() * edge.brightness, approach, inside, flash, viewport.getViewMult());
    }

    // the searchlight spot without rim and tower spikes, sized by the player's profile like the towers' spots
    private void renderSpot(CampaignFleetAPI player, ViewportAPI viewport) {
        if (spotBrightness <= 0f && spotRing >= SearchlightTerrain.RING_SECONDS) return;

        float radius = SearchlightTerrain.getSpotRadius() * profileScale.getMult(SearchlightTerrain.MIN_SPOT_MULT);
        Vector2f loc = getSpotLocation(player.getLocation(), radius);
        float fade = viewport.getAlphaMult();

        if (spotBrightness > 0f) {
            float locking = SearchlightRenderer.smooth(spotLocking);
            float alpha = fade * spotBrightness * (1f + SearchlightTerrain.SEEING_BOOST * locking);
            Color color = Misc.interpolateColor(SPOT_COLOR, DroneConeTerrain.LOCK_COLOR, locking);
            Color tint = Misc.interpolateColor(SearchlightTerrain.WARM, color, SearchlightTerrain.HALO_TINT);

            LightConeRenderer.draw(loc, 0f, 360f, radius, null, SearchlightTerrain.LIGHT, tint, alpha);
            LightConeRenderer.drawDust(viewport, loc, SPOT_ANGLE, 360f, radius, null, SearchlightTerrain.LIGHT,
                    Misc.interpolateColor(SearchlightTerrain.WARM, color, SearchlightTerrain.DUST_TINT), alpha, dustTime);
            SearchlightRenderer.drawGlow(loc, radius * SearchlightTerrain.CORE_RADIUS_SHARE, color, alpha * SearchlightTerrain.CORE_ALPHA);
            SearchlightRenderer.drawGlow(loc, radius * SearchlightTerrain.CENTER_RADIUS_SHARE, Color.WHITE, alpha * SearchlightTerrain.CENTER_ALPHA);
        }

        if (spotRing < SearchlightTerrain.RING_SECONDS)
            SearchlightRenderer.drawRing(player.getLocation(), radius, spotRing / SearchlightTerrain.RING_SECONDS, DroneConeTerrain.LOCK_COLOR, fade);
    }

    // two slow sines per axis wander the spot over the fleet inside its core, a lock pulls it to the centre
    private Vector2f getSpotLocation(Vector2f center, float radius) {
        float reach = WANDER_SHARE * radius * (1f - SearchlightRenderer.smooth(spotSnap));
        float x = 0.6f * wave(0) + 0.4f * wave(1);
        float y = 0.6f * wave(2) + 0.4f * wave(3);
        return new Vector2f(center.x + x * reach, center.y + y * reach);
    }

    private float wave(int index) {
        return (float) Math.sin(2.0 * Math.PI * spotWander / WANDER_PERIODS[index] + spotPhase[index]);
    }

    private void drawEdge(Edge edge, float facing, Color color, float fade, float approach, float inside, float flash, float unitsPerPixel) {
        Vector2f loc = edge.entity.getLocation();
        float radius = edge.radius;

        for (int i = 0; i <= SEGMENTS; i++) {
            double offset = 2.0 * Math.PI * i / SEGMENTS - Math.PI;
            float along = (float) offset * radius / ARC_SPAN;
            float near = (float) Math.exp(-along * along);

            ALPHAS[i] = Math.min(1f, fade * near * (approach + inside * INSIDE_NEAR_ALPHA + flash));
            COS[i] = (float) Math.cos(facing + offset);
            SIN[i] = (float) Math.sin(facing + offset);
        }

        for (SectorEntityToken blocker : edge.blockers) mask(loc, radius, blocker.getLocation(), blocker.getRadius() + BLOCK_GAP);

        // a stretch inside another edge of the same kind is already seen, so outside only the outline of the area they watch together shows
        for (Edge other : edges.values()) {
            if (other == edge || other.tower != edge.tower || other.brightness <= 0f || !other.entity.isInCurrentLocation()) continue;
            unionMask(loc, radius, other.entity.getLocation(), other.radius, other.brightness * (1f - inside));
        }

        float max = 0f;
        for (float alpha : ALPHAS) max = Math.max(max, alpha);
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

    // gone within clear of the object's centre, back to full over BLOCK_FADE
    private static void mask(Vector2f loc, float radius, Vector2f at, float clear) {
        for (int i = 0; i <= SEGMENTS; i++) {
            if (ALPHAS[i] <= 0f) continue;

            float dx = loc.x + COS[i] * radius - at.x;
            float dy = loc.y + SIN[i] * radius - at.y;
            ALPHAS[i] *= SearchlightRenderer.smooth(((float) Math.sqrt(dx * dx + dy * dy) - clear) / BLOCK_FADE);
        }
    }

    private static void unionMask(Vector2f loc, float radius, Vector2f at, float otherRadius, float strength) {
        if (strength <= 0f) return;

        for (int i = 0; i <= SEGMENTS; i++) {
            if (ALPHAS[i] <= 0f) continue;

            float dx = loc.x + COS[i] * radius - at.x;
            float dy = loc.y + SIN[i] * radius - at.y;
            float depth = otherRadius - (float) Math.sqrt(dx * dx + dy * dy);
            ALPHAS[i] *= 1f - strength * SearchlightRenderer.smooth(depth / UNION_FADE);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
