package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableIndustryItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.engineeringhub.industry.EngineeringHub;

import java.util.HashMap;
import java.util.Map;

public class BlueprintInstallableItemPlugin extends BaseInstallableIndustryItemPlugin {

     //SpecialItemData of a ForgeTemplateItemPlugin contains the HullID of the relevant ship as data (SpecialItemData specItem.getData)
    //SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(specItem.getId());

    /**
     * Very important for implemenations of this to not store *any* references to campaign data in data members, since
     * this is stored in a static map and persists between game loads etc.
     * <p>
     * this is also called a memory leak
     * don't touch this
     */

    public interface BlueprintEffect {
        void apply(Industry industry, SpecialItemData specItem);

        void unapply(Industry industry);

        void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode);
    }

    public static final Map<String, BlueprintEffect> BLUEPRINT_EFFECTS = new HashMap<String, BlueprintEffect>() {{

        //IndEvo_relicSpecialItem
        put(ItemIds.RELIC_SPECIAL_ITEM, new BlueprintEffect() {
            public void apply(Industry industry, SpecialItemData specItem) {
            }

            public void unapply(Industry industry) {
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                String name = Global.getSettings().getSpecialItemSpec(ItemIds.RELIC_SPECIAL_ITEM).getName();
                String pre = "";
                float pad = 0f;
                if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_LIST ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_TOOLTIP) {
                    pre = name + ". ";
                } else if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_INSTALLED ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP) {
                    pre = "Using " + name + ". ";
                }
                if (mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP ||
                        mode == InstallableItemDescriptionMode.CARGO_TOOLTIP) {
                    pad = 10f;
                }

                String subName = "";
                for (SubmarketSpecAPI spec :  Global.getSettings().getAllSubmarketSpecs()) if (Ids.SHAREDSTORAGE.equals(spec.getId())) subName = spec.getName();
                text.addPara(pre + "Taken from the %s. Consumed quantity depends on ship deployment points. Prioritized over blueprints.",
                        pad, Misc.getHighlightColor(), new String[]{subName, "replaced"});
            }
        });

        put(Items.SHIP_BP, new BlueprintEffect() {
            public void apply(Industry industry, SpecialItemData specItem) {
            }

            public void unapply(Industry industry) {
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                String name = Global.getSettings().getHullSpec(data.getData()).getNameWithDesignationWithDashClass() + " blueprint";
                String pre = "";
                float pad = 0f;
                if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_LIST ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_TOOLTIP) {
                    pre = name + ". ";
                } else if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_INSTALLED ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP) {
                    pre = name + " currently installed. ";
                }
                if (mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP ||
                        mode == InstallableItemDescriptionMode.CARGO_TOOLTIP) {
                    pad = 10f;
                }

                String size = Misc.ucFirst(Global.getSettings().getHullSpec(data.getData()).getHullSize().name().toLowerCase());

                text.addPara(pre + "Specification: %s. " + "The contents of this item will be %s by the re-writing process.",
                        pad, Misc.getHighlightColor(), new String[]{size, "replaced"});
            }
        });

        put("tiandong_retrofit_bp", new BlueprintEffect() {
            public void apply(Industry industry, SpecialItemData specItem) {
            }

            public void unapply(Industry industry) {
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                String name = Global.getSettings().getHullSpec(data.getData()).getNameWithDesignationWithDashClass() + " Retrofit Template";
                String pre = "";
                float pad = 0f;
                if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_LIST ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_TOOLTIP) {
                    pre = name + ". ";
                } else if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_INSTALLED ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP) {
                    pre = name + " currently installed. ";
                }
                if (mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP ||
                        mode == InstallableItemDescriptionMode.CARGO_TOOLTIP) {
                    pad = 10f;
                }

                text.addPara(pre + "The contents of this item will be %s by the re-writing process. Only works with Tiandong ships.",
                        pad, Misc.getHighlightColor(), new String[]{"replaced"});
            }
        });

        put("roider_retrofit_bp", new BlueprintEffect() {
            public void apply(Industry industry, SpecialItemData specItem) {
            }

            public void unapply(Industry industry) {
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                String name = Global.getSettings().getHullSpec(data.getData()).getNameWithDesignationWithDashClass() + " Retrofit Template";
                String pre = "";
                float pad = 0f;
                if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_LIST ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_TOOLTIP) {
                    pre = name + ". ";
                } else if (mode == InstallableItemDescriptionMode.MANAGE_ITEM_DIALOG_INSTALLED ||
                        mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP) {
                    pre = name + " currently installed. ";
                }
                if (mode == InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP ||
                        mode == InstallableItemDescriptionMode.CARGO_TOOLTIP) {
                    pad = 10f;
                }

                text.addPara(pre + "The contents of this item will be %s by the re-writing process. Only works with Roider Union ships.",
                        pad, Misc.getHighlightColor(), new String[]{"replaced"});
            }
        });
    }};

    private final EngineeringHub industry;

    public BlueprintInstallableItemPlugin(EngineeringHub industry) {
        this.industry = industry;

    }

    @Override
    public String getMenuItemTitle() {

        //this is the dirtiest hack ever
        //used to insert relic components into the dialogue
        //they are removed from cargo in the industry apply method which doesn't run while the menu that needs this title is visible but does trigger immediately after the menu is closed
        industry.removeDummyRelicComponentFromCargo();
        industry.addDummyRelicComponentToCargo();

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

        return BLUEPRINT_EFFECTS.containsKey(stack.getSpecialDataIfSpecial().getId());
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
        BlueprintEffect effect = BLUEPRINT_EFFECTS.get(data.getId());
        if (effect != null) {
            effect.addItemDescription(text, data, mode);
        }
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
            BlueprintEffect effect = BLUEPRINT_EFFECTS.get(data.getId());
            effect.addItemDescription(tooltip, data, InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP);
        }
    }
}