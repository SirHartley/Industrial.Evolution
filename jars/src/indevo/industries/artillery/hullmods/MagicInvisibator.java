package indevo.industries.artillery.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

public class MagicInvisibator extends BaseHullMod {

    //IndEvo_magicInvisibator

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        super.applyEffectsAfterShipCreation(ship, id);
        ship.setAlphaMult(0);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        super.advanceInCombat(ship, amount);

        if (!ship.isAlive()) return;

        ship.setFixedLocation(new Vector2f(100000f, 100000f));
        ship.setSprite("IndEvo", "invisibleHull");

        Vector2f damageFrom = new Vector2f(ship.getLocation());
        Global.getCombatEngine().applyDamage(ship,
                damageFrom,
                1000000f,
                DamageType.ENERGY,
                0,
                true,
                false,
                ship,
                false);
    }
    /*
        List<ShipAPI> modules = ship.getParentStation().getChildModulesCopy();
        int aliveModules = modules.size();

        for (ShipAPI module : modules) {
            if (module == ship
                    || module.getStationSlot() == null
                    || !module.isAlive()
                    || !MiscIE.isActiveModule(module))

                aliveModules--;
        }

        if (aliveModules < 1) {
            Vector2f damageFrom = new Vector2f(ship.getLocation());

            Global.getCombatEngine().applyDamage(ship,
                    damageFrom,
                    1000000f,
                    DamageType.ENERGY,
                    0,
                    true,
                    false,
                    ship,
                    false);
        }*/

}
