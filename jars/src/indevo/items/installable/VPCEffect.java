package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.assembler.industry.VPCUserIndustryAPI;
import indevo.items.VPCItemPlugin;

public class VPCEffect extends BaseInstallableItemEffect {

    public VPCEffect(String id) {
        super(id);
    }

    // supply and demand come from the industry's own apply, base apply runs this too late for them
    @Override
    public void apply(Industry industry) {
    }

    @Override
    public void unapply(Industry industry) {
        if (industry instanceof VPCUserIndustryAPI user) user.vpcUnapply();
    }

    @Override
    public String getItemInstalledText(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
        String name = VPCItemPlugin.getDisplayName(data);

        if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_LIST || mode == InstallableItemDescriptionMode.INDUSTRY_TOOLTIP) {
            return name + ". ";
        } else if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_INSTALLED || mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP) {
            return name + " currently installed. ";
        }

        return "";
    }

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
                                          InstallableItemDescriptionMode mode, String pre, float pad) {
        if (data == null || (data.getData() == null && Global.CODEX_TOOLTIP_MODE)) {
            text.addPara(pre + "Contains production protocols for one to three commodities, depending on its tier.", pad);
            return;
        }

        if (!VPCItemPlugin.isValid(data)) {
            text.addPara("Invalid item: the production data %s can not be read. Please report this.", pad, Misc.getNegativeHighlightColor(), String.valueOf(data.getData()));
            return;
        }

        text.addPara(pre + VPCItemPlugin.getTier(data).desc, pad, Misc.getHighlightColor(), VPCItemPlugin.getOutputString(data));
    }
}
