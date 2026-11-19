package indevo.industries.petshop.item;

import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetLongevity;
import indevo.industries.petshop.plugins.PetTreatDialog;

import java.awt.*;

public class LongevitySyringeItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);

        float opad = 10f;
        Color h = Misc.getHighlightColor();
        int cycles = Setting.PET_LONGEVITY_CYCLES.intValue();
        int hamster = PetConstants.HAMSTER_TREATMENT_CYCLES;

        tooltip.addPara("Adds %s " + (cycles == 1 ? "cycle" : "cycles") + " to the life of one pet.", opad, h, String.valueOf(cycles));
        tooltip.addPara("Hamsters gain %s " + (hamster == 1 ? "cycle" : "cycles") + " per syringe.", opad, h, String.valueOf(hamster));
        addCostLabel(tooltip, opad, transferHandler, stackSource);

        // the codex shows the item outside a campaign
        if (stack == null || stack.getCargo() == null) return;
        if (!PetItemPlugin.isFleetCargo()) tooltip.addPara("Can only be used from fleet cargo", Misc.getNegativeHighlightColor(), opad);
        else if (!PetLongevity.hasPetWithFleet()) tooltip.addPara("No pet with the fleet", Misc.getNegativeHighlightColor(), opad);
        else tooltip.addPara("Right-click to use", Misc.getGrayColor(), opad);
    }

    @Override
    public boolean hasRightClickAction() {
        return stack != null && stack.getCargo() != null && PetItemPlugin.isFleetCargo() && PetLongevity.hasPetWithFleet();
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        PetTreatDialog.syringe(helper).show();
    }
}
