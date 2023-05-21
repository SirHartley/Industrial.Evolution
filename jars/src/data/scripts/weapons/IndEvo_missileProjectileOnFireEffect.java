package data.scripts.weapons;

import com.fs.graphics.particle.DynamicParticleGroup;
import com.fs.graphics.particle.SmoothParticle;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.MuzzleFlashSpec;
import com.fs.starfarer.combat.CombatEngine;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class IndEvo_missileProjectileOnFireEffect implements OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        Vector2f loc = MathUtils.getPointOnCircumference(
                projectile.getSpawnLocation(),
                40f,
                projectile.getFacing() - 180f);

        float angle = projectile.getFacing();
        Vector2f speed = VectorUtils.getDirectionalVector(loc, MathUtils.getPointOnCircumference(loc, 10f, angle - 180f));
        speed.scale(20f);

        MuzzleFlashSpec spec = new MuzzleFlashSpec(
                30f,
                200f,
                10f,
                60f,
                5f,
                30,
                new Color(110, 100, 100, 255));

        renderSmoke(spec, loc, angle, speed);
    }

    public static void renderSmoke(MuzzleFlashSpec spec, Vector2f renderLoc, float arcOffset, Vector2f projectileVelocity) {
        if (spec != null) {
            CombatEngine combatEngine = CombatEngine.getInstance();
            DynamicParticleGroup particles = combatEngine.getSmokeParticles();
            Color particleColor = spec.getParticleColor();
            float particleSizeMin = spec.getParticleSizeMin();
            float particleSizeRange = spec.getParticleSizeRange();
            float spread = spec.getSpread();
            float length = spec.getLength();

            for (int i = 0; i < spec.getParticleCount(); ++i) {
                float var12 = particleSizeRange * (float) Math.random() + particleSizeMin;
                SmoothParticle particle = new SmoothParticle(particleColor, var12);
                particle.setMaxAge(spec.getParticleDuration());
                float angle = (float) (Math.random() * Math.toRadians((double) spread) + Math.toRadians((double) (arcOffset - spread / 2.0F)));
                float lengthOffset = (float) (Math.random() * (double) length);
                float cosOffset = (float) Math.cos((double) angle) * lengthOffset;
                float sinOffset = (float) Math.sin((double) angle) * lengthOffset;
                particle.setPos(renderLoc.x + cosOffset, renderLoc.y + sinOffset);
                particle.setVel(cosOffset + projectileVelocity.x, sinOffset + projectileVelocity.y);
                particles.add(particle);
            }
        }
    }
}
