package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.terrain.DroneConeTerrain;
import indevo.industries.artillery.utils.ArtilleryStationPlacer;
import indevo.industries.artillery.utils.VisionBlockers;
import indevo.items.consumables.entityAbilities.DecoyMineAbility;
import indevo.items.consumables.fleet.MissileMemFlags;
import indevo.utils.trails.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DroneNetScript implements EveryFrameScript {

    public static class Spotter {
        public SectorEntityToken entity;
        public SectorEntityToken nest;
        public SectorEntityToken decoy;
        public Vector2f waypoint;
        public Vector2f lastSeen;
        public float heading;
        public float turnRate;
        public float speed;
        public float coneAngle;
        public float sweep;
        public float noticed;
        public float unseen;
        public float fuse = -1f;
        public boolean seesPlayer;
        public boolean following;
        public long lostAt;
        public transient float alert;
        public transient float[] trailIds;
    }

    public static final String MEM_KEY = "$IndEvo_droneNet";
    public static final String NEST_ENTITY_ID = "IndEvo_DroneNest";
    public static final String SPOTTER_ENTITY_ID = "IndEvo_SpotterDrone";
    public static final String DECOY_ENTITY_ID = "IndEvo_decoy_mine";
    public static final String NEST_DROP_GROUP = "IndEvo_droneNest";
    public static final String EXPLOSION_SOUND_ID = "IndEvo_mortar_hit";
    public static final Color EXPLOSION_COLOR = new Color(255, 160, 60);
    public static final Color ENGINE_COLOR = new Color(255, 125, 25);

    public static final float CHECK_SECONDS = 0.25f;
    public static final float FADE_SECONDS = 1f;
    public static final int SPOTTERS_PER_NEST = 2;
    public static final float NEST_ZONE_RADIUS = 300f;
    public static final float RELAUNCH_DAYS = 3f;
    public static final int MIN_NEST_RELICS = 6;
    public static final int MAX_NEST_RELICS = 10;
    public static final float DEBRIS_RADIUS = 150f;
    public static final float DEBRIS_DAYS = 30f;
    public static final long DEBRIS_SALVAGE_XP = 250;

    public static final float CONE_ARC = 50f;
    public static final float SWEEP_ARC = 30f;
    public static final float SWEEP_SECONDS = 8f;
    public static final float CONE_TURN_RATE = 30f;
    public static final float CONE_TURN_GAIN = 2f;
    public static final float NOTICE_SECONDS = 0.5f;
    public static final float LOSE_SECONDS = 3f;
    public static final float ALERT_SECONDS = 0.6f;
    public static final float FOLLOW_FRACTION = 0.45f;
    public static final float MIN_PROFILE_MULT = 0.5f;
    public static final float MAX_PROFILE_MULT = 1.5f;
    public static final float PROFILE_BASE = 1000f;
    public static final float CONE_RESIZE_SECONDS = 0.5f;

    public static final float ROAM_BURN = 6f;
    public static final float FOLLOW_BURN = 7f;
    public static final float ACCELERATION = 90f;
    public static final float TURN_RATE = 50f;
    public static final float TURN_GAIN = 1.5f;
    public static final float TURN_ACCELERATION = 150f;
    public static final float TURN_SLOWDOWN = 0.5f;
    public static final float SLOW_DISTANCE = 300f;
    public static final float ROAM_RADIUS = 4000f;
    public static final float WAYPOINT_REACHED = 250f;
    public static final float DECOY_STANDOFF = 300f;
    public static final float ENGINE_BACK = 9.6f;
    public static final float ENGINE_SIDE = 6f;
    public static final float TRAIL_WIDTH = 2.5f;
    public static final float TRAIL_SECONDS = 0.45f;
    public static final float TRAIL_MIN_SPEED = 20f;

    public static final float CHAIN_RADIUS = 400f;
    public static final float CHAIN_DELAY = 0.2f;
    public static final float SPOTTER_EXPLOSION_SIZE = 70f;
    public static final float NEST_EXPLOSION_SIZE = 140f;

    private final StarSystemAPI system;
    private final String factionId;
    private final List<SectorEntityToken> nests = new ArrayList<>();
    private final List<Spotter> spotters = new ArrayList<>();
    private SectorEntityToken cones;

    private boolean active;
    private boolean done;
    private float playerFraction = -1f;
    private float coneLength = Setting.ARTILLERY_DRONE_CONE_LENGTH.value();
    private transient float coneTarget;
    private transient float coneRate;
    private float visibility;
    private float sinceCheck;
    private transient List<VisionBlockers.Blocker> blockers;

    public DroneNetScript(StarSystemAPI system, String factionId) {
        this.system = system;
        this.factionId = factionId;
    }

    public static DroneNetScript getInstanceOrRegister(StarSystemAPI system, String factionId) {
        DroneNetScript script = get(system);
        if (script != null) return script;

        script = new DroneNetScript(system, factionId);
        system.addScript(script);
        system.getMemoryWithoutUpdate().set(MEM_KEY, script);
        return script;
    }

    public static DroneNetScript get(LocationAPI loc) {
        return loc.getMemoryWithoutUpdate().get(MEM_KEY) instanceof DroneNetScript script ? script : null;
    }

    public static void reportBlast(LocationAPI loc, Vector2f point, float radius) {
        DroneNetScript net = get(loc);
        if (net != null) net.light(point, radius, 0f);
    }

    public void addNest(float angle, float radius, float orbitDays, Random random) {
        SectorEntityToken center = system.getCenter();
        SectorEntityToken nest = system.addCustomEntity(Misc.genUID(), null, NEST_ENTITY_ID, factionId, null);
        nest.setOrbit(Global.getFactory().createCircularOrbitWithSpin(center, angle, radius, orbitDays, random.nextFloat() * 10f + 1f));
        if (Misc.getMarketsInLocation(system).isEmpty()) MiscellaneousThemeGenerator.makeDiscoverable(nest, 200f, 2000f);

        // the orbit has not moved the nest yet at placement
        Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
        loc.scale(radius);
        Vector2f.add(loc, center.getLocation(), loc);
        nest.setLocation(loc.x, loc.y);
        nests.add(nest);

        for (int i = 0; i < SPOTTERS_PER_NEST; i++) {
            Spotter spotter = new Spotter();
            spotter.nest = nest;
            spotters.add(spotter);
            launch(spotter);
        }
    }

    public void destroyNest(SectorEntityToken nest, boolean withDebris) {
        if (!nests.remove(nest)) return;

        spawnExplosion(nest.getLocation(), NEST_EXPLOSION_SIZE);
        if (withDebris) addDebris(nest);
        Misc.fadeAndExpire(nest, 1f);
    }

    public void addDebris(SectorEntityToken nest) {
        DebrisFieldTerrainPlugin.DebrisFieldParams params = new DebrisFieldTerrainPlugin.DebrisFieldParams(DEBRIS_RADIUS, 1f, DEBRIS_DAYS, 0f);
        params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED;
        params.baseSalvageXP = DEBRIS_SALVAGE_XP;

        SectorEntityToken debris = Misc.addDebrisField(system, params, Misc.random);
        debris.setDiscoverable(null);
        debris.setDiscoveryXP(null);
        debris.getDropValue().clear();
        debris.addDropRandom(createNestDrop());

        if (nest.getOrbit() != null) debris.setOrbit(nest.getOrbit().makeCopy());
        debris.setLocation(nest.getLocation().x, nest.getLocation().y);
    }

    public static SalvageEntityGenDataSpec.DropData createNestDrop() {
        SalvageEntityGenDataSpec.DropData drop = new SalvageEntityGenDataSpec.DropData();
        drop.group = NEST_DROP_GROUP;
        drop.chances = MIN_NEST_RELICS;
        drop.maxChances = MAX_NEST_RELICS;
        return drop;
    }

    public boolean isActive() {
        return active;
    }

    public float getPlayerFraction() {
        return playerFraction;
    }

    public List<Spotter> getSpotters() {
        return Collections.unmodifiableList(spotters);
    }

    public List<SectorEntityToken> getNests() {
        return Collections.unmodifiableList(nests);
    }

    public List<VisionBlockers.Blocker> getBlockers() {
        if (blockers == null) blockers = VisionBlockers.collect(system);
        return blockers;
    }

    public float getConeLength() {
        return coneLength;
    }

    public float getVisibility() {
        return visibility;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        if (!system.isCurrentLocation()) {
            playerFraction = -1f;
            return;
        }

        sinceCheck += amount;
        if (sinceCheck >= CHECK_SECONDS) {
            check(sinceCheck);
            sinceCheck = 0f;
            if (done) return;
        }

        visibility = MathUtils.clamp(visibility + (active ? amount : -amount) / FADE_SECONDS, 0f, 1f);

        float gap = coneTarget - coneLength;
        float step = coneRate * amount;
        if (coneRate > 0f) coneLength = Math.abs(gap) <= step ? coneTarget : coneLength + Math.signum(gap) * step;

        for (Spotter spotter : spotters) {
            if (spotter.entity == null) continue;

            if (spotter.fuse >= 0f) {
                spotter.fuse -= amount;
                if (spotter.fuse < 0f) {
                    explode(spotter);
                    continue;
                }
            }

            spotter.alert = MathUtils.clamp(spotter.alert + (spotter.seesPlayer ? amount : -amount) / ALERT_SECONDS, 0f, 1f);
            move(spotter, amount);
        }
    }

    private void check(float elapsed) {
        if (hasPlayerMarket()) {
            convertToWatchtowers();
            return;
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        popBonked();
        popPulsed();
        relaunch();

        if (nests.isEmpty() && getLiveSpotterCount() == 0) {
            finish();
            return;
        }

        active = !system.getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)
                && ArtilleryStationEntityPlugin.needsVisionAgainstPlayer(system);
        blockers = VisionBlockers.collect(system);
        setConeTarget(getConeLengthFor(player));
        playerFraction = -1f;

        List<SectorEntityToken> decoys = getDecoys();
        for (Spotter spotter : spotters) {
            if (spotter.entity == null) continue;
            spotter.decoy = pickDecoy(spotter, decoys);
            look(spotter, player, elapsed);
        }

        ensureCones();
        if (!active) return;

        for (SectorEntityToken nest : nests) {
            float dist = Misc.getDistance(nest, player);
            if (dist < NEST_ZONE_RADIUS && !VisionBlockers.isBlocked(blockers, nest.getLocation(), player.getLocation()))
                playerFraction = Math.max(playerFraction, 1f - dist / NEST_ZONE_RADIUS);
        }

        markHostileFleets();
    }

    private boolean hasPlayerMarket() {
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market.isPlayerOwned()) return true;
        }

        return false;
    }

    private void convertToWatchtowers() {
        for (SectorEntityToken nest : nests) Misc.fadeAndExpire(nest, 1f);
        for (Spotter spotter : spotters) {
            if (spotter.entity != null) Misc.fadeAndExpire(spotter.entity, 1f);
        }

        nests.clear();
        spotters.clear();
        finish();

        system.getMemoryWithoutUpdate().set(Ids.MEM_SYSTEM_ARTILLERY_VISION, ArtilleryStationPlacer.VisionType.WATCHTOWER.name());
        ArtilleryStationPlacer.placeTowers(system, factionId, WatchtowerEntityPlugin.ENTITY_ID, new Random());
    }

    private void popBonked() {
        for (Spotter spotter : spotters) {
            if (spotter.entity != null && spotter.entity.getMemoryWithoutUpdate().contains(MissileMemFlags.MEM_CAUGHT_BY_MISSILE)) light(spotter, 0f);
        }

        for (SectorEntityToken nest : new ArrayList<>(nests)) {
            if (nest.getMemoryWithoutUpdate().contains(MissileMemFlags.MEM_CAUGHT_BY_MISSILE)) destroyNest(nest, true);
        }
    }

    // no listener or target flag for the vanilla pulse, the pulsing fleet carries this flag for 0.1 days
    private void popPulsed() {
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.JUST_DID_INTERDICTION_PULSE))
                light(fleet.getLocation(), InterdictionPulseAbility.getRange(fleet), 0f);
        }
    }

    private void light(Vector2f point, float radius, float delay) {
        for (Spotter spotter : spotters) {
            if (spotter.entity != null && Misc.getDistance(point, spotter.entity.getLocation()) <= radius) light(spotter, delay);
        }
    }

    private void light(Spotter spotter, float delay) {
        if (spotter.fuse < 0f || spotter.fuse > delay) spotter.fuse = delay;
    }

    private void explode(Spotter spotter) {
        Vector2f loc = new Vector2f(spotter.entity.getLocation());

        spawnExplosion(loc, SPOTTER_EXPLOSION_SIZE);
        Misc.fadeAndExpire(spotter.entity, 0.1f);

        spotter.entity = null;
        spotter.decoy = null;
        spotter.fuse = -1f;
        spotter.seesPlayer = false;
        spotter.following = false;
        spotter.lastSeen = null;
        spotter.lostAt = Global.getSector().getClock().getTimestamp();

        light(loc, CHAIN_RADIUS, CHAIN_DELAY);
    }

    private void spawnExplosion(Vector2f loc, float size) {
        VariableExplosionEntityPlugin.VariableExplosionParams params = new VariableExplosionEntityPlugin.VariableExplosionParams(
                EXPLOSION_SOUND_ID, false, 0f, EXPLOSION_COLOR, system, loc, size, 0.5f);
        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.NONE;

        SectorEntityToken explosion = system.addCustomEntity(Misc.genUID(), "Explosion", "IndEvo_VariableExplosion", Factions.NEUTRAL, params);
        explosion.setLocation(loc.x, loc.y);
    }

    private void relaunch() {
        Iterator<Spotter> iterator = spotters.iterator();
        while (iterator.hasNext()) {
            Spotter spotter = iterator.next();
            if (spotter.entity != null) continue;

            if (!nests.contains(spotter.nest)) iterator.remove();
            else if (Global.getSector().getClock().getElapsedDaysSince(spotter.lostAt) >= RELAUNCH_DAYS) launch(spotter);
        }
    }

    private void launch(Spotter spotter) {
        Vector2f loc = spotter.nest.getLocation();
        SectorEntityToken drone = system.addCustomEntity(Misc.genUID(), null, SPOTTER_ENTITY_ID, factionId, null);
        drone.setLocation(loc.x, loc.y);

        spotter.entity = drone;
        spotter.waypoint = null;
        spotter.lastSeen = null;
        spotter.heading = Misc.random.nextFloat() * 360f;
        spotter.turnRate = 0f;
        spotter.speed = 0f;
        spotter.coneAngle = spotter.heading;
        spotter.noticed = 0f;
        spotter.unseen = 0f;
        spotter.fuse = -1f;
        spotter.seesPlayer = false;
        spotter.following = false;
        drone.setFacing(spotter.heading);
    }

    private int getLiveSpotterCount() {
        int count = 0;
        for (Spotter spotter : spotters) if (spotter.entity != null) count++;
        return count;
    }

    private float getConeLengthFor(CampaignFleetAPI fleet) {
        float profile = fleet.getStats().getDetectedRangeMod().computeEffective(PROFILE_BASE) / PROFILE_BASE;
        return Setting.ARTILLERY_DRONE_CONE_LENGTH.value() * MathUtils.clamp(profile, MIN_PROFILE_MULT, MAX_PROFILE_MULT);
    }

    // drawing and sight use the same length, so the cone on screen is the cone that sees
    private void setConeTarget(float target) {
        if (target == coneTarget) return;

        coneTarget = target;
        coneRate = Math.abs(target - coneLength) / CONE_RESIZE_SECONDS;
    }

    private void ensureCones() {
        if (cones != null && !cones.isExpired() && cones.getContainingLocation() == system) return;

        cones = system.addTerrain(DroneConeTerrain.TERRAIN_ID, new BaseRingTerrain.RingParams(1f, 0f, system.getCenter(), "Spotter Cones"));
        cones.setCircularOrbit(system.getCenter(), 0f, 0f, 100f);
    }

    private List<SectorEntityToken> getDecoys() {
        List<SectorEntityToken> decoys = new ArrayList<>();
        for (CustomCampaignEntityAPI entity : system.getCustomEntities()) {
            if (DECOY_ENTITY_ID.equals(entity.getCustomEntityType()) && !entity.isExpired()) decoys.add(entity);
        }

        return decoys;
    }

    private SectorEntityToken pickDecoy(Spotter spotter, List<SectorEntityToken> decoys) {
        SectorEntityToken closest = null;
        float closestDist = DecoyMineAbility.BASE_RANGE;

        for (SectorEntityToken decoy : decoys) {
            float dist = Misc.getDistance(decoy, spotter.entity);
            if (dist > closestDist) continue;

            closestDist = dist;
            closest = decoy;
        }

        return closest;
    }

    private void look(Spotter spotter, CampaignFleetAPI player, float elapsed) {
        spotter.seesPlayer = active && sees(spotter, player, coneLength);

        if (spotter.seesPlayer) {
            float dist = Misc.getDistance(spotter.entity, player);
            playerFraction = Math.max(playerFraction, MathUtils.clamp(1f - dist / coneLength, 0f, 1f));
            spotter.noticed += elapsed;
            spotter.unseen = 0f;

            // a short graze fills the eye but does not pull the spotter off its route
            if (spotter.noticed >= NOTICE_SECONDS) {
                spotter.following = true;
                spotter.lastSeen = new Vector2f(player.getLocation());
            }
            return;
        }

        spotter.noticed = 0f;
        if (!spotter.following) return;

        spotter.unseen += elapsed;
        if (!active || spotter.unseen >= LOSE_SECONDS) {
            spotter.following = false;
            spotter.unseen = 0f;
            spotter.waypoint = spotter.lastSeen;
            spotter.lastSeen = null;
        }
    }

    private boolean sees(Spotter spotter, SectorEntityToken target, float length) {
        Vector2f from = spotter.entity.getLocation();
        float dist = Misc.getDistance(from, target.getLocation());
        if (dist > length + target.getRadius()) return false;
        if (dist > target.getRadius() + spotter.entity.getRadius() && !Misc.isInArc(spotter.coneAngle, CONE_ARC, from, target.getLocation())) return false;

        return !VisionBlockers.isBlocked(blockers, from, target.getLocation());
    }

    private void markHostileFleets() {
        float length = Setting.ARTILLERY_DRONE_CONE_LENGTH.value();

        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (fleet.isPlayerFleet() || fleet.isStationMode() || fleet.getAI() == null) continue;
            if (!fleet.getFaction().isHostileTo(factionId)) continue;

            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            if (mem.getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY)) continue;

            String source = getSourceSeeing(fleet, length);
            if (source == null) continue;

            mem.set(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY, true, EyeIndicatorScript.BASE_NPC_KNOWN_DURATION);
            fleet.addFloatingText("Detected by " + source, Misc.getNegativeHighlightColor(), 1f);
        }
    }

    private String getSourceSeeing(CampaignFleetAPI fleet, float length) {
        for (Spotter spotter : spotters) {
            if (spotter.entity != null && sees(spotter, fleet, length)) return "Spotter Drone";
        }

        for (SectorEntityToken nest : nests) {
            if (Misc.getDistance(nest, fleet) < NEST_ZONE_RADIUS && !VisionBlockers.isBlocked(blockers, nest.getLocation(), fleet.getLocation()))
                return "Drone Nest";
        }

        return null;
    }

    private void finish() {
        done = true;
        active = false;
        playerFraction = -1f;
        if (cones != null) system.removeEntity(cones);
        system.getMemoryWithoutUpdate().unset(MEM_KEY);
    }

    // the spotter only knows where it last saw the player, never where the player is now
    private void move(Spotter spotter, float amount) {
        SectorEntityToken drone = spotter.entity;
        Vector2f loc = drone.getLocation();
        Vector2f focus = spotter.decoy != null ? spotter.decoy.getLocation() : spotter.following ? spotter.lastSeen : null;
        Vector2f dest;
        float wantedSpeed;

        if (focus != null) {
            float standoff = spotter.decoy != null ? DECOY_STANDOFF : coneLength * FOLLOW_FRACTION;
            dest = focus;
            // closes to the standoff and then holds, it never backs off from an approaching fleet
            wantedSpeed = Misc.getSpeedForBurnLevel(FOLLOW_BURN) * MathUtils.clamp((Misc.getDistance(loc, focus) - standoff) / SLOW_DISTANCE, 0f, 1f);
        } else {
            if (spotter.waypoint == null || Misc.getDistance(loc, spotter.waypoint) < WAYPOINT_REACHED) spotter.waypoint = pickWaypoint(spotter);
            dest = spotter.waypoint;
            wantedSpeed = Misc.getSpeedForBurnLevel(ROAM_BURN);
        }

        float coneOffset = MathUtils.getShortestRotation(spotter.heading, spotter.coneAngle);
        float turn = Misc.getDistance(loc, dest) > 1f ? MathUtils.getShortestRotation(spotter.heading, Misc.getAngleInDegrees(loc, dest)) : 0f;
        wantedSpeed *= 1f - TURN_SLOWDOWN * Math.min(1f, Math.abs(turn) / 90f);

        // TURN_ACCELERATION above TURN_GAIN * TURN_RATE, or the heading overshoots and swings back
        float wantedTurnRate = MathUtils.clamp(turn * TURN_GAIN, -TURN_RATE, TURN_RATE);
        spotter.turnRate += MathUtils.clamp(wantedTurnRate - spotter.turnRate, -TURN_ACCELERATION * amount, TURN_ACCELERATION * amount);
        spotter.heading = Misc.normalizeAngle(spotter.heading + spotter.turnRate * amount);
        spotter.speed += MathUtils.clamp(wantedSpeed - spotter.speed, -ACCELERATION * amount, ACCELERATION * amount);

        Vector2f step = Misc.getUnitVectorAtDegreeAngle(spotter.heading);
        step.scale(spotter.speed * amount);
        drone.setLocation(loc.x + step.x, loc.y + step.y);
        drone.setFacing(spotter.heading);

        float wantedOffset;
        if (focus != null) {
            wantedOffset = MathUtils.getShortestRotation(spotter.heading, Misc.getAngleInDegrees(loc, focus));
        } else {
            spotter.sweep += amount;
            wantedOffset = (float) Math.sin(spotter.sweep * 2f * Math.PI / SWEEP_SECONDS) * SWEEP_ARC;
        }

        // the lamp turns on the hull, so the cone swings with the heading and no faster than CONE_TURN_RATE on top
        coneOffset += MathUtils.clamp(MathUtils.getShortestRotation(coneOffset, wantedOffset) * CONE_TURN_GAIN, -CONE_TURN_RATE, CONE_TURN_RATE) * amount;
        spotter.coneAngle = Misc.normalizeAngle(spotter.heading + coneOffset);

        if (spotter.speed < TRAIL_MIN_SPEED) return;

        if (spotter.trailIds == null) spotter.trailIds = new float[]{MagicCampaignTrailPlugin.getUniqueID(), MagicCampaignTrailPlugin.getUniqueID()};
        for (int i = 0; i < 2; i++) {
            MagicCampaignTrailPlugin.AddTrailMemberSimple(drone, spotter.trailIds[i], Global.getSettings().getSprite("fx", "IndEvo_stream_core"),
                    getEnginePoint(spotter, ENGINE_BACK, i == 0 ? ENGINE_SIDE : -ENGINE_SIDE), 0f, spotter.heading, TRAIL_WIDTH, 0.5f, ENGINE_COLOR, 0.5f, TRAIL_SECONDS, true, new Vector2f(0f, 0f));
        }
    }

    // offsets from the picket hull file (engines 22 px back, 15 px to each side) at the entity's 0.4 sprite scale
    public static Vector2f getEnginePoint(Spotter spotter, float back, float side) {
        Vector2f loc = spotter.entity.getLocation();
        double rad = Math.toRadians(spotter.heading);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vector2f(loc.x - cos * back - sin * side, loc.y - sin * back + cos * side);
    }

    public static float getThrottle(Spotter spotter) {
        return MathUtils.clamp(spotter.speed / Misc.getSpeedForBurnLevel(FOLLOW_BURN), 0f, 1f);
    }

    private Vector2f pickWaypoint(Spotter spotter) {
        Vector2f home = nests.contains(spotter.nest) ? spotter.nest.getLocation() : spotter.entity.getLocation();
        return Misc.getPointWithinRadius(home, ROAM_RADIUS);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
