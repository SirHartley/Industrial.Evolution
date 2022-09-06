package com.fs.starfarer.api.artilleryStation.station;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.projectiles.IndEvo_ArtilleryShotScript;
import com.fs.starfarer.api.artilleryStation.projectiles.IndEvo_MissileShotScript;
import com.fs.starfarer.api.artilleryStation.projectiles.IndEvo_RailgunShotEntity;
import com.fs.starfarer.api.artilleryStation.terrain.IndEvo_ArtilleryTerrain;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.econ.conditions.IndEvo_ArtilleryStationCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.IndEvo_ArtilleryStation;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CampaignEntity;
import com.fs.starfarer.campaign.fleet.CampaignFleet;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.fs.starfarer.api.artilleryStation.IndEvo_FleetVisibilityManager.WAS_SEEN_BY_HOSTILE_ENTITY;

public class IndEvo_ArtilleryStationEntityPlugin extends BaseCustomEntityPlugin {

    public static final float MIN_DELAY_BETWEEN_SHOTS = 1f;
    public static final float MAX_DELAY_BETWEEN_SHOTS = 2f;
    public static final float MIN_RELOAD_TIME = 10;
    public static final float MAX_RELOAD_TIME = 13f;
    public static final float RANGE = 12000f;
    public static final float MIN_RANGE = 500f;

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

    private String type;
    private IntervalUtil stationFireInterval = new IntervalUtil(MIN_DELAY_BETWEEN_SHOTS, MAX_DELAY_BETWEEN_SHOTS);
    private Map<String, IntervalUtil> forcedTargetMap = new HashMap<>();

    public float range = RANGE;
    public float terrainRange = 0f; //we save this here because getting the terrain is expensive

