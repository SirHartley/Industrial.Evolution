package indevo.items.installable;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.StringHelper;

import static com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo.COLD_OR_EXTREME_COLD;
import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;
import static indevo.items.installable.SpecialItemEffectsRepo.SIMULATOR_BASE_INCREASE;

public class SimulatorEffect extends BaseInstallableItemEffect {

    public SimulatorEffect(String id) {
        super(id);
    }

    @Override
    public void apply(Industry industry) {
    }

    @Override
    public void unapply(Industry industry) {
    }

    @Override
    public String[] getSimpleReqs(Industry industry) {
        return new String[]{COLD_OR_EXTREME_COLD};
    }

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                          InstallableItemDescriptionMode mode, String pre, float pad) {
        String s = StringHelper.getAbsPercentString(SIMULATOR_BASE_INCREASE, false) + "%";

        text.addPara(pre + "Applies the Supercomputer Effect to any friendly colony within " + RANGE_LY_TWELVE + "LY" +
                        " and increases the effect by " + s + ", scaling with distance.",
                pad, Misc.getHighlightColor(), RANGE_LY_TWELVE + "LY", s);
    }
}
