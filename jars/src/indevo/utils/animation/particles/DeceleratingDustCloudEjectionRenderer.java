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

public class DeceleratingDustCloudEjectionRenderer implements LunaCampaignRenderingPlugin {
    public static final float DURATION = 6f;
    /*public static final float SPAWN_DUR = 0.4f;
    public static final float SPEED = 150f;
    public static final float PARTICLES_PER_FRAME = 10f;
    public static final float BASE_NEBULA_PARTICLE_SIZE = 100f;
    public static final float PARTICLE_LIFETIME = 2f;*/

    public SectorEntityToken centerEntity;
    public float speed;
    public float particlesPerFrame;
    public float particleSize;
    public float particleLifetime;
    public float baseAlpha;
    public float maxMoveTime;
    public boolean additive = false;
    public boolean expired = false;
    public CampaignEngineLayers layer = CampaignEngineLayers.TERRAIN_4;

    public Color originalColor = new Color(120, 90, 90, 80);
    public Color targetColor = new Color(15, 10, 10, 80);

    public List<NebulaParticle> nebulaParticles = new ArrayList<>();
    public float angle;
    public float animationRadius;
    public transient SpriteAPI nebulaSprite;

    public DeceleratingDustCloudEjectionRenderer(SectorEntityToken center, float animationRadius, float speed, float particlesPerFrame, float particleSize, float particleLifetime, float baseAlpha, float maxMoveTime, Color originalColor, boolean additive, CampaignEngineLayers layer) {
        this.originalColor = originalColor;
        this.layer = layer;
        this.centerEntity = center;
        this.animationRadius = animationRadius;
        this.speed = speed;
        this.particlesPerFrame = particlesPerFrame;
        this.particleSize = particleSize;
        this.particleLifetime = particleLifetime;
        this.baseAlpha = baseAlpha;
        this.maxMoveTime = maxMoveTime;
        this.additive = additive;
    }

    public DeceleratingDustCloudEjectionRenderer(SectorEntityToken center, float animationRadius, float speed, float particlesPerFrame, float particleSize, float particleLifetime, float baseAlpha, float maxMoveTime) {
        this.centerEntity = center;
        this.animationRadius = animationRadius;
        this.speed = speed;
        this.particlesPerFrame = particlesPerFrame;
        this.particleSize = particleSize;
        this.particleLifetime = particleLifetime;
        this.baseAlpha = baseAlpha;
        this.maxMoveTime = maxMoveTime;
    }

    @Override
    public void advance(float amount) {
        for (int i = 0; i < particlesPerFrame; i++) {
            Random random = new Random();
            NebulaParticle.LocationData location = new NebulaParticle.LocationData(animationRadius, MathUtils.getRandomNumberInRange(0, 360), new Vector2f(centerEntity.getLocation()));
            float alpha = 0.1f + 0.7f * (random.nextFloat());

            nebulaParticles.add(new NebulaParticle(particleSize * alpha,
                    (float) MathUtils.getRandomNumberInRange(0, 360),
                    alpha,
                    (float) (particleLifetime * Math.random()),
                    originalColor,
                    targetColor.darker(),
                    location));
        }

        for (NebulaParticle particle : nebulaParticles) {
            particle.advance(amount);

            float baseSpeedThisFrame = speed * amount * particle.baseAlpha;
            float adjustedSpeedForMoveDur = baseSpeedThisFrame * logisticDecreasing(particle.elapsed / maxMoveTime);
            particle.pos.incrementRadius(adjustedSpeedForMoveDur);
        }
    }

    public static float logisticDecreasing(float x) {
        //rough logistic decrease with midpoint 0.5
        return (float) (1f / (1f + Math.exp(10f * (x - 0.5f))));
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (nebulaSprite == null) nebulaSprite = Global.getSettings().getSprite("misc", "nebula_particles");

        for (NebulaParticle p : nebulaParticles) {
            if (p.isExpired()) continue;

            Vector2f loc = p.pos.getLocation();

            nebulaSprite.setTexWidth(0.25f);
            nebulaSprite.setTexHeight(0.25f);

            if (additive) nebulaSprite.setAdditiveBlend();
            else nebulaSprite.setNormalBlend();

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
