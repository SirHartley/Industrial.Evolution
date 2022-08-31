package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class IndEvo_causalityGunEffect implements BeamEffectPlugin {

    private final IntervalUtil interval = new IntervalUtil(0.08f, 0.12f);
    private static final Color NEBULA_COLOR = new Color(100, 255, 240, 100);
    private boolean wasZero = true;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (beam.getBrightness() < 1f) {
            return;
        }

        interval.advance(amount);
        if (interval.intervalElapsed()) {

            // along beam visuals
            for (int i = 0; i < 3; i++) {
                engine.addNegativeNebulaParticle(
                        MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo()),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(30f, 80f),
                        1.3f,
                        0.1f,
                        0.3f,
                        MathUtils.getRandomNumberInRange(0.4f, 0.8f),
                        NEBULA_COLOR
                );
                engine.addNebulaParticle(
                        MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo()),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(30f, 80f),
                        1.3f,
                        0.1f,
                        0.3f,
                        MathUtils.getRandomNumberInRange(0.4f, 0.8f),
                        beam.getFringeColor()
                );
            }

            // muzzle visuals
            if (Math.random() > 0.75f) {
                engine.addNegativeNebulaParticle(
                        beam.getFrom(),
                        beam.getWeapon().getShip().getVelocity(),
                        MathUtils.getRandomNumberInRange(50f, 70f),
                        1.3f,
                        0.1f,
                        0.3f,
                        MathUtils.getRandomNumberInRange(0.4f, 0.8f),
                        NEBULA_COLOR
                );
            }
            float size = beam.getWidth() * MathUtils.getRandomNumberInRange(2f, 2.2f);
            float flashDur = MathUtils.getRandomNumberInRange(0.2f, 0.25f);
            engine.addHitParticle(beam.getFrom(), beam.getSource().getVelocity(), beam.getWidth(), 0.8f, flashDur, beam.getCoreColor());
            engine.addHitParticle(beam.getFrom(), beam.getSource().getVelocity(), size, 0.8f, flashDur, beam.getFringeColor().brighter());

            // target hit visuals - only every other tick
            // Don't question the dumb implementation, I changed it later and was lazy
            if ((beam.getDamageTarget() != null) && (Math.random() > 0.75f)) {
                engine.addNegativeSwirlyNebulaParticle(
                        beam.getTo(),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(60f, 120f),
                        1.3f,
                        0f,
                        0.3f,
                        MathUtils.getRandomNumberInRange(0.5f, 1f),
                        NEBULA_COLOR
                );
                engine.addSwirlyNebulaParticle(
                        beam.getTo(),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(60f, 120f),
                        1.3f,
                        0f,
                        0.3f,
                        MathUtils.getRandomNumberInRange(0.5f, 1f),
                        beam.getFringeColor(),
                        false
                );
                engine.addSwirlyNebulaParticle(
                        beam.getTo(),
                        new Vector2f(0f, 0f),
                        MathUtils.getRandomNumberInRange(60f, 120f),
                        1.3f,
                        0f,
                        0.3f,
                        MathUtils.getRandomNumberInRange(0.5f, 1f),
                        beam.getFringeColor().brighter(),
                        false
                );
            }

            if (Math.random() == 0.5f) {
                return;
            }

            CombatEntityAPI target = beam.getDamageTarget();
            if (target instanceof ShipAPI && beam.getBrightness() >= 1f) {
                float dur = beam.getDamage().getDpsDuration();
                // needed because when the ship is in fast-time, dpsDuration will not be reset every frame as it should be
                if (!wasZero) dur = 0;
                wasZero = dur <= 0;

                ShipAPI ship = (ShipAPI) target;
                boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
                float pierceChance = ((ShipAPI) target).getHardFluxLevel() - 0.1f;
                pierceChance *= ship.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);

                boolean piercedShield = hitShield && (float) Math.random() < pierceChance;
                //piercedShield = true;

                if (!hitShield || piercedShield) {
                    Vector2f point = beam.getRayEndPrevFrame();
                    float emp = beam.getDamage().getFluxComponent() * 0.5f;
                    float dam = beam.getDamage().getDamage() * 0.25f;
                    engine.spawnEmpArcPierceShields(
                            beam.getSource(), point, beam.getDamageTarget(), beam.getDamageTarget(),
                            DamageType.ENERGY,
                            dam, // damage
                            emp, // emp
                            100000f, // max range
                            "tachyon_lance_emp_impact",
                            beam.getWidth() + 5f,
                            beam.getFringeColor(),
                            beam.getCoreColor()
                    );
                }

            }
        }

    }
}
