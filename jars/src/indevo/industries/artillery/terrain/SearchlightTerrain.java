package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.artillery.renderers.LightConeRenderer;
import indevo.industries.artillery.renderers.SearchlightRenderer;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.industries.artillery.utils.VisionBlockers;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.List;

public class SearchlightTerrain extends BaseRingTerrain {

    public static final String TERRAIN_ID = "IndEvo_searchlights";
    public static final int SEES_MOVING = 0;
    public static final int SEES_STOPPED = 1;
    public static final int RINGS = 3;
    public static final int SPOTS_PER_RING = 2;
    public static final float[] RING_RADIUS_SHARE = {0.3125f, 0.5875f, 0.825f};
    public static final float[] RING_SPEED = {190f, 150f, 170f};
    public static final float SPOT_RADIUS_SHARE = 0.1625f;
    public static final float STOPPED_BURN = 2f;
    public static final float CROSSFADE_SECONDS = 0.6f;
    public static final float MIN_SEEING_STRENGTH = 0.5f;
    public static final float MIN_EYE_RATE = 0.02f;
    public static final float TRACK_SPEED = 160f;
    public static final float RETURN_SPEED = 260f;
    public static final float TRACK_RANGE_MULT = 1.3f;
    public static final float LOSE_SECONDS = 1.2f;
    public static final float LOCK_FADE_SECONDS = 0.4f;
    public static final float RING_SECONDS = 1.1f;
    public static final float FADE_SECONDS = 1f;
    public static final float BLOCKER_SECONDS = 0.1f;
    public static final float DIM_HALO = 0.35f;
    public static final float DIM_CORE = 0.2f;
    public static final float SEEING_BOOST = 0.3f;
    public static final float HALO_TINT = 0.45f;
    public static final float DUST_TINT = 0.5f;
    public static final float EMITTER_TINT = 0.7f;
    public static final float RIM_ALPHA = 0.24f;
    public static final float NEAR_START_SHARE = 0.7f;
    public static final float NEAR_WIDTH_SHARE = 0.6f;
    public static final float CORE_RADIUS_SHARE = 0.5f;
    public static final float CORE_ALPHA = 0.5f;
    public static final float CENTER_RADIUS_SHARE = 0.12f;
    public static final float CENTER_ALPHA = 0.55f;
    public static final float LAMP_RADIUS = 60f;
    public static final float LAMP_ALPHA = 0.55f;
    public static final float EMITTER_RENDER_RANGE = 500f;
    public static final Color ORANGE = new Color(255, 150, 55);
    public static final Color PURPLE = new Color(188, 124, 255);
    public static final Color WARM = new Color(255, 240, 214);
    public static final LightConeRenderer.Style LIGHT = new LightConeRenderer.Style();

    private static final float[] SPOT_ANGLE = {0f};

    protected float[] ringAngles = {(float) Math.random() * 360f, (float) Math.random() * 360f, (float) Math.random() * 360f};
    protected boolean lit = false;

    protected transient Spot[] spots;
    protected transient float still;
    protected transient float brightness;
    protected transient float pingCooldown;
    protected transient List<VisionBlockers.Blocker> blockers;
    protected transient float sinceBlockers;
    protected transient float dustTime;

    // offset from the tower, so a spot keeps its place while the tower orbits
    public static class Spot {
        public final int type;
        public float x;
        public float y;
        public boolean sees;
        public boolean locked;
        public float lost;
        public float seeing;
        public float lock;
        public float ring = RING_SECONDS;

        public Spot(int type) {
            this.type = type;
        }
    }

