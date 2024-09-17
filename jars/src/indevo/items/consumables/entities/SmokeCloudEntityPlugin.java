package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.items.consumables.particles.NebulaParticle;
import indevo.items.consumables.terrain.SmokeCloudTerrain;
import indevo.utils.helper.MiscIE;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SmokeCloudEntityPlugin extends BaseCustomEntityPlugin {

    public static final float FULL_EFFECT_FRACT = 0.7f; //what %age of the dur the cloud provides full cover, then falls off linear

    public static final float DURATION = Global.getSector().getClock().getSecondsPerDay() * 7f;
    public static final float BASE_RADIUS = 400f;

    public static final float RAMPUP_DUR_FRACT = 0.05f;
    public static final float PX_PER_PARTICLE = 200f;
    public static final float EXPLOSION_SIZE = 100f;
    public static final float BASE_NEBULA_PARTICLE_SIZE = 100f;

    public transient SpriteAPI sprite;
    public transient SpriteAPI nebulaSprite;

    public IntervalUtil particleInterval = new IntervalUtil(0.05f, 0.1f);
    public IntervalUtil additionalParticleInterval = new IntervalUtil(0.05f, 0.2f);
    public Color color = new Color(0, 0, 0, 255);
    public Color originalColor = new Color(100, 20, 20, 255);

    public Color targetColor = new Color(30, 25, 20, 255);


    public boolean explosion = true;
    public boolean finishing = false;
    public SectorEntityToken terrain;

    public float elapsed = 0;
    public float angle;
    public float duration;
    public float rad;

    public List<NebulaParticle> nebulaParticles = new ArrayList<>();

    public static class SmokeCloudParams {
        public LocationAPI loc;
        public Vector2f pos;
        public float dur;
        public float rad;

        public SmokeCloudParams(LocationAPI loc, Vector2f pos, float dur, float rad) {
            this.loc = loc;
            this.pos = pos;
            this.dur = dur;
            this.rad = rad;
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        if (pluginParams instanceof SmokeCloudParams) {
            this.duration = ((SmokeCloudParams) pluginParams).dur;
            this.rad = ((SmokeCloudParams) pluginParams).rad;

            angle = MathUtils.getRandomNumberInRange(0, 360);
            entity.setLocation(((SmokeCloudParams) pluginParams).pos.x, ((SmokeCloudParams) pluginParams).pos.y);

            terrain = ((SmokeCloudParams) pluginParams).loc.addTerrain("IndEvo_SmokeCloud", new BaseRingTerrain.RingParams(rad, 0f, entity, "Chaff Cloud"));
            terrain.setCircularOrbit(entity, 0, 0, 0);
        }

        readResolve();
    }

    Object readResolve() {
        sprite = Global.getSettings().getSprite("fx", "IndEvo_sub_missile_explosion");
        return this;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (!finishing && elapsed > duration) {
            Misc.fadeAndExpire(terrain, 0.1f);
            Misc.fadeAndExpire(entity, 0.1f);
            finishing = true;
            return;
        }

        elapsed += amount;
        particleInterval.advance(amount);
        additionalParticleInterval.advance(amount);
        for (NebulaParticle particle : nebulaParticles) particle.advance(amount);

        float timePassedMult = Math.min(elapsed / (duration * RAMPUP_DUR_FRACT), 1);
        float currentRadius = rad * timePassedMult;

        if (explosion) {
            spawnExplosion(EXPLOSION_SIZE);
            explosion = false;
        }

        if (elapsed < duration * RAMPUP_DUR_FRACT){
            float smoothedDur = MiscIE.smootherstep(0,1,elapsed / duration*RAMPUP_DUR_FRACT);

            int rDiff = originalColor.getRed() - targetColor.getRed();
            int gDiff = originalColor.getGreen() - targetColor.getGreen();
            int bDiff = originalColor.getBlue() - targetColor.getBlue();

            int rMix = Math.round(originalColor.getRed() + rDiff * smoothedDur);
            int gMix = Math.round(originalColor.getGreen() + gDiff * smoothedDur);
            int bMix = Math.round(originalColor.getBlue() + bDiff * smoothedDur);

            this.color = new Color(rMix, gMix, bMix, 255);
        }

        //spawn initial particles
        if (elapsed < duration * RAMPUP_DUR_FRACT && particleInterval.intervalElapsed()) {
            float circ = (float) (2 * currentRadius * Math.PI);
            float particleAmt = circ / PX_PER_PARTICLE;

            for (int i = 0; i < particleAmt; i++) {
                Random random = new Random();
                Vector2f loc = MathUtils.getPointOnCircumference(entity.getLocation(), currentRadius, MathUtils.getRandomNumberInRange(0, 360));
                float alpha = 0.8f + 0.2f * (random.nextFloat());

                nebulaParticles.add(new NebulaParticle(BASE_NEBULA_PARTICLE_SIZE * alpha,
                        (float) MathUtils.getRandomNumberInRange(0, 360),
                        alpha,
                        (float) (duration - elapsed * 0.8f + random.nextFloat() * 0.2 * (duration - elapsed)),
                        color,
                        new Color(120, 115, 110, 255),
                        loc));
            }
        }

        if (elapsed > duration * FULL_EFFECT_FRACT) advanceTerrainEffect();
    }

    public void advanceTerrainEffect() {
        float remainingAlphaDur = duration - duration * SmokeCloudEntityPlugin.FULL_EFFECT_FRACT;
        float correctedDur = elapsed - duration * SmokeCloudEntityPlugin.FULL_EFFECT_FRACT;
        ((SmokeCloudTerrain)terrain).setEffectFract(Math.max(0, MiscIE.smootherstep(1, 0, correctedDur / remainingAlphaDur)));
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        float size = 3f * rad * MiscIE.smootherstep(0, (duration * RAMPUP_DUR_FRACT), elapsed);
        float alpha = 1 - Math.min(elapsed / (duration * RAMPUP_DUR_FRACT), 1);

        sprite.setAdditiveBlend();
        sprite.setAngle(angle);
        sprite.setSize(size, size);
        sprite.setAlphaMult(alpha);
        sprite.setColor(color);
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);

        if (nebulaSprite == null) nebulaSprite = Global.getSettings().getSprite("misc", "nebula_particles");

        for (NebulaParticle p : nebulaParticles) {
            if (p.isExpired()) continue;
            p.getScaledSprite(nebulaSprite).renderAtCenter(p.pos.x, p.pos.y);
        }
    }

    public void spawnExplosion(float size) {
        LocationAPI cl = entity.getContainingLocation();

        VariableExplosionEntityPlugin.VariableExplosionParams params =
                new VariableExplosionEntityPlugin.VariableExplosionParams(
                        "IndEvo_missile_hit",
                        false,
                        0f,
                        color, cl, entity.getLocation(), size, 0.3f);

        params.damage = ExplosionEntityPlugin.ExplosionFleetDamage.NONE;

        SectorEntityToken explosion = cl.addCustomEntity(Misc.genUID(), "Explosion",
                "IndEvo_VariableExplosion", Factions.NEUTRAL, params);

        explosion.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public float getRenderRange() {
        return 9999999999999f;
    }
}
