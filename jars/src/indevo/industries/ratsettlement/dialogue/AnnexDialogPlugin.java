package indevo.industries.ratsettlement.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;
import indevo.industries.ratsettlement.facility.EngineeringAnnex;

import java.util.Map;
import java.util.function.Consumer;

// hosts the annex panel in rat's settlement dialog; return hands the dialog to onReturn a frame after the panel is gone
public class AnnexDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private final EngineeringAnnex annex;
    private final AnnexView view;
    private final Consumer<InteractionDialogAPI> onReturn;

    private InteractionDialogAPI dialog;
    private AnnexPanelDelegate panel;
    private boolean returnPending = false;
    private boolean reopenPending = false;

    public AnnexDialogPlugin(EngineeringAnnex annex, AnnexView view, Consumer<InteractionDialogAPI> onReturn) {
        this.annex = annex;
        this.view = view;
        this.onReturn = onReturn;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        if (!Global.getSector().getListenerManager().hasListenerOfClass(EscapeInterceptListener.class))
            Global.getSector().getListenerManager().addListener(new EscapeInterceptListener(), true);

        boolean hasScript = false;
        for (EveryFrameScript s : Global.getSector().getTransientScripts()) {
            if (s instanceof EscBlockerMemoryIterator) {
                hasScript = true;
                break;
            }
        }

        if (!hasScript) Global.getSector().addTransientScript(new EscBlockerMemoryIterator());

        dialog.setPromptText("");
        dialog.getOptionPanel().clearOptions();
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        showPanel();
    }

    private void showPanel() {
        panel = new AnnexPanelDelegate(annex, view, this::leave, this::reopen);
        dialog.showCustomDialog(AnnexPanelDelegate.getWidth(), panel.getHeight(), panel);
    }

    @Override
    public void close() {
        if (panel != null && panel.isOpen()) panel.cancel();
        else leave();
    }

    private void leave() {
        returnPending = true;
    }

    // the panel height follows the phase, so a phase change shows the panel again
    private void reopen() {
        reopenPending = true;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().getCampaignUI().isShowingDialog()) EscapeInterceptListener.block(this);

        if (reopenPending) {
            reopenPending = false;
            showPanel();
        } else if (returnPending) {
            returnPending = false;
            onReturn.accept(dialog);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
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
