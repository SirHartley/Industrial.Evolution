package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;

import java.awt.*;

public class PetHullmod extends SelfRepairingBuiltInHullmod {
    public static final float BASE_DECREASE = -5f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        Pet pet = PetStatusManager.getInstance().getPet(stats.getVariant());
        if (pet == null) return;

        stats.getCRLossPerSecondPercent().modifyPercent(id, BASE_DECREASE * pet.getEffectFract(), pet.name + " the " + pet.getData().species);
    }

    @Override
    public Color getNameColor() {
        return super.getNameColor();
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);
        float opad = 10f;
        float spad = 3f;
        Color hl = Misc.getHighlightColor();

        Pet pet = PetStatusManager.getInstance().getPet(ship.getVariant());

        if (pet == null) return;

        tooltip.addSectionHeading("Effect", Alignment.MID, opad);
        tooltip.addPara("Reduces CR decay during combat by %s", opad, hl, Misc.getRoundedValueMaxOneAfterDecimal(BASE_DECREASE * pet.getEffectFract()) + " %");

        tooltip.addSectionHeading("Pet Data", Alignment.MID, opad);
        tooltip.addPara("Name: %s", spad, hl, pet.name);
        tooltip.addPara("Species: %s", spad, hl, pet.getData().species);
        tooltip.addPara("Disposition: %s", spad, Academy.COLOURS_BY_PERSONALITY.get(pet.personality), pet.personality.toLowerCase());
        tooltip.addPara("Age: %s", spad, hl, pet.getAgeString());

        tooltip.addPara("Status: %s", spad, pet.isStarving() ? Misc.getNegativeHighlightColor() : hl, pet.isStarving() ? "Starving" : "Well fed and happy");

        tooltip.addPara("Dietary options: ", opad);
        for (String commodity : pet.getData().foodCommodities) {
            String name = Global.getSettings().getCommoditySpec(commodity).getName();
            tooltip.addPara(BaseIntelPlugin.BULLET + " " + name, spad);
        }

        tooltip.addSectionHeading("Description", Alignment.MID, opad);
        tooltip.addPara(pet.getData().desc, opad);
    }
}
