package indevo.items.installable;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.StringHelper;

import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_RANGE_LY;
import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_SP_PER_FP;
import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_SP_SHARE;

public class SalvageDronesEffect extends BaseInstallableItemEffect {

    public SalvageDronesEffect(String id) {
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
        String range = Math.round(SALVAGE_DRONES_RANGE_LY) + " LY";
        String share = StringHelper.getAbsPercentString(SALVAGE_DRONES_SP_SHARE, false);

        text.addPara(pre + "Battles in other systems and in hyperspace within %s give %s of the salvage units a battle in this system gives. "
                        + "Hulls, weapons and fighter wings lost in battles here or in range go into a Wreck Registry: each can be ordered once from the ship assembly contract without a blueprint. "
                        + "Registry hulls cost %s salvage units per fleet point and can come as fitted.",
                pad, Misc.getHighlightColor(), range, share, "" + SALVAGE_DRONES_SP_PER_FP);
    }
}
