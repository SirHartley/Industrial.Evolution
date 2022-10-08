package data.scripts.weapons.combatEntities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class IndEvo_ForcedOverload extends BaseEveryFrameCombatPlugin {

    public ShipAPI ship;
    public float dur;
    CombatEngineAPI engine;

    public float elapsed = 0f;

    public IndEvo_ForcedOverload(ShipAPI ship, float dur) {
        this.ship = ship;
        this.dur = dur;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        super.init(engine);

        this.engine = engine;

        ship.getFluxTracker().forceOverload(dur);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        super.advance(amount, events);

        elapsed += amount;

        if (elapsed >= dur){
            ship.getFluxTracker().stopOverload();
            engine.removePlugin(this);
        }
    }
}
