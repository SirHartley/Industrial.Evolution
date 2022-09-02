package com.fs.starfarer.api.artilleryStation.projectiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.impl.items.consumables.entityAbilities.InterdictionMineAbility;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.IndEvo_IndustryHelper.smootherstep;

public class IndEvo_ArtilleryProjectileEntityPlugin extends BaseCustomEntityPlugin {

    public static final float GLOW_SIZE = 30f;
    public static Color GLOW_COLOR = new Color(255, 80, 60, 255);
    public static float GLOW_FREQUENCY = 3f; // on/off cycles per second
    public static float EFFECT_SIZE = 300f;

    public static final float RETICULE_ALPHA_RAMPUP_FRACTION = 0.1f;
    public static final float RETICULE_ALPHA_FADEOUT_FRACTION = 0.8f;
    public static final float MAX_FADE_IN_SIZE_MULT = 1.5f;
    public static final float MAX_RETICULE_ALPHA = 0.7f;
    public static final float PROJECTILE_VELOCITY = 1500f;

    transient private SpriteAPI glow;
    transient private SpriteAPI targetReticule;

    //glow
    protected float phase = 0f;
    protected FlickerUtilV2 flicker = new FlickerUtilV2();
    private float timePassedSeconds = 0f;

    //reticule
    public float currentAlpha = 0f;
    public float angle = 360f;
    public float sizeMult = 1f;

    //base
    public float impactSeconds;
    public SectorEntityToken origin;
    public Vector2f target;
    private float projectileDelayTime;

    //the projectile is an entity, the target reticule a particle
    //render reticule, wait until flight time matches impact eta, then render projectile at fixed velocity on impact vector
    //when projectile matches reticule pos, spawn explosion entity
    //add the avoidance script

    public static class ArtilleryReactionScript implements EveryFrameScript {
        float delay;
        boolean done;
        CampaignFleetAPI other;
        SectorEntityToken token;
        float secondsUntilActivation;

        public ArtilleryReactionScript(SectorEntityToken token, CampaignFleetAPI other, float secondsUntilActivation) {
            this.token = token;
            this.other = other;
            this.secondsUntilActivation = secondsUntilActivation;
            delay = 0.3f + 0.3f * (float) Math.random();
            //delay = 0f;
        }

        public void advance(float amount) {
            if (done) return;

            delay -= amount;
            if (delay > 0) return;

            SectorEntityToken.VisibilityLevel level = token.getVisibilityLevelTo(other);
            if (level == SectorEntityToken.VisibilityLevel.NONE || level == SectorEntityToken.VisibilityLevel.SENSOR_CONTACT) {
                done = true;
                return;
            }

            if (!(other.getAI() instanceof ModularFleetAIAPI)) {
                done = true;
                return;
            }
            ModularFleetAIAPI ai = (ModularFleetAIAPI) other.getAI();

            float dist = Misc.getDistance(token.getLocation(), other.getLocation());
            float speed = Math.max(1f, other.getTravelSpeed());
            float rushTime = secondsUntilActivation;
            rushTime += 0.5f + 0.5f * (float) Math.random();

            float range = InterdictionMineAbility.getRange();
            float getAwayTime = 1f + (range - dist) / speed;
            AbilityPlugin sb = other.getAbility(Abilities.SENSOR_BURST);
            if (getAwayTime > rushTime && sb != null && sb.isUsable() && (float) Math.random() > 0.67f) {
                sb.activate();
                done = true;
                return;
            }

            //float avoidRange = Math.min(dist, getRange(other));
            float avoidRange = EFFECT_SIZE + 100f;
            ai.getNavModule().avoidLocation(token.getContainingLocation(),
                    token.getLocation(), avoidRange, avoidRange + 50f, secondsUntilActivation + 0.01f);

            ai.getNavModule().avoidLocation(token.getContainingLocation(),
                    //fleet.getLocation(), dist, dist + 50f, activationDays + 0.01f);
                    Misc.getPointAtRadius(token.getLocation(), avoidRange * 0.5f), avoidRange, avoidRange * 1.5f + 50f, secondsUntilActivation + 0.05f);

            done = true;
        }

        public boolean isDone() {
            return done;
        }

        public boolean runWhilePaused() {
            return false;
        }
    }

    public static class ProjectileParams {
        public SectorEntityToken origin;
        public Vector2f target;
        public float impactSeconds;

        public ProjectileParams(SectorEntityToken origin, Vector2f target, float impactSeconds) {
            this.origin = origin;
            this.target = target;
            this.impactSeconds = impactSeconds;
        }
    }

    public static void spawn(LocationAPI loc, SectorEntityToken origin, Vector2f target, float impactSeconds) {
        SectorEntityToken t = loc.addCustomEntity(null, null, "IndEvo_artillery_projectile", null, new ProjectileParams(origin, target, impactSeconds));
        t.setLocation(origin.getLocation().x, origin.getLocation().y);
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof ProjectileParams) {
            this.origin = ((ProjectileParams) pluginParams).origin;
            this.target = new Vector2f();
            target.x = ((ProjectileParams) pluginParams).target.x;
            target.y = ((ProjectileParams) pluginParams).target.y;

            this.impactSeconds = ((ProjectileParams) pluginParams).impactSeconds;

            float projectileFlightTime = Misc.getDistance(origin.getLocation(), target) / PROJECTILE_VELOCITY;
            this.projectileDelayTime = impactSeconds - projectileFlightTime;
            this.angle = Misc.getAngleInDegrees(target, Global.getSector().getPlayerFleet().getLocation());
        }

        readResolve();
    }

    Object readResolve() {
        targetReticule = Global.getSettings().getSprite("fx", "IndEvo_target");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    private boolean finishing = false;

    public void advance(float amount) {
        timePassedSeconds += amount;

        //make other fleets avoid the location
        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == entity) continue;

            float dist = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (dist > EFFECT_SIZE + 500f) continue;
            if (!other.hasScriptOfClass(ArtilleryReactionScript.class))
                other.addScript(new ArtilleryReactionScript(entity, other, impactSeconds - timePassedSeconds));
        }

        //target reticule alpha
        float maxFadeInTime = impactSeconds * RETICULE_ALPHA_RAMPUP_FRACTION;
        float minFadeoutTime = impactSeconds * RETICULE_ALPHA_FADEOUT_FRACTION;

        if(timePassedSeconds < maxFadeInTime) {
            float mult = smootherstep(0, maxFadeInTime, timePassedSeconds);
            currentAlpha = MAX_RETICULE_ALPHA * mult;
            sizeMult = MAX_FADE_IN_SIZE_MULT - (MAX_FADE_IN_SIZE_MULT - 1) * mult;
        } else if (timePassedSeconds < minFadeoutTime) {
            currentAlpha = MAX_RETICULE_ALPHA;
            sizeMult = 1f;
        }  else currentAlpha = MAX_RETICULE_ALPHA * (1 - smootherstep(minFadeoutTime, impactSeconds, timePassedSeconds));

        //check if the projectile should be in the air
        boolean projectileDelayPassed = timePassedSeconds > projectileDelayTime;

        if (projectileDelayPassed && !finishing) {
            //advance projectile location

            advanceEntityPosition(amount);

            if (Misc.getDistance(entity.getLocation(), target) < 30f) {
                spawnExplosion();
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }

            //glow and flicker projectile
            phase += amount * GLOW_FREQUENCY;
            while (phase > 1) phase--;
            flicker.advance(amount);
        } else if(!finishing) entity.setLocation(origin.getLocation().x, origin.getLocation().y);
    }

    public void advanceEntityPosition(float amount) {
        float distanceThisFrame = PROJECTILE_VELOCITY * amount;
        Vector2f currentTargetDirection = VectorUtils.getDirectionalVector(entity.getLocation(), target);
        currentTargetDirection = VectorUtils.resize(currentTargetDirection, Misc.getDistance(entity.getLocation(), target) - distanceThisFrame);
        Vector2f newCurrentPos = Vector2f.sub(target, currentTargetDirection, null);
        entity.setLocation(newCurrentPos.x, newCurrentPos.y);
    }

    public void spawnExplosion() {
        LocationAPI cl = entity.getContainingLocation();

        Color color = new Color(255, 120, 100);
        ExplosionEntityPlugin.ExplosionParams params = new ExplosionEntityPlugin.ExplosionParams(color, cl, target, EFFECT_SIZE, 0.65f);
        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.LOW;

        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                Entities.EXPLOSION, Factions.NEUTRAL, params);
        explosion.setLocation(target.x, target.y);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderTargetReticule();

        boolean projectileDelayPassed = timePassedSeconds > projectileDelayTime;
        if(projectileDelayPassed) renderProjectileGlow(viewport);
    }

    public void renderTargetReticule() {
        float timeRemainingMult = 1 - Math.min(timePassedSeconds / impactSeconds, 1);
        int gColour = Math.max( (int) Math.round(200 * timeRemainingMult), 0);
        int rColour = Math.max( (int) Math.round(100 * timeRemainingMult), 0);
        Color color = new Color(255, gColour, rColour, 255); //Start

        targetReticule.setAdditiveBlend();
        targetReticule.setAngle(angle);
        targetReticule.setSize(EFFECT_SIZE * sizeMult, EFFECT_SIZE * sizeMult);
        targetReticule.setAlphaMult(currentAlpha);
        targetReticule.setColor(color);
        targetReticule.renderAtCenter(target.x, target.y);
    }

    public float getGlowAlpha() {
        float glowAlpha = 0f;
        if (phase < 0.5f) glowAlpha = phase * 2f;
        if (phase >= 0.5f) glowAlpha = (1f - (phase - 0.5f) * 2f);
        glowAlpha = 0.75f + glowAlpha * 0.25f;
        glowAlpha *= 1f - flicker.getBrightness();
        if (glowAlpha < 0) glowAlpha = 0;
        if (glowAlpha > 1) glowAlpha = 1;
        return glowAlpha;
    }

    public float getRenderRange() {
        return 1000000f;
    }

    public void renderProjectileGlow(ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec == null) return;

        float w = GLOW_SIZE;
        float h = GLOW_SIZE;

        Vector2f loc = entity.getLocation();

        float glowAlpha = getGlowAlpha();

        glow.setColor(GLOW_COLOR);
        glow.setSize(w, h);
        glow.setAlphaMult(alphaMult * glowAlpha * 0.5f);
        glow.setAdditiveBlend();

        glow.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 5; i++) {
            w *= 0.3f;
            h *= 0.3f;
            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * 0.67f);
            glow.renderAtCenter(loc.x, loc.y);
        }
    }
}
