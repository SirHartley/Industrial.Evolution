package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.utils.trails.MagicCampaignTrailPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.utils.helper.IndustryHelper.smootherstep;

public class RailgunProjectileEntityPlugin extends BaseCustomEntityPlugin {
    //show 1 lines where the projectiles will fly
    //flash line when projectile fires
    //fire 1 times with small deviations

    //always hit when player does not change course
    public static final float MAX_RANGE = 50000f;
    public static final float GLOW_SIZE = 50f;
    public static Color GLOW_COLOR = new Color(100, 220, 255, 255);
    public static float GLOW_FREQUENCY = 1.5f; // on/off cycles per second
    public static final float PROJECTILE_VELOCITY = 6000f;
    public static final float EXPLOSION_SIZE = 250f;

    public static final float LINE_LENGTH = 265;
    public static final float ALPHA_FADE_IN_FRACTION = 0.1f;
    public static final float MAX_LINE_ALPHA = 0.4f;

    //line
    public float currentAlpha = 0f;

    transient private SpriteAPI glow;
    transient private SpriteAPI targetLineSprite;

    //projectile glow
    public float currentProjectileDistance = 0f;
    protected float phase = 0f;
    protected FlickerUtilV2 flicker = new FlickerUtilV2();
    private float timePassedSeconds = 0f;

    //base
    public float impactSeconds;
    public SectorEntityToken origin;
    public Vector2f target;
    private float projectileDelayTime;
    private boolean finishing = false;
    private boolean sound = true;

    public Vector2f originLocation = new Vector2f();

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
        SectorEntityToken t = loc.addCustomEntity(
                null,
                null,
                "IndEvo_railgun_projectile",
                null,
                new ProjectileParams(origin, target, impactSeconds));

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
            updateProjectileFlightTime();

