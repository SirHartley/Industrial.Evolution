package industrial_evolution.items;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import industrial_evolution.items.installable.IndEvo_VPCInstallableItemPlugin;
import industrial_evolution.items.installable.IndEvo_VPCInstallableItemPlugin.IndEvo_ItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class IndEvo_VPCItemPlugin extends BaseSpecialItemPlugin {

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


        IndEvo_ItemEffect effect = IndEvo_VPCInstallableItemPlugin.IndEvo_VPC_EFFECTS.get(getId());
        if (effect != null) {
            effect.addItemDescription(tooltip, new SpecialItemData(getId(), null), InstallableItemDescriptionMode.CARGO_TOOLTIP);
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);

    }
}





