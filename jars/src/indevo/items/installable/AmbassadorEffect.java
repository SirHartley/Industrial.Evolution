package indevo.items.installable;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.embassy.AmbassadorItemHelper;

public class AmbassadorEffect extends BaseInstallableItemEffect {

    public AmbassadorEffect(String id) {
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
        if (AmbassadorItemHelper.isPersonMissing(data)) {
            text.addPara("Invalid item: the person %s is missing. Please report this.", pad, Misc.getNegativeHighlightColor(), String.valueOf(data == null ? null : data.getData()));
            return;
        }

        PersonAPI person = AmbassadorItemHelper.getPersonForItem(data);
        FactionAPI faction = person.getFaction();
        text.addPara("%s is an official faction representative to %s.", pad, faction.getColor(), person.getNameString(), faction.getDisplayNameWithArticle());
    }
}
