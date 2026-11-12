package indevo.abilities.splitfleet.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetAssignmentAIs.DetachmentAI;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;

import java.util.Map;

// hosts the detachment panel; the panel closes around each vanilla picker and opens again in the same view
public class DetachmentDialogPlugin implements InteractionDialogPlugin, SplinterFleetDialoguePluginAPI {

    private final DetachmentView view;
    private final DetachmentPanel panel;
    private InteractionDialogAPI dialog;
    private CampaignFleetAPI met;
    private boolean reopen = false;

    public DetachmentDialogPlugin() {
        this(new DetachmentView());
    }

    public DetachmentDialogPlugin(DetachmentView view) {
        this.view = view;
        this.panel = new DetachmentPanel(this, view);
    }

    // opens the panel in the given view, met > 0 opens it as meeting that slot's detachment; for code and tests
    public static boolean show(DetachmentView view) {
        CampaignFleetAPI target = view.met > 0 ? DetachmentMemory.getDetachment(view.met) : null;
        if (target == null) {
            view.met = 0;
            target = Global.getSector().getPlayerFleet();
        }

        return Global.getSector().getCampaignUI().showInteractionDialog(new DetachmentDialogPlugin(view), target);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI fleet && FleetUtils.isDetachmentFleet(fleet)) {
            int num = DetachmentMemory.getNumForFleet(fleet);
            if (num > 0) {
                met = fleet;
                view.met = num;
                Behaviour.removeOverride(fleet, Behaviour.FleetBehaviour.TRADE_RUN);
            }
        }

        dialog.setPromptText("");
        dialog.getOptionPanel().clearOptions();
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        open();
    }

    private void open() {
        dialog.showCustomDialog(DetachmentPanel.getWidth(), DetachmentPanel.getHeight(), panel);
    }

    @Override
    public InteractionDialogAPI getDialog() {
        return dialog;
    }

    // the pickers call this when they close
    @Override
    public void showBaseOptions() {
        reopen = true;
    }

    @Override
    public void notice(String text) {
        view.notice = text;
    }

    // a dialog shows one sub dialog at a time; dismissing the panel frees the slot at once (InteractionDialog.dialogDismissed)
    void openPicker(int slot, Runnable picker) {
        view.scroll.capture();
        view.noticeSlot = slot;
        panel.dismissForPicker();
        picker.run();
    }

    void leave() {
        if (met != null && DetachmentMemory.getNumForFleet(met) > 0) {
            // both fleets can start a battle with each other on contact
            met.setBattle(null);
            Global.getSector().getPlayerFleet().setBattle(null);
            Global.getSector().getPlayerFleet().setInteractionTarget(null);

            DetachmentAI ai = FleetUtils.getAssignmentAI(met);
            if (ai != null) ai.refresh();
        }

        Behaviour.updateActiveDetachmentBehaviour();
        dialog.dismiss();
    }

    // picker listeners run before the picker dismisses itself, so the panel opens on the next frame
    @Override
    public void advance(float amount) {
        if (!reopen || panel.isOpen()) return;

        open();
        if (panel.isOpen()) reopen = false;
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
