package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.InterdictionPulseAbility;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.industries.artillery.terrain.DroneConeTerrain;
import indevo.items.consumables.fleet.MissileMemFlags;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DroneNetScript implements EveryFrameScript {

    public static class Nest {
        public SectorEntityToken anchor;
        public CampaignFleetAPI fleet;
        public boolean awake;
    }

    public static class Spotter {
        public CampaignFleetAPI fleet;
        public Vector2f waypoint;
        public float coneAngle;
        public float sweep;
        public float seen;
        public float unseen;
        public boolean locked;
        public long lostAt;
        public float respawnDays;
    }

    public static final String MEM_KEY = "$IndEvo_droneNet";
    public static final String MEM_INTERDICTED = "$IndEvo_droneInterdicted";
    public static final String TAG_DRONE = "IndEvo_netDrone";
    public static final String NEST_ENTITY_ID = "IndEvo_DroneNest";
    public static final String SPOTTER_VARIANT = "picket_Assault";
    public static final String MOD_ID = "IndEvo_droneNet";
    public static final String EXPLOSION_SOUND_ID = "IndEvo_mortar_hit";
    public static final Color EXPLOSION_COLOR = new Color(255, 160, 60);

    public static final float CHECK_SECONDS = 0.25f;
    public static final float FADE_SECONDS = 1f;
    public static final float CONE_ARC = 50f;
    public static final float SWEEP_ARC = 30f;
    public static final float SWEEP_SECONDS = 5f;
    public static final float CONE_TURN_RATE = 180f;
    public static final float GRACE_SECONDS = 1f;
    public static final float LOSE_SECONDS = 3f;
    public static final float FOLLOW_FRACTION = 0.45f;
    public static final float MIN_PROFILE_MULT = 0.5f;
    public static final float MAX_PROFILE_MULT = 1.5f;
    public static final float PROFILE_BASE = 1000f;
    public static final float SPOTTER_BURN = 14f;
    public static final float ALERT_BURN_BONUS = 3f;
    public static final float MIN_RESPAWN_DAYS = 10f;
    public static final float MAX_RESPAWN_DAYS = 20f;
    public static final float WAYPOINT_REACHED = 300f;
    public static final float WAYPOINT_SPREAD = 1500f;
    public static final float EXPLOSION_PAD = 60f;

    private final StarSystemAPI system;
    private final String factionId;
    private final float patrolRadius;
    private final List<Nest> nests = new ArrayList<>();
    private final List<Spotter> spotters = new ArrayList<>();
    private SectorEntityToken cones;

    private boolean active;
    private boolean spotted;
    private boolean wasCurrent;
    private boolean done;
    private float coneLength = Setting.ARTILLERY_DRONE_CONE_LENGTH.value();
    private float visibility;
    private float sinceCheck;

    public DroneNetScript(StarSystemAPI system, String factionId, float patrolRadius) {
        this.system = system;
        this.factionId = factionId;
        this.patrolRadius = patrolRadius;
    }

    public static DroneNetScript getInstanceOrRegister(StarSystemAPI system, String factionId, float patrolRadius) {
        DroneNetScript script = get(system);
        if (script != null) return script;

        script = new DroneNetScript(system, factionId, patrolRadius);
        system.addScript(script);
        system.getMemoryWithoutUpdate().set(MEM_KEY, script);
        return script;
    }

    public static DroneNetScript get(LocationAPI loc) {
        return loc.getMemoryWithoutUpdate().get(MEM_KEY) instanceof DroneNetScript script ? script : null;
    }

    public static void onInterdicted(CampaignFleetAPI fleet) {
        if (fleet.hasTag(TAG_DRONE)) fleet.getMemoryWithoutUpdate().set(MEM_INTERDICTED, true, 1f);
    }

    public boolean addNest(float angle, float radius, float orbitDays, Random random) {
        CampaignFleetAPI fleet = createNestFleet(random);
        if (fleet == null) return false;

        SectorEntityToken center = system.getCenter();
        SectorEntityToken anchor = system.addCustomEntity(Misc.genUID(), null, NEST_ENTITY_ID, factionId, null);
        anchor.setOrbit(Global.getFactory().createCircularOrbitWithSpin(center, angle, radius, orbitDays, random.nextFloat() * 10f + 1f));
        if (Misc.getMarketsInLocation(system).isEmpty()) MiscellaneousThemeGenerator.makeDiscoverable(anchor, 200f, 2000f);

        // the orbit has not moved the anchor yet at placement
        Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
        loc.scale(radius);
        Vector2f.add(loc, center.getLocation(), loc);

        system.addEntity(fleet);
        fleet.setLocation(loc.x, loc.y);

        Nest nest = new Nest();
        nest.anchor = anchor;
        nest.fleet = fleet;
        nests.add(nest);
        sleep(nest);

        Spotter spotter = new Spotter();
        spotters.add(spotter);
        spawnSpotter(spotter, loc);
        return true;
    }

    public boolean isPlayerSpotted() {
        return active && spotted;
    }

    public List<Spotter> getSpotters() {
        return Collections.unmodifiableList(spotters);
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
            if (wasCurrent) standDown();
            wasCurrent = false;
            return;
        }

        wasCurrent = true;
        sinceCheck += amount;
        if (sinceCheck >= CHECK_SECONDS) {
            check(sinceCheck);
            sinceCheck = 0f;
        }

        visibility = MathUtils.clamp(visibility + (active ? amount : -amount) / FADE_SECONDS, 0f, 1f);
        if (!active) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        for (Spotter spotter : spotters) {
            if (spotter.fleet != null) steer(spotter, player, amount);
        }
    }

    private void check(float elapsed) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        popDrones();
        removeDead();

        active = !system.getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)
                && ArtilleryStationEntityPlugin.needsVisionAgainstPlayer(system);
        coneLength = getConeLengthFor(player);
        spotted = false;

        if (active) {
            ensureCones();
            for (Spotter spotter : spotters) {
                if (spotter.fleet == null) continue;
                look(spotter, player, elapsed);
                if (spotter.locked) spotted = true;
            }

            markHostileFleets();
            respawnSpotters();
        } else {
            for (Spotter spotter : spotters) unlock(spotter);
        }

        updateNests(player);

        if (nests.isEmpty() && getLiveSpotterCount() == 0) finish();
    }

    // no listener or target flag for the vanilla pulse, the pulsing fleet carries this flag for 0.1 days
    private void popDrones() {
        List<CampaignFleetAPI> pulsers = new ArrayList<>();
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (!fleet.hasTag(TAG_DRONE) && fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.JUST_DID_INTERDICTION_PULSE)) pulsers.add(fleet);
        }

        for (Spotter spotter : spotters) {
            CampaignFleetAPI fleet = spotter.fleet;
            if (fleet == null || !isAlive(fleet) || fleet.getBattle() != null) continue;

            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            if (mem.contains(MissileMemFlags.MEM_CAUGHT_BY_MISSILE) || mem.getBoolean(MEM_INTERDICTED) || isPulsed(fleet, pulsers)) explode(fleet);
        }

        for (Nest nest : nests) {
            CampaignFleetAPI fleet = nest.fleet;
            if (!isAlive(fleet) || fleet.getBattle() != null) continue;
            if (fleet.getMemoryWithoutUpdate().contains(MissileMemFlags.MEM_CAUGHT_BY_MISSILE)) explode(fleet);
        }
    }

    private boolean isPulsed(CampaignFleetAPI drone, List<CampaignFleetAPI> pulsers) {
        for (CampaignFleetAPI pulser : pulsers) {
            if (Misc.getDistance(pulser, drone) > InterdictionPulseAbility.getRange(pulser)) continue;
            if (InterdictionPulseAbility.getInterdictSeconds(pulser, drone) > 0f) return true;
        }

        return false;
    }

    private void explode(CampaignFleetAPI fleet) {
        Vector2f loc = new Vector2f(fleet.getLocation());

        VariableExplosionEntityPlugin.VariableExplosionParams params = new VariableExplosionEntityPlugin.VariableExplosionParams(
                EXPLOSION_SOUND_ID, false, 0f, EXPLOSION_COLOR, system, loc, fleet.getRadius() + EXPLOSION_PAD, 0.5f);
        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.NONE;

        SectorEntityToken explosion = system.addCustomEntity(Misc.genUID(), "Explosion", "IndEvo_VariableExplosion", Factions.NEUTRAL, params);
        explosion.setLocation(loc.x, loc.y);

        fleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
    }

    private void removeDead() {
        for (Spotter spotter : spotters) {
            if (spotter.fleet == null || isAlive(spotter.fleet)) continue;

            spotter.fleet = null;
            spotter.lostAt = Global.getSector().getClock().getTimestamp();
            spotter.respawnDays = MIN_RESPAWN_DAYS + Misc.random.nextFloat() * (MAX_RESPAWN_DAYS - MIN_RESPAWN_DAYS);
            unlock(spotter);
        }

        Iterator<Nest> iterator = nests.iterator();
        while (iterator.hasNext()) {
            Nest nest = iterator.next();
            if (isAlive(nest.fleet)) continue;

            Misc.fadeAndExpire(nest.anchor);
            iterator.remove();
        }
    }

    private boolean isAlive(CampaignFleetAPI fleet) {
        return fleet.isAlive() && !fleet.isEmpty() && fleet.getContainingLocation() == system;
    }

    private int getLiveSpotterCount() {
        int count = 0;
        for (Spotter spotter : spotters) if (spotter.fleet != null) count++;
        return count;
    }

    private float getConeLengthFor(CampaignFleetAPI fleet) {
        float profile = fleet.getStats().getDetectedRangeMod().computeEffective(PROFILE_BASE) / PROFILE_BASE;
        return Setting.ARTILLERY_DRONE_CONE_LENGTH.value() * MathUtils.clamp(profile, MIN_PROFILE_MULT, MAX_PROFILE_MULT);
    }

    private void ensureCones() {
        if (cones != null && !cones.isExpired() && cones.getContainingLocation() == system) return;

        cones = system.addTerrain(DroneConeTerrain.TERRAIN_ID, new BaseRingTerrain.RingParams(1f, 0f, system.getCenter(), "Spotter Cones"));
        cones.setCircularOrbit(system.getCenter(), 0f, 0f, 100f);
    }

    private void look(Spotter spotter, CampaignFleetAPI player, float elapsed) {
        if (spotter.fleet.getBattle() != null) {
            unlock(spotter);
            return;
        }

        boolean inCone = isInCone(spotter, player, coneLength);

        if (spotter.locked) {
            spotter.unseen = inCone ? 0f : spotter.unseen + elapsed;
            if (spotter.unseen >= LOSE_SECONDS) {
                unlock(spotter);
                spotter.waypoint = new Vector2f(player.getLocation());
            }
            return;
        }

        if (!inCone) {
            spotter.seen = Math.max(0f, spotter.seen - elapsed);
            return;
        }

        if (spotter.seen <= 0f) spotter.fleet.addFloatingText("?", Misc.getHighlightColor(), 0.5f);
        spotter.seen += elapsed;

        if (spotter.seen >= GRACE_SECONDS) {
            spotter.locked = true;
            spotter.unseen = 0f;
            spotter.fleet.addFloatingText("!", Misc.getNegativeHighlightColor(), 1f);
        }
    }

    private void unlock(Spotter spotter) {
        spotter.locked = false;
        spotter.seen = 0f;
        spotter.unseen = 0f;
    }

    private boolean isInCone(Spotter spotter, SectorEntityToken target, float length) {
        Vector2f from = spotter.fleet.getLocation();
        float dist = Misc.getDistance(from, target.getLocation());
        if (dist > length + target.getRadius()) return false;
        if (dist <= target.getRadius() + spotter.fleet.getRadius()) return true;

        return Misc.isInArc(spotter.coneAngle, CONE_ARC, from, target.getLocation());
    }

    private void markHostileFleets() {
        float length = Setting.ARTILLERY_DRONE_CONE_LENGTH.value();

        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (fleet.isPlayerFleet() || fleet.isStationMode() || fleet.getAI() == null || fleet.hasTag(TAG_DRONE)) continue;
            if (!fleet.getFaction().isHostileTo(factionId)) continue;

            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            if (mem.getBoolean(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY)) continue;

            for (Spotter spotter : spotters) {
                if (spotter.fleet == null || !isInCone(spotter, fleet, length)) continue;

                mem.set(EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY, true, EyeIndicatorScript.BASE_NPC_KNOWN_DURATION);
                fleet.addFloatingText("Detected by Spotter Drone", Misc.getNegativeHighlightColor(), 1f);
                break;
            }
        }
    }

    private void respawnSpotters() {
        if (nests.isEmpty()) return;

        int live = getLiveSpotterCount();
        for (Spotter spotter : spotters) {
            if (live >= nests.size()) return;
            if (spotter.fleet != null) continue;
            if (Global.getSector().getClock().getElapsedDaysSince(spotter.lostAt) < spotter.respawnDays) continue;

            Nest nest = nests.get(Misc.random.nextInt(nests.size()));
            spawnSpotter(spotter, nest.anchor.getLocation());
            live++;
        }
    }

    private void updateNests(CampaignFleetAPI player) {
        float alertRange = Setting.ARTILLERY_DRONE_ALERT_RANGE.value();

        for (Nest nest : nests) {
            if (nest.fleet.getBattle() != null) continue;

            boolean alert = spotted && (nest.awake || Misc.getDistance(nest.fleet, player) <= alertRange);
            if (alert && !nest.awake) wake(nest, player);
            else if (!alert && nest.awake) sleep(nest);

            if (nest.awake) chase(nest, player);
        }
    }

    private void wake(Nest nest, CampaignFleetAPI player) {
        nest.awake = true;

        CampaignFleetAPI fleet = nest.fleet;
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        mem.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        mem.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        mem.set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);

        fleet.getStats().getSensorRangeMod().modifyFlat(MOD_ID, Setting.ARTILLERY_DRONE_ALERT_RANGE.value(), "Spotter uplink");
        fleet.getStats().getFleetwideMaxBurnMod().modifyFlat(MOD_ID, ALERT_BURN_BONUS, "Scrambled");

        fleet.clearAssignments();
        fleet.setOrbit(null);
        fleet.addAssignment(FleetAssignment.INTERCEPT, player, 30f, "scrambling");
        fleet.addFloatingText("Launching", Misc.getNegativeHighlightColor(), 1f);
    }

    private void chase(Nest nest, CampaignFleetAPI player) {
        CampaignFleetAPI fleet = nest.fleet;

        AbilityPlugin burn = fleet.getAbility(Abilities.EMERGENCY_BURN);
        if (burn != null && burn.isUsable() && !burn.isActiveOrInProgress()) burn.activate();

        if (fleet.getAI() != null && fleet.getAI().getCurrentAssignmentType() != FleetAssignment.INTERCEPT) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.INTERCEPT, player, 30f, "scrambling");
        }
    }

    private void sleep(Nest nest) {
        nest.awake = false;

        CampaignFleetAPI fleet = nest.fleet;
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
        mem.unset(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT);

        fleet.getStats().getSensorRangeMod().unmodify(MOD_ID);
        fleet.getStats().getFleetwideMaxBurnMod().unmodify(MOD_ID);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, nest.anchor, 30f, "returning to nest");
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, nest.anchor, 1000000f, "dormant");
    }

    private void standDown() {
        spotted = false;
        for (Spotter spotter : spotters) unlock(spotter);
        for (Nest nest : nests) {
            if (nest.awake && isAlive(nest.fleet)) sleep(nest);
        }
    }

    private void finish() {
        done = true;
        active = false;
        if (cones != null) system.removeEntity(cones);
        system.getMemoryWithoutUpdate().unset(MEM_KEY);
    }

    // the override lasts one frame, the fleet's own patrol assignment takes over when this stops
    private void steer(Spotter spotter, CampaignFleetAPI player, float amount) {
        CampaignFleetAPI fleet = spotter.fleet;
        if (fleet.getBattle() != null) return;

        Vector2f loc = fleet.getLocation();
        float target;

        if (spotter.locked) {
            Vector2f dest = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(player.getLocation(), loc));
            dest.scale(coneLength * FOLLOW_FRACTION);
            Vector2f.add(dest, player.getLocation(), dest);
            fleet.setMoveDestinationOverride(dest.x, dest.y);

            target = Misc.getAngleInDegrees(loc, player.getLocation());
        } else {
            if (spotter.waypoint == null || Misc.getDistance(loc, spotter.waypoint) < WAYPOINT_REACHED) spotter.waypoint = pickWaypoint();
            fleet.setMoveDestinationOverride(spotter.waypoint.x, spotter.waypoint.y);

            spotter.sweep += amount;
            float heading = fleet.getVelocity().length() > 10f ? Misc.getAngleInDegrees(fleet.getVelocity()) : fleet.getFacing();
            target = heading + (float) Math.sin(spotter.sweep * 2f * Math.PI / SWEEP_SECONDS) * SWEEP_ARC;
        }

        float turn = MathUtils.clamp(MathUtils.getShortestRotation(spotter.coneAngle, target), -CONE_TURN_RATE * amount, CONE_TURN_RATE * amount);
        spotter.coneAngle = Misc.normalizeAngle(spotter.coneAngle + turn);
    }

    private Vector2f pickWaypoint() {
        if (Misc.random.nextBoolean()) {
            List<SectorEntityToken> spots = new ArrayList<>(system.getJumpPoints());
            for (PlanetAPI planet : system.getPlanets()) {
                if (!planet.isStar()) spots.add(planet);
            }

            if (!spots.isEmpty()) return Misc.getPointWithinRadius(spots.get(Misc.random.nextInt(spots.size())).getLocation(), WAYPOINT_SPREAD);
        }

        return Misc.getPointWithinRadius(system.getCenter().getLocation(), patrolRadius);
    }

    private CampaignFleetAPI createNestFleet(Random random) {
        FleetParamsV3 params = new FleetParamsV3(null, null, factionId, 1f, FleetTypes.PATROL_SMALL,
                Setting.ARTILLERY_DRONE_NEST_FP.value(), 0, 0, 0, 0, 0, 0);
        params.random = random;
        params.ignoreMarketFleetSizeMult = true;
        params.withOfficers = false;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setName("Nest Swarm");
        fleet.setNoFactionInName(true);
        fleet.setTransponderOn(false);
        fleet.addTag(TAG_DRONE);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        if (!fleet.hasAbility(Abilities.EMERGENCY_BURN)) fleet.addAbility(Abilities.EMERGENCY_BURN);

        return fleet;
    }

    private void spawnSpotter(Spotter spotter, Vector2f loc) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(factionId, "Spotter Drone", true);
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, SPOTTER_VARIANT);
        fleet.getFleetData().addFleetMember(member);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        fleet.forceSync();

        fleet.setNoFactionInName(true);
        fleet.setTransponderOn(false);
        fleet.addTag(TAG_DRONE);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getStats().getFleetwideMaxBurnMod().modifyFlat(MOD_ID,
                SPOTTER_BURN - member.getStats().getMaxBurnLevel().getModifiedValue(), "Spotter drive");

        system.addEntity(fleet);
        fleet.setLocation(loc.x, loc.y);
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, system.getCenter(), 1000000f, "scanning");

        spotter.fleet = fleet;
        spotter.waypoint = null;
        spotter.coneAngle = Misc.random.nextFloat() * 360f;
        unlock(spotter);
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
