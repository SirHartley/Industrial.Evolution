package indevo.industries.senate.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.dialogue.sidepanel.HelpIcon;

import java.util.Map;
import java.util.function.Consumer;

public class EdictConfirmDialog extends ButtonReportingDialogueDelegate {

    public static final float WIDTH = 500f;
    private static final float HEIGHT = 130f;
    private static final float RELIC_HEIGHT = 70f;
    private static final float BUTTON_HEIGHT = 25f;

    private final String confirmText;
    private final Consumer<TooltipMakerAPI> body;
    private final Runnable onConfirm;

    private Runnable onRelics;
    private boolean relicsEnabled;
    private Consumer<TooltipMakerAPI> relicTooltip;

    private InteractionDialogAPI dialog;
    private CustomDialogCallback callback;
    private boolean useRelics = false;

    public EdictConfirmDialog(String confirmText, Consumer<TooltipMakerAPI> body, Runnable onConfirm) {
        this.confirmText = confirmText;
        this.body = body;
        this.onConfirm = onConfirm;
    }

    public EdictConfirmDialog withRelics(Runnable onRelics, boolean enabled, Consumer<TooltipMakerAPI> tooltip) {
        this.onRelics = onRelics;
        this.relicsEnabled = enabled;
        this.relicTooltip = tooltip;
        return this;
    }

    // opens over the edict panel, showCustomDialog can not stack a second custom dialog in one dialog
    public void show(Runnable onClose) {
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(new Host(), null, onClose::run);
    }

    public float getHeight() {
        return onRelics == null ? HEIGHT : HEIGHT + RELIC_HEIGHT;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;

        float height = getHeight();
        TooltipMakerAPI info = panel.createUIElement(WIDTH, height, false);
        info.setParaInsigniaLarge();
        body.accept(info);
        panel.addUIElement(info).inTL(0f, 0f);

        if (onRelics != null) addRelicButton(panel);
    }

    private void addRelicButton(CustomPanelAPI panel) {
        CustomPanelAPI holder = panel.createCustomPanel(WIDTH, BUTTON_HEIGHT, new ButtonReportingCustomPanel(this));
        TooltipMakerAPI row = holder.createUIElement(WIDTH, BUTTON_HEIGHT, false);

        ButtonAPI button = row.addButton("Use Relics", new ButtonAction(this) {
            @Override
            public void execute() {
                useRelics = true;
                callback.dismissCustomDialog(0);
            }
        }, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, WIDTH, BUTTON_HEIGHT, 0f);
        button.setEnabled(relicsEnabled);

        row.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return HelpIcon.TOOLTIP_WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                relicTooltip.accept(tooltip);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);

        holder.addUIElement(row).inTL(0f, 0f);
        panel.addComponent(holder).inBL(0f, 0f);
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return confirmText;
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    @Override
    public void customDialogConfirm() {
        if (useRelics) onRelics.run();
        else onConfirm.run();
        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        dialog.dismiss();
    }

    private class Host implements InteractionDialogPlugin {

        @Override
        public void init(InteractionDialogAPI dialog) {
            EdictConfirmDialog.this.dialog = dialog;
            dialog.setPromptText("");
            dialog.hideVisualPanel();
            dialog.hideTextPanel();
            dialog.showCustomDialog(WIDTH, getHeight(), EdictConfirmDialog.this);
        }

        @Override
        public void optionSelected(String optionText, Object optionData) {
        }

        @Override
        public void optionMousedOver(String optionText, Object optionData) {
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult) {
        }

        @Override
        public Object getContext() {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap() {
            return null;
        }
    }
}
