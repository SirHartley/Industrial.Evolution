package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;

import java.awt.*;

public class PetHullmod extends SelfRepairingBuiltInHullmod {
    public static final float BASE_DECREASE = 5f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        Pet pet = PetStatusManager.getInstance().getPet(stats.getVariant());
        if (pet == null) return;

        stats.getCRLossPerSecondPercent().modifyPercent(id, -BASE_DECREASE * pet.getEffectFract(), pet.name + " the " + pet.getData().species);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);
        float opad = 10f;
        Color hl = Misc.getHighlightColor();

        Pet pet = PetStatusManager.getInstance().getPet(ship.getVariant());
        if (pet == null) return;

        tooltip.addSectionHeading("Effect", Alignment.MID, opad);
        tooltip.addPara("Reduces CR decay during combat by %s", opad, hl, Misc.getRoundedValueMaxOneAfterDecimal(BASE_DECREASE * pet.getEffectFract()) + " %");

        pet.addHullmodDescriptionSection(tooltip);
    }
}
