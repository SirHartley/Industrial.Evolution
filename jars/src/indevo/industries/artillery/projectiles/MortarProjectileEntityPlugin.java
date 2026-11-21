package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.industries.artillery.renderers.ArtilleryFireRenderer;
import indevo.industries.artillery.scripts.DroneNetScript;
import indevo.utils.trails.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.utils.helper.MiscIE.smootherstep;

public class MortarProjectileEntityPlugin extends BaseCustomEntityPlugin {

    public static final float GLOW_SIZE = 20f;
    public static Color GLOW_COLOR = new Color(255, 80, 60, 255);
    public static float GLOW_FREQUENCY = 3f; // on/off cycles per second

    public static final float RETICULE_ALPHA_RAMPUP_FRACTION = 0.1f;
    public static final float RETICULE_ALPHA_FADEOUT_FRACTION = 0.8f;
    public static final float MAX_FADE_IN_SIZE_MULT = 1.5f;
    public static final float MAX_RETICULE_ALPHA = 0.7f;
    public static final float PROJECTILE_VELOCITY = 1500f;
    public static final float MIN_LAUNCH_DELAY = 0.5f;
    // min delay plus the mean of the 0 to 3 s warning spread
    public static final float AVERAGE_LAUNCH_DELAY = MIN_LAUNCH_DELAY + 1.5f;
    public static final float MIN_RETARGET_SPEED_MULT = 0.25f;
    public static final float MAX_RETARGET_SPEED_MULT = 3f;
    public static final float TRAIL_TIME = 0.1f;
    public static final float TRAIL_WIDTH = 5f;

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
    public float warningSeconds;
    public float revealSeconds;
    public boolean revealed = false;
    public SectorEntityToken origin;
    public Vector2f target;
    private float projectileDelayTime;
    private float velocity = PROJECTILE_VELOCITY;
    public float trailID;

    //player shells aim again when the reticule shows
    public SectorEntityToken track;
    public Vector2f trackOffset;

    //the projectile is an entity, the target reticule a particle
    //leave when flight time matches impact eta, show the reticule warningSeconds before impact
    //when projectile matches reticule pos, spawn explosion entity
    //add the avoidance script

    public static class ArtilleryReactionScript implements EveryFrameScript {
        float delay;
        boolean done;
        CampaignFleetAPI other;
        Vector2f target;
        float avoidRange;
        @Deprecated
        SectorEntityToken token;
        float secondsUntilActivation;

        public ArtilleryReactionScript(Vector2f target, float avoidRange, CampaignFleetAPI other, float secondsUntilActivation) {
            this.target = target;
            this.avoidRange = avoidRange;
            this.other = other;
            this.secondsUntilActivation = secondsUntilActivation;
            delay = 0.3f + 0.3f * (float) Math.random();
        }

        public void advance(float amount) {
            if (done) return;

            delay -= amount;
            if (delay > 0) return;

            if (!(other.getAI() instanceof ModularFleetAIAPI)) {
                done = true;
                return;
            }

            ModularFleetAIAPI ai = (ModularFleetAIAPI) other.getAI();
            ai.getNavModule().avoidLocation(other.getContainingLocation(),
                    target, avoidRange, avoidRange + 10, secondsUntilActivation + 0.01f);

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
        public float warningSeconds;
        public SectorEntityToken track;
        public Vector2f trackOffset;

        public ProjectileParams(SectorEntityToken origin, Vector2f target, float warningSeconds, SectorEntityToken track, Vector2f trackOffset) {
            this.origin = origin;
            this.target = target;
            this.warningSeconds = warningSeconds;
            this.track = track;
            this.trackOffset = trackOffset;
        }
    }