    public boolean disrupted = false;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        this.type = (String) pluginParams;
        if (type == null) type = TYPE_MORTAR;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!entity.isInCurrentLocation()) return;
        matchTerrainRange();

        if (disrupted) return;

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
            IndEvo_modPlugin.log("Artillery Forced Targets iterating");
            boolean fired = fireAtTarget(e, loc);

            if(fired) return;

        } else for (Map.Entry<String, IntervalUtil> e : targetMap.entrySet()) {
            IndEvo_modPlugin.log("Artillery Regular Targets iterating");
            boolean fired = fireAtTarget(e, loc);

            if(fired) return;
        }
    }

    public void forceTarget(SectorEntityToken t, float timeout) {
        if (forcedTargetMap.containsKey(t.getId())) return;

        IndEvo_modPlugin.log("Adding Forced Target " + t.getId());

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
            IndEvo_modPlugin.log(s + " - checking");
            boolean isForced = target.getMemoryWithoutUpdate().contains(FORCED_TARGET);

            if (!isForced) {
                if (!isValid(target)) return false;

                if (isInCombat(target)) {
                    IndEvo_modPlugin.log("combat, skipping");
                    interval.setElapsed(interval.getIntervalDuration() - 10f);
                    return false;
                }

                //area blocking by artillery to avoid overlap when multiple hostiles are in one location
                Vector2f anticipatedArea = IndEvo_ArtilleryShotScript.getAnticipatedTargetLoc(target);
                if (type.equals(TYPE_MORTAR)) {
                    if (isBlocked(anticipatedArea)) {
                        IndEvo_modPlugin.log("mortar invalid target, skipping");
                        //check again after the location is free so we don't overlap too much
                        interval.setElapsed(interval.getIntervalDuration() - 0.1f - getRemainingBlockTime(anticipatedArea));
                        return false;
                    }
                }
            }

            if (isInSafeSpot(target)) {
                IndEvo_modPlugin.log("safe spot, skipping");
                //lapse by 1 second if it's in a safe spot, then check again
                //not part of IsValid as to not remove targets that are temporarily safe
                interval.setElapsed(interval.getIntervalDuration() - 1f);

                //forced target in safe spot would never time out so we remove it in nebula
                if (loc.isNebula()) target.getMemoryWithoutUpdate().set(FORCE_INVALID, true, MAX_DELAY_BETWEEN_SHOTS + 0.1f);
                return false;
            }

            IndEvo_modPlugin.log(s + " - artillery firing on target, type " + type);

            switch (type) {
                case TYPE_RAILGUN:
                    IndEvo_RailgunShotEntity.spawn(entity, target, IndEvo_RailgunShotEntity.DEFAULT_PROJECTILE_AMT);
                    break;
                case TYPE_MISSILE:
                    Global.getSector().addScript(new IndEvo_MissileShotScript(entity, target, IndEvo_MissileShotScript.DEFAULT_MISSILE_AMT));
                    break;
                case TYPE_MORTAR:
                    Global.getSector().addScript(new IndEvo_ArtilleryShotScript(entity, target, IndEvo_ArtilleryShotScript.DEFAULT_PROJECTILE_AMT));
                    blockArea(IndEvo_ArtilleryShotScript.getAnticipatedTargetLoc(target));
                    break;
            }

            interval.advance(0.01f); //reset the cooldown

            //forced targets get cleared after one shot, not when they become invalid
            if (isForced) {
                IndEvo_modPlugin.log(s + " Timing out forced target");
                target.getMemoryWithoutUpdate().set(FORCE_INVALID, true, stationFireInterval.getIntervalDuration());
            }

            return true;

        } else IndEvo_modPlugin.log(e.getKey() + " not ready - " + e.getValue().getElapsed());

        return false;
    }

    @Override
    public boolean hasCustomMapTooltip() {
        return true;
    }

    @Override
    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        Color color = entity.getFaction().getBaseUIColor();

        tooltip.addPara(Misc.ucFirst(type) + " Artillery", color, 0);
        tooltip.addPara("Targets enemy fleets at %s range", 3f, Misc.getHighlightColor(), (int) Math.round(range) + " SU");

        boolean hostile = isHostileTo(Global.getSector().getPlayerFleet());
        Color c = hostile ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        String s = hostile ? "hostile" : " not hostile";

        tooltip.addPara("You are %s to the controlling faction.", 3f, c, s);
    }

    private boolean isValid(SectorEntityToken t) {
        if (t == null) return false;

        //it exists, is hostile, is in range and was seen
        boolean hostile = !(t instanceof CampaignFleetAPI) || isHostileTo((CampaignFleetAPI) t);
        boolean isNotNullAI = !t.isPlayerFleet() && (!(t instanceof CampaignFleetAPI) || ((CampaignFleetAPI) t).getAI() != null);

        return hostile
                && t.isAlive()
                && isNotNullAI
                && Misc.getDistance(t, entity) <= range
                && Misc.getDistance(t, entity) >= MIN_RANGE
                && t.getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY);
    }

    private boolean isForcedValid(SectorEntityToken t){
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
                IndEvo_modPlugin.log(e.getKey() + " removing forced target");
                forcedTargetMap.remove(e.getKey());
            }
        }

        //only remove on intervalElapsed so fleets that dodge in and out of range don't reset the cooldown
        for (Map.Entry<String, IntervalUtil> e : new HashSet<>(targetMap.entrySet())) {
            SectorEntityToken t = loc.getEntityById(e.getKey());

            if (e.getValue().intervalElapsed() && !isValid(t)) {
                IndEvo_modPlugin.log(e.getKey() + " removing entry");
                targetMap.remove(e.getKey());
            }
        }

        //add new targets
        for (CampaignFleetAPI f : loc.getFleets()) {
            if (targetMap.containsKey(f.getId())) continue;

            if (isHostileTo(f) && isValid(f)) {
                IntervalUtil interval = new IntervalUtil(MIN_RELOAD_TIME, MAX_RELOAD_TIME);
                interval.forceIntervalElapsed();
                targetMap.put(f.getId(), interval);
            }
        }

        if (Global.getSettings().isDevMode() && devmodeInterval.intervalElapsed()) {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            forceTarget(player, 20f);
        }
    }

    public IntervalUtil devmodeInterval = new IntervalUtil(5f, 5f);

    public void blockArea(Vector2f targetLoc) {
        blockedAreas.put(targetLoc, new IntervalUtil(IndEvo_ArtilleryShotScript.AVERAGE_PROJ_IMPACT_TIME, IndEvo_ArtilleryShotScript.AVERAGE_PROJ_IMPACT_TIME));
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

    public boolean isHostileTo(CampaignFleetAPI target) {
        FactionAPI faction = entity.getFaction();
        FactionAPI targetFaction = target.getFaction();

        if (faction == null) return !targetFaction.getId().equals(Factions.REMNANTS);
        else return faction.isHostileTo(targetFaction);
    }

    public boolean isInSafeSpot(SectorEntityToken target) {
        for (MarketAPI m : Misc.getMarketsInLocation(target.getContainingLocation())) {
            if (m.getPrimaryEntity() == null) continue;

            Vector2f anticipatedLoc = null;

            switch (type) {
                case TYPE_RAILGUN:
                    anticipatedLoc = IndEvo_RailgunShotEntity.getAnticipatedTargetLoc(target);
                    break;
                case TYPE_MISSILE:
                    anticipatedLoc = IndEvo_MissileShotScript.getAnticipatedTargetLoc(target);
                    break;
                case TYPE_MORTAR:
                    anticipatedLoc = IndEvo_ArtilleryShotScript.getAnticipatedTargetLoc(target);
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

    public static SectorEntityToken placeAtMarket(MarketAPI m, String forceType, boolean showFleetVisual) {
        if (m.getPrimaryEntity() == null) return null;

        SectorEntityToken primaryEntity = m.getPrimaryEntity();
        SectorEntityToken station = getOrbitalStationAtMarket(m);

        String factionID = m.isPlanetConditionMarketOnly() ? IndEvo_ids.DERELICT : m.getFactionId();

        LocationAPI loc = primaryEntity.getContainingLocation();
        SectorEntityToken artillery = loc.addCustomEntity(Misc.genUID(), null, "IndEvo_ArtilleryStation", factionID, forceType);

        float angle = station != null ? station.getCircularOrbitAngle() - 180 : (float) Math.random() * 360f;
        float radius = station != null ? station.getCircularOrbitRadius() : primaryEntity.getRadius() + 150f;
        float period = station != null ? station.getCircularOrbitPeriod() : radius / 10f;

        artillery.setCircularOrbitWithSpin(primaryEntity,
                angle,
                radius,
                period,
                5f,
                5f);

        m.getConnectedEntities().add(artillery);
        artillery.setMarket(m);

        artillery.addTag(IndEvo_ids.TAG_ARTILLERY_STATION);
        if (showFleetVisual) artillery.addTag(Tags.USE_STATION_VISUAL);
        artillery.getMemoryWithoutUpdate().set(IndEvo_ArtilleryStationCondition.ARTILLERY_KEY, true);

        SectorEntityToken t = loc.addTerrain("IndEvo_artillery_range_terrain", new BaseRingTerrain.RingParams(RANGE, 0f, artillery, "In artillery range"));
        t.setCircularOrbit(artillery, 0, 0, 0);

        return artillery;
    }

    public static SectorEntityToken getOrbitalStationAtMarket(MarketAPI market) {
        SectorEntityToken station = null;

        for (SectorEntityToken t : market.getConnectedEntities()) {
            if (t.hasTag(Tags.STATION) && !t.hasTag(IndEvo_ids.TAG_ARTILLERY_STATION)) {
                station = t;
                break;
            }
        }

        return station;
    }

    public void preRemoveActions() {
        targetMap.clear();
        forcedTargetMap.clear();
        blockedAreas.clear();

        getTerrainPlugin().remove();
    }

    public void matchTerrainRange() {
        if (Math.round(range) != Math.round(terrainRange)) {
            adjustTerrainRange(range);
        }
    }

    public IndEvo_ArtilleryTerrain getTerrainPlugin() {
        for (CampaignTerrainAPI t : entity.getContainingLocation().getTerrainCopy()) {
            if (t.getPlugin() instanceof IndEvo_ArtilleryTerrain) {
                IndEvo_ArtilleryTerrain p = ((IndEvo_ArtilleryTerrain) t.getPlugin());

                if (p.getRelatedEntity() != null && p.getRelatedEntity().getId().equals(entity.getId())) return p;
            }
        }

        return null;
    }

    public void adjustTerrainRange(float range) {
        getTerrainPlugin().setRange(range);
        terrainRange = range;
    }

    public boolean isDisrupted() {
        return disrupted;
    }

    public void setDisrupted(boolean disrupted) {
        this.disrupted = disrupted;
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

    public static List<SectorEntityToken> getArtilleriesInLoc(LocationAPI loc) {
        return loc.getEntitiesWithTag(IndEvo_ids.TAG_ARTILLERY_STATION);
    }

    public String getType() {
        return type;
    }

    public Industry getRelatedIndustry() {
        MarketAPI m = entity.getMarket();

        if (m.isPlanetConditionMarketOnly()) return null;
        Industry ind = null;

        for (Industry i : m.getIndustries()) {
            if (i instanceof IndEvo_ArtilleryStation) {
                ind = i;
                break;
            }
        }

        return ind;
    }

    @Override
    public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {
        super.appendToCampaignTooltip(tooltip, level);
        float opad = 10f;
        float spad = 3f;
        Color highlight = Misc.getHighlightColor();

        IndEvo_modPlugin.log("call");

        if (disrupted) {
            tooltip.addPara("This defence platform is %s.", opad, Misc.getNegativeHighlightColor(), "not functional");
        } else switch (type) {
            case TYPE_RAILGUN:
                tooltip.addPara("The defence platform is armed with a %s.\n" +
                        "It will fire multiple extremely fast projectiles at an extreme range.", opad, highlight, "railgun");
                break;
            case TYPE_MISSILE:
                tooltip.addPara("The defence platform is armed with a %s.\n" +
                        "It will launch long-range target seeking ECM missiles.", opad, highlight, "missile launcher");
                break;
            case TYPE_MORTAR:
                tooltip.addPara("The defence platform is armed with a %s.\n" +
                        "It will bombard targets with a high volume of explosive ordinance at extreme range.", opad, highlight, "mortar");
                break;
        }

        tooltip.addPara("Old safety protocols prohibit targeting of inhabited areas.", opad);
    }
}