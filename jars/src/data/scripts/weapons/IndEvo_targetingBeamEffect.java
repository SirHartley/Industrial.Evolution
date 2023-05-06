package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicFakeBeamPlugin;

import java.awt.*;

/**
 * Shamelessly stolen from Vayra, who in turn stole it from Nicke
 * real communism hours
 */

public class IndEvo_targetingBeamEffect implements EveryFrameWeaponEffectPlugin {
    public static final float BEAM_WIDTH = 5f;
    public static final Color BEAM_CORE = Color.BLUE;
    public static final Color BEAM_FRINGE = Color.red.darker();

    private boolean fired = false;
    private final IntervalUtil render = new IntervalUtil(0.05f, 0.05f);
    private int arcs = 1;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Don't run if we are paused, or our weapon is null
        if (engine.isPaused() || weapon == null) {
            return;
        }

        render.advance(amount);
        float charge = weapon.getChargeLevel();

        if (charge > 0f && charge < 0.98f && !fired) {

            if (render.intervalElapsed()) {
                float duration = render.getIntervalDuration();
                float fadeDuration = 0f;
                float angle = weapon.getCurrAngle();
                float muzzle = weapon.getSpec().getTurretFireOffsets().get(0).getX();
                Vector2f weaponLoc = weapon.getLocation();
                //float x = weaponLoc.x + muzzle - 30f;
                float x = weaponLoc.x + (muzzle * 2f);
                float y = weaponLoc.y - 9f;
                Vector2f offset = new Vector2f(x, y);
                Vector2f from = VectorUtils.rotateAroundPivot(offset, weapon.getLocation(), angle);
                float length = weapon.getRange();

                MagicFakeBeamPlugin.addBeam(duration, fadeDuration, BEAM_WIDTH, from, angle, length, BEAM_CORE, BEAM_FRINGE);
            }

            switch (arcs) {
                case 1:
                    if (charge >= 0.2f) {
                        spawnArc(engine, weapon, charge);
                        arcs++;
                    }
                    break;
                case 2:
                    if (charge >= 0.45f) {
                        spawnArc(engine, weapon, charge);
                        arcs++;
                    }
                    break;
                case 3:
                    if (charge >= 0.65f) {
                        spawnArc(engine, weapon, charge);
                        arcs++;
                    }
                    break;
                case 4:
                    if (charge >= 0.8f) {
                        spawnArc(engine, weapon, charge);
                        arcs++;
                    }
                    break;
                case 5:
                    if (charge >= 0.9f) {
                        spawnArc(engine, weapon, charge);
                        arcs++;
                    }
                    break;
                case 6:
                    if (charge >= 0.95f) {
                        spawnArc(engine, weapon, charge);
                        arcs++;
                    }
                    break;
                default:
                    break;
            }
        }

        if (charge >= 0.98f) {
            fired = true;
            arcs = 1;
        }

        if (weapon.getCooldownRemaining() <= 0f) {
            fired = false;
        }
    }

    private void spawnArc(CombatEngineAPI engine, WeaponAPI weapon, float charge) {

        ShipAPI ship = weapon.getShip();
        Vector2f loc = weapon.getLocation();
        Vector2f point = MathUtils.getRandomPointInCone(loc, 135f, ship.getFacing() - 15f, ship.getFacing() + 15f);

        engine.spawnEmpArcPierceShields(
                ship,
                weapon.getLocation(),
                weapon.getShip(),
                new SimpleEntity(point),
                DamageType.OTHER,
                0f,
                0f,
                69420f,
                "tachyon_lance_emp_impact",
                charge * 20f,
                weapon.getSpec().getGlowColor().brighter(),
                weapon.getSpec().getGlowColor()
        );
    }
}
