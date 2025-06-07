package indevo.exploration.meteor;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.animation.particles.NebulaParticle;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

public class MeteorDebrisRenderer implements LunaCampaignRenderingPlugin {

    public static class CloudSpawnerModificationData {
        public float lifetimeFactor;
        public float spawnAngleDelta;
        public int cloudAmt;
        public float cloudSpeedFactor;
        public float cloudSizeFactor;

        public CloudSpawnerModificationData(float lifetimeFactor, float spawnAngleDelta, int cloudAmt, float cloudSpeedFactor, float cloudSizeFactor) {
            this.lifetimeFactor = lifetimeFactor;
            this.spawnAngleDelta = spawnAngleDelta;
            this.cloudAmt = cloudAmt;
            this.cloudSpeedFactor = cloudSpeedFactor;
            this.cloudSizeFactor = cloudSizeFactor;
        }
    }

    public static List<CloudSpawnerModificationData> spawnerData = new ArrayList<>(Arrays.asList(
            new CloudSpawnerModificationData(0.3f, 90f, 5, 1.2f, 0.3f), //perpendicular right
            new CloudSpawnerModificationData(0.3f, -90f, 5, 1.2f, 0.3f), //perpendicular left
            new CloudSpawnerModificationData(1f, 23f, 1, 0.1f, 1f), //arcing
            new CloudSpawnerModificationData(1f, 5f, 1, 0.1f, 1f), //arcing
            new CloudSpawnerModificationData(1f, -30f, 1, 0.1f, 1f), //arcing
            new CloudSpawnerModificationData(1f, -10f, 1, 0.1f, 1f) //arcing
    ));

    public static final int RADIUS_PX_PER_BASE_LIFETIME_SECOND = 30;

    public float elapsed = 0f;
    public List<NebulaParticle> nebulaParticles = new ArrayList<>();

    public transient SpriteAPI nebulaSprite;

    public MeteorDebrisRenderer(SectorEntityToken target, SectorEntityToken meteor) {
        Vector2f loc = new Vector2f(meteor.getLocation());
        Vector2f velocity = target.getVelocity();
        Vector2f meteorVel = meteor.getVelocity();

        // Compute relative velocity (vector from meteor to target)
        Vector2f relativeVel = Vector2f.sub(meteorVel, velocity, null);
        float relativeSpeed = relativeVel.length();

        // Determine impact direction based on relative velocity, fall back to facing if too slow
        float impactAngle;
        if (relativeSpeed >= 10f) {
            impactAngle = Misc.getAngleInDegrees(relativeVel);
        } else if (velocity.length() >= 10f) {
            impactAngle = Misc.getAngleInDegrees(velocity);
        } else {
            impactAngle = target.getFacing();
        }

        impactAngle -= 180f;

        float meteorSize = meteor.getRadius();
        float baseLifetimeSeconds = meteorSize / RADIUS_PX_PER_BASE_LIFETIME_SECOND;

        for (CloudSpawnerModificationData data : spawnerData){
            for (int i = 0; i <= data.cloudAmt; i++){
                Random random = new Random();
                NebulaParticle.LocationData location = new NebulaParticle.LocationData(0f, impactAngle + data.spawnAngleDelta, loc);
                float alpha = 0.1f + 0.7f * (random.nextFloat());

                nebulaParticles.add(new NebulaParticle(meteorSize * data.cloudSizeFactor * alpha,
                        (float) MathUtils.getRandomNumberInRange(0, 360),
                        alpha,
                        (float) (baseLifetimeSeconds * data.lifetimeFactor + Math.random()),
                        Color.DARK_GRAY,
                        Color.DARK_GRAY.darker(),
                        location));
            }
        }
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
        for (NebulaParticle particle : nebulaParticles) {
            particle.advance(amount);
            particle.pos.incrementRadius(particle.speed * amount * particle.baseAlpha);
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

            Vector2f loc = p.pos.getLocation();

            nebulaSprite.setTexWidth(0.25f);
            nebulaSprite.setTexHeight(0.25f);
            nebulaSprite.setAdditiveBlend();

            nebulaSprite.setTexX(p.i * 0.25f);
            nebulaSprite.setTexY(p.j * 0.25f);

            nebulaSprite.setAngle(p.angle);
            nebulaSprite.setSize(p.size, p.size);
            nebulaSprite.setAlphaMult(p.getCurrentAlpha() * 0.7f);
            nebulaSprite.setColor(p.color);
            nebulaSprite.renderAtCenter(loc.x, loc.y);
        }
    }
}
