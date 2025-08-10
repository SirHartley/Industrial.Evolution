package indevo.industries.museum.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.museum.industry.Museum;
import indevo.industries.museum.data.MuseumSubmarketData;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

public class MuseumRemoveSubmarketOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    public static void register(){
        Global.getSector().getListenerManager().addListener(new MuseumRemoveSubmarketOptionProvider(), true);
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.MUSEUM;
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Museum museum = ((Museum) opt.ind);
        int num = museum.getArchiveSubMarkets().size();
        MuseumSubmarketData data = museum.getArchiveSubMarkets().get(num - 1);

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
                Museum museum = ((Museum) opt.ind);
                int num = museum.getArchiveSubMarkets().size();
                MuseumSubmarketData data = museum.getArchiveSubMarkets().get(num - 1);
                museum.removeSubmarket(data);
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
        tooltip.addPara("Remove a museum storage from your colony.", 0f);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Remove storage...";
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return !((Museum) opt.ind).getArchiveSubMarkets().isEmpty();
    }
}
