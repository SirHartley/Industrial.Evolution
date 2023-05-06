package data.scripts.weapons.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.combatEntities.IndEvo_CombatSlowFieldTerrain;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class IndEvo_missileProjectileAI implements MissileAIPlugin, GuidedMissileAI {

    private CombatEngineAPI engine;
    private boolean runOnce = false;
    private final MissileAPI missile;
    private CombatEntityAPI target;

    public static final float EXPLOSION_TRIGGER_RANGE = 100f;

    public IndEvo_missileProjectileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }

        this.missile = missile;
    }

    @Override
    public void advance(float amount) {
        if (engine.isPaused() || missile.isFading()) {
            return;
        }

        if (!runOnce) {
            runOnce = true;
            //missile.setCollisionClass(CollisionClass.SHIP);
            missile.setMass(5000);
        }

        if (missile.getFlightTime() > missile.getMaxFlightTime() - 0.1f) splode();
        if (!AIUtils.getNearbyEnemies(missile, EXPLOSION_TRIGGER_RANGE).isEmpty() || (!enemiesInPath() && enemiesInSideArcPresent())) splode();
    }

    public boolean enemiesInPath(){
        for (ShipAPI s : AIUtils.getEnemiesOnMap(missile)){
            if(Misc.isInArc(missile.getFacing(), 50f, Misc.getAngleInDegrees(missile.getLocation(), s.getLocation()))) return true;
        }

        return false;
    }

    public boolean enemiesInSideArcPresent(){
        for (ShipAPI s : AIUtils.getEnemiesOnMap(missile)){
            if(Misc.isInArc(missile.getFacing() - 180, 180, Misc.getAngleInDegrees(missile.getLocation(), s.getLocation())) && Misc.getDistance(missile.getLocation(), s.getLocation()) < 500f) return true;
        }

        return false;
    }

    public void spawnSlowTerrain() {
        engine.addPlugin(new IndEvo_CombatSlowFieldTerrain(IndEvo_CombatSlowFieldTerrain.DURATION, IndEvo_CombatSlowFieldTerrain.BASE_RADIUS, missile.getLocation()));
    }

    public void splode() {
        spawnSlowTerrain();

        float maxRad = 300f;
        float core = 100f;

        DamagingExplosionSpec explosion = new DamagingExplosionSpec(
                0.5f,
                maxRad * 0.7f,
                core * 0.7f,
                1000,
                200,
                CollisionClass.MISSILE_FF,
                CollisionClass.MISSILE_FF,
                2,
                5,
                0.5f,
                25,
                new Color(100, 255, 255, 64),
                new Color(100, 200, 255, 64)
        );

        explosion.setDamageType(DamageType.HIGH_EXPLOSIVE);
        explosion.setShowGraphic(true);
        explosion.setSoundSetId("IndEvo_missile_hit");

        engine.spawnDamagingExplosion(explosion, missile.getSource(), missile.getLocation(), false);

        float angle = (float) Math.random() * 360;
        //visual effect
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "IndEvo_mortar_wave"),
                new Vector2f(missile.getLocation()),
                new Vector2f(),
                new Vector2f(core, core),
                new Vector2f(IndEvo_CombatSlowFieldTerrain.BASE_RADIUS, IndEvo_CombatSlowFieldTerrain.BASE_RADIUS),
                (float) Math.random() * 360,
                MathUtils.getRandomNumberInRange(-5, 5),
                new Color(100, 50, 200, 180),
                true,
                0,
                0,
                1.75f
        );

        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "IndEvo_mortar_wave"),
                new Vector2f(missile.getLocation()),
                new Vector2f(),
                new Vector2f(core * 1.2f, core * 1.2f),
                new Vector2f(IndEvo_CombatSlowFieldTerrain.BASE_RADIUS * 0.8f, IndEvo_CombatSlowFieldTerrain.BASE_RADIUS * 0.8f),
                (float) Math.random() * 360,
                MathUtils.getRandomNumberInRange(-5, 5),
                new Color(100, 150, 50, 255),
                true,
                0,
                0,
                1.5f
        );
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "IndEvo_mortar_smoke"),
                new Vector2f(missile.getLocation()),
                new Vector2f(),
                new Vector2f(core * 1.2f, core * 1.2f),
                new Vector2f(maxRad, maxRad),
                angle + (float) Math.random() * 5,
                MathUtils.getRandomNumberInRange(-5, 5),
                new Color(100, 150, 75, 128),
                true,
                0f,
                0.5f,
                1.5f
        );

        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "IndEvo_mortar_glow"),
                new Vector2f(missile.getLocation()),
                new Vector2f(),
                new Vector2f(maxRad, maxRad),
                new Vector2f(maxRad * 0.1f, maxRad * 0.1f),
                angle + (float) Math.random() * 10,
                MathUtils.getRandomNumberInRange(-5, 5),
                Color.WHITE,
                true,
                0f,
                0f,
                0.5f
        );

        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "IndEvo_mortar_burn"),
                new Vector2f(missile.getLocation()),
                new Vector2f(),
                new Vector2f(maxRad, maxRad),
                new Vector2f(maxRad * 0.1f, maxRad * 0.1f),
                angle + (float) Math.random() * 10,
                MathUtils.getRandomNumberInRange(-5, 5),
                new Color(50, 128, 240, 255),
                true,
                0f,
                0.25f,
                1f
        );

        engine.addSmoothParticle(missile.getLocation(), new Vector2f(), 3000, 2, 0.1f, Color.white);
        engine.addHitParticle(missile.getLocation(), new Vector2f(), 2000, 1, 0.4f, new Color(100, 100, 255));
        engine.spawnExplosion(missile.getLocation(), new Vector2f(), Color.DARK_GRAY, 2000, 1f);

        engine.removeEntity(missile);
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }
}
