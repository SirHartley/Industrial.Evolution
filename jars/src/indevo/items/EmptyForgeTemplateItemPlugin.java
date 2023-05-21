package indevo.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.installable.VPCInstallableItemPlugin;

import java.awt.*;

public class EmptyForgeTemplateItemPlugin extends BaseSpecialItemPlugin {

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult,
                       float glowMult, SpecialItemRendererAPI renderer) {
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        return super.getPrice(market, submarket);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, false);

        float pad = 3f;
        float opad = 10f;
        float small = 5f;
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color b = Misc.getButtonTextColor();


        VPCInstallableItemPlugin.IndEvo_ItemEffect effect = VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(getId());
        if (effect != null) {
            effect.addItemDescription(tooltip, new SpecialItemData(getId(), null), InstallableIndustryItemPlugin.InstallableItemDescriptionMode.CARGO_TOOLTIP);
        }

        if (getId().equals(ItemIds.BROKENFORGETEMPLATE)) {
            if (Global.getSector().getMemory().getBoolean("$" + Ids.LAB)) {
                tooltip.addPara("This Forge Template has seen better days. You can restore it by installing it in a Research Laboratory.", opad);
            } else {
                tooltip.addPara("This Forge Template has seen better days. Restoring it will require technologies that are very hard to come by in the post-Collapse Sector. Residual data contains hints towards planets lost to automated fleets in the first AI war.", opad);
            }

        } else if (getId().equals(ItemIds.EMPTYFORGETEMPLATE)) {
            if (Global.getSector().getMemory().getBoolean("$" + Ids.DECONSTRUCTOR)) {
                tooltip.addPara("This Forge Template does not contain any ship data. You can add some by installing this in a Deconstructor and feeding it a ship.", opad);
            } else {
                tooltip.addPara("This Forge Template does not contain any ship data, but seems to be otherwise functional. It is likely there is a way to add data to it somewhere. Residual data contains hints towards planets lost to automated fleets in the first AI war.", opad);
            }
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);

    }
}