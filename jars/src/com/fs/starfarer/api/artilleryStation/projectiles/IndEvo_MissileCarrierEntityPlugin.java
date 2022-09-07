package com.fs.starfarer.api.artilleryStation.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.station.IndEvo_ArtilleryStationEntityPlugin;
import com.fs.starfarer.api.artilleryStation.trails.IndEvo_MagicCampaignTrailPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.plugins.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.IndEvo_IndustryHelper.smootherstep;

public class IndEvo_MissileCarrierEntityPlugin extends BaseCustomEntityPlugin {
    //travel in arc to loc
    //track player with pulsing, at high range
    //if caught enough split into 3 slower, high tracking shards
    //project cones, track player loc
    //ECM cone

    //show warning sign when the missile launches, rotated in a way that shows the missile trajectory

    public static class MissileParams {
        public SectorEntityToken origin;
        public Vector2f target;
        public Vector2f trajectoryCenter;
        public float trajectoryRadius;
        public float splitAngleOnTrajectory;
        public float impactSeconds;

        public MissileParams(SectorEntityToken origin, Vector2f target, float impactSeconds, Vector2f trajectoryCenter, float trajectoryRadius, float splitAngleOnTrajectory) {
            this.origin = origin;
            this.target = target;
            this.trajectoryCenter = trajectoryCenter;
            this.trajectoryRadius = trajectoryRadius;
            this.splitAngleOnTrajectory = splitAngleOnTrajectory;
            this.impactSeconds = impactSeconds;
        }
    }

    public static final float PROJECTILE_VELOCITY = 700f;
    public static final float PROJECTILE_SIZE = 30f;
    public static final float FRIENDLY_FIRE_IMMUNITY_PROJ_FLIGHT_TIME_FRACT = 0.3f;
    public static final float EXPLOSION_SIZE = 600f;
    public static final float DETECTION_RADIUS = 150f;

    public static final float SUBMUNITION_DEPLOY_EXPLOSION_SIZE = 100f;
    public static final float SUBMUNITION_SPREAD_DIST = 300f;
    public static final float SUBMUNITION_MIN_DURATION = 10f;
    public static final float SUBMUNITION_MAX_DURATION = 15f;

    //render the warning sign and add the X shots
    public static final float AVERAGE_PROJ_IMPACT_TIME = 10f;
    public static final float DANGER_SIGN_FADEOUT_TIME = 10f;

    public static float DANGER_SIGN_SIZE = 150f;
    public static final float DANGERSIGN_ALPHA_RAMPUP_FRACTION = 0.1f;
    public static final float DANGERSIGN_ALPHA_FADEOUT_FRACTION = 0.8f;
    public static final float MAX_FADE_IN_SIZE_MULT = 1.5f;
    public static final float MAX_DANGER_SIGN_ALPHA = 0.4f;

    //dangerSign
    transient private SpriteAPI dangerSign;
    public float currentAlpha = 0f;
    public float sizeMult = 1f;
    public Vector2f dangerSignPos = new Vector2f();
    public float angle = 0;

    //base
    transient private SpriteAPI missileSprite;

    private float timePassedSeconds = 0f;
    public SectorEntityToken origin;
    public Vector2f target;
    public Vector2f trajectoryCenter;
    public float trajectoryRadius;
    public float splitAngleOnTrajectory;
    public Vector2f originLocation = null;

    public float impactSeconds;
    public float projectileDelaySeconds;
    public float projectileFlightTime;

    public float trailID;
    public boolean finishing = false;

    public static void spawn(MissileParams params) {
        SectorEntityToken origin = params.origin;
        SectorEntityToken t = origin.getContainingLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_missileCarrierPlugin", null, params);
        t.setLocation(origin.getLocation().x, origin.getLocation().y);
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof MissileParams) {
            this.origin = ((MissileParams) pluginParams).origin;
            this.target = ((MissileParams) pluginParams).target;
            this.trajectoryCenter = ((MissileParams) pluginParams).trajectoryCenter;
            this.trajectoryRadius = ((MissileParams) pluginParams).trajectoryRadius;
            this.splitAngleOnTrajectory = ((MissileParams) pluginParams).splitAngleOnTrajectory;
            this.impactSeconds = ((MissileParams) pluginParams).impactSeconds;

            trailID = MagicCampaignTrailPlugin.getUniqueID();

            updateProjectileFlightTime();
        }

        initDangerSign();

