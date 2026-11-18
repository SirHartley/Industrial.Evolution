package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.patches.PetRefitRowPatch;
import indevo.industries.petshop.plugins.PetCard;
import patchlib.api.PatchLib;

/**
 * Use indevo.other.YourMod_PetName_PetHullmod for a ready-made copy and paste solution for custom pet hullmods that won't crash unlike this one.
 */
public class PetHullmod extends SelfRepairingBuiltInHullmod {
    public static final float BASE_DECREASE = 5f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        Pet pet = PetStatusManager.getInstance().getPet(stats.getVariant());
        if (pet == null || pet.isUnsettled()) return;

        stats.getCRLossPerSecondPercent().modifyPercent(id, -BASE_DECREASE * pet.getEffectFract(), pet.name + " the " + pet.getData().species);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        Pet pet = ship != null ? PetStatusManager.getInstance().getPet(ship.getVariant()) : null;
        // the refit row is the only place with a click target under this tooltip
        if (pet != null) PetCard.hover(tooltip, pet, true, !isForModSpec && PatchLib.isPatchEnabled(PetRefitRowPatch.class));
    }
}
