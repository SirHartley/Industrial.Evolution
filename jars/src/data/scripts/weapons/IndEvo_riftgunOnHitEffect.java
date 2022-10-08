package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class IndEvo_riftgunOnHitEffect implements OnHitEffectPlugin {
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {

        engine.addSwirlyNebulaParticle(projectile.getLocation(), target.getVelocity(), 50f, 1.5f, 0f, 0.3f, 0.5f, projectile.getProjectileSpec().getFringeColor(), true);
        engine.addSwirlyNebulaParticle(projectile.getLocation(), target.getVelocity(), 30f, 1.5f, 0f, 0.3f, 0.5f, projectile.getProjectileSpec().getCoreColor(), true);

        if (!(target instanceof ShipAPI)) {
            return;
        }
        if (Math.random() < 0.8f) {
            return;
        }

        Vector2f mineLoc = MathUtils.getRandomPointInCone(projectile.getLocation(), 100f, projectile.getFacing() + 120f, projectile.getFacing() + 240f);
        spawnMine(projectile.getSource(), mineLoc);
        EmpArcEntityAPI arc = engine.spawnEmpArcVisual(projectile.getLocation(), null, mineLoc, null, projectile.getProjectileSpec().getWidth(), projectile.getProjectileSpec().getFringeColor(), projectile.getProjectileSpec().getCoreColor());
        arc.setCoreWidthOverride(Math.max(20f, projectile.getProjectileSpec().getWidth() * 0.67f));
    }

    public void spawnMine(ShipAPI source, Vector2f mineLoc) {
        CombatEngineAPI engine = Global.getCombatEngine();


        //Vector2f currLoc = mineLoc;
        MissileAPI mine = (MissileAPI) engine.spawnProjectile(source, null,
                "IndEvo_riftgun_minelayer",
                mineLoc,
                (float) Math.random() * 360f, null);
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(
                    source, WeaponAPI.WeaponType.BALLISTIC, false, mine.getDamage());
        }


        float fadeInTime = 0.05f;
        mine.getVelocity().scale(0);
        mine.fadeOutThenIn(fadeInTime);

        float liveTime = 0f;
        //liveTime = 0.01f;
        mine.setFlightTime(mine.getMaxFlightTime() - liveTime);
        mine.addDamagedAlready(source);
        mine.setNoMineFFConcerns(true);
    }
}
