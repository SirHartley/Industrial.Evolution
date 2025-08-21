package indevo.industries.museum.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.ids.Ids;
import indevo.industries.museum.data.MuseumSubmarketData;
import indevo.industries.museum.industry.Museum;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;

import java.awt.*;

public class MuseumManageParadeOptionProvider extends SingleIndustrySimpifiedOptionProvider {

    //recall - set parade loc outside viewport (angle to old loc) and set return assignment (forceReturnToSource)
    //create - set up new parade, data: Name, Target planet, Ships, duration (add button: repeat)
    //stop parades & resume random parades

    //make sure to have contingency for multiple parades

    public static final float WIDTH = 600f;
    public static final float HEIGHT_200 = 150f;
    protected static final float BUTTON_HEIGHT = 30, SELECT_BUTTON_WIDTH = 150f;

    public static void register(){
        Global.getSector().getListenerManager().addListener(new MuseumManageParadeOptionProvider(), true);
    }

    @Override
    public String getTargetIndustryId() {
        return Ids.MUSEUM;
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return ((Museum) opt.ind).getArchiveSubMarkets().size() < 5;
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Museum museum = ((Museum) opt.ind);
        int num = museum.getArchiveSubMarkets().size() + 1;

        CustomDialogDelegate delegate = new ButtonReportingDialogueDelegate() {

            public MuseumSubmarketData data = new MuseumSubmarketData(Ids.MUSEUM_SUBMARKET + "_" + num, "Archive " + num, true, true);
            public TextFieldAPI nameField = null;

            CustomPanelAPI basePanel;
            CustomPanelAPI panel;

            public void createCustomDialog(CustomPanelAPI panel, final CustomDialogCallback callback) {
                this.basePanel = panel;
                recreatePanel(callback);
            }

            public void recreatePanel(final CustomDialogCallback callback){
                if (panel != null) basePanel.removeComponent(panel);

                panel = Global.getSettings().createCustom(basePanel.getPosition().getWidth(), basePanel.getPosition().getHeight(), null);

                float opad = 10f;
                float spad = 5f;

                Color baseColor = Misc.getButtonTextColor();
                Color bgColour = Misc.getDarkPlayerColor();
                Color brightColor = Misc.getBrightPlayerColor();
                //--------- Add naming selection ---------

                TooltipMakerAPI heading = panel.createUIElement(WIDTH, BUTTON_HEIGHT * 3f, false);
                heading.addSectionHeading("Configure the new storage area", Alignment.MID, opad);

                CustomPanelAPI subPanel = panel.createCustomPanel(WIDTH, BUTTON_HEIGHT + 2f, new BaseCustomUIPanelPlugin());
                TooltipMakerAPI anchor = subPanel.createUIElement(WIDTH, BUTTON_HEIGHT, false);

                TextFieldAPI nameField = anchor.addTextField(WIDTH, opad);
                nameField.setText(data.submarketName);

                if (this.nameField != null) nameField.setText(this.nameField.getText());
                this.nameField = nameField;

                subPanel.addUIElement(anchor).inTL(-opad, 0f); //if we don't -opad it kinda does it by its own, no clue why
                heading.addCustom(subPanel, 0f);

                //--------- Add storage selection ---------

                CustomPanelAPI selectorPanel = panel.createCustomPanel(WIDTH, BUTTON_HEIGHT + opad, new ButtonReportingCustomPanel(this));
                TooltipMakerAPI selectorAnchor = selectorPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

                //ships
                ButtonAPI showInFleetScreenButton = selectorAnchor.addAreaCheckbox("Show in fleet screen", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        data.showInFleetScreen = !data.showInFleetScreen;
                        if (!data.showInCargoScreen && !data.showInFleetScreen) data.showInCargoScreen = true;

                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, brightColor,  SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);

                showInFleetScreenButton.setChecked(data.showInFleetScreen);

                selectorPanel.addUIElement(selectorAnchor).inLMid(-opad); //if we don't -opad it kinda does it by its own, no clue why
                TooltipMakerAPI lastUsedAnchor = selectorAnchor;

                selectorAnchor = selectorPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);

                //Cargo
                ButtonAPI showInCargoButton = selectorAnchor.addAreaCheckbox("Show in cargo screen", new ButtonAction(this) {
                    @Override
                    public void execute() {
                        data.showInCargoScreen = !data.showInCargoScreen;
                        if (!data.showInCargoScreen && !data.showInFleetScreen) data.showInFleetScreen = true;

                        recreatePanel(callback);
                    }
                }, baseColor, bgColour, brightColor,  SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);

                showInCargoButton.setChecked(data.showInCargoScreen);

                selectorPanel.addUIElement(selectorAnchor).rightOfMid(lastUsedAnchor, opad);
                lastUsedAnchor = selectorAnchor;

                heading.addCustom(selectorPanel, opad);
                heading.addPara("A custom storage does not incur any additional cost. You can remove this storage at any time.", opad);

                panel.addUIElement(heading).inTL(0f, 0.0F);
                basePanel.addComponent(panel);
            }

            @Override
            public boolean hasCancelButton() {
                return true;
            }

            @Override
            public void customDialogConfirm() {

                museum.addSubmarket(data);
            }

            @Override
            public String getCancelText() {
                return "Cancel";
            }

            @Override
            public String getConfirmText() {
                return "Add storage";
            }
        };

        ui.showDialog(WIDTH, HEIGHT_200, delegate);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, IndustryOptionData opt) {
        Color hl = Misc.getHighlightColor();
        float opad = 10f;

        tooltip.addPara("Manage the parade fleets your museum dispatches. Stop, resume them, or set up your own configurations.", 0f);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Manage Parades...";
    }
}