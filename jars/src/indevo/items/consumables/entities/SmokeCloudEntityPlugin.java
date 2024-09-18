package indevo.items.consumables.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.abilities.splitfleet.OrbitFocus;
import indevo.industries.artillery.entities.VariableExplosionEntityPlugin;
import indevo.items.consumables.particles.NebulaParticle;
import indevo.utils.helper.MiscIE;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SmokeCloudEntityPlugin extends BaseCustomEntityPlugin {
    //todo make this rotate around the sun or closest object, will have to update the position of the particles manually.

    public static final float FULL_EFFECT_FRACT = 0.7f; //what %age of the dur the cloud provides full cover, then falls off linear

    public static final float DURATION_IN_DAYS = 4f;
    public static final float BASE_RADIUS = 400f;

    public static final float RAMPUP_DUR_FRACT = 0.02f;
    //public static final float PX_PER_PARTICLE = 400f;
    public static final float PARTICLES_PER_INTERVAL = 12;
    public static final float EXPLOSION_SIZE = 200f;
    public static final float BASE_NEBULA_PARTICLE_SIZE = 200f;

    public transient SpriteAPI sprite;
    public transient SpriteAPI nebulaSprite;

    public IntervalUtil particleInterval = new IntervalUtil(0.05f, 0.1f);
    public IntervalUtil additionalParticleInterval = new IntervalUtil(0.05f, 0.2f);
    public Color color = new Color(0, 0, 0, 255);
    public Color originalColor = new Color(50, 25, 25, 255);

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

        if (entity.getOrbit() == null) {
            OrbitAPI orbit = OrbitFocus.getClosestValidOrbit(entity, true, true);
            if (orbit != null) entity.setOrbit(orbit);
        }

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
            float smoothedDur = MiscIE.smootherstep(0,1,elapsed / (duration * RAMPUP_DUR_FRACT));
            this.color = mixColors(originalColor, targetColor, smoothedDur);
        }

        //spawn initial particles
        if (elapsed < duration * RAMPUP_DUR_FRACT && particleInterval.intervalElapsed()) {
            for (int i = 0; i < PARTICLES_PER_INTERVAL; i++) {
                Random random = new Random();
                NebulaParticle.LocationData location = new NebulaParticle.LocationData(currentRadius, MathUtils.getRandomNumberInRange(0, 360));
                float alpha = 0.7f + 0.2f * (random.nextFloat());

                nebulaParticles.add(new NebulaParticle(BASE_NEBULA_PARTICLE_SIZE * alpha,
                        (float) MathUtils.getRandomNumberInRange(0, 360),
                        alpha,
                        (float) (duration - elapsed * 0.7f + (random.nextFloat() * 0.2 * (duration - elapsed))),
                        color,
                        targetColor.darker(),
                        location));
            }
        }
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

            Vector2f loc = p.pos.getLocation(entity.getLocation());

            nebulaSprite.setTexWidth(0.25f);
            nebulaSprite.setTexHeight(0.25f);
            nebulaSprite.setAdditiveBlend();

            nebulaSprite.setTexX(p.i * 0.25f);
            nebulaSprite.setTexY(p.j * 0.25f);

            //ModPlugin.log("i " + p.i + " j " + p.j + " Angle " + p.angle + " size " + p.size + " alpha " + p.alpha + " colour " + p.color + " sprite " + sprite.getTextureId());

            nebulaSprite.setAngle(p.angle);
            nebulaSprite.setSize(p.size, p.size);
            nebulaSprite.setAlphaMult(p.getCurrentAlpha());
            nebulaSprite.setColor(p.color);
            nebulaSprite.renderAtCenter(loc.x, loc.y);
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

    public static Color mixColors(Color originalColor, Color targetColor, float fraction) {
        if (fraction < 0f) fraction = 0f;
        if (fraction > 1f) fraction = 1f;

        int red1 = originalColor.getRed();
        int green1 = originalColor.getGreen();
        int blue1 = originalColor.getBlue();

        int red2 = targetColor.getRed();
        int green2 = targetColor.getGreen();
        int blue2 = targetColor.getBlue();

        int newRed = (int) (red1 + (red2 - red1) * fraction);
        int newGreen = (int) (green1 + (green2 - green1) * fraction);
        int newBlue = (int) (blue1 + (blue2 - blue1) * fraction);

        return new Color(newRed, newGreen, newBlue);
    }
}
