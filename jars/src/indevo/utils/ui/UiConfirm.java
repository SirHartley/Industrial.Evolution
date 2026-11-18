package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.*;

import java.util.Map;
import java.util.function.Consumer;

// the game's confirmation popup over a panel: text in the large paragraph font, confirm and Cancel
public class UiConfirm extends BaseCustomDialogDelegate {

    public static final float WIDTH = 500f;
    public static final float HEIGHT = 130f;

    private final String confirmText;
    private final float height;
    private final Consumer<TooltipMakerAPI> body;
    private final Runnable onConfirm;

    private InteractionDialogAPI dialog;

    public UiConfirm(String confirmText, Consumer<TooltipMakerAPI> body, Runnable onConfirm) {
        this(confirmText, HEIGHT, body, onConfirm);
    }

    public UiConfirm(String confirmText, float height, Consumer<TooltipMakerAPI> body, Runnable onConfirm) {
        this.confirmText = confirmText;
        this.height = height;
        this.body = body;
        this.onConfirm = onConfirm;
    }

    // opens its own interaction dialog, showCustomDialog can not stack a second custom dialog in one dialog
    public void show(Runnable onClose) {
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(new Host(), null, onClose::run);
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

    private class Host implements InteractionDialogPlugin {

        @Override
        public void init(InteractionDialogAPI dialog) {
            UiConfirm.this.dialog = dialog;
            dialog.setPromptText("");
            dialog.hideVisualPanel();
            dialog.hideTextPanel();
            dialog.showCustomDialog(WIDTH, height, UiConfirm.this);
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
