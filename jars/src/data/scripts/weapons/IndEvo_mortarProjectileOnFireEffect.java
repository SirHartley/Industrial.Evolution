package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class IndEvo_mortarProjectileOnFireEffect implements OnFireEffectPlugin {

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        ShipAPI ship = weapon.getShip();
        ShipAPI target = null;

        if (ship.getWeaponGroupFor(weapon) != null) {
            //WEAPON IN AUTOFIRE
            if (ship.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
                    && ship.getSelectedGroupAPI() != ship.getWeaponGroupFor(weapon)) { //weapon group is not the selected group
                target = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
            } else {
                target = ship.getShipTarget();
            }
        }

        if (projectile instanceof MissileAPI && target != null) {
            MissileAPI missile = (MissileAPI) projectile;

            Vector2f loc = missile.getLocation() != null ? missile.getLocation() : weapon.getLocation();

            float distance = Misc.getDistance(loc, target.getLocation());
            float flightTime = distance / missile.getMaxSpeed();

            missile.setMaxFlightTime(flightTime);
        }
    }
}