            trailID = MagicCampaignTrailPlugin.getUniqueID();
        }

        readResolve();
    }

    private void updateProjectileFlightTime() {
        float projectileFlightTime = Misc.getDistance(origin.getLocation(), target) / PROJECTILE_VELOCITY;
        this.projectileDelayTime = impactSeconds - projectileFlightTime;
    }

    Object readResolve() {
        targetLineSprite = Global.getSettings().getSprite("fx", "IndEvo_line");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        return this;
    }

    public static final float FRIENDLY_FIRE_IMMUNITY_PROJ_FLIGHT_TIME_FRACT = 0.3f;
    protected static final String ARTILLERY_REACTION_SCRIPT_KEY = "$RailReaction_";
    protected static final float AREA_AVOIDANCE_RADIUS = 300f;

    public void advance(float amount) {
        timePassedSeconds += amount;

        if (originLocation == null || target == null) {
            Misc.fadeAndExpire(entity, 0.1f);
            finishing = true;
            return;
        }
        ;

        timePassedSeconds += amount;

        //make other fleets avoid the location
        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == entity) continue;

            float dist = Misc.getDistance(target, other.getLocation());
            if (dist > 1000f) continue;

            float timing = impactSeconds - timePassedSeconds;
            String key = ARTILLERY_REACTION_SCRIPT_KEY + entity.getId();

            if (!other.getMemoryWithoutUpdate().contains(key)) {
                other.addScript(new MortarProjectileEntityPlugin.ArtilleryReactionScript(target, AREA_AVOIDANCE_RADIUS, other, timing));
                other.getMemoryWithoutUpdate().set(key, true, timing);
            }
        }


        //we update flight time and station location while the station is moving, once it gets shot, the values stay static so the projectile does not drift
        boolean projectileDelayPassed = timePassedSeconds > projectileDelayTime;

        if (!projectileDelayPassed && origin != null && origin.getLocation() != null) {
            updateProjectileFlightTime();
            originLocation = new Vector2f(origin.getLocation().x, origin.getLocation().y);
        }

        //target reticule alpha
        float maxFadeInTime = impactSeconds * ALPHA_FADE_IN_FRACTION;

        if (timePassedSeconds < maxFadeInTime) {
            float mult = smootherstep(0, maxFadeInTime, timePassedSeconds);
            currentAlpha = MAX_LINE_ALPHA * mult;
        } else currentAlpha = MAX_LINE_ALPHA * (1 - smootherstep(maxFadeInTime, impactSeconds, timePassedSeconds));

        if (projectileDelayPassed && !finishing) {

            if (sound) {
                Global.getSoundPlayer().playSound("IndEvo_railgun_fire", MathUtils.getRandomNumberInRange(0.9f, 1.1f), 0.5f, origin.getLocation(), Misc.ZERO);
                sound = false;
            }

            if (timePassedSeconds > projectileDelayTime + 0.05f)
                addTrailToProj(); //clips into station without the delay
            advanceEntityPosition(amount);
            boolean friendlyFireDelayPassed = timePassedSeconds > projectileDelayTime + (impactSeconds - projectileDelayTime) * FRIENDLY_FIRE_IMMUNITY_PROJ_FLIGHT_TIME_FRACT;

            for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
                if (friendlyFireDelayPassed
                        && Misc.getDistance(entity, origin) > ArtilleryStationEntityPlugin.MIN_RANGE
                        && Misc.getDistance(entity, fleet) <= EXPLOSION_SIZE * 0.2f) {

                    spawnExplosion();
                    Misc.fadeAndExpire(entity, 0.1f);
                    finishing = true;
                }
            }

            if (timePassedSeconds > impactSeconds * 1.5f) {
                Misc.fadeAndExpire(entity, 0.1f);
                finishing = true;
            }

            //glow and flicker projectile
            phase += amount * GLOW_FREQUENCY;
            while (phase > 1) phase--;
            flicker.advance(amount);
        }
    }

    public float trailID;
    public static final float TRAIL_TIME = 0.5f;

    private void addTrailToProj() {
        MagicCampaignTrailPlugin.AddTrailMemberSimple(
                entity,
                trailID,
                Global.getSettings().getSprite("fx", "IndEvo_trail_foggy"),
                entity.getLocation(),
                0f,
                Misc.getAngleInDegrees(originLocation, entity.getLocation()),
                GLOW_SIZE * 0.8f,
                1f,
                GLOW_COLOR,
                0.8f,
                TRAIL_TIME,
                true,
                new Vector2f(0, 0));
    }

    public void renderLine() {
        if (targetLineSprite == null) targetLineSprite = Global.getSettings().getSprite("fx", "IndEvo_line");

        float timeRemainingMult = 1 - Math.min(timePassedSeconds / impactSeconds, 1);
        int gColour = Math.max((int) Math.round(200 * timeRemainingMult), 0);
        int rColour = Math.max((int) Math.round(100 * timeRemainingMult), 0);
        Color color = new Color(255, gColour, rColour, 255); //Start

        targetLineSprite.setAdditiveBlend();
        targetLineSprite.setAngle(VectorUtils.getAngle(originLocation, target));
        targetLineSprite.setSize(LINE_LENGTH, 11f);
        targetLineSprite.setAlphaMult(currentAlpha);
        targetLineSprite.setColor(color);

        float pos = LINE_LENGTH / 2f;
        float angle = Misc.getAngleInDegrees(originLocation, target);

        for (int i = 0; i < Math.ceil(MAX_RANGE / LINE_LENGTH); i++) {
            Vector2f newLoc = MathUtils.getPointOnCircumference(originLocation, pos, angle);
            targetLineSprite.renderAtCenter(newLoc.x, newLoc.y);
            pos += LINE_LENGTH;
        }
    }

    public void advanceEntityPosition(float amount) {
        currentProjectileDistance += PROJECTILE_VELOCITY * amount;
        float angle = Misc.getAngleInDegrees(originLocation, target);
        Vector2f newLoc = MathUtils.getPointOnCircumference(originLocation, currentProjectileDistance, angle);
        entity.setLocation(newLoc.x, newLoc.y);
    }

    public void spawnExplosion() {
        LocationAPI cl = entity.getContainingLocation();

        Color color = new Color(100, 160, 255);

        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        "IndEvo_railgun_hit",
                        false,
                        1f,
                        color, cl, entity.getLocation(), EXPLOSION_SIZE, 0.6f);

        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.LOW;
        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                "IndEvo_VariableExplosion", Factions.NEUTRAL, params);

        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderLine();
        if (timePassedSeconds > projectileDelayTime) renderProjectileGlow(viewport);
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
        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

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
