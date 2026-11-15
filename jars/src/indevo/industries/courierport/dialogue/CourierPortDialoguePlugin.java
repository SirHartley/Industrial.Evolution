package indevo.industries.courierport.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;

import java.util.List;
import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

// rule command and host of the Courier Port panel; pickers close the panel and it reopens a frame after they are gone
public class CourierPortDialoguePlugin extends BaseCommandPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private static final String RETURN_OPTION = "IndEvo_SelectreturnToMenu";

    private InteractionDialogPlugin originalPlugin;
    private Map<String, MemoryAPI> memoryMap;
    private SectorEntityToken entity;

    private CourierPanelDelegate panel;
    private InteractionDialogAPI dialog;
    private boolean returnPending = false;
    private boolean reopenPending = false;

    public CourierPortDialoguePlugin() {
    }

    private CourierPortDialoguePlugin(CourierView view) {
        this.panel = new CourierPanelDelegate(view, this);
    }

    // opens the panel at the entity in the given view, for code and tests; Return closes the dialog
    public static boolean show(SectorEntityToken entity, CourierView view) {
        return Global.getSector().getCampaignUI().showInteractionDialog(new CourierPortDialoguePlugin(view), entity);
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();
        this.panel = new CourierPanelDelegate(new CourierView(), this);
        dialog.setPlugin(this);
        init(dialog);
        return true;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.entity = dialog.getInteractionTarget();

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

    InteractionDialogAPI getDialog() {
        return dialog;
    }

    private void showPanel() {
        dialog.showCustomDialog(CourierPanelDelegate.getWidth(), CourierPanelDelegate.getHeight(), panel);
    }

    // the custom dialog clears the dialog's sub dialog state only after its dismiss returns, so a picker opens right after it
    // pickers call their listener before their own dismiss, so the panel reopens from advance
    void openPicker(Runnable picker) {
        Global.getSector().getMemoryWithoutUpdate().unset(BLOCK_ESC);
        panel.closeForPicker();
        picker.run();
    }

    void reopen() {
        reopenPending = true;
    }

    @Override
    public void close() {
        if (panel.isOpen()) panel.cancel();
        else if (!reopenPending) leave();
    }

    void leave() {
        if (originalPlugin == null) dialog.dismiss();
        else returnPending = true;
    }

    // runs a frame after the custom dialog is gone, rules must not fire from inside its dismiss
    private void returnToRules() {
        dialog.setPlugin(originalPlugin);
        dialog.showTextPanel();
        dialog.showVisualPanel();
        dialog.getTextPanel().clear();

        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        if (entity != null && entity.getTags().contains(Tags.COMM_RELAY)) {
            FireAll.fire(null, dialog, memoryMap, "COB_AddOptions");
            return;
        }

        memoryMap.get(MemKeys.LOCAL).set("$option", RETURN_OPTION, 0f);
        FireAll.fire(null, dialog, memoryMap, "DialogOptionSelected");
    }

    @Override
    public void advance(float amount) {
        if (panel.isOpen() && Global.getSector().getCampaignUI().isShowingDialog())
            Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);

        if (reopenPending) {
            reopenPending = false;
            showPanel();
        }

        if (returnPending) {
            returnPending = false;
            returnToRules();
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
        return memoryMap;
    }
}
