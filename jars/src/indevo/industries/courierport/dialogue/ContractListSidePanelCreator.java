package indevo.industries.courierport.dialogue;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.courierport.ShippingTooltipHelper;
import indevo.dialogue.sidepanel.InteractionDialogCustomPanelPlugin;
import indevo.dialogue.sidepanel.NoFrameCustomPanelPlugin;
import indevo.dialogue.sidepanel.VisualCustomPanel;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class ContractListSidePanelCreator {

    protected static final float PANEL_WIDTH_1 = 240;
    protected static final float PANEL_WIDTH_2 = VisualCustomPanel.PANEL_WIDTH - PANEL_WIDTH_1 - 8;
    protected static final float SHIP_ICON_WIDTH = 48;
    protected static final float ARROW_BUTTON_WIDTH = 20, BUTTON_HEIGHT = 30;
    protected static final float SELECT_BUTTON_WIDTH = 95f;
    protected static final float TEXT_FIELD_WIDTH = 250;
    protected static final float TEXT_FIELD_HEIGHT = 110f;

    public void showPanel(InteractionDialogAPI dialogue) {
        VisualCustomPanel.createPanel(dialogue, true);
        showCustomPanel(dialogue);
    }

    private void showCustomPanel(final InteractionDialogAPI dialogue) {
        float opad = 10f;
        float spad = 3f;
        float pad = 5f;

        final CustomPanelAPI panel = VisualCustomPanel.getPanel();
        TooltipMakerAPI panelTooltip = VisualCustomPanel.getTooltip();

        for (ShippingContract c : ShippingContractMemory.getContractList()){

            final ShippingContract contract = c;
            CustomPanelAPI contractPanel = panel.createCustomPanel(PANEL_WIDTH_1, BUTTON_HEIGHT, new NoFrameCustomPanelPlugin());
            boolean isValid = contract.isValid();

            Color baseColor = Misc.getButtonTextColor();
            Color bgColour = Misc.getDarkPlayerColor();
            Color brightColor = Misc.getBrightPlayerColor();

            String s = contract.isActive ? "" : "[Inactive] ";
            s = isValid ? s : "[Invalid]";

            Color bg = Misc.getDarkPlayerColor();
            if(!contract.isActive) bg = new Color(110, 110, 110, 255);
            if (!isValid) bg = new Color(140, 70, 20, 255);

            panelTooltip.addSectionHeading(s + contract.name, contract.isActive && isValid ? Misc.getTextColor() : Color.WHITE, bg,  Alignment.MID, opad);

            String buttonId = "button_edit_" + contract.id;

            TooltipMakerAPI lastUsedVariableButtonAnchor;
            TooltipMakerAPI anchor = contractPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI button = anchor.addButton("Edit", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            InteractionDialogCustomPanelPlugin.ButtonEntry entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
                @Override
                public void onToggle() {
                    new ContractSidePanelCreator().showPanel(dialogue, contract);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            contractPanel.addUIElement(anchor).inTL(spad, opad);
            lastUsedVariableButtonAnchor = anchor;

            buttonId = "button_remove_" + contract.id;
            baseColor = Color.WHITE;
            bgColour = new Color(80, 20, 10, 255);

            anchor = contractPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            button = anchor.addButton("Remove", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
                @Override
                public void onToggle() {
                    ShippingContractMemory.removeContract(contract);
                    showPanel(dialogue);
                    CourierPortDialoguePlugin.reload();
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            contractPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = anchor;

            buttonId = "button_trigger_" + contract.id;

            bgColour = isValid ? new Color(50, 100, 0, 255) : Misc.getGrayColor();

            anchor = contractPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            button = anchor.addButton("Ship now", buttonId, baseColor, bgColour, Alignment.MID, CutStyle.C2_MENU, SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, 0);
            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
                @Override
                public void onToggle() {
                    contract.elapsedDays = 999;
                    showPanel(dialogue);
                }
            };

            button.setEnabled(isValid);

            VisualCustomPanel.getPlugin().addButton(entry);
            contractPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = anchor;

            buttonId = "button_active_" + contract.id;
            baseColor = Misc.getButtonTextColor();
            bgColour = Misc.getDarkPlayerColor();

            anchor = contractPanel.createUIElement(SELECT_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            button = anchor.addAreaCheckbox(contract.isActive ? "Deactivate" : "Activate", new Object(), baseColor, bgColour, brightColor,
                    SELECT_BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    0f,
                    false);

            button.setChecked(contract.isActive);
            button.setEnabled(contract.isValid());

            entry = new InteractionDialogCustomPanelPlugin.ButtonEntry(button, buttonId) {
                @Override
                public void onToggle() {
                    contract.isActive = !contract.isActive;
                    showPanel(dialogue);
                }
            };

            VisualCustomPanel.getPlugin().addButton(entry);
            contractPanel.addUIElement(anchor).rightOfMid(lastUsedVariableButtonAnchor, opad);
            lastUsedVariableButtonAnchor = anchor;

            panelTooltip.addCustom(contractPanel, opad); //add panel

            CustomPanelAPI textPanel = panel.createCustomPanel(PANEL_WIDTH_1, 50f, new NoFrameCustomPanelPlugin());

            if(contract.isValid()){
                anchor = textPanel.createUIElement(TEXT_FIELD_WIDTH, BUTTON_HEIGHT, false);
                anchor.setParaFont(Fonts.DEFAULT_SMALL);
                anchor.addPara("From: %s, %s",
                        pad,
                        contract.getFromMarket().getTextColorForFactionOrPlanet(),
                        contract.getFromMarket().getName(),
                        contract.getFromSubmarket().getNameOneLine());

                anchor.addPara("To: %s, %s",
                        pad,
                        contract.getToMarket().getTextColorForFactionOrPlanet(),
                        contract.getToMarket().getName(),
                        contract.getToSubmarket().getNameOneLine());

                anchor.setParaFontDefault();
                textPanel.addUIElement(anchor).inTL(spad, opad);
                lastUsedVariableButtonAnchor = anchor;

                anchor = textPanel.createUIElement(TEXT_FIELD_WIDTH + 60f, 50f, false);
                anchor.setParaFont(Fonts.DEFAULT_SMALL);

                boolean ships = contract.scope != ShippingContract.Scope.ALL_CARGO && contract.scope != ShippingContract.Scope.SPECIFIC_CARGO;
                boolean cargo = contract.scope != ShippingContract.Scope.ALL_SHIPS && contract.scope != ShippingContract.Scope.SPECIFIC_SHIPS;
                String and = ships && cargo ? " and %s" : "";
                String shipStr = ShippingTooltipHelper.getShipAmtString(contract);
                String cargoStr = ShippingTooltipHelper.getCargoAmtString(contract);

                String firstHL = ships ? shipStr : cargoStr;
                String secondHL = ships && cargo ? cargoStr : ShippingTooltipHelper.getCadenceString(contract.getRecurrentDays());
                String thirdHL = ShippingTooltipHelper.getCadenceString(contract.getRecurrentDays());

                anchor.addPara("Transport %s" + and + (contract.getRecurrentDays() > 0 ? ", every %s." : ", %s."),
                        spad,
                        Misc.getHighlightColor(),
                        firstHL, secondHL, thirdHL);

                s = ShippingTooltipHelper.getDaysMonthString(contract.getRecurrentDays() - contract.elapsedDays);
                if (contract.isActive || contract.elapsedDays > contract.getRecurrentDays()) anchor.addPara("Next delivery " + (s.contains("today") ? "%s": "in %s"), pad, Misc.getHighlightColor(), s);
                else anchor.addPara("Next delivery: %s.", pad, Misc.getNegativeHighlightColor(), "only on demand");

                anchor.setParaFontDefault();
                PositionAPI pos = textPanel.addUIElement(anchor).rightOfTop(lastUsedVariableButtonAnchor, opad);
                pos.setYAlignOffset(-2f);
                lastUsedVariableButtonAnchor = anchor;

            } else {
                anchor = textPanel.createUIElement(PANEL_WIDTH_1 - 20, BUTTON_HEIGHT, false);
                anchor.setParaFont(Fonts.DEFAULT_SMALL);
                anchor.addPara("Contract is invalid: %s.",
                        pad,
                        Misc.getNegativeHighlightColor(),
                        contract.getInvalidReason());

                anchor.setParaFontDefault();
                textPanel.addUIElement(anchor).inTL(spad, opad);
                lastUsedVariableButtonAnchor = anchor;
                anchor.setParaFontDefault();
            }

            panelTooltip.addCustom(textPanel, opad); //add panel

        }

        VisualCustomPanel.addTooltipToPanel();
    }
}
