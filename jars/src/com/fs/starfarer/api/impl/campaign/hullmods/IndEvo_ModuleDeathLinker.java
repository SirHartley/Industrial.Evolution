package com.fs.starfarer.api.impl.campaign.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class IndEvo_ModuleDeathLinker extends BaseHullMod {

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        super.advanceInCombat(ship, amount);

        if (!ship.isAlive()) return;

        List<ShipAPI> modules = ship.getParentStation().getChildModulesCopy();
        int aliveModules = modules.size();

        for (ShipAPI module : modules) {
            if (module == ship
                    || module.getStationSlot() == null
                    || !module.isAlive()
                    || !Misc.isActiveModule(module))

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
        }
    }
}
