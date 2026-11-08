package indevo.industries.senate.dialogue;

import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.dialogue.sidepanel.HelpIcon;

import java.util.function.Consumer;

public class EdictConfirmDialog extends ButtonReportingDialogueDelegate {

    public static final float WIDTH = 500f;
    private static final float HEIGHT = 130f;
    private static final float RELIC_HEIGHT = 70f;
    private static final float BUTTON_HEIGHT = 25f;

    private final EdictDialogPlugin host;
    private final String confirmText;
    private final Consumer<TooltipMakerAPI> body;
    private final Runnable onConfirm;

    private Runnable onRelics;
    private boolean relicsEnabled;
    private Consumer<TooltipMakerAPI> relicTooltip;

    private CustomDialogCallback callback;
    private boolean open = false;
    private boolean useRelics = false;

    public EdictConfirmDialog(EdictDialogPlugin host, String confirmText, Consumer<TooltipMakerAPI> body, Runnable onConfirm) {
        this.host = host;
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

    public float getHeight() {
        return onRelics == null ? HEIGHT : HEIGHT + RELIC_HEIGHT;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        this.open = true;

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

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
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
        open = false;
        if (useRelics) onRelics.run();
        else onConfirm.run();
        host.showPanelNextFrame();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        host.showPanelNextFrame();
    }
}
