package com.fs.starfarer.api.campaign.impl.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.installableItemPlugins.IndEvo_VPCInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_Items;
import com.fs.starfarer.api.impl.campaign.ids.IndEvo_ids;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_EmptyForgeTemplateItemPlugin extends BaseSpecialItemPlugin {

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


        IndEvo_VPCInstallableItemPlugin.IndEvo_ItemEffect effect = IndEvo_VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(getId());
        if (effect != null) {
            effect.addItemDescription(tooltip, new SpecialItemData(getId(), null), InstallableIndustryItemPlugin.InstallableItemDescriptionMode.CARGO_TOOLTIP);
        }

        if (getId().equals(IndEvo_Items.BROKENFORGETEMPLATE)) {
            if (Global.getSector().getMemory().getBoolean("$" + IndEvo_ids.LAB)) {
                tooltip.addPara("This Forge Template has seen better days. You can restore it by installing it in a Research Laboratory.", opad);
            } else {
                tooltip.addPara("This Forge Template has seen better days. Restoring it will require technologies that are very hard to come by in the post-Collapse Sector. Residual data contains hints towards planets lost to automated fleets in the first AI war.", opad);
            }

        } else if (getId().equals(IndEvo_Items.EMPTYFORGETEMPLATE)) {
            if (Global.getSector().getMemory().getBoolean("$" + IndEvo_ids.DECONSTRUCTOR)) {
                tooltip.addPara("This Forge Template does not contain any ship data. You can add some by installing this in a Deconstructor and feeding it a ship.", opad);
            } else {
                tooltip.addPara("This Forge Template does not contain any ship data, but seems to be otherwise functional. It is likely there is a way to add data to it somewhere. Residual data contains hints towards planets lost to automated fleets in the first AI war.", opad);
            }
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);

    }
}