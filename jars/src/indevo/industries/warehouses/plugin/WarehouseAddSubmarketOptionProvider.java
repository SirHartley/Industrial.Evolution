package indevo.industries.warehouses.plugin;

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
import indevo.industries.warehouses.industry.Warehouses;
import indevo.industries.warehouses.data.WarehouseConstants;
import indevo.industries.warehouses.data.WarehouseSubmarketData;
import indevo.utils.plugins.SingleIndustrySimpifiedOptionProvider;
import indevo.utils.plugins.TagBasedSimplifiedIndustryOptionProvider;

import java.awt.*;

public class WarehouseAddSubmarketOptionProvider extends TagBasedSimplifiedIndustryOptionProvider {
    public static final float WIDTH = 600f;
    public static final float HEIGHT_200 = 150f;
    protected static final float BUTTON_HEIGHT = 30, SELECT_BUTTON_WIDTH = 150f;

    public static void register(){
        Global.getSector().getListenerManager().addListener(new WarehouseAddSubmarketOptionProvider(), true);
    }

    @Override
    public boolean optionEnabled(IndustryOptionData opt) {
        return ((Warehouses) opt.ind).getWarehouseSubMarkets().size() < 5;
    }

    @Override
    public void onClick(IndustryOptionData opt, DialogCreatorUI ui) {
        Warehouses warehouses = ((Warehouses) opt.ind);
        int num = warehouses.getWarehouseSubMarkets().size() + 1;

        CustomDialogDelegate delegate = new ButtonReportingDialogueDelegate() {

            public WarehouseSubmarketData data = new WarehouseSubmarketData(Ids.WAREHOUSE_SUBMARKET + "_" + num, "Warehouse " + num, true, true, false);
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
                data.submarketName = formatName(nameField.getText());

                warehouses.addSubmarket(data);
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

        tooltip.addPara("Add up to %s additional configurable storage areas to your colony.", 0f, hl, WarehouseConstants.MAX_ADDITIONAL_SUBMARKETS + "");
        tooltip.addPara("Currently used: %s", opad, hl, ((Warehouses) opt.ind).getWarehouseSubMarkets().size() + "/" + WarehouseConstants.MAX_ADDITIONAL_SUBMARKETS);
    }

    @Override
    public String getOptionLabel(Industry ind) {
        return "Add storage...";
    }

    //thx chatgpt (vibe coding this took almost as long as I would have taken just making it myself...)
    public static String formatName(String raw) {
        int maxLineLength = 16;

        if (raw == null) return "";
        String text = raw.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return "";

        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        boolean secondLineTruncated = false; // cut or removed on line 2

        String[] words = text.split(" ");
        for (String word : words) {
            if (line2.length() > 0) {
                // Already on second line
                if (line2.length() + 1 + word.length() <= maxLineLength) {
                    line2.append(' ').append(word);
                } else if (word.length() > maxLineLength) {
                    // Hyphenate on line 2 and mark truncation
                    int spaceLeft = maxLineLength - line2.length() - 1; // room after a space
                    if (spaceLeft >= 1) {
                        line2.append(' ').append(word, 0, spaceLeft - 1).append('-');
                        secondLineTruncated = true;
                    } else {
                        // No room at all - show ellipsis later
                        secondLineTruncated = true;
                    }
                    break; // two lines only
                } else {
                    // Word does not fit on line 2 - removed
                    secondLineTruncated = true;
                    break;
                }
            } else {
                // Filling first line
                if (line1.length() == 0) {
                    if (word.length() <= maxLineLength) {
                        line1.append(word);
                    } else {
                        // Break long first word: part on line 1, remainder starts line 2
                        line1.append(word, 0, maxLineLength - 1).append('-');
                        String remainder = word.substring(maxLineLength - 1);
                        if (remainder.length() <= maxLineLength) {
                            line2.append(remainder);
                        } else {
                            line2.append(remainder, 0, maxLineLength - 1).append('-');
                            secondLineTruncated = true;
                            break;
                        }
                    }
                } else {
                    int needed = 1 + word.length(); // space + word
                    if (line1.length() + needed <= maxLineLength) {
                        line1.append(' ').append(word);
                    } else {
                        // Move to second line
                        if (word.length() <= maxLineLength) {
                            line2.append(word);
                        } else {
                            line2.append(word, 0, maxLineLength - 1).append('-');
                            secondLineTruncated = true;
                            break;
                        }
                    }
                }
            }
        }

        // If second line experienced a cut or a word was removed, end with "..."
        if (secondLineTruncated) {
            // Trim trailing spaces and any trailing hyphen before adding ellipsis
            int len = line2.length();
            while (len > 0 && line2.charAt(len - 1) == ' ') { line2.setLength(--len); }
            if (len > 0 && line2.charAt(len - 1) == '-') { line2.setLength(--len); }

            // Ensure room for "..." within maxLineLength
            int roomForEllipsis = maxLineLength - 3;
            if (line2.length() > roomForEllipsis) {
                line2.setLength(roomForEllipsis);
                // remove a trailing space if truncation landed on one
                while (line2.length() > 0 && line2.charAt(line2.length() - 1) == ' ') {
                    line2.setLength(line2.length() - 1);
                }
            }
            line2.append("...");
        }

        return line2.length() == 0
                ? line1.toString()
                : line1.toString() + "\n" + line2.toString();
    }

    @Override
    public String getTargetTag() {
        return "IndEvo_warehouse";
    }
}
