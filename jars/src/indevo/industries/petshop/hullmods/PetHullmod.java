package indevo.industries.petshop.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.ModPlugin;
import indevo.utils.helper.StringHelper;

import java.awt.*;

public class PetHullmod extends SelfRepairingBuiltInHullmod {
    public static final float BASE_DECREASE = -5f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        Pet pet = getPet(stats.getVariant());
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

        Pet pet = getPet(ship.getVariant());

        if (pet == null) return;

        tooltip.addSectionHeading("Effect", Alignment.MID, opad);
        tooltip.addPara("Reduces CR decay during combat by %s", opad, hl, Misc.getRoundedValueMaxOneAfterDecimal(BASE_DECREASE * pet.getEffectFract()) + " %");

        tooltip.addSectionHeading("Pet Data", Alignment.MID, opad);
        tooltip.addPara("Name: %s", spad, hl, pet.name);
        tooltip.addPara("Species: %s", spad, hl, pet.getData().species);
        tooltip.addPara("Disposition: %s", spad, Academy.COLOURS_BY_PERSONALITY.get(pet.personality), pet.personality.toLowerCase());

        int years = (int) Math.ceil(pet.age / 364f);
        float months = (int) Math.ceil(pet.age / 31f);

        if (pet.age < 31) tooltip.addPara("Age: %s", spad, hl, Misc.getStringForDays((int) Math.ceil(pet.age)));
        else if (pet.age < 365) tooltip.addPara("Age: %s", spad, hl, months + (months <= 1 ? " month" : " months"));
        else tooltip.addPara("Age: %s", spad, hl, years + (years <= 1 ? " year" : " years"));

        tooltip.addPara("Status: %s", spad, pet.isStarving() ? Misc.getNegativeHighlightColor() : hl, pet.isStarving() ? "Starving" : "Well fed and happy");

        tooltip.addPara("Dietary options: ", opad);
        for (String commodity : pet.getData().foodCommodities){
            String name = Global.getSettings().getCommoditySpec(commodity).getName();
            tooltip.addPara(BaseIntelPlugin.BULLET + " " + name, spad);
        }

        tooltip.addSectionHeading("Description", Alignment.MID, opad);
        tooltip.addPara(pet.getData().desc, opad);
    }

    public Pet getPet(ShipVariantAPI variant) {
        Pet pet = null;

        for (String tag : variant.getTags()) {
            if (tag.contains(Pet.HULLMOD_DATA_PREFIX)) {
                String id = tag.substring(Pet.HULLMOD_DATA_PREFIX.length());
                pet = PetStatusManager.get().get(id);
                break;
            }
        }

        return pet;
    }
}
