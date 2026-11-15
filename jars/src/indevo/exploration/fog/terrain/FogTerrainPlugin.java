package indevo.exploration.fog.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.fog.renderers.FogRenderer;
import indevo.exploration.fog.sim.FogGrid;
import indevo.exploration.meteor.MeteorSwarmManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FogTerrainPlugin extends BaseTerrain {

    public enum Kind {SPELL, BANK}

    public static final String TERRAIN_ID = "IndEvo_fog";
    public static final String EFFECT_CATEGORY = "IndEvo_fog";
    public static final float SENSOR_CUT = 0.9f;
    public static final float CACHE_SENSOR_CUT = 0.7f;
    public static final String CACHE_MOD_ID = "IndEvo_fog_cache";
    public static final float CONTAINS_THRESHOLD = 0.05f;
    public static final int GRID_CELLS = 128;
    public static final float CELL_SIZE = 80f;
    public static final int MAX_CLEAR_PATCHES = 16;
    public static final float PATCH_RADIUS_MULT = 2.6f;
    public static final float RING_SECONDS = 0.6f;
    public static final float KEEP_REFRESH_DAYS = 0.1f;
    public static final float JUMP_POINT_KEEP = 400f;
    public static final float STATION_KEEP = 300f;
    public static final float TELEPORT_DISTANCE = 600f;
    // meteor radius is twice the sprite, rocks cut a thinner trail than a fleet
    public static final float ROCK_RADIUS_MULT = 0.5f;
    public static final float ROCK_CUT = 0.8f;
    public static final int MAP_CELLS = 40;
    public static final float MAP_REACH = 1.4f;

    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_8);

    protected Kind kind;
    protected float radius;
    protected int seed;
    protected long start;
    protected float rollInDays;
    protected float holdDays;
    protected float fadeDays;
    protected SectorEntityToken cache;

    // view state, rebuilt while the player is in this system and never saved
    private transient FogGrid.Shape shape;
    private transient long keepStamp;
    private transient FogGrid grid;
    private transient Map<SectorEntityToken, Tracked> tracked;
    private transient List<Ring> rings;
    private transient Vector2f lastAnchor;
    private transient Vector2f anchorVel;
    private transient float[] patches;
    private transient int patchCount;
    private transient int frame;
    private transient float[][] colours;

    public static class FogParams {

        public final Kind kind;
        public final float radius;
        public final int seed;
        public final float rollInDays;
        public final float holdDays;
        public final float fadeDays;

        public FogParams(Kind kind, float radius, int seed, float rollInDays, float holdDays, float fadeDays) {
            this.kind = kind;
            this.radius = radius;
            this.seed = seed;
            this.rollInDays = rollInDays;
            this.holdDays = holdDays;
            this.fadeDays = fadeDays;
        }
    }

    private static class Tracked {

        final FogGrid.Mover mover = new FogGrid.Mover();
        float lastX, lastY;
        float ebLevel;
        boolean pulsed;
        float seen;
        int frame;
    }

    private static class Ring {

        final float x, y, range;
        float t, last;

        Ring(float x, float y, float range) {
            this.x = x;
            this.y = y;
            this.range = range;
        }
    }

    @Override
    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);
        if (!(param instanceof FogParams p)) throw new IllegalStateException("IndEvo_fog needs FogParams, got " + param);
        kind = p.kind;
        radius = p.radius;
        seed = p.seed;
        rollInDays = p.rollInDays;
        holdDays = p.holdDays;
        fadeDays = p.fadeDays;
        start = Global.getSector().getClock().getTimestamp();
        name = "Fog";
    }

    public Kind getKind() {
        return kind;
    }

    public float getRadius() {
        return radius;
    }

    public SectorEntityToken getCache() {
        return cache;
    }

    public void setCache(SectorEntityToken cache) {
        this.cache = cache;
        hideCache();
    }

    public float getStrength() {
        float e = getElapsedDays();
        if (e < rollInDays) return e / rollInDays;
        if (e < rollInDays + holdDays) return 1f;
        return Math.max(0f, 1f - (e - rollInDays - holdDays) / fadeDays);
    }

    public boolean isOver() {
        return getElapsedDays() >= rollInDays + holdDays + fadeDays;
    }

    public float getDensityAt(Vector2f point) {
        float x = point.x - entity.getLocation().x, y = point.y - entity.getLocation().y;
        if (grid != null && grid.inWindow(x, y)) return grid.density(x, y);
        return getShape().mask(x, y, getMaskTime()) * getStrength();
    }

    public float getRestMaskAt(float localX, float localY) {
        return getShape().mask(localX, localY, getMaskTime());
    }

    public static float getSensorMult(float density) {
        return 1f - SENSOR_CUT * Math.min(1f, Math.max(0f, density));
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (amount <= 0) return;

        if (isOver()) {
            dropView();
            if (cache != null && cache.isAlive()) Misc.fadeAndExpire(cache);
            entity.getContainingLocation().removeEntity(entity);
            return;
        }

        if (!entity.isInCurrentLocation()) {
            dropView();
            return;
        }

        advanceView(amount);
        hideCache();
    }

    // while the fog rolls in the cache counts as covered, it must not sit in the open first
    private void hideCache() {
        if (cache == null) return;
        if (!cache.isAlive()) {
            cache = null;
            return;
        }
        Vector2f loc = cache.getLocation();
        float density = getElapsedDays() < rollInDays
                ? getRestMaskAt(loc.x - entity.getLocation().x, loc.y - entity.getLocation().y)
                : getDensityAt(loc);
        cache.getDetectedRangeMod().modifyMult(CACHE_MOD_ID, 1f - CACHE_SENSOR_CUT * Math.min(1f, Math.max(0f, density)), "In fog");
    }

    @Override
    public String getEffectCategory() {
        return EFFECT_CATEGORY;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        return getDensityAt(point) > CONTAINS_THRESHOLD;
    }

    @Override
    public void applyEffect(SectorEntityToken entity, float days) {
        if (!(entity instanceof CampaignFleetAPI fleet)) return;
        float mult = getSensorMult(getDensityAt(fleet.getLocation()));
        fleet.getStats().addTemporaryModMult(0.1f, getModId() + "_1", "In fog", mult, fleet.getStats().getDetectedRangeMod());

        AbilityPlugin burst = fleet.getAbility(Abilities.SENSOR_BURST);
        if (burst != null && burst.isActiveOrInProgress()) return;
        fleet.getStats().addTemporaryModMult(0.1f, getModId() + "_2", "In fog", mult, fleet.getStats().getSensorRangeMod());
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public float getRenderRange() {
        return radius * 1.5f + GRID_CELLS * CELL_SIZE * 0.5f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (grid == null || layer != CampaignEngineLayers.TERRAIN_8) return;
        FogRenderer.render(grid, entity.getLocation(), viewport, patches, patchCount, getMaskTime(), getColours());
    }

    private float[][] getColours() {
        if (colours == null) colours = FogRenderer.coloursFor(entity.getContainingLocation());
        return colours;
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        float strength = getStrength();
        if (strength <= 0f) return;
        // coarse grid of the rest mask, so the map shows the bank's real outline
        float cx = entity.getLocation().x, cy = entity.getLocation().y;
        float reach = radius * MAP_REACH, step = reach * 2f / MAP_CELLS, time = getMaskTime();
        FogGrid.Shape s = getShape();
        float[] c = getColours()[2];
        float a = 0.35f * strength * alphaMult;
        float[] row = new float[MAP_CELLS + 1], next = new float[MAP_CELLS + 1];
        for (int i = 0; i <= MAP_CELLS; i++) row[i] = s.mask(-reach + i * step, -reach, time);

        boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = 0; j < MAP_CELLS; j++) {
            float y0 = -reach + j * step, y1 = y0 + step;
            for (int i = 0; i <= MAP_CELLS; i++) next[i] = s.mask(-reach + i * step, y1, time);
            for (int i = 0; i < MAP_CELLS; i++) {
                if (row[i] + row[i + 1] + next[i] + next[i + 1] <= 0f) continue;
                float x0 = -reach + i * step, x1 = x0 + step;
                mapVertex(c, a * row[i], cx + x0, cy + y0, factor);
                mapVertex(c, a * row[i + 1], cx + x1, cy + y0, factor);
                mapVertex(c, a * next[i + 1], cx + x1, cy + y1, factor);
                mapVertex(c, a * next[i], cx + x0, cy + y1, factor);
            }
            float[] swap = row;
            row = next;
            next = swap;
        }
        GL11.glEnd();
        if (textured) GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void mapVertex(float[] c, float a, float x, float y, float factor) {
        GL11.glColor4f(c[0], c[1], c[2], a);
        GL11.glVertex2f(x * factor, y * factor);
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;
        tooltip.addTitle(getTerrainName());
        tooltip.addPara(Global.getSettings().getDescription(getTerrainId(), Description.Type.TERRAIN).getText1(), pad);
        tooltip.addPara("Reduces the sensor range of fleets inside and the range at which they can be detected by up to %s.", pad,
                Misc.getHighlightColor(), Math.round(SENSOR_CUT * 100f) + "%");
        tooltip.addPara("Moving fleets stir the fog and leave trails, slow fleets fainter ones. Emergency Burn and Interdiction Pulse blow it clear for a short time. Active Sensor Burst ignores the sensor range penalty while it lasts.", pad);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && player.getContainingLocation() == entity.getContainingLocation()) {
            float mult = getSensorMult(getDensityAt(player.getLocation()));
            tooltip.addPara("Your fleet's sensor range and the range at which it can be detected are at %s.", pad,
                    Misc.getHighlightColor(), Math.round(mult * 100f) + "%");
        }
    }

    private float getElapsedDays() {
        return Global.getSector().getClock().getElapsedDaysSince(start);
    }

    private float getMaskTime() {
        return getElapsedDays() * Global.getSector().getClock().getSecondsPerDay();
    }

    private FogGrid.Shape getShape() {
        if (shape == null) {
            shape = new FogGrid.Shape(seed, radius, kind == Kind.SPELL);
            keepStamp = 0;
        }
        if (Global.getSector().getClock().getElapsedDaysSince(keepStamp) > KEEP_REFRESH_DAYS) {
            shape.keep = computeKeep();
            keepStamp = Global.getSector().getClock().getTimestamp();
        }
        return shape;
    }

    private float[] computeKeep() {
        LocationAPI loc = entity.getContainingLocation();
        Vector2f anchor = entity.getLocation();
        float reach = radius * MAP_REACH;
        List<Float> out = new ArrayList<>();

        Set<SectorEntityToken> marketEntities = new HashSet<>();
        if (kind == Kind.BANK) for (MarketAPI market : Misc.getMarketsInLocation(loc)) marketEntities.add(market.getPrimaryEntity());

        for (PlanetAPI planet : loc.getPlanets()) {
            float extra = marketEntities.contains(planet) ? STATION_KEEP : 0f;
            addKeep(out, planet, planet.getRadius() + extra, anchor, reach);
        }

        if (kind == Kind.BANK) {
            for (SectorEntityToken jp : loc.getJumpPoints()) addKeep(out, jp, jp.getRadius() + JUMP_POINT_KEEP, anchor, reach);
            for (SectorEntityToken station : loc.getEntitiesWithTag(Tags.STATION)) addKeep(out, station, station.getRadius() + STATION_KEEP, anchor, reach);
            for (SectorEntityToken market : marketEntities) {
                if (market != null && !(market instanceof PlanetAPI)) addKeep(out, market, market.getRadius() + STATION_KEEP, anchor, reach);
            }
        }

        float[] keep = new float[out.size()];
        for (int i = 0; i < keep.length; i++) keep[i] = out.get(i);
        return keep;
    }

    private void addKeep(List<Float> out, SectorEntityToken other, float keepRadius, Vector2f anchor, float reach) {
        float x = other.getLocation().x - anchor.x, y = other.getLocation().y - anchor.y;
        if (Math.hypot(x, y) - keepRadius * 1.7f > reach) return;
        out.add(x);
        out.add(y);
        out.add(keepRadius);
    }

    private void advanceView(float amount) {
        Vector2f anchor = entity.getLocation();
        Vector2f centre = Global.getSector().getViewport().getCenter();
        float cx = centre.x - anchor.x, cy = centre.y - anchor.y;

        if (kind == Kind.BANK && Math.hypot(cx, cy) > getRenderRange()) {
            dropView();
            return;
        }

        float strength = getStrength(), time = getMaskTime();
        FogGrid.Shape s = getShape();
        if (grid == null) {
            grid = new FogGrid(GRID_CELLS, CELL_SIZE, s, cx, cy, strength, time);
            tracked = new IdentityHashMap<>();
            rings = new ArrayList<>();
            patches = new float[MAX_CLEAR_PATCHES * 3];
            anchorVel = new Vector2f();
        }

        if (lastAnchor != null) anchorVel.set((anchor.x - lastAnchor.x) / amount, (anchor.y - lastAnchor.y) / amount);
        lastAnchor = new Vector2f(anchor);

        float dt = Math.min(amount, FogGrid.MAX_DT);
        grid.setStrength(strength);
        grid.recentre(cx, cy);
        feedMovers(amount, dt);

        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring ring = it.next();
            ring.t += dt;
            float r = ring.range * Math.min(1f, ring.t / RING_SECONDS);
            grid.ring(ring.x, ring.y, ring.last, r, dt);
            ring.last = r;
            if (ring.t >= RING_SECONDS) it.remove();
        }

        grid.step(dt, time);
    }

    private void feedMovers(float amount, float dt) {
        frame++;
        LocationAPI loc = entity.getContainingLocation();
        Vector2f anchor = entity.getLocation();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        patchCount = 0;

        for (CampaignFleetAPI fleet : loc.getFleets()) {
            float x = fleet.getLocation().x - anchor.x, y = fleet.getLocation().y - anchor.y;
            if (!grid.inWindow(x, y)) continue;
            Tracked t = track(fleet, x, y);

            FogGrid.Mover m = t.mover;
            m.x = x;
            m.y = y;
            m.vx = fleet.getVelocity().x - anchorVel.x;
            m.vy = fleet.getVelocity().y - anchorVel.y;
            m.r = fleet.getRadius();
            grid.mover(m, dt);

            readAbilities(fleet, t, x, y);

            boolean visible = fleet == player || fleet.getVisibilityLevelToPlayerFleet() != SectorEntityToken.VisibilityLevel.NONE;
            t.seen = fleet == player ? 1f : Math.max(0f, Math.min(1f, t.seen + (visible ? dt * 2f : -dt * 1.2f)));
            if (t.seen > 0f) addPatch(x, y, fleet.getRadius() * PATCH_RADIUS_MULT * t.seen, fleet == player);
        }

        for (PlanetAPI planet : loc.getPlanets()) moveBody(planet, planet.getRadius(), 1f, amount, dt);
        for (SectorEntityToken station : loc.getEntitiesWithTag(Tags.STATION)) {
            if (!(station instanceof CampaignFleetAPI)) moveBody(station, station.getRadius(), 1f, amount, dt);
        }
        // shower and rogue rocks move by script, so their speed is taken frame to frame like a planet
        for (SectorEntityToken rock : loc.getEntitiesWithTag(MeteorSwarmManager.METEOR_TAG)) {
            moveBody(rock, rock.getRadius() * ROCK_RADIUS_MULT, ROCK_CUT, amount, dt);
        }

        tracked.values().removeIf(t -> t.frame != frame);
    }

    private Tracked track(SectorEntityToken other, float x, float y) {
        Tracked t = tracked.get(other);
        if (t == null) {
            t = new Tracked();
            t.lastX = x;
            t.lastY = y;
            AbilityPlugin burn = other.getAbility(Abilities.EMERGENCY_BURN);
            t.ebLevel = burn != null ? burn.getLevel() : 0f;
            t.pulsed = other.getMemoryWithoutUpdate().getBoolean(MemFlags.JUST_DID_INTERDICTION_PULSE);
            tracked.put(other, t);
        }
        t.frame = frame;
        return t;
    }

    // no callback reports npc ability use, so the burn level and the pulse flag are read per fleet in the window
    private void readAbilities(CampaignFleetAPI fleet, Tracked t, float x, float y) {
        AbilityPlugin burn = fleet.getAbility(Abilities.EMERGENCY_BURN);
        float level = burn != null ? burn.getLevel() : 0f;
        if (t.ebLevel < 1f && level >= 1f) grid.blast(x, y, FogGrid.EB_RADIUS + fleet.getRadius(), 300f);
        t.ebLevel = level;

        boolean pulsed = fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.JUST_DID_INTERDICTION_PULSE);
        if (pulsed && !t.pulsed) rings.add(new Ring(x, y, InterdictionPulseAbility.getRange(fleet)));
        t.pulsed = pulsed;
    }

    private void moveBody(SectorEntityToken body, float bodyRadius, float cut, float amount, float dt) {
        float x = body.getLocation().x - entity.getLocation().x, y = body.getLocation().y - entity.getLocation().y;
        if (!grid.inWindow(x, y)) return;
        Tracked t = track(body, x, y);
        FogGrid.Mover m = t.mover;
        boolean jumped = Math.hypot(x - t.lastX, y - t.lastY) > TELEPORT_DISTANCE;
        m.vx = jumped ? 0f : (x - t.lastX) / amount;
        m.vy = jumped ? 0f : (y - t.lastY) / amount;
        m.x = x;
        m.y = y;
        m.r = bodyRadius;
        m.cut = cut;
        t.lastX = x;
        t.lastY = y;
        grid.mover(m, dt);
    }

    private void addPatch(float x, float y, float r, boolean player) {
        if (patchCount >= MAX_CLEAR_PATCHES) {
            if (!player) return;
            patchCount = MAX_CLEAR_PATCHES - 1;
        }
        patches[patchCount * 3] = x;
        patches[patchCount * 3 + 1] = y;
        patches[patchCount * 3 + 2] = r;
        patchCount++;
    }

    private void dropView() {
        grid = null;
        tracked = null;
        rings = null;
        lastAnchor = null;
        patchCount = 0;
    }
}
