package indevo.items.installable;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import static indevo.items.installable.SpecialItemEffectsRepo.LOG_CORE_MAX_BONUS;
import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class LogCoreEffect extends BaseInstallableItemEffect {

    public LogCoreEffect(String id) {
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
        text.addPara(pre + "All industries within " + RANGE_LY_TWELVE + "LY will be counted for Bureau bonuses," +
                        " and the maximum bonus effect is increased to " + LOG_CORE_MAX_BONUS,
                pad, Misc.getHighlightColor(), RANGE_LY_TWELVE + "LY", LOG_CORE_MAX_BONUS + "");
    }
}
