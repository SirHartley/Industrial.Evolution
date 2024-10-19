package indevo.exploration.crucible.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.items.consumables.particles.NebulaParticle;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public class DustCloudRenderer implements LunaCampaignRenderingPlugin {
    public static final float DURATION = 6f;
    public static final float SPAWN_DUR = 0.4f;
    public static final float SPEED = 150f;
    public static final float PARTICLES_PER_FRAME = 10f;
    public static final float BASE_NEBULA_PARTICLE_SIZE = 100f;
    public static final float PARTICLE_LIFETIME = 2f;

    public Color originalColor = new Color(45, 30, 25, 255);
    public Color targetColor = new Color(25, 20, 20, 255);

    public float elapsed = 0f;
    public SectorEntityToken crucible;
    public List<NebulaParticle> nebulaParticles = new ArrayList<>();
    public float angle;
    public float radius;

    public transient SpriteAPI nebulaSprite;

    public DustCloudRenderer(SectorEntityToken crucible) {
        this.crucible = crucible;
        radius = 0.55f * crucible.getRadius();
    }

    @Override
    public boolean isExpired() {
        return elapsed > DURATION;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
        if (elapsed < SPAWN_DUR) {
            for (int i = 0; i < PARTICLES_PER_FRAME; i++) {
                Random random = new Random();
                NebulaParticle.LocationData location = new NebulaParticle.LocationData(radius, MathUtils.getRandomNumberInRange(0, 360));
                float alpha = 0.1f + 0.7f * (random.nextFloat());

                nebulaParticles.add(new NebulaParticle(BASE_NEBULA_PARTICLE_SIZE * alpha,
                        (float) MathUtils.getRandomNumberInRange(0, 360),
                        alpha,
                        (float) (PARTICLE_LIFETIME + Math.random()),
                        originalColor,
                        targetColor.darker(),
                        location));
            }
        }

        for (NebulaParticle particle : nebulaParticles) {
            particle.advance(amount);
            particle.pos.incrementRadius(SPEED * amount * particle.baseAlpha);
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

            Vector2f loc = p.pos.getLocation(crucible.getLocation());

            nebulaSprite.setTexWidth(0.25f);
            nebulaSprite.setTexHeight(0.25f);
            nebulaSprite.setAdditiveBlend();

            nebulaSprite.setTexX(p.i * 0.25f);
            nebulaSprite.setTexY(p.j * 0.25f);

            nebulaSprite.setAngle(p.angle);
            nebulaSprite.setSize(p.size, p.size);
            nebulaSprite.setAlphaMult(p.getCurrentAlpha());
            nebulaSprite.setColor(p.color);
            nebulaSprite.renderAtCenter(loc.x, loc.y);
        }
    }
}
