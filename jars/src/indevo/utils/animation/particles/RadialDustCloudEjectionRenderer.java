package indevo.utils.animation.particles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public class RadialDustCloudEjectionRenderer implements LunaCampaignRenderingPlugin {
    public static final float DURATION = 6f;
    /*public static final float SPAWN_DUR = 0.4f;
    public static final float SPEED = 150f;
    public static final float PARTICLES_PER_FRAME = 10f;
    public static final float BASE_NEBULA_PARTICLE_SIZE = 100f;
    public static final float PARTICLE_LIFETIME = 2f;*/

    public SectorEntityToken centerEntity;
    public float spawnDur;
    public float speed;
    public float particlesPerFrame;
    public float particleSize;
    public float particleLifetime;
    public float baseAlpha;

    public Color originalColor = new Color(45, 30, 25, 255);
    public Color targetColor = new Color(25, 20, 20, 255);

    public float elapsed = 0f;

    public List<NebulaParticle> nebulaParticles = new ArrayList<>();
    public float angle;
    public float animationRadius;
    public transient SpriteAPI nebulaSprite;

    public RadialDustCloudEjectionRenderer(SectorEntityToken center, float animationRadius, float spawnDur, float speed, float particlesPerFrame, float particleSize, float particleLifetime, float baseAlpha) {
        this.centerEntity = center;
        this.animationRadius = animationRadius;
        this.spawnDur = spawnDur;
        this.speed = speed;
        this.particlesPerFrame = particlesPerFrame;
        this.particleSize = particleSize;
        this.particleLifetime = particleLifetime;
        this.baseAlpha = baseAlpha;
    }

    @Override
    public boolean isExpired() {
        return elapsed > DURATION;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
        if (elapsed < spawnDur) {
            for (int i = 0; i < particlesPerFrame; i++) {
                Random random = new Random();
                NebulaParticle.LocationData location = new NebulaParticle.LocationData(animationRadius, MathUtils.getRandomNumberInRange(0, 360));
                float alpha = 0.1f + 0.7f * (random.nextFloat());

                nebulaParticles.add(new NebulaParticle(particleSize * alpha,
                        (float) MathUtils.getRandomNumberInRange(0, 360),
                        alpha,
                        (float) (particleLifetime+ Math.random()),
                        originalColor,
                        targetColor.darker(),
                        location));
            }
        }

        for (NebulaParticle particle : nebulaParticles) {
            particle.advance(amount);
            particle.pos.incrementRadius(speed * amount * particle.baseAlpha);
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_6A);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (nebulaSprite == null) nebulaSprite = Global.getSettings().getSprite("misc", "nebula_particles");

        for (NebulaParticle p : nebulaParticles) {
            if (p.isExpired()) continue;

            Vector2f loc = p.pos.getLocation(centerEntity.getLocation());

            nebulaSprite.setTexWidth(0.25f);
            nebulaSprite.setTexHeight(0.25f);
            nebulaSprite.setAdditiveBlend();

            nebulaSprite.setTexX(p.i * 0.25f);
            nebulaSprite.setTexY(p.j * 0.25f);

            nebulaSprite.setAngle(p.angle);
            nebulaSprite.setSize(p.size, p.size);
            nebulaSprite.setAlphaMult(p.getCurrentAlpha() * baseAlpha);
            nebulaSprite.setColor(p.color);
            nebulaSprite.renderAtCenter(loc.x, loc.y);
        }
    }
}
