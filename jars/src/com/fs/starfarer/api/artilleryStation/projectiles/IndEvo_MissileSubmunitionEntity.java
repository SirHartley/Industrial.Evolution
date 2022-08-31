package com.fs.starfarer.api.artilleryStation.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.artilleryStation.trails.IndEvo_MagicCampaignTrailPlugin;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.impl.items.consumables.entityAbilities.InterdictionMineAbility;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.plugins.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.campaign.impl.items.consumables.entityAbilities.InterdictionMineAbility.getInterdictSeconds;

public class IndEvo_MissileSubmunitionEntity extends BaseCustomEntityPlugin {
    //travels in a wiggly line from spawn point for a certain duration
    //checks surroundings for enemy, if found, home in with limited turn rate
    //splode into slow field and interdict if close enough

    public static class MissileSubmunitionParams {
        public Vector2f origin;
        public Vector2f target;
        public float duration;
        public LocationAPI loc;
        public FactionAPI faction;

        public MissileSubmunitionParams(LocationAPI loc, FactionAPI faction, Vector2f origin, Vector2f target, float duration) {
            this.origin = origin;
            this.target = target;
            this.duration = duration;
            this.loc = loc;
            this.faction = faction;
        }
    }

    public static final float PROJECTILE_VELOCITY = 200f;
    public static final float EXPLOSION_SIZE = 350f;
    public static final float INITIATE_EXPLOSION_DISTANCE = 50f;
    public static final float SINE_WAVE_MAX_VARIANCE = 40f;
    public static final float TRACKING_DISTANCE = 500f;

    //trail
    public static final float TRAIL_TIME = 0.4f;
    public static final Color BASE_TRAIL_COLOUR = new Color(100, 200, 255, 255);
    public static final Color HOSTILE_TRAIL_COLOUR = new Color(255, 50, 50, 255);

    //base
    transient private SpriteAPI missileSprite;

    private float timePassedSeconds = 0f;
    public Vector2f origin;
    public Vector2f target;
    public float duration;
    public float cadence;
    public float startAngle;

    //tracking mode
    public static final float MAX_ANGLE_TURN_DIST_PER_SECOND = 40f;
    public SectorEntityToken currentTarget = null;

    public float trailID;
    public boolean finishing = false;

    public static void spawn(MissileSubmunitionParams params) {
        SectorEntityToken t = params.loc.addCustomEntity(Misc.genUID(), null, "IndEvo_missileSubmunition", params.faction.getId(), params);
        t.setLocation(params.origin.x, params.origin.y);
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof MissileSubmunitionParams) {
            this.origin = ((MissileSubmunitionParams) pluginParams).origin;
            this.target = ((MissileSubmunitionParams) pluginParams).target;
            this.duration = ((MissileSubmunitionParams) pluginParams).duration;

            startAngle = Misc.getAngleInDegrees(origin, target);
            cadence = MathUtils.getRandomNumberInRange(SINE_WAVE_MAX_VARIANCE * 0.5f, SINE_WAVE_MAX_VARIANCE);
            trailID = MagicCampaignTrailPlugin.getUniqueID();
        }

        readResolve();
    }

    Object readResolve() {
        missileSprite = Global.getSettings().getSprite("fx", "IndEvo_sub_missile");
        return this;
    }

    public void searchForTarget() {
        //track fleet when in range
        for (CampaignFleetAPI fleet : Misc.getNearbyFleets(entity, TRACKING_DISTANCE)) {
            if ((fleet.isHostileTo(entity) || entity.getFaction().isNeutralFaction())
                    || (Global.getSettings().isDevMode() && fleet.isPlayerFleet())) {
                currentTarget = fleet;
                break;
            }
        }
    }

    public void advance(float amount) {
        timePassedSeconds += amount;

        if (!finishing) {

            if (currentTarget != null && Misc.getDistance(currentTarget, entity) > TRACKING_DISTANCE) {
                startAngle = entity.getFacing();
                currentTarget = null;
            }

            if (currentTarget == null) searchForTarget();
            if (currentTarget == null) advanceProjectileNoTarget(amount); //go in a wibbly wobbly straight line
            else advanceProjectileTowardsEnemy(amount); //chase the cunt that entered range

            //explode when fleet in range
            for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
                boolean devmodePlayerFleet = (Global.getSettings().isDevMode() && fleet.isPlayerFleet() && Misc.getDistance(entity, fleet) <= INITIATE_EXPLOSION_DISTANCE);
                boolean hostileAndClose = (fleet.isHostileTo(entity) || entity.getFaction().isNeutralFaction()) && Misc.getDistance(entity, fleet) <= INITIATE_EXPLOSION_DISTANCE;

                if (devmodePlayerFleet || hostileAndClose){
                    spawnExplosion();
                    Misc.fadeAndExpire(entity, 0.1f);
                    finishing = true;
                }
            }

            //or splode when duration over
            if (timePassedSeconds > duration) {
                spawnExplosion();
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }

            addTrailToProj();
        }
    }

    public void advanceProjectileTowardsEnemy(float amount){
        float dist = PROJECTILE_VELOCITY * amount;
        float turn = MAX_ANGLE_TURN_DIST_PER_SECOND * amount;
        float currAngle = Misc.getAngleInDegrees(entity.getLocation(), currentTarget.getLocation());
        float angleDiff = Misc.getAngleDiff(currAngle, entity.getFacing());

        if (angleDiff < turn) turn = angleDiff;
        float nextAngle = entity.getFacing() + turn * (currAngle > entity.getFacing() ? 1 : -1);

        //IndEvo_modPlugin.log("current angle " + currAngle + " current diff " + angleDiff + " TURN " + turn + " next angle " + nextAngle);
        Vector2f nextPos = MathUtils.getPointOnCircumference(entity.getLocation(), dist, nextAngle);
        entity.setLocation(nextPos.x, nextPos.y);
        entity.setFacing(nextAngle);
    }

    public void advanceProjectileNoTarget(float amount) {
        float nextAngle = (float) (startAngle + Math.sin(timePassedSeconds * duration * 0.1f) * cadence);
        float dist = PROJECTILE_VELOCITY * amount;

        Vector2f nextPos = MathUtils.getPointOnCircumference(entity.getLocation(), dist, nextAngle);
        entity.setLocation(nextPos.x, nextPos.y);
        entity.setFacing(nextAngle);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderProjectile(viewport);
    }

    public void renderProjectile(ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        missileSprite.setNormalBlend();
        missileSprite.setAngle(entity.getFacing() - 90f);
        missileSprite.setSize(12, 26);
        missileSprite.setAlphaMult(alphaMult);
        missileSprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }


    //explosion

    public void spawnExplosion() {
        IndEvo_ECMExplosion.ECMExplosionParams p = new IndEvo_ECMExplosion.ECMExplosionParams(
                entity.getContainingLocation(),
                entity.getLocation(),
                IndEvo_ECMExplosion.DURATION,
                IndEvo_ECMExplosion.BASE_RADIUS);

        IndEvo_ECMExplosion.spawn(p);
    }

    //trail

    private void addTrailToProj() {
        IndEvo_MagicCampaignTrailPlugin.AddTrailMemberSimple(
                entity,
                trailID,
                Global.getSettings().getSprite("fx", "IndEvo_stream_core"),
                entity.getLocation(),
                0f,
                entity.getFacing(),
                12f,
                1f,
                currentTarget == null ? BASE_TRAIL_COLOUR : HOSTILE_TRAIL_COLOUR,
                0.9f,
                TRAIL_TIME,
                true,
                new Vector2f(0, 0));
    }

    public float getRenderRange() {
        return 9999999999999f;
    }
}
