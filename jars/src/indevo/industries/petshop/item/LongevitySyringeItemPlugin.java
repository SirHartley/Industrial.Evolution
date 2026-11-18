package indevo.industries.petshop.item;

import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.petshop.PetConstants;

import java.awt.Color;

public class LongevitySyringeItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Gives a pet in your fleet %s more to live. It can be given again before the time runs out.", opad, h, years(Setting.PET_LONGEVITY_YEARS.intValue()));
        tooltip.addPara("Hamsters gain %s per syringe.", opad, h, years(PetConstants.HAMSTER_TREATMENT_YEARS));
        addCostLabel(tooltip, opad, transferHandler, stackSource);
    }

    public static String years(int years) {
        return years + (years == 1 ? " year" : " years");
    }
}