    public static void spawn(LocationAPI loc, SectorEntityToken origin, Vector2f target, float warningSeconds, SectorEntityToken track, Vector2f trackOffset) {
        SectorEntityToken t = loc.addCustomEntity(null, null, "IndEvo_artillery_projectile", null, new ProjectileParams(origin, target, warningSeconds, track, trackOffset));
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

            this.warningSeconds = ((ProjectileParams) pluginParams).warningSeconds;
            this.track = ((ProjectileParams) pluginParams).track;
            this.trackOffset = ((ProjectileParams) pluginParams).trackOffset;

            // a flight longer than the warning leaves after a short wind up and the reticule waits instead
            float projectileFlightTime = Misc.getDistance(origin.getLocation(), target) / PROJECTILE_VELOCITY;
            float launchDelay = MIN_LAUNCH_DELAY + Math.max(0f, warningSeconds - Setting.ARTILLERY_MORTAR_PROJECTILES_IMPACT_TIME.value());
            this.impactSeconds = Math.max(warningSeconds, launchDelay + projectileFlightTime);
            this.projectileDelayTime = impactSeconds - projectileFlightTime;
            this.revealSeconds = impactSeconds - warningSeconds;
            this.angle = MathUtils.getRandomNumberInRange(-20, 20);
            trailID = MagicCampaignTrailPlugin.getUniqueID();
        }

        readResolve();
    }