    static {
        LIGHT.peak = 0.21f;
        LIGHT.edgeStart = 1f;
        LIGHT.edgeEnd = 1.2f;
        LIGHT.farShare = 0.3f;
        LIGHT.falloff = 4f;
        LIGHT.endStart = 0.6f;
        LIGHT.nearSoftShare = 0.01f;
        LIGHT.shadow = 0.95f;
        LIGHT.sourceRadius = 50f;
        LIGHT.dustGain = 6f;
    }

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7);

        float range = getRange();
        spots = new Spot[RINGS * SPOTS_PER_RING];
        for (int i = 0; i < spots.length; i++) {
            spots[i] = new Spot((i / SPOTS_PER_RING + i % SPOTS_PER_RING) % 2);
            Vector2f home = getHome(i, range);
            spots[i].x = home.x;
            spots[i].y = home.y;
        }

        return this;
    }

    public static float getRange() {
        return Setting.ARTILLERY_WATCHTOWER_RANGE.value();
    }

    public static float getSpotRadius() {
        return getRange() * SPOT_RADIUS_SHARE;
    }

    public static boolean isStopped(CampaignFleetAPI fleet) {
        return fleet.getCurrBurnLevel() <= STOPPED_BURN;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }

    public boolean isLit() {
        return lit;
    }

    @Override
    public void advance(float amount) {
        if (amount <= 0) return;

        SectorEntityToken tower = params.relatedEntity;
        if (tower == null || !tower.isAlive()) {
            Misc.fadeAndExpire(entity, 0f);
            return;
        }

        float range = getRange();
        for (int i = 0; i < RINGS; i++) {
            float direction = i % 2 == 0 ? 1f : -1f;
            float turn = (float) Math.toDegrees(RING_SPEED[i] / (range * RING_RADIUS_SHARE[i]));
            ringAngles[i] = Misc.normalizeAngle(ringAngles[i] + direction * turn * amount);
        }

        brightness = clamp(brightness + (lit ? amount : -amount) / FADE_SECONDS);
        pingCooldown -= amount;
        dustTime += amount;

        sinceBlockers += amount;
        if (sinceBlockers >= BLOCKER_SECONDS) blockers = null;

        CampaignFleetAPI player = lit && entity.isInCurrentLocation() ? Global.getSector().getPlayerFleet() : null;
        if (player != null) still = clamp(still + (isStopped(player) ? amount : -amount) / CROSSFADE_SECONDS);

        for (int i = 0; i < spots.length; i++) advanceSpot(spots[i], getHome(i, range), player, range, amount);
    }

    // a spot that sees the player leaves its path and follows, out to TRACK_RANGE_MULT times the tower range
    private void advanceSpot(Spot spot, Vector2f goal, CampaignFleetAPI player, float range, float amount) {
        spot.sees = player != null && seesPlayer(spot, player);

        if (spot.sees) {
            if (!spot.locked) startLock(spot);
            spot.locked = true;
            spot.lost = 0f;
        } else if (spot.locked) {
            spot.lost += amount;
            if (player == null || spot.lost > LOSE_SECONDS) spot.locked = false;
        }

        spot.seeing = clamp(spot.seeing + (spot.sees ? amount : -amount) / LOCK_FADE_SECONDS);
        spot.lock = clamp(spot.lock + (spot.locked ? amount : -amount) / LOCK_FADE_SECONDS);
        if (spot.ring < RING_SECONDS) spot.ring += amount;

        float speed = RETURN_SPEED;
        if (spot.locked) {
            Vector2f tower = entity.getLocation();
            goal.set(player.getLocation().x - tower.x, player.getLocation().y - tower.y);
            float dist = goal.length();
            float limit = range * TRACK_RANGE_MULT;
            if (dist > limit) goal.scale(limit / dist);
            speed = TRACK_SPEED;
        }

        float dx = goal.x - spot.x;
        float dy = goal.y - spot.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float step = speed * amount;

        if (dist <= step) {
            spot.x = goal.x;
            spot.y = goal.y;
        } else {
            spot.x += dx / dist * step;
            spot.y += dy / dist * step;
        }
    }

    // the cooldown keeps a fleet on the edge of a spot from pinging every frame
    private void startLock(Spot spot) {
        spot.ring = 0f;
        if (pingCooldown > 0f) return;

        pingCooldown = DroneNetScript.LOCK_PING_COOLDOWN;
        Global.getSoundPlayer().playUISound(DroneNetScript.LOCK_SOUND_ID, 1f, DroneNetScript.LOCK_SOUND_VOLUME);
    }

    private Vector2f getHome(int index, float range) {
        int ring = index / SPOTS_PER_RING;
        double rad = Math.toRadians(ringAngles[ring] + 360f * (index % SPOTS_PER_RING) / SPOTS_PER_RING);
        float radius = range * RING_RADIUS_SHARE[ring];
        return new Vector2f((float) Math.cos(rad) * radius, (float) Math.sin(rad) * radius);
    }

    private Vector2f getLocation(Spot spot) {
        Vector2f tower = entity.getLocation();
        return new Vector2f(tower.x + spot.x, tower.y + spot.y);
    }

    // chaff clouds only, a spot is a lit area and planets throw no shadow in it
    private List<VisionBlockers.Blocker> getBlockers() {
        if (blockers == null) {
            blockers = VisionBlockers.collectSmoke(entity.getContainingLocation());
            sinceBlockers = 0f;
        }

        return blockers;
    }

    // 1 for the type that sees the player's fleet as it flies now, eased over the crossfade
    public float getStrength(int type) {
        float stopped = SearchlightRenderer.smooth(still);
        return type == SEES_STOPPED ? stopped : 1f - stopped;
    }

    private boolean covers(Spot spot, Vector2f point) {
        Vector2f loc = getLocation(spot);
        return Misc.getDistance(loc, point) < getSpotRadius() && !VisionBlockers.isBlocked(getBlockers(), loc, point);
    }

    // what a light sees follows its brightness, so a dim light never catches the player
    private boolean seesPlayer(Spot spot, CampaignFleetAPI player) {
        return getStrength(spot.type) > MIN_SEEING_STRENGTH && covers(spot, player.getLocation());
    }

    public boolean seesPlayer() {
        if (!lit) return false;

        for (Spot spot : spots) if (spot.sees) return true;
        return false;
    }

    // 1 to 2 from the edge of a lit spot to its middle, below 0 when no spot fills the eye
    public float getPlayerRate() {
        if (!lit || !entity.isInCurrentLocation()) return -1f;

        Vector2f at = Global.getSector().getPlayerFleet().getLocation();
        float radius = getSpotRadius();
        float best = 0f;

        for (Spot spot : spots) {
            if (!covers(spot, at)) continue;
            best = Math.max(best, (2f - Misc.getDistance(getLocation(spot), at) / radius) * getStrength(spot.type));
        }

        return best > MIN_EYE_RATE ? best : -1f;
    }

    public boolean sees(CampaignFleetAPI fleet) {
        if (!lit) return false;

        int type = isStopped(fleet) ? SEES_STOPPED : SEES_MOVING;
        for (Spot spot : spots) if (spot.type == type && covers(spot, fleet.getLocation())) return true;
        return false;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        if (!lit) return false;

        for (Spot spot : spots) if (covers(spot, point)) return true;
        return false;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (brightness <= 0f) return;

        float alpha = viewport.getAlphaMult() * brightness;
        float radius = getSpotRadius();
        Vector2f tower = entity.getLocation();
        boolean drawEmitters = viewport.isNearViewport(tower, EMITTER_RENDER_RANGE);

        for (Spot spot : spots) {
            float strength = getStrength(spot.type);
            float boost = 1f + SEEING_BOOST * SearchlightRenderer.smooth(spot.seeing);
            float halo = alpha * (DIM_HALO + (1f - DIM_HALO) * strength) * boost;
            float core = alpha * (DIM_CORE + (1f - DIM_CORE) * strength) * boost;
            Color color = Misc.interpolateColor(spot.type == SEES_STOPPED ? PURPLE : ORANGE, DroneConeTerrain.LOCK_COLOR,
                    SearchlightRenderer.smooth(spot.lock));
            Color tint = Misc.interpolateColor(WARM, color, HALO_TINT);

            // the hints toward the tower fade while the spot covers it
            float dist = (float) Math.sqrt(spot.x * spot.x + spot.y * spot.y);
            float near = SearchlightRenderer.smooth((dist - radius * NEAR_START_SHARE) / (radius * NEAR_WIDTH_SHARE));
            float angle = (float) Math.atan2(spot.y, spot.x);

            if (drawEmitters) SearchlightRenderer.drawEmitter(tower, angle, dist, radius, Misc.interpolateColor(WARM, color, EMITTER_TINT), halo * near);

            Vector2f loc = getLocation(spot);
            if (!viewport.isNearViewport(loc, radius * LightConeRenderer.DRAWN_LENGTH_MULT)) continue;

            LightConeRenderer.draw(loc, 0f, 360f, radius, getBlockers(), LIGHT, tint, halo);
            LightConeRenderer.drawDust(viewport, loc, SPOT_ANGLE, 360f, radius, getBlockers(), LIGHT,
                    Misc.interpolateColor(WARM, color, DUST_TINT), halo, dustTime);
            SearchlightRenderer.drawRim(loc, radius, angle + (float) Math.PI, tint, halo * near * RIM_ALPHA);
            SearchlightRenderer.drawGlow(loc, radius * CORE_RADIUS_SHARE, color, core * CORE_ALPHA);
            SearchlightRenderer.drawGlow(loc, radius * CENTER_RADIUS_SHARE, Color.WHITE, core * CENTER_ALPHA);

            if (spot.ring < RING_SECONDS) SearchlightRenderer.drawRing(loc, radius, spot.ring / RING_SECONDS, DroneConeTerrain.LOCK_COLOR, alpha);
        }

        if (drawEmitters) SearchlightRenderer.drawGlow(tower, LAMP_RADIUS,
                Misc.interpolateColor(ORANGE, PURPLE, SearchlightRenderer.smooth(still)), alpha * LAMP_ALPHA);
    }

    @Override
    public float getRenderRange() {
        return getRange() * TRACK_RANGE_MULT + getSpotRadius() + 1000f;
    }

    @Override
    protected boolean shouldCheckFleetsToApplyEffect() {
        return false;
    }

    @Override
    public boolean canPlayerHoldStationIn() {
        return false;
    }

    @Override
    public String getTerrainName() {
        return "Searchlight";
    }

    @Override
    public String getNameForTooltip() {
        return "Searchlight";
    }

    @Override
    public Color getNameColor() {
        if (!seesPlayer()) return Misc.getHighlightColor();

        return Misc.interpolateColor(Misc.getHighlightColor(), Misc.getNegativeHighlightColor(),
                Global.getSector().getCampaignUI().getSharedFader().getBrightness());
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Searchlight");
        tooltip.addPara("Orange lights see fleets that %s, purple lights see fleets that %s. Only the bright ones see your fleet.",
                10f, Misc.getHighlightColor(), "move", "hold at burn " + Math.round(STOPPED_BURN) + " or less");
        tooltip.addPara("A light that sees a fleet %s, follows it and sends its position to the artillery in this system.",
                10f, Misc.getNegativeHighlightColor(), "turns red");
        tooltip.addPara("Chaff clouds block the lights.", 10f);
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public float getTooltipWidth() {
        return 350f;
    }

    @Override
    public String getEffectCategory() {
        return TERRAIN_ID;
    }

    @Override
    public float getMaxEffectRadius(Vector2f locFrom) {
        return getRange() * TRACK_RANGE_MULT + getSpotRadius();
    }

    @Override
    public float getMinEffectRadius(Vector2f locFrom) {
        return 0f;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
