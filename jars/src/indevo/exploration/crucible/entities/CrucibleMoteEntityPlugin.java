package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.projectiles.RailgunProjectileEntityPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class CrucibleMoteEntityPlugin extends BaseCustomEntityPlugin {

    public static final float GLOW_SIZE = 30f;
    public static final float PHASE_1_VEL = 90f;
    public static final float SINE_WAVE_MAX_VARIANCE = 70f;

    protected int phase = 1;
    protected float timePassedSeconds = 0f;
    protected FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);

    protected float phase2Vel;
    protected float cadence;
    protected Vector2f origin;
    protected SectorEntityToken target1;
    protected SectorEntityToken target2;
    protected Color color;
    
    transient SpriteAPI moteSprite;

    public static class CrucibleMotePluginParams {
        Vector2f origin;
        SectorEntityToken target1;
        SectorEntityToken target2;
        Color color;


        public CrucibleMotePluginParams(Vector2f origin, SectorEntityToken target1, SectorEntityToken target2, Color color) {
            this.origin = origin;
            this.target1 = target1;
            this.target2 = target2;
            this.color = color;
        }
    }
    
    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        CrucibleMotePluginParams p = (CrucibleMotePluginParams) pluginParams;

        this.origin = p.origin;
        this.target1 = p.target1;
        this.target2 = p.target2;
        this.color = p.color;
        this.cadence = MathUtils.getRandomNumberInRange(SINE_WAVE_MAX_VARIANCE * 0.3f, SINE_WAVE_MAX_VARIANCE);

        float dist = Misc.getDistance(target1, target2);
        float timeToTravel = dist / RailgunProjectileEntityPlugin.PROJECTILE_VELOCITY;
        this.phase2Vel = dist / Math.max(timeToTravel, 2f);
        
        readResolve();
    }

    Object readResolve() {
        moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        flicker.advance(amount);
        timePassedSeconds += amount;

        if (phase == 1){
            advanceLocation(amount, target1, PHASE_1_VEL);
        } else if (phase == 2){
            advanceLocation(amount, target2, phase2Vel);
        }

        if (phase > 2) Misc.fadeAndExpire(entity);
    }

    private void triggerCatapultBoom(SectorEntityToken t, float size, boolean boom){
        if (t.getCustomPlugin() instanceof YeetopultEntityPlugin){
            YeetopultEntityPlugin p = (YeetopultEntityPlugin) t.getCustomPlugin();
            p.fireAnimation(size, boom);
        }
    }

    public void advanceLocation(float amt, SectorEntityToken target, float vel){
        float dist = vel * amt;
        float distToTarget = Misc.getDistance(entity, target);

        if (dist > distToTarget) {
            dist = distToTarget;
            triggerCatapultBoom(target, 1.5f, phase == 1);
            phase++;
        }

        float angle = Misc.getAngleInDegrees(entity.getLocation(), target.getLocation());
        if (phase == 1) angle = (float) (angle + Math.sin(timePassedSeconds *1.5f) * cadence);


        Vector2f nextPos = MathUtils.getPointOnCircumference(entity.getLocation(), dist, angle);
        entity.setLocation(nextPos.x, nextPos.y);
    }

    public static final float FADE_OUT_PHASE_2_CUTOFF_FRACT = 0.2f;

    public float getPhase2DistanceAlpha(){
        float totalDist = Misc.getDistance(target1, target2);
        float dist = Misc.getDistance(target1, entity);

        if (dist < totalDist * FADE_OUT_PHASE_2_CUTOFF_FRACT) return 1 - (totalDist * FADE_OUT_PHASE_2_CUTOFF_FRACT) / dist;
        else if (dist > totalDist * (1 - FADE_OUT_PHASE_2_CUTOFF_FRACT)) {
            float totalTravelRemaining = totalDist * (1 - FADE_OUT_PHASE_2_CUTOFF_FRACT);
            float correctedDist = dist - (1-FADE_OUT_PHASE_2_CUTOFF_FRACT);
            return correctedDist / totalTravelRemaining;
        } else return 0f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if (phase > 2) return;
        if (moteSprite == null) moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        float w = GLOW_SIZE;
        float h = GLOW_SIZE;

        Vector2f loc = entity.getLocation();

        float moteSpriteAlpha = 1f - 0.5f * flicker.getBrightness();
        if (phase == 2) moteSpriteAlpha *= getPhase2DistanceAlpha();

        moteSprite.setColor(color);
        moteSprite.setSize(w, h);
        moteSprite.setAlphaMult(alphaMult * moteSpriteAlpha);
        moteSprite.setAdditiveBlend();

        moteSprite.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 5; i++) {
            w *= 0.3f;
            h *= 0.3f;
            moteSprite.setSize(w, h);
            moteSprite.setAlphaMult(alphaMult * moteSpriteAlpha * 0.67f);
            moteSprite.renderAtCenter(loc.x, loc.y);
        }
    }
}