    Object readResolve() {
        targetReticule = Global.getSettings().getSprite("fx", "IndEvo_target");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    private boolean finishing = false;
    private boolean sound = true;

    protected static final String ARTILLERY_REACTION_SCRIPT_KEY = "$ArtyReaction_";

    public void advance(float amount) {
        timePassedSeconds += amount;

        if (!revealed && timePassedSeconds >= revealSeconds) reveal();
        if (revealed) warnFleets();

        //target reticule alpha
        float shown = timePassedSeconds - revealSeconds;
        float maxFadeInTime = warningSeconds * RETICULE_ALPHA_RAMPUP_FRACTION;
        float minFadeoutTime = warningSeconds * RETICULE_ALPHA_FADEOUT_FRACTION;

        if (!revealed) currentAlpha = 0f;
        else if (shown < maxFadeInTime) {
            float mult = smootherstep(0, maxFadeInTime, shown);
            currentAlpha = MAX_RETICULE_ALPHA * mult;
            sizeMult = MAX_FADE_IN_SIZE_MULT - (MAX_FADE_IN_SIZE_MULT - 1) * mult;
        } else if (shown < minFadeoutTime) {
            currentAlpha = MAX_RETICULE_ALPHA;
            sizeMult = 1f;
        } else currentAlpha = MAX_RETICULE_ALPHA * (1 - smootherstep(minFadeoutTime, warningSeconds, shown));

        //check if the projectile should be in the air
        boolean projectileDelayPassed = timePassedSeconds > projectileDelayTime;

        if (!projectileDelayPassed && !finishing) {
            ArtilleryFireRenderer.reportIncoming(origin, ArtilleryStationEntityPlugin.TYPE_MORTAR, Misc.getAngleInDegrees(origin.getLocation(), target), projectileDelayTime - timePassedSeconds);
        }

        if (projectileDelayPassed && !finishing) {
            //advance projectile location
            if (sound) {
                ArtilleryFireRenderer.reportShot(origin, ArtilleryStationEntityPlugin.TYPE_MORTAR, Misc.getAngleInDegrees(origin.getLocation(), target));
                Global.getSoundPlayer().playSound("IndEvo_mortar_fire", MathUtils.getRandomNumberInRange(0.9f, 1.1f), 0.5f, origin.getLocation(), Misc.ZERO);
                sound = false;
            }

            advanceEntityPosition(amount);
            if (timePassedSeconds > projectileDelayTime + 0.05f) addTrailToProj();

            if (Misc.getDistance(entity.getLocation(), target) < 30f) {
                spawnExplosion();
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }

            //glow and flicker projectile
            phase += amount * GLOW_FREQUENCY;
            while (phase > 1) phase--;
            flicker.advance(amount);
        } else if (!finishing) entity.setLocation(origin.getLocation().x, origin.getLocation().y);
    }

    private void reveal() {
        revealed = true;

        if (track == null || trackOffset == null || finishing || !track.isAlive() || track.getContainingLocation() != entity.getContainingLocation()) return;

        float remaining = impactSeconds - timePassedSeconds;
        Vector2f aim = MortarShotScript.predict(track, remaining);
        target.set(aim.x + trackOffset.x, aim.y + trackOffset.y);

        if (timePassedSeconds > projectileDelayTime) {
            float speed = Misc.getDistance(entity.getLocation(), target) / Math.max(remaining, 0.1f);
            velocity = MathUtils.clamp(speed, PROJECTILE_VELOCITY * MIN_RETARGET_SPEED_MULT, PROJECTILE_VELOCITY * MAX_RETARGET_SPEED_MULT);
        } else projectileDelayTime = impactSeconds - Misc.getDistance(origin.getLocation(), target) / PROJECTILE_VELOCITY;
    }

    private void warnFleets() {
        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == entity) continue;

            float dist = Misc.getDistance(target, other.getLocation());
            if (dist > Setting.ARTILLERY_MORTAR_EXPLOSION_SIZE.value() + 500f) continue;

            float timing = impactSeconds - timePassedSeconds;
            String key = ARTILLERY_REACTION_SCRIPT_KEY + entity.getId();

            if (!other.getMemoryWithoutUpdate().contains(key)) {
                other.addScript(new ArtilleryReactionScript(target, Setting.ARTILLERY_MORTAR_EXPLOSION_SIZE.value() + 100f, other, timing));
                other.getMemoryWithoutUpdate().set(key, true, timing);
            }
        }
    }

    private void addTrailToProj() {
        MagicCampaignTrailPlugin.AddTrailMemberSimple(
                entity,
                trailID,
                Global.getSettings().getSprite("fx", "IndEvo_stream_core"),
                entity.getLocation(),
                0f,
                Misc.getAngleInDegrees(entity.getLocation(), target),
                TRAIL_WIDTH,
                1f,
                GLOW_COLOR,
                0.8f,
                TRAIL_TIME,
                true,
                new Vector2f(0, 0));
    }

    public void advanceEntityPosition(float amount) {
        float distanceThisFrame = velocity * amount;
        if (distanceThisFrame >= Misc.getDistance(entity.getLocation(), target)) {
            entity.setLocation(target.x, target.y);
            return;
        }

        Vector2f currentTargetDirection = VectorUtils.getDirectionalVector(entity.getLocation(), target);
        currentTargetDirection = VectorUtils.resize(currentTargetDirection, Misc.getDistance(entity.getLocation(), target) - distanceThisFrame);
        Vector2f newCurrentPos = Vector2f.sub(target, currentTargetDirection, null);
        entity.setLocation(newCurrentPos.x, newCurrentPos.y);
    }

    public void spawnExplosion() {
        LocationAPI cl = entity.getContainingLocation();

        Color color = new Color(255, 120, 100);
        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        "IndEvo_mortar_hit",
                        true,
                        1f,
                        color,
                        cl,
                        target,
                        Setting.ARTILLERY_MORTAR_EXPLOSION_SIZE.value(),
                        0.65f);

        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.LOW;

        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                "IndEvo_VariableExplosion", Factions.NEUTRAL, params);
        explosion.setLocation(target.x, target.y);
        DroneNetScript.reportBlast(cl, target, Setting.ARTILLERY_MORTAR_EXPLOSION_SIZE.value());
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderTargetReticule();

        boolean projectileDelayPassed = timePassedSeconds > projectileDelayTime;
        if (projectileDelayPassed) renderProjectileGlow(viewport);
    }

    public void renderTargetReticule() {
        if (!revealed) return;

        float timeRemainingMult = 1 - Math.min((timePassedSeconds - revealSeconds) / warningSeconds, 1);
        int gColour = Math.max((int) Math.round(200 * timeRemainingMult), 0);
        int rColour = Math.max((int) Math.round(100 * timeRemainingMult), 0);
        Color color = new Color(255, gColour, rColour, 255); //Start

        targetReticule.setAdditiveBlend();
        targetReticule.setAngle(angle);
        targetReticule.setSize(Setting.ARTILLERY_MORTAR_EXPLOSION_SIZE.value() * sizeMult, Setting.ARTILLERY_MORTAR_EXPLOSION_SIZE.value() * sizeMult);
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
