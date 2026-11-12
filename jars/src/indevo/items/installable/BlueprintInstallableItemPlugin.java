package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableIndustryItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.engineeringhub.industry.EngineeringHub;

import java.awt.Color;
import java.util.Set;

public class BlueprintInstallableItemPlugin extends BaseInstallableIndustryItemPlugin {

    public static final Set<String> BLUEPRINT_IDS = Set.of(Items.SHIP_BP, "tiandong_retrofit_bp", "roider_retrofit_bp");

    public static boolean isBlueprint(SpecialItemData data) {
        return data != null && BLUEPRINT_IDS.contains(data.getId());
    }

    public static void addBlueprintDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
        String name = switch (data.getId()) {
            case Items.SHIP_BP -> Global.getSettings().getHullSpec(data.getData()).getNameWithDesignationWithDashClass() + " blueprint";
            default -> Global.getSettings().getHullSpec(data.getData()).getNameWithDesignationWithDashClass() + " Retrofit Template";
        };

        String pre = "";
        if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_LIST || mode == InstallableItemDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = name + ". ";
        } else if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_INSTALLED || mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP) {
            pre = name + " currently installed. ";
        }

        float pad = mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP || mode == InstallableItemDescriptionMode.CARGO_TOOLTIP ? 10f : 0f;
        Color hl = Misc.getHighlightColor();

        switch (data.getId()) {
            case Items.SHIP_BP -> {
                String size = Misc.ucFirst(Global.getSettings().getHullSpec(data.getData()).getHullSize().name().toLowerCase());
                text.addPara(pre + "Specification: %s. The contents of this item will be %s by the re-writing process.", pad, hl, size, "replaced");
            }
            case "tiandong_retrofit_bp" -> text.addPara(pre + "The contents of this item will be %s by the re-writing process. Only works with Tiandong ships.", pad, hl, "replaced");
            default -> text.addPara(pre + "The contents of this item will be %s by the re-writing process. Only works with Roider Union ships.", pad, hl, "replaced");
        }
    }

    private final EngineeringHub industry;

    public BlueprintInstallableItemPlugin(EngineeringHub industry) {
        this.industry = industry;
    }

    public EngineeringHub getIndustry() {
        return industry;
    }

    @Override
    public String getMenuItemTitle() {
        if (getCurrentlyInstalledItemData() == null) {
            return "Install Blueprint...";
        }
        return "Manage Blueprint...";
    }

    @Override
    public String getUninstallButtonText() {
        return "Uninstall Blueprint";
    }

    @Override
    public boolean isInstallableItem(CargoStackAPI stack) {
        if (!stack.isSpecialStack()) return false;

        return isBlueprint(stack.getSpecialDataIfSpecial());
    }

    @Override
    public SpecialItemData getCurrentlyInstalledItemData() {
        return industry.getSpecialItem();
    }

    @Override
    public void setCurrentlyInstalledItemData(SpecialItemData data) {
        industry.setSpecialItem(data);
    }

    @Override
    public String getNoItemCurrentlyInstalledText() {
        return "No Blueprint currently installed";
    }

    @Override
    public String getNoItemsAvailableText() {
        return "No Blueprint available";
    }

    @Override
    public String getNoItemsAvailableTextRemote() {
        return "No Blueprint available in storage";
    }

    @Override
    public String getSelectItemToAssignToIndustryText() {
        return "Select Blueprint to install for " + industry.getCurrentName();
    }

    @Override
    public void addItemDescription(TooltipMakerAPI text, SpecialItemData data,
                                   InstallableItemDescriptionMode mode) {
        addBlueprintDescription(text, data, mode);
    }

    @Override
    public boolean isMenuItemTooltipExpandable() {
        return false;
    }

    @Override
    public float getMenuItemTooltipWidth() {
        return super.getMenuItemTooltipWidth();
    }

    @Override
    public boolean hasMenuItemTooltip() {
        return super.hasMenuItemTooltip();
    }

    @Override
    public void createMenuItemTooltip(TooltipMakerAPI tooltip, boolean expanded) {

        float pad = 3f;
        float opad = 10f;

        tooltip.addPara("Install a Blueprint to overwrite with new ship data. Use blueprints of the same hull size as the desired ship.", 0f);

        SpecialItemData data = industry.getSpecialItem();
        if (data == null) {
            tooltip.addPara(getNoItemCurrentlyInstalledText() + ".", opad);
        } else {
            addBlueprintDescription(tooltip, data, InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP);
        }
    }
}