package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;

import java.awt.*;

public class YourMod_PetName_PetHullmod extends BaseHullMod {
    public static final float BASE_DECREASE = 5f;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        super.applyEffectsAfterShipCreation(ship, id);

        //build yourself in if s-mod dialogue broke it again
        //DO NOT TOUCH THIS
        String hullmodID = spec.getId();
        if (ship.getVariant().getNonBuiltInHullmods().contains(hullmodID)) ship.getVariant().removeMod(hullmodID);
        if (!ship.getVariant().getHullMods().contains(hullmodID)) ship.getVariant().addPermaMod(hullmodID, false);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (!Global.getSettings().getModManager().isModEnabled("indEvo")) return;

        Pet pet = PetStatusManager.getInstance().getPet(stats.getVariant());
        if (pet == null) return;

        float effectFract = pet.getEffectFract(); //this is the effect fraction, it reaches 100% after a year of assignment
        String name = pet.name + " the " + pet.getData().species;

        //Your effects go here
        stats.getCRLossPerSecondPercent().modifyPercent(id, -BASE_DECREASE * effectFract, name);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);

        //don't touch this!
        if (!Global.getSettings().getModManager().isModEnabled("indEvo")) return;
        Pet pet = PetStatusManager.getInstance().getPet(ship.getVariant());
        if (pet == null) return;

        float opad = 10f;
        Color hl = Misc.getHighlightColor();
        float effectFract = pet.getEffectFract();

        tooltip.addSectionHeading("Effect", Alignment.MID, opad);

        //your effect descriptions go here
        tooltip.addPara("Reduces CR decay during combat by %s", opad, hl, Misc.getRoundedValueMaxOneAfterDecimal(BASE_DECREASE * effectFract) + " %");

        //don't touch this!
        pet.addHullmodDescriptionSection(tooltip);
    }

    @Override
    public Color getNameColor() {
        return super.getNameColor();
    }
}
