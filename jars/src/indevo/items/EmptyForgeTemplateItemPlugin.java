package indevo.items;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.GenericSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.ids.Ids;
import indevo.ids.ItemIds;

public class EmptyForgeTemplateItemPlugin extends GenericSpecialItemPlugin {

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, false);

        float opad = 10f;

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