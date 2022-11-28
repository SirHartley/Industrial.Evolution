package indevo.industries.derelicts.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import indevo.industries.SharedSubmarketUser;
import indevo.items.installable.ForgeTemplateInstallableItemPlugin;
import indevo.ids.Ids;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static indevo.industries.derelicts.industry.IndEvo_Ruins.INDUSTRY_ID_MEMORY_KEY;

public class IndEvo_BaseForgeTemplateUser extends SharedSubmarketUser {
    //this is the base for any industry using forge templates - any extension MUST call super.apply/unapply, respectively

    public static Logger log = Global.getLogger(IndEvo_BaseForgeTemplateUser.class);

    protected SpecialItemData forgeTemplate = null;
    protected ShipHullSpecAPI forgeShip = null;

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    public boolean isAvailableToBuild() {
        String id = market.getMemoryWithoutUpdate().getString(INDUSTRY_ID_MEMORY_KEY);

        boolean check = (id != null && getId().equals(id))
                && (market.hasIndustry(Ids.RUINS) && !(market.getIndustry(Ids.RUINS).isUpgrading())); //no ruins id specified or wrong general ID

        return check && super.isAvailableToBuild();
    }

    @Override
    public void init(String id, MarketAPI market) {
        super.init(id, market);
        if (market.hasIndustry(getId())) spec.setDowngrade(null);
    }

    @Override
    public IndustrySpecAPI getSpec() {
        if (spec == null) spec = Global.getSettings().getIndustrySpec(id);
        if (market.hasIndustry(getId())) spec.setDowngrade(null);
        return spec;
    }

    @Override
    public boolean canDowngrade() {
        return false;
    }

    public void apply() {
        super.apply(true);
        applyForgeTemplateEffects();

        if (market.hasIndustry(getId())) spec.setDowngrade(null);

        /* spec.setDowngrade(null);*/

        //needed for Forge template item tooltips
        MemoryAPI memory = Global.getSector().getMemory();
        if (!memory.contains("$" + getId())) {
            Global.getSector().getMemory().set("$" + getId(), true);
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        spec.setDowngrade(Ids.RUINS);

        if (forgeTemplate != null) {
            ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect effect = ForgeTemplateInstallableItemPlugin.FORGETEMPLATE_EFFECTS.get(forgeTemplate.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }
    }

    public void setForgeShip(ShipHullSpecAPI ship) {
        forgeShip = ship;
    }

    public void unSetForgeShip() {
        forgeShip = null;
    }

    protected void applyForgeTemplateEffects() {
        if (forgeTemplate != null) {
            ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect effect = ForgeTemplateInstallableItemPlugin.FORGETEMPLATE_EFFECTS.get(forgeTemplate.getId());

            if (effect != null) {
                effect.apply(this, forgeTemplate);
            }
        }
    }

    public void setForgeTemplate(SpecialItemData forgeTemplate) {
        if (forgeTemplate == null && this.forgeTemplate != null) {
            ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect effect = ForgeTemplateInstallableItemPlugin.FORGETEMPLATE_EFFECTS.get(this.forgeTemplate.getId());
            if (effect != null) {
                effect.unapply(this);
            }
        }
        this.forgeTemplate = forgeTemplate;
    }

    public SpecialItemData getSpecialItem() {
        return forgeTemplate;
    }

    public void setSpecialItem(SpecialItemData special) {
        forgeTemplate = special;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        return forgeTemplate == null &&
                data != null &&
                ForgeTemplateInstallableItemPlugin.FORGETEMPLATE_EFFECTS.containsKey(data.getId());
    }

    @Override
    protected boolean addNonAICoreInstalledItems(Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (forgeTemplate == null) return false;

        float opad = 10f;

        FactionAPI faction = market.getFaction();
        Color color = faction.getBaseUIColor();
        Color dark = faction.getDarkUIColor();


        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(forgeTemplate.getId());

        TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48);
        ForgeTemplateInstallableItemPlugin.ForgeTemplateEffect effect = ForgeTemplateInstallableItemPlugin.FORGETEMPLATE_EFFECTS.get(forgeTemplate.getId());
        effect.addItemDescription(text, forgeTemplate, InstallableIndustryItemPlugin.InstallableItemDescriptionMode.INDUSTRY_TOOLTIP);
        tooltip.addImageWithText(opad);

        return true;
    }

    @Override
    public java.util.List<InstallableIndustryItemPlugin> getInstallableItems() {
        ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<>();
        list.add(new ForgeTemplateInstallableItemPlugin(this));
        return list;
    }

    @Override
    public void initWithParams(java.util.List<String> params) {
        super.initWithParams(params);

        for (String str : params) {
            if (ForgeTemplateInstallableItemPlugin.FORGETEMPLATE_EFFECTS.containsKey(str)) {
                setForgeTemplate(new SpecialItemData(str, null));
                break;
            }
        }
    }

    @Override
    public java.util.List<SpecialItemData> getVisibleInstalledItems() {
        List<SpecialItemData> result = super.getVisibleInstalledItems();

        if (forgeTemplate != null) {
            result.add(forgeTemplate);
        }

        return result;
    }

    @Override
    public boolean isLegalOnSharedSubmarket(CargoStackAPI stack) {
        return false;
    }

    @Override
    public void addTooltipLine(TooltipMakerAPI tooltip, boolean expanded) {

    }
}
