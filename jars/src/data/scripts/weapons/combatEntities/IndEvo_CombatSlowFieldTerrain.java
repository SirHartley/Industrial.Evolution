package data.scripts.weapons.combatEntities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Iterator;
import java.util.List;

public class IndEvo_CombatSlowFieldTerrain extends BaseEveryFrameCombatPlugin {
    public static final float DURATION = 15f;
    public static final float RAMPUP_DUR = 1f;
    public static final float BASE_RADIUS = 1200;
    public static final float BASE_OVERLOAD_DUR = 0.5f;
    public static final float SPEED_RED_MULT = 0.5f;

    public Color color = new Color(20, 200, 255, 255);

    public float elapsed = 0;
    public float angle;
    public float dur;
    public float rad;
    public Vector2f loc;

    public CombatEngineAPI engine;

    public IndEvo_CombatSlowFieldTerrain(float dur, float rad, Vector2f loc) {
        this.dur = dur;
        this.rad = rad;
        this.loc = loc;
        this.angle = MathUtils.getRandomNumberInRange(0, 360);

        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        super.init(engine);

        MagicRender.battlespace(
                Global.getSettings().getSprite("graphics/fx/shields256.png"),
                new Vector2f(loc),
                Misc.ZERO,
                new Vector2f(500, 500),   // initial size
                new Vector2f(BASE_RADIUS * 2, BASE_RADIUS * 2),  // expansion
                (float) (360f * Math.random()),
                0f,
                new Color(20, 200, 255, 150),
                true,
                0f,
                0.1f,
                0.8f
        );
    }

    public void interdictShipsInRange(float radius){
        Iterator<Object> iter = Global.getCombatEngine().getAllObjectGrid().getCheckIterator(loc,
                rad * 2f, rad * 2f);

        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof ShipAPI)) continue;
            ShipAPI other = (ShipAPI) o;

            float dist = Misc.getDistance(loc, other.getLocation());
            dist -= other.getCollisionRadius();
            if (dist > radius) continue;

            if (other.getMass() <= 0) continue;
            interdict(other);
        }
    }

    public void interdict(ShipAPI other){
        if (other.getFluxTracker().isOverloaded()) return;
        engine.addPlugin(new IndEvo_ForcedOverload(other, BASE_OVERLOAD_DUR));
    }

    public void applySlowToEntitiesInRange() {
        for (ShipAPI ship : CombatUtils.getShipsWithinRange(loc, rad)) {
            float dist = Misc.getDistance(loc, ship.getLocation());
            dist -= ship.getCollisionRadius();
            if (dist > rad) continue;

            ship.setInsideNebula(true);
            ship.getMutableStats().getMaxSpeed().modifyMult("IndEvo_slowfield_speed_mult", SPEED_RED_MULT, "Slow Field");

            if (ship == Global.getCombatEngine().getPlayerShip()) {
                Global.getCombatEngine().maintainStatusForPlayerShip("IndEvo_slowfield_speed_mult", "graphics/icons/tactical/nebula_slowdown2.png", "Slow Field", "reduced top speed -" + SPEED_RED_MULT + "%", true);
            }
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        super.advance(amount, events);

        if (elapsed > dur) {
            Global.getCombatEngine().removePlugin(this);

            for (ShipAPI s : CombatUtils.getShipsWithinRange(loc, 99999f)){
                s.getMutableStats().getMaxSpeed().unmodify("IndEvo_slowfield_speed_mult");
                s.setInsideNebula(false);
            }

            return;
        }

        elapsed += amount;

        float timePassedMult = Math.min(elapsed / RAMPUP_DUR, 1);
        float currentRadius = rad * timePassedMult;

        double colourRandomizer = Math.random();
        int rColour = Math.max((int) Math.round(20 + 140 * colourRandomizer), 0);
        int gColour = Math.max((int) Math.round(200 * (1 - colourRandomizer)), 5);
        int bColour = 255;

        this.color = new Color(rColour, gColour, bColour, 255); //Start

        applySlowToEntitiesInRange();

        if (elapsed < RAMPUP_DUR) {
            interdictShipsInRange(currentRadius);

        } else {
            for (int i = 0; i < 3; i++){
                Vector2f loc = MathUtils.getPointOnCircumference(this.loc, MathUtils.getRandomNumberInRange(10f, currentRadius), MathUtils.getRandomNumberInRange(0, 360));

                rColour = Math.max((int) Math.round(20 + 140 * Math.random()), 0);
                gColour = Math.max((int) Math.round(200 * (1 - Math.random())), 5);

                Color particleColour = new Color(rColour, gColour, bColour, 255); //Start

                engine.addHitParticle(
                        loc,
                        new Vector2f(0, 0),
                        (float) (12f + 8f * Math.random()),
                        (float) (0.4f + 0.5f * Math.random()),
                        (float) ((dur - elapsed) * Math.random()),
                        particleColour);
            }
        }
    }
}
