package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

public class SelfRepairingBuiltInHullmod extends BaseHullMod {

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        super.applyEffectsAfterShipCreation(ship, id);

        //build yourself in if s-mod dialogue broke it again
        String hullmodID = spec.getId();
        if (ship.getVariant().getNonBuiltInHullmods().contains(hullmodID)) ship.getVariant().removeMod(hullmodID);
        if (!ship.getVariant().getHullMods().contains(hullmodID)) ship.getVariant().addPermaMod(hullmodID, false);
    }
}
