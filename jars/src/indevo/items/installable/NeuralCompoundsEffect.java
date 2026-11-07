package indevo.items.installable;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.StringHelper;

import static indevo.items.installable.SpecialItemEffectsRepo.NEURAL_COMPOUNDS_UNREST_RED;
import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class NeuralCompoundsEffect extends BaseInstallableItemEffect {

    public NeuralCompoundsEffect(String id) {
        super(id);
    }

    @Override
    public void apply(Industry industry) {
    }

    @Override
    public void unapply(Industry industry) {
    }

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                          InstallableItemDescriptionMode mode, String pre, float pad) {
        String s = StringHelper.getAbsPercentString(NEURAL_COMPOUNDS_UNREST_RED, false) + "%";

        text.addPara(pre + "Allows you to issue Edicts on any friendly colony within " + RANGE_LY_TWELVE + "LY" +
                        " and reduces the unrest after preliminary removal by " + s,
                pad, Misc.getHighlightColor(), RANGE_LY_TWELVE + "LY", s);
    }
}
