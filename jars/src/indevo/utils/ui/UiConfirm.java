package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.function.Consumer;

// the game's confirmation popup over a panel: text in the large paragraph font, confirm and Cancel
public class UiConfirm extends BaseCustomDialogDelegate {

    public static final float WIDTH = 500f;

    private final String confirmText;
    private final float height;
    private final Consumer<TooltipMakerAPI> body;
    private final Runnable onConfirm;

    private InteractionDialogAPI dialog;

    // as high as its text: the game puts the buttons 10 px under the panel
    public UiConfirm(String confirmText, Consumer<TooltipMakerAPI> body, Runnable onConfirm) {
        this(confirmText, measure(body), body, onConfirm);
    }

    public UiConfirm(String confirmText, float height, Consumer<TooltipMakerAPI> body, Runnable onConfirm) {
        this.confirmText = confirmText;
        this.height = height;
        this.body = body;
        this.onConfirm = onConfirm;
    }

    private static float measure(Consumer<TooltipMakerAPI> body) {
        TooltipMakerAPI probe = Global.getSettings().createCustom(WIDTH, 0f, null).createUIElement(WIDTH, 0f, false);
        probe.setParaInsigniaLarge();
        body.accept(probe);
        return (float) Math.ceil(probe.getHeightSoFar());
    }

    public void show(Runnable onClose) {
        UiHost.over(dialog -> {
            this.dialog = dialog;
            dialog.showCustomDialog(WIDTH, height, this);
        }, onClose);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        TooltipMakerAPI info = panel.createUIElement(WIDTH, height, false);
        info.setParaInsigniaLarge();
        body.accept(info);
        panel.addUIElement(info).inTL(0f, 0f);
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
        onConfirm.run();
        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        dialog.dismiss();
    }
}
