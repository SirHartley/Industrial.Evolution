package industrial_evolution.items.installable;

import industrial_evolution.IndEvo_StringHelper;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import industrial_evolution.items.specialitemdata.IndEvo_AmbassadorItemData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableIndustryItemPlugin;
import industrial_evolution.industries.embassy.industry.IndEvo_embassy;
import industrial_evolution.campaign.ids.IndEvo_AmbassadorIdHandling;
import industrial_evolution.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.HashMap;
import java.util.Map;

/**
 * Very important for implementations of this to not store *any* references to campaign data in data members, since
 * this is stored in a static map and persists between game loads etc.
 */

public class IndEvo_AmbassadorInstallableItemPlugin extends BaseInstallableIndustryItemPlugin {

    public static final String IDENT_STRING = "IndEvo_AmbassadorItem";

    public static final Map<String, IndEvo_AmbassadorEffect> AMBASSADOR_EFFECTS = new HashMap<String, IndEvo_AmbassadorEffect>() {{
        put(IndEvo_Items.AMBASSADOR, new IndEvo_AmbassadorEffect() {
            public void apply(Industry industry) {
            }

            public void unapply(Industry industry) {
            }

            public void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode) {
                float pad = 10f;
                PersonAPI person = IndEvo_AmbassadorIdHandling.getPersonForItem(data);

                Map<String, String> toReplace = new HashMap<>();
                toReplace.put("$personName", person.getNameString());
                toReplace.put("$personFaction", person.getFaction().getDisplayNameWithArticle());
                String s = IndEvo_StringHelper.getStringAndSubstituteTokens(IDENT_STRING, "itemTooltip", toReplace);
                String[] add = new String[]{toReplace.get("$personName"), toReplace.get("$personFaction")};

                text.addPara(s, pad, person.getFaction().getColor(), add);
            }
        });
    }};

    private final IndEvo_embassy industry;

    public IndEvo_AmbassadorInstallableItemPlugin(IndEvo_embassy industry) {
        this.industry = industry;
    }

    @Override
    public boolean isInstallableItem(CargoStackAPI stack) {
        if (!stack.isSpecialStack()) return false;

        return AMBASSADOR_EFFECTS.containsKey(stack.getSpecialDataIfSpecial().getId());
    }

    @Override
    public String getMenuItemTitle() {
        if (getCurrentlyInstalledItemData() == null) {
            return IndEvo_StringHelper.getString(IDENT_STRING, "install");
        }
        return IndEvo_StringHelper.getString(IDENT_STRING, "manage");
    }

    //The following methods implement functionality for the colony industry UI

    @Override
    public String getUninstallButtonText() {
        return IndEvo_StringHelper.getString(IDENT_STRING, "uninstall");
    }

    @Override
    public String getNoItemCurrentlyInstalledText() {
        return IndEvo_StringHelper.getString(IDENT_STRING, "noCurrent");
    }

    @Override
    public String getNoItemsAvailableText() {
        return IndEvo_StringHelper.getString(IDENT_STRING, "noAvailable");
    }

    @Override
    public String getNoItemsAvailableTextRemote() {
        return IndEvo_StringHelper.getString(IDENT_STRING, "noStorage");
    }

    @Override
    public String getSelectItemToAssignToIndustryText() {
        return IndEvo_StringHelper.getStringAndSubstituteToken(IDENT_STRING, "selectForIndustry", "$industryName", industry.getCurrentName());
    }

    @Override
    public void addItemDescription(TooltipMakerAPI text, SpecialItemData data,
                                   InstallableItemDescriptionMode mode) {

        IndEvo_AmbassadorEffect effect = AMBASSADOR_EFFECTS.get(data.getId());
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

    public SpecialItemData getCurrentlyInstalledItemData() {
        return this.industry.getAmbassadorItemData();
    }

    public void setCurrentlyInstalledItemData(SpecialItemData data) {
        this.industry.setAmbassadorItemData(data);
    }

    public void createMenuItemTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 3.0F;
        float opad = 10.0F;
        tooltip.addPara(IndEvo_StringHelper.getString(IDENT_STRING, "indTooltip"), 0.0F);
        IndEvo_AmbassadorItemData data = this.industry.getAmbassadorItemData();

        if (data == null) {
            tooltip.addPara(this.getNoItemCurrentlyInstalledText() + ".", opad);
        } else {
            IndEvo_AmbassadorInstallableItemPlugin.IndEvo_AmbassadorEffect effect = (IndEvo_AmbassadorInstallableItemPlugin.IndEvo_AmbassadorEffect) AMBASSADOR_EFFECTS.get(data.getId());
            effect.addItemDescription(tooltip, data, InstallableItemDescriptionMode.INDUSTRY_MENU_TOOLTIP);
        }
    }

    public interface IndEvo_AmbassadorEffect {
        void apply(Industry industry);

        void unapply(Industry industry);

        void addItemDescription(TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode);
    }
}




