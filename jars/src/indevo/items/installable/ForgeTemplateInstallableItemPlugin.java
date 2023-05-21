package indevo.items.installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableIndustryItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.derelicts.industry.BaseForgeTemplateUser;

import java.util.HashMap;
import java.util.Map;

public class ForgeTemplateInstallableItemPlugin extends BaseInstallableIndustryItemPlugin {

    //SpecialItemData of a ForgeTemplateItemPlugin contains the HullID of the relevant ship as data (SpecialItemData specItem.getData)
    //SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(specItem.getId());

    /**
     * Very important for implemenations of this to not store *any* references to campaign data in data members, since
     * this is stored in a static map and persists between game loads etc.
     * <p>
     * this is also called a memory leak
     * don't touch this
     */
    public interface ForgeTemplateEffect {
        void apply(Industry industry, SpecialItemData specItem);

        void unapply(Industry industry);

        void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode);
    }

    public static final Map<String, ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect> FORGETEMPLATE_EFFECTS = new HashMap<String, ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect>() {{
        //put entries for Forge template 1-5 onto the list without duplicates
        for (int i = 1; i <= 5; i++) {
            put(ItemIds.FORGETEMPLATE + "_" + i, new ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect() {
                public void apply(Industry industry, SpecialItemData specItem) {
                    if (industry instanceof BaseForgeTemplateUser) {
                        BaseForgeTemplateUser b = (BaseForgeTemplateUser) industry;
                        b.setForgeShip(Global.getSettings().getHullSpec(specItem.getData()));
                    }
                }

                public void unapply(Industry industry) {
                    if (industry instanceof BaseForgeTemplateUser) {
                        BaseForgeTemplateUser b = (BaseForgeTemplateUser) industry;
                        b.unSetForgeShip();
                    }
                }

                public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                    SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(data.getId());
                    String name = Misc.ucFirst(spec.getName().toLowerCase());
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

                    text.addPara(pre + "Contains production data for a %s. Production cycles remaining: %s. Base material cost per hull: %s",
                            pad,
                            Misc.getHighlightColor(),
                            new String[]{Global.getSettings().getHullSpec(data.getData()).getNameWithDesignationWithDashClass(),
                                    data.getId().substring(data.getId().length() - 1),
                                    Misc.getDGSCredits((Global.getSettings().getHullSpec(data.getData()).getBaseValue() * 0.2f))});
                }
            });
        }

        put(ItemIds.EMPTYFORGETEMPLATE, new ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect() {
            public void apply(Industry industry, SpecialItemData specItem) {
            }

            public void unapply(Industry industry) {
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(data.getId());
                String name = Misc.ucFirst(spec.getName().toLowerCase());
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

                text.addPara(pre + "Contains %s.",
                        pad, Misc.getHighlightColor(), "no production data");
            }
        });

        put(ItemIds.BROKENFORGETEMPLATE, new ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect() {
            public void apply(Industry industry, SpecialItemData specItem) {
                if (industry instanceof BaseForgeTemplateUser) {
                    BaseForgeTemplateUser b = (BaseForgeTemplateUser) industry;
                    b.setForgeShip(Global.getSettings().getHullSpec(specItem.getData()));
                }
            }

            public void unapply(Industry industry) {
                if (industry instanceof BaseForgeTemplateUser) {
                    BaseForgeTemplateUser b = (BaseForgeTemplateUser) industry;
                    b.unSetForgeShip();
                }
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(data.getId());
                String name = Misc.ucFirst(spec.getName().toLowerCase());
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

                text.addPara(pre + "This template is %s.",
                        pad, Misc.getHighlightColor(), "not functional");
            }
        });
    }};

    private final BaseForgeTemplateUser industry;

    public ForgeTemplateInstallableItemPlugin(BaseForgeTemplateUser industry) {
        this.industry = industry;

    }

    @Override
    public String getMenuItemTitle() {
        if (getCurrentlyInstalledItemData() == null) {
            return "Install Forge Template...";
        }
        return "Manage Forge Template...";
    }

    @Override
    public String getUninstallButtonText() {
        return "Uninstall Forge Template";
    }

    @Override
    public boolean isInstallableItem(CargoStackAPI stack) {
        if (!stack.isSpecialStack()) return false;

        return FORGETEMPLATE_EFFECTS.containsKey(stack.getSpecialDataIfSpecial().getId());
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
        return "No Forge Template currently installed";
    }

    @Override
    public String getNoItemsAvailableText() {
        return "No Forge Template available";
    }

    @Override
    public String getNoItemsAvailableTextRemote() {
        return "No Forge Template available in storage";
    }

    @Override
    public String getSelectItemToAssignToIndustryText() {
        return "Select Forge Template to install for " + industry.getCurrentName();
    }

    @Override
    public void addItemDescription(TooltipMakerAPI text, SpecialItemData data,
                                   InstallableItemDescriptionMode mode) {
        ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect effect = FORGETEMPLATE_EFFECTS.get(data.getId());
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

        tooltip.addPara("Forge Templates have different uses depending on the structure they are installed in.", 0f);

        SpecialItemData data = industry.getSpecialItem();
        if (data == null) {
            tooltip.addPara(getNoItemCurrentlyInstalledText() + ".", opad);
        } else {
            ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect effect = FORGETEMPLATE_EFFECTS.get(data.getId());
            effect.addItemDescription(tooltip, data, InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP);
        }
    }
}