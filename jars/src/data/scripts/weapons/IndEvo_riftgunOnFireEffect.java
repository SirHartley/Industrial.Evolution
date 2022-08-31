package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;

public class IndEvo_riftgunOnFireEffect implements OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        engine.addSwirlyNebulaParticle(
                projectile.getLocation(),
                weapon.getShip().getVelocity(),
                MathUtils.getRandomNumberInRange(40f, 60f),
                1.2f,
                0.1f,
                0.3f,
                MathUtils.getRandomNumberInRange(0.6f, 1.6f),
                projectile.getProjectileSpec().getFringeColor(),
                true
        );

        engine.spawnExplosion(
                projectile.getLocation(),
                weapon.getShip().getVelocity(),
                projectile.getProjectileSpec().getFringeColor().brighter().brighter(),
                30f,
                0.15f
        );

        engine.spawnExplosion(
                projectile.getLocation(),
                weapon.getShip().getVelocity(),
                projectile.getProjectileSpec().getFringeColor(),
                30f,
                0.15f
        );
    }
}
