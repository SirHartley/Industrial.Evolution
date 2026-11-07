package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class NamedItemSlotPlugin extends GenericInstallableItemPlugin {

    protected final String itemName;
    protected final String description;

    public NamedItemSlotPlugin(Industry industry, String itemName, String description) {
        super(industry);
        this.itemName = itemName;
        this.description = description;
    }

    @Override
    public String getMenuItemTitle() {
        return (getCurrentlyInstalledItemData() == null ? "Install " : "Manage ") + itemName + "...";
    }

    @Override
    public String getUninstallButtonText() {
        return "Uninstall " + itemName;
    }

    @Override
    public String getNoItemCurrentlyInstalledText() {
        return "No " + itemName + " currently installed";
    }

    @Override
    public String getNoItemsAvailableText() {
        return "No " + itemName + " available";
    }

    @Override
    public String getNoItemsAvailableTextRemote() {
        return "No " + itemName + " available in storage";
    }

    @Override
    public String getSelectItemToAssignToIndustryText() {
        return "Select " + itemName + " to install for " + industry.getCurrentName();
    }

    @Override
    public void createMenuItemTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;
        tooltip.addPara(description, 0f);

        SpecialItemData data = getCurrentlyInstalledItemData();
        if (data == null) {
            tooltip.addPara(getNoItemCurrentlyInstalledText() + ".", opad);
            return;
        }

        TooltipMakerAPI text = tooltip.beginImageWithText(getIconName(data), 48);
        ItemEffectsRepo.ITEM_EFFECTS.get(data.getId()).addItemDescription(industry, text, data, InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP);
        tooltip.addImageWithText(opad);
    }

    protected String getIconName(SpecialItemData data) {
        return Global.getSettings().getSpecialItemSpec(data.getId()).getIconName();
    }
}