        readResolve();
    }

    Object readResolve() {
        dangerSign = Global.getSettings().getSprite("fx", "IndEvo_missile_target");
        missileSprite = Global.getSettings().getSprite("fx", "IndEvo_carrier_missile");
        return this;
    }

    public void advance(float amount) {
        timePassedSeconds += amount;

        advanceDangerSign();

        if (timePassedSeconds > projectileDelaySeconds && !finishing){
            advanceProjectile();
            if(timePassedSeconds > projectileDelaySeconds + 0.05f) addTrailToProj();

            boolean friendlyFireDelayPassed = timePassedSeconds > projectileDelaySeconds + (impactSeconds - projectileDelaySeconds) * FRIENDLY_FIRE_IMMUNITY_PROJ_FLIGHT_TIME_FRACT;

            //explode when fleet in range
            for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()){
                if(friendlyFireDelayPassed
                        && fleet.isHostileTo(origin)
                        && Misc.getDistance(entity, origin) >= IndEvo_ArtilleryStationEntityPlugin.MIN_RANGE
                        && Misc.getDistance(entity, fleet) <= DETECTION_RADIUS) {

                    spawnECCMExplosion(EXPLOSION_SIZE);
                    Misc.fadeAndExpire(entity, 0.1f);
                    finishing = true;
                }
            }

            //split when loc reached
            if (Misc.getDistance(getPointForTrajectoryAngle(splitAngleOnTrajectory), entity.getLocation()) < 60f){
                spawnExplosion(SUBMUNITION_DEPLOY_EXPLOSION_SIZE);

                deploySubMunitions();
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }

            //failsafe if it didn't despawn properly
            if (timePassedSeconds > impactSeconds * 1.5f) {
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }
        }
    }

    public void spawnECCMExplosion(float size) {
        IndEvo_ECMExplosion.ECMExplosionParams p = new IndEvo_ECMExplosion.ECMExplosionParams(
                entity.getContainingLocation(),
                entity.getLocation(),
                IndEvo_ECMExplosion.DURATION,
                size);

        IndEvo_ECMExplosion.spawn(p);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderDangerSign();
        if(timePassedSeconds > projectileDelaySeconds) renderProjectile(viewport);
    }

    public void advanceProjectile() {
        float originAngle = Misc.getAngleInDegrees(trajectoryCenter, originLocation);
        float targetAngle = Misc.getAngleInDegrees(trajectoryCenter, target);
        float totalAngle = Misc.getAngleDiff(originAngle, targetAngle);

        float fractionTrajectoryPassed = (Math.max(0, timePassedSeconds - Math.max(projectileDelaySeconds, 0))) / projectileFlightTime;

        //direction
        float nextAngle;
        if(originAngle > 180){
            float A = originAngle - 180;
            boolean targetIsInLeftHemisphere = targetAngle < originAngle && targetAngle > A;
            nextAngle = Misc.getAngleInDegrees(trajectoryCenter, originLocation) + totalAngle * fractionTrajectoryPassed * (targetIsInLeftHemisphere ? -1 : 1);
        } else {
            float A = originAngle + 180;
            boolean targetIsInLeftHemisphere = targetAngle > originAngle && targetAngle < A;
            nextAngle = Misc.getAngleInDegrees(trajectoryCenter, originLocation) + totalAngle * fractionTrajectoryPassed * (targetIsInLeftHemisphere ? 1 : -1);
        }

        Vector2f nextPos = getPointForTrajectoryAngle(nextAngle);
        float projRotation = Misc.getAngleInDegrees(entity.getLocation(), nextPos);

        entity.setLocation(nextPos.x, nextPos.y);
        entity.setFacing(projRotation - 90);
    }

    public void renderProjectile(ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        missileSprite.setNormalBlend();
        missileSprite.setAngle(entity.getFacing());
        missileSprite.setSize(PROJECTILE_SIZE, PROJECTILE_SIZE);
        missileSprite.setAlphaMult(alphaMult);
        missileSprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }

    private void updateProjectileFlightTime() {
        this.originLocation = origin.getLocation();
        this.projectileFlightTime = getTotalDistToTarget() / PROJECTILE_VELOCITY;
        this.projectileDelaySeconds = impactSeconds - projectileFlightTime;
    }

    public float getTotalDistToTarget(){
        float angle1 = Misc.getAngleInDegrees(trajectoryCenter, originLocation);
        float angle2 = Misc.getAngleInDegrees(trajectoryCenter, target);

        return getDistanceBetweenangles(angle1, angle2);
    }

    public Vector2f getPointForTrajectoryAngle(float angle){
        return MathUtils.getPointOnCircumference(trajectoryCenter, trajectoryRadius, angle);
    }

    public float getDistanceBetweenangles(float a1, float a2) {
        float totalCircumfence = (float) (2f * Math.PI * trajectoryRadius);
        float deltaAngle = Misc.getAngleDiff(a1, a2);
        return totalCircumfence * (deltaAngle / 360);
    }

    //explosion and split

    public void spawnExplosion(float size) {
        LocationAPI cl = entity.getContainingLocation();

        Color color = new Color(100, 160, 255);
        ExplosionEntityPlugin.ExplosionParams params = new ExplosionEntityPlugin.ExplosionParams(color, cl, entity.getLocation(), size, 0.6f);
        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.LOW;

        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                Entities.EXPLOSION, Factions.NEUTRAL, params);
        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public void deploySubMunitions(){
        Vector2f t1 = target;
        Vector2f t2 = MathUtils.getPointOnCircumference(t1, SUBMUNITION_SPREAD_DIST, 90f);
        Vector2f t3 = MathUtils.getPointOnCircumference(t1, SUBMUNITION_SPREAD_DIST, -90f);
        Vector2f[] v = new Vector2f[]{t1, t2, t3};

        for (Vector2f target : v){
            IndEvo_MissileSubmunitionEntity.MissileSubmunitionParams p = new IndEvo_MissileSubmunitionEntity.MissileSubmunitionParams(
                    origin.getContainingLocation(),
                    origin.getFaction(),
                    entity.getLocation(),
                    target,
                    MathUtils.getRandomNumberInRange(SUBMUNITION_MIN_DURATION, SUBMUNITION_MAX_DURATION));

            IndEvo_MissileSubmunitionEntity.spawn(p);
        }
    }

    //trail

    public static final float TRAIL_TIME = 0.5f;
    public static final Color GLOW_COLOR = new Color(255, 200, 50, 255);

    private void addTrailToProj(){
        IndEvo_MagicCampaignTrailPlugin.AddTrailMemberSimple(
                entity,
                trailID,
                Global.getSettings().getSprite("fx", "IndEvo_trail_foggy"),
                entity.getLocation(),
                0f,
                entity.getFacing() + 90f,
                PROJECTILE_SIZE * 0.8f,
                1f,
                GLOW_COLOR,
                0.8f,
                TRAIL_TIME,
                true,
                new Vector2f(0, 0));
    }

    //-------------------danger sign

    public void initDangerSign() {
        this.dangerSignPos = getPointForTrajectoryAngle(splitAngleOnTrajectory);
        this.angle = Misc.getAngleInDegrees(dangerSignPos, target) + 90;
    }

    public void advanceDangerSign() {
        //target reticule alpha
        float maxFadeInTime = DANGER_SIGN_FADEOUT_TIME * DANGERSIGN_ALPHA_RAMPUP_FRACTION;
        float minFadeoutTime = impactSeconds * DANGERSIGN_ALPHA_FADEOUT_FRACTION;

        if(timePassedSeconds < maxFadeInTime) {
            float mult = smootherstep(0, maxFadeInTime, timePassedSeconds);
            currentAlpha = MAX_DANGER_SIGN_ALPHA * mult;
            sizeMult = MAX_FADE_IN_SIZE_MULT - (MAX_FADE_IN_SIZE_MULT - 1) * mult;
        } else if (timePassedSeconds < minFadeoutTime) {
            currentAlpha = MAX_DANGER_SIGN_ALPHA;
            sizeMult = 1f;
        }  else currentAlpha = MAX_DANGER_SIGN_ALPHA * (1 - smootherstep(minFadeoutTime, impactSeconds, timePassedSeconds));

    }

    public void renderDangerSign() {
        float timeRemainingMult = 1 - Math.min(timePassedSeconds / AVERAGE_PROJ_IMPACT_TIME, 1);
        int gColour = Math.max((int) Math.round(200 * timeRemainingMult), 0);
        int rColour = Math.max((int) Math.round(100 * timeRemainingMult), 0);
        Color color = new Color(255, gColour, rColour, 255); //Start

        dangerSign.setAdditiveBlend();
        dangerSign.setAngle(angle);
        dangerSign.setSize(DANGER_SIGN_SIZE * sizeMult, DANGER_SIGN_SIZE * sizeMult);
        dangerSign.setAlphaMult(currentAlpha);
        dangerSign.setColor(color);
        dangerSign.renderAtCenter(dangerSignPos.x, dangerSignPos.y);
    }

    public float getRenderRange() {
        return 9999999999999f;
    }

}
