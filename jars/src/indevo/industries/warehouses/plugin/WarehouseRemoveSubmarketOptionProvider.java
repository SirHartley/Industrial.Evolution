package indevo.industries.warehouses.plugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.warehouses.industry.Warehouses;
import indevo.industries.warehouses.data.WarehouseSubmarketData;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

public class WarehouseRemoveSubmarketOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new WarehouseRemoveSubmarketOptionProvider(), true);
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.WAREHOUSES;
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Warehouses warehouses = ((Warehouses) opt.ind);
        int num = warehouses.getWarehouseSubMarkets().size();
        WarehouseSubmarketData data = warehouses.getWarehouseSubMarkets().get(num - 1);

        CustomDialogDelegate delegate = new BaseCustomDialogDelegate() {
            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                TooltipMakerAPI info = panel.createUIElement(500f, 150f, false);

                info.setParaInsigniaLarge();

                info.addPara("This will %s the storage named %s and move all ships and items stored in it to the normal colony storage.", 10f, Misc.getHighlightColor(), "remove", data.submarketName);
                info.addPara("You can add a new storage at any time.", Misc.getHighlightColor(), 10f);

                panel.addUIElement(info).inTL(0,0);
            }

            @Override
            public boolean hasCancelButton() {
                return true;
            }

            @Override
            public void customDialogConfirm() {
                Warehouses warehouses = ((Warehouses) opt.ind);
                int num = warehouses.getWarehouseSubMarkets().size();
                WarehouseSubmarketData data = warehouses.getWarehouseSubMarkets().get(num - 1);
                warehouses.removeSubmarket(data);
            }

            @Override
            public String getCancelText() {
                return "Cancel";
            }

            @Override
            public String getConfirmText() {
                return "Confirm";
            }
        };
        ui.showDialog(500f, 150f, delegate);

        //remove a cargo area (select from list)
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        tooltip.addPara("Remove a warehouse storage from your colony.", 0f);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Remove storage...";
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return !((Warehouses) opt.ind).getWarehouseSubMarkets().isEmpty();
    }
}
