package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.projectiles.MissileShotScript;
import indevo.industries.artillery.projectiles.MortarShotScript;
import indevo.industries.artillery.projectiles.RailgunShotEntity;
import indevo.industries.artillery.terrain.ArtilleryTerrain;
import indevo.utils.ModPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static indevo.industries.artillery.scripts.EyeIndicatorScript.WAS_SEEN_BY_HOSTILE_ENTITY;

public class CampaignAttackScript implements EveryFrameScript {

    public static final float NPC_RELOAD_FACTOR = Global.getSettings().getFloat("IndEvo_Artillery_cooldownNPCMult");
    public static final float MIN_DELAY_BETWEEN_SHOTS = Global.getSettings().getFloat("IndEvo_Artillery_minDelayBetweenShots");
    public static final float MAX_DELAY_BETWEEN_SHOTS = Global.getSettings().getFloat("IndEvo_Artillery_maxDelayBetweenShots");
    public static final float MIN_RELOAD_TIME = Global.getSettings().getFloat("IndEvo_Artillery_minCooldown");
    public static final float MAX_RELOAD_TIME = Global.getSettings().getFloat("IndEvo_Artillery_maxCooldown");
    public static final float RANGE = Global.getSettings().getFloat("IndEvo_Artillery_maxRange");
    public static final float MIN_RANGE = Global.getSettings().getFloat("IndEvo_Artillery_minRange");

    //exclusions
    public static final float ARTILLERY_BLOCKOUT_RANGE = 400f;
    public static final float INHABITED_AREA_SAFE_RADIUS = 700f;

    public static final String TYPE_RAILGUN = "railgun";
    public static final String TYPE_MORTAR = "mortar";
    public static final String TYPE_MISSILE = "missile";

    public static final String FORCED_TARGET = "$IndEvo_ForcedTarget";
    public static final String FORCE_INVALID = "$IndEvo_FORCE";

    private Map<String, IntervalUtil> targetMap = new HashMap<>();
    private Map<Vector2f, IntervalUtil> blockedAreas = new HashMap<>();

    private IntervalUtil stationFireInterval = new IntervalUtil(MIN_DELAY_BETWEEN_SHOTS, MAX_DELAY_BETWEEN_SHOTS);
    private Map<String, IntervalUtil> forcedTargetMap = new HashMap<>();

    private SectorEntityToken terrain = null;

    public float range = RANGE;
    public float terrainRange = RANGE; //we save this here because getting the terrain is expensive

    public boolean disabled = false;
    public boolean isDone = false;

    public SectorEntityToken entity;
    private String type;

    public String getType() {
        return type;
    }

    public CampaignAttackScript(SectorEntityToken entity, String type){
        this.entity = entity;
        this.type = type;
    }

    public void addTerrainToLoc(){
        SectorEntityToken t = entity.getContainingLocation().addTerrain("IndEvo_artillery_range_terrain", new BaseRingTerrain.RingParams(terrainRange, 0f, entity, "In artillery range"));
        t.setCircularOrbit(entity, 0, 0, 0);

        terrain = t;
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    public void setDone(boolean done) {
        isDone = done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (!entity.isInCurrentLocation() || isDone) return;
        if (terrain == null) addTerrainToLoc();

        matchTerrainRangeAndLoc();

        if (disabled) return;

        advanceStationFireInterval(amount);
        advanceBlockedLocations(amount);
        advanceTargetCooldowns(amount);
        updateTargets(); //call BEFORE fireAtTargets to remove any invalid targets
        fireAtTargets();
    }

    private void fireAtTargets() {
        //we are out for blood

        if (!stationFireInterval.intervalElapsed()) return;
        stationFireInterval.advance(0.01f); //new station fire interval

        LocationAPI loc = entity.getContainingLocation();

        //fire at forced targets
        if (!forcedTargetMap.isEmpty()) for (Map.Entry<String, IntervalUtil> e : forcedTargetMap.entrySet()) {
            ModPlugin.log("Artillery Forced Targets iterating");
            boolean fired = fireAtTarget(e, loc);

            if (fired) return;

        }
        else for (Map.Entry<String, IntervalUtil> e : targetMap.entrySet()) {
            ModPlugin.log("Artillery Regular Targets iterating");
            boolean fired = fireAtTarget(e, loc);

            if (fired) return;
        }
    }

    public void forceTarget(SectorEntityToken t, float timeout) {
        if (forcedTargetMap.containsKey(t.getId())) return;

        ModPlugin.log("Adding Forced Target " + t.getId());

        t.getMemoryWithoutUpdate().set(FORCED_TARGET, true, timeout);
        t.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, timeout);

        IntervalUtil interval = new IntervalUtil(timeout, timeout);
        interval.forceIntervalElapsed();

        forcedTargetMap.put(t.getId(), interval);
    }

    public boolean fireAtTarget(Map.Entry<String, IntervalUtil> e, LocationAPI loc) {
        String s = e.getKey();
        IntervalUtil interval = e.getValue();

        if (interval.intervalElapsed()) {
            SectorEntityToken target = loc.getEntityById(s);
            ModPlugin.log(s + " - checking");
            boolean isForced = target.getMemoryWithoutUpdate().contains(FORCED_TARGET);

            if (!isForced) {
                if (!isValid(target)) return false;

                if (isInCombat(target)) {
                    ModPlugin.log("combat, skipping");
                    interval.setElapsed(interval.getIntervalDuration() - 10f);
                    return false;
                }

                //area blocking by artillery to avoid overlap when multiple hostiles are in one location
                Vector2f anticipatedArea = MortarShotScript.getAnticipatedTargetLoc(target);
                if (type.equals(TYPE_MORTAR)) {
                    if (isBlocked(anticipatedArea)) {
                        ModPlugin.log("mortar invalid target, skipping");
                        //check again after the location is free so we don't overlap too much
                        interval.setElapsed(interval.getIntervalDuration() - 0.1f - getRemainingBlockTime(anticipatedArea));
                        return false;
                    }
                }
            }

            if (isInSafeSpot(target)) {
                ModPlugin.log("safe spot, skipping");
                //lapse by 1 second if it's in a safe spot, then check again
                //not part of IsValid as to not remove targets that are temporarily safe
                interval.setElapsed(interval.getIntervalDuration() - 1f);

                //forced target in safe spot would never time out so we remove it in nebula
                if (loc.isNebula())
                    target.getMemoryWithoutUpdate().set(FORCE_INVALID, true, MAX_DELAY_BETWEEN_SHOTS + 0.1f);
                return false;
            }

            ModPlugin.log(s + " - artillery firing on target, type " + type);

            switch (type) {
                case TYPE_RAILGUN:
                    RailgunShotEntity.spawn(entity, target, RailgunShotEntity.DEFAULT_PROJECTILE_AMT);
                    break;
                case TYPE_MISSILE:
                    Global.getSector().addScript(new MissileShotScript(entity, target, MissileShotScript.DEFAULT_MISSILE_AMT));
                    break;
                case TYPE_MORTAR:
                    Global.getSector().addScript(new MortarShotScript(entity, target, MortarShotScript.DEFAULT_PROJECTILE_AMT));
                    blockArea(MortarShotScript.getAnticipatedTargetLoc(target));
                    break;
            }

            interval.advance(0.01f); //reset the cooldown

            //forced targets get cleared after one shot, not when they become invalid
            if (isForced) {
                ModPlugin.log(s + " Timing out forced target");
                target.getMemoryWithoutUpdate().set(FORCE_INVALID, true, stationFireInterval.getIntervalDuration());
            }

            return true;

        } else ModPlugin.log(e.getKey() + " not ready - " + e.getValue().getElapsed());

        return false;
    }


    private boolean isValid(SectorEntityToken t) {
        if (t == null) return false;

        //it exists, is hostile, is in range and was seen
        boolean hostile = !(t instanceof CampaignFleetAPI) || isHostileTo((CampaignFleetAPI) t);
        boolean isNotNullAI = t.isPlayerFleet() || !(t instanceof CampaignFleetAPI) || ((CampaignFleetAPI) t).getAI() != null;

        return hostile
                && t.isAlive()
                && isNotNullAI
                && Misc.getDistance(t, entity) <= range
                && Misc.getDistance(t, entity) >= MIN_RANGE
                && t.getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY)
                && !t.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT);
    }

    private boolean isForcedValid(SectorEntityToken t) {
        return t != null
                && Misc.getDistance(t, entity) <= range
                && Misc.getDistance(t, entity) >= MIN_RANGE
                && !t.getMemoryWithoutUpdate().contains(FORCE_INVALID);
    }

    private boolean isInCombat(SectorEntityToken target) {
        if (target instanceof CampaignFleetAPI) {
            return ((CampaignFleetAPI) target).getBattle() != null;
        }

        return false;
    }

    private void updateTargets() {
        LocationAPI loc = entity.getContainingLocation();

        for (Map.Entry<String, IntervalUtil> e : new HashSet<>(forcedTargetMap.entrySet())) {
            SectorEntityToken t = loc.getEntityById(e.getKey());

            if (e.getValue().intervalElapsed() && !isForcedValid(t)) {
                ModPlugin.log(e.getKey() + " removing forced target");
                forcedTargetMap.remove(e.getKey());
            }
        }

        //only remove on intervalElapsed so fleets that dodge in and out of range don't reset the cooldown
        for (Map.Entry<String, IntervalUtil> e : new HashSet<>(targetMap.entrySet())) {
            SectorEntityToken t = loc.getEntityById(e.getKey());

            if (e.getValue().intervalElapsed() && !isValid(t)) {
                ModPlugin.log(e.getKey() + " removing entry");
                targetMap.remove(e.getKey());
            }
        }

        //add new targets
        for (CampaignFleetAPI f : loc.getFleets()) {
            if (targetMap.containsKey(f.getId())) continue;

            if (isHostileTo(f) && isValid(f)) {
                float reloadTimeFactor = f.isPlayerFleet() ? 1f : NPC_RELOAD_FACTOR;

                IntervalUtil interval = new IntervalUtil(MIN_RELOAD_TIME * reloadTimeFactor, MAX_RELOAD_TIME * reloadTimeFactor);
                interval.forceIntervalElapsed();
                targetMap.put(f.getId(), interval);
            }
        }

        if (Global.getSettings().isDevMode() && devmodeInterval.intervalElapsed()) {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            forceTarget(player, 20f);
        }
    }

    public boolean isHostileTo(CampaignFleetAPI target) {
        FactionAPI faction = entity.getFaction();
        FactionAPI targetFaction = target.getFaction();

        if (faction == null) return !targetFaction.getId().equals(Factions.REMNANTS);
        else return faction.isHostileTo(targetFaction);
    }

    public IntervalUtil devmodeInterval = new IntervalUtil(5f, 5f);

    public void blockArea(Vector2f targetLoc) {
        blockedAreas.put(targetLoc, new IntervalUtil(MortarShotScript.AVERAGE_PROJ_IMPACT_TIME, MortarShotScript.AVERAGE_PROJ_IMPACT_TIME));
    }

    private boolean isBlocked(Vector2f targetLoc) {
        for (Vector2f v : blockedAreas.keySet()) {
            if (Misc.getDistance(targetLoc, v) < ARTILLERY_BLOCKOUT_RANGE) return true;
        }

        return false;
    }

    private float getRemainingBlockTime(Vector2f targetLoc) {
        float longestBlockTime = 0f;

        for (Map.Entry<Vector2f, IntervalUtil> e : blockedAreas.entrySet()) {
            float duration = e.getValue().getIntervalDuration();

            if (Misc.getDistance(targetLoc, e.getKey()) < ARTILLERY_BLOCKOUT_RANGE
                    && duration > longestBlockTime) longestBlockTime = duration;
        }

        return longestBlockTime;
    }

    public boolean isInSafeSpot(SectorEntityToken target) {
        for (MarketAPI m : Misc.getMarketsInLocation(target.getContainingLocation())) {
            if (m.getPrimaryEntity() == null) continue;

            Vector2f anticipatedLoc = null;

            switch (type) {
                case TYPE_RAILGUN:
                    anticipatedLoc = RailgunShotEntity.getAnticipatedTargetLoc(target);
                    break;
                case TYPE_MISSILE:
                    anticipatedLoc = MissileShotScript.getAnticipatedTargetLoc(target);
                    break;
                case TYPE_MORTAR:
                    anticipatedLoc = MortarShotScript.getAnticipatedTargetLoc(target);
                    break;
            }

            float safeRadius = m.getPrimaryEntity().getRadius() + INHABITED_AREA_SAFE_RADIUS;

            //the fleet or the anticipated target are within an inhabited area
            if (Misc.getDistance(m.getPrimaryEntity().getLocation(), target.getLocation()) < safeRadius
                    || (anticipatedLoc != null && Misc.getDistance(m.getPrimaryEntity().getLocation(), anticipatedLoc) < safeRadius))
                return true;
        }

        return false;
    }

    private void advanceTargetCooldowns(float amt) {
        for (IntervalUtil i : targetMap.values()) {
            if (!i.intervalElapsed()) i.advance(amt); //we only advance if the interval is not elapsed
        }

        for (IntervalUtil i : forcedTargetMap.values()) {
            if (!i.intervalElapsed()) i.advance(amt); //we only advance if the interval is not elapsed
        }
    }

    private void advanceStationFireInterval(float amount) {
        //if no targets, reset to give time to turn
        //else, advance until ready
        if (targetMap.isEmpty() && forcedTargetMap.isEmpty()) stationFireInterval.setElapsed(0f);
        else if (!stationFireInterval.intervalElapsed()) stationFireInterval.advance(amount);

        devmodeInterval.advance(amount);
    }

    private void advanceBlockedLocations(float amount) {
        for (Map.Entry<Vector2f, IntervalUtil> e : new HashSet<>(blockedAreas.entrySet())) {
            e.getValue().advance(amount);

            if (e.getValue().intervalElapsed()) blockedAreas.remove(e.getKey());
        }
    }

    public void preRemoveActions() {
        targetMap.clear();
        forcedTargetMap.clear();
        blockedAreas.clear();

        getTerrainPlugin().remove();
        setDone(true);
    }

    public void matchTerrainRangeAndLoc() {
        if (Math.round(range) != Math.round(terrainRange)) {
            adjustTerrainRange(range);
        }

        if (terrain.getContainingLocation() != entity.getContainingLocation()) {
            getTerrainPlugin().remove();
            addTerrainToLoc();
        }
    }

    public ArtilleryTerrain getTerrainPlugin() {
        for (CampaignTerrainAPI t : terrain.getContainingLocation().getTerrainCopy()) {
            if (t.getPlugin() instanceof ArtilleryTerrain) {
                ArtilleryTerrain p = ((ArtilleryTerrain) t.getPlugin());

                if (p.getRelatedEntity() != null && p.getRelatedEntity().getId().equals(entity.getId())) return p;
            }
        }

        return null;
    }

    public void adjustTerrainRange(float range) {
        getTerrainPlugin().setRange(range);
        terrainRange = range;
    }

    public void addToRange(float range) {
        this.range += range;
    }

    public void resetRange() {
        this.range = RANGE;
    }

    public float getRange() {
        return range;
    }
}
