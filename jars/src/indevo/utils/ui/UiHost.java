package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

// interaction dialog that only hosts one custom dialog, the panel dismisses it on leave
public class UiHost implements InteractionDialogPlugin {

    public interface Opener {
        void open(InteractionDialogAPI dialog);
    }

    private final Opener opener;

    private UiHost(Opener opener) {
        this.opener = opener;
    }

    public static boolean show(SectorEntityToken entity, Opener opener) {
        return Global.getSector().getCampaignUI().showInteractionDialog(new UiHost(opener), entity);
    }

    // a popup over an open panel: showCustomDialog can not stack a second custom dialog in one dialog,
    // showInteractionDialogFromCargo opens one more without the open dialog check
    public static void over(Opener opener, Runnable onClose) {
        Global.getSector().getCampaignUI().showInteractionDialogFromCargo(new UiHost(opener), null, onClose::run);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        dialog.setPromptText("");
        dialog.getOptionPanel().clearOptions();
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        opener.open(dialog);
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
