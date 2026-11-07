package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.items.ForgeTemplateItemPlugin;

public class ForgeTemplateEffect extends BaseInstallableItemEffect {

    public ForgeTemplateEffect(String id) {
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
        switch (id) {
            case ItemIds.EMPTYFORGETEMPLATE:
                text.addPara(pre + "Contains %s.", pad, Misc.getHighlightColor(), "no production data");
                break;
            case ItemIds.BROKENFORGETEMPLATE:
                text.addPara(pre + "This template is %s.", pad, Misc.getHighlightColor(), "not functional");
                break;
            default:
                String hullId = ForgeTemplateItemPlugin.getHullId(data);
                if (hullId == null) {
                    text.addPara(pre + "Contains production data for a ship hull.", pad);
                    break;
                }

                ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
                text.addPara(pre + "Contains production data for a %s. Production cycles remaining: %s. Base material cost per hull: %s",
                        pad,
                        Misc.getHighlightColor(),
                        hull.getNameWithDesignationWithDashClass(),
                        ForgeTemplateItemPlugin.getCharges(data) + "",
                        Misc.getDGSCredits(hull.getBaseValue() * 0.2f));
        }
    }
}
