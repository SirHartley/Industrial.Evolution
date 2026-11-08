package indevo.items.installable;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class HolobenchEffect extends BaseInstallableItemEffect {

    public HolobenchEffect(String id) {
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
        String xp = Misc.getWithDGS(SpecialItemEffectsRepo.HOLOBENCH_MONTHLY_XP);
        text.addPara(pre + "Officers can train %s more personality step, %s more with an alpha core. Officers leave training mentored. Stored officers gain %s experience per month.",
                pad, Misc.getHighlightColor(), "one", "two", "mentored", xp);
    }
}
