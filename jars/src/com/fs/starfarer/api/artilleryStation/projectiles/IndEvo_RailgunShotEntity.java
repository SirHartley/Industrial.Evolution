package com.fs.starfarer.api.artilleryStation.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.plugins.IndEvo_modPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;

import static com.fs.starfarer.api.IndEvo_IndustryHelper.smootherstep;
import static java.lang.Math.random;

public class IndEvo_RailgunShotEntity extends BaseCustomEntityPlugin {

    //render the warning sign and add the X shots
    public static final int DEFAULT_PROJECTILE_AMT = Global.getSettings().getInt("IndEvo_Artillery_railgun_projectilesPerShot");
    public static final float AVERAGE_PROJ_IMPACT_TIME = Global.getSettings().getFloat("IndEvo_Artillery_railgun_projectilesImpactTime");
    public static final float DANGER_SIGN_FADEOUT_TIME = 8f;

    public static float DANGER_SIGN_SIZE = 150f;
    public static final float DANGERSIGN_ALPHA_RAMPUP_FRACTION = 0.1f;
    public static final float MAX_FADE_IN_SIZE_MULT = 1.5f;
    public static final float MAX_RETICULE_ALPHA = 0.7f;

    private static final float BASE_FUZZ_MULT = Global.getSettings().getFloat("IndEvo_Artillery_railgun_shotFuzzMult");

    //dangerSign
    transient private SpriteAPI dangerSign;
    public float currentAlpha = 0f;
    public float sizeMult = 1f;
    public Vector2f dangerSignPos = new Vector2f();

    //base
    private float timePassedSeconds = 0f;
    public int num;
    public SectorEntityToken origin;
    public SectorEntityToken target;

    public static class RailgunShotParams{
        public int num;
        public SectorEntityToken origin;
        public SectorEntityToken target;

        public RailgunShotParams(int num, SectorEntityToken origin, SectorEntityToken target) {
            this.num = num;
            this.origin = origin;
            this.target = target;
        }
    }

    public static void spawn(SectorEntityToken origin, SectorEntityToken target, int num) {
        SectorEntityToken t = origin.getContainingLocation().addCustomEntity(null, null, "IndEvo_RailgunShotEntity", null, new RailgunShotParams(num, origin, target));
        t.setLocation(origin.getLocation().x, origin.getLocation().y);
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof RailgunShotParams) {
            this.num = ((RailgunShotParams) pluginParams).num;
            this.origin = ((RailgunShotParams) pluginParams).origin;
            this.target = ((RailgunShotParams) pluginParams).target;
        }

        initProjectiles(target);

        readResolve();
    }

    Object readResolve() {
        dangerSign = Global.getSettings().getSprite("fx", "IndEvo_danger_notif");
        return this;
    }

    public void advance(float amount) {
        timePassedSeconds += amount;

        //target reticule alpha
        float maxFadeInTime = DANGER_SIGN_FADEOUT_TIME * DANGERSIGN_ALPHA_RAMPUP_FRACTION;

        if(timePassedSeconds < maxFadeInTime) {
            float mult = smootherstep(0, maxFadeInTime, timePassedSeconds);
            currentAlpha = MAX_RETICULE_ALPHA * mult;
            sizeMult = MAX_FADE_IN_SIZE_MULT - (MAX_FADE_IN_SIZE_MULT - 1) * mult;
        } else currentAlpha = MAX_RETICULE_ALPHA * (1 - smootherstep(maxFadeInTime, DANGER_SIGN_FADEOUT_TIME, timePassedSeconds));
    }

    public static Vector2f getAnticipatedTargetLoc(SectorEntityToken entity){
        Vector2f vel = entity.getVelocity();
        float dist = vel.length() * (AVERAGE_PROJ_IMPACT_TIME + 1f);
        float currentNavigationAngle = Misc.getAngleInDegrees(vel);
        Vector2f location = Misc.getUnitVectorAtDegreeAngle(currentNavigationAngle);
        location.scale(dist);
        return Vector2f.add(location, entity.getLocation(), location);
    }

    public void initProjectiles(SectorEntityToken target){
        Random random = new Random();
        boolean fuzz = true;

        for (int i = 1; i <= num; i++){
            float impactTimeSeconds = (float) (AVERAGE_PROJ_IMPACT_TIME + i + 1 * random());

            Vector2f vel = target.getVelocity();

            float fuzzMult = 1f;
            if (fuzz) {
                fuzzMult = BASE_FUZZ_MULT * random.nextFloat();
                fuzzMult *= random.nextBoolean() ? -1 : 1;
                fuzzMult += 1;

                IndEvo_modPlugin.log("fuzzing at " + fuzzMult);
            }

            float dist = vel.length() * impactTimeSeconds * fuzzMult;
            float currentNavigationAngle = Misc.getAngleInDegrees(vel);
            Vector2f location = Misc.getUnitVectorAtDegreeAngle(currentNavigationAngle);
            location.scale(dist);
            Vector2f anticipatedEntityPos = Vector2f.add(location, target.getLocation(), location);

            IndEvo_RailgunProjectileEntityPlugin.spawn(
                    target.getContainingLocation(),
                    origin,
                    anticipatedEntityPos,
                    impactTimeSeconds);

            fuzz = !fuzz;
        }

        float angle = Misc.getAngleInDegrees(target.getLocation(), origin.getLocation());
        dangerSignPos = MathUtils.getPointOnCircumference(target.getLocation(), 400f, angle);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderTargetReticule();
    }

    public void renderTargetReticule() {
        float timeRemainingMult = 1 - Math.min(timePassedSeconds / AVERAGE_PROJ_IMPACT_TIME, 1);
        int gColour = Math.max( (int) Math.round(200 * timeRemainingMult), 0);
        int rColour = Math.max( (int) Math.round(100 * timeRemainingMult), 0);
        Color color = new Color(255, gColour, rColour, 255); //Start

        dangerSign.setAdditiveBlend();
        dangerSign.setSize(DANGER_SIGN_SIZE * sizeMult, DANGER_SIGN_SIZE * sizeMult);
        dangerSign.setAlphaMult(currentAlpha);
        dangerSign.setColor(color);
        dangerSign.renderAtCenter(dangerSignPos.x, dangerSignPos.y);
    }

    public float getRenderRange() {
        return 9999999999999f;
    }
}
