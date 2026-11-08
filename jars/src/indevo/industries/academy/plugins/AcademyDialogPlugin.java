package indevo.industries.academy.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.economy.RelicSpeedUpTarget;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;

import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class AcademyDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private final Academy academy;
    private final InteractionDialogPlugin originalPlugin;
    private final Map<String, MemoryAPI> memoryMap;
    private final String returnOption;

    private InteractionDialogAPI dialog;
    private final AcademyPanelDelegate panel;
    private AcademyTrainingPopupDelegate popup;
    private AcademyAdminTrainingConfirm adminConfirm;
    private RelicSpeedUpDialog relicDialog;
    private boolean showPanelPending = false;
    private boolean returnPending = false;

    public AcademyDialogPlugin(Academy academy) {
        this(academy, null, null, null);
    }

    public AcademyDialogPlugin(Academy academy, InteractionDialogPlugin originalPlugin, Map<String, MemoryAPI> memoryMap, String returnOption) {
        this.academy = academy;
        this.originalPlugin = originalPlugin;
        this.memoryMap = memoryMap;
        this.returnOption = returnOption;
        this.panel = new AcademyPanelDelegate(academy, this);
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

    public void showPanel() {
        popup = null;
        adminConfirm = null;
        relicDialog = null;
        dialog.showCustomDialog(AcademyPanelDelegate.getWidth(), panel.getHeight(), panel);
    }

    public void showPanelNextFrame() {
        popup = null;
        adminConfirm = null;
        relicDialog = null;
        showPanelPending = true;
    }

    public void showTrainingPopup(PersonAPI officer) {
        popup = new AcademyTrainingPopupDelegate(academy, this, officer);
        dialog.showCustomDialog(AcademyTrainingPopupDelegate.WIDTH, popup.getHeight(), popup);
    }

    public void showAdminTraining(PersonAPI admin) {
        adminConfirm = new AcademyAdminTrainingConfirm(academy, this, admin);
        dialog.showCustomDialog(AcademyAdminTrainingConfirm.WIDTH, adminConfirm.getHeight(), adminConfirm);
    }

    public void showRelicDialog(RelicSpeedUpTarget target) {
        relicDialog = new RelicSpeedUpDialog(target, this::showPanelNextFrame);
        relicDialog.show(dialog);
    }

    @Override
    public void close() {
        if (adminConfirm != null && adminConfirm.isOpen()) adminConfirm.cancel();
        else if (relicDialog != null && relicDialog.isOpen()) relicDialog.cancel();
        else if (popup != null && popup.isOpen()) popup.cancel();
        else if (panel.isOpen()) panel.cancel();
        else leave();
    }

    public void leave() {
        if (originalPlugin == null) dialog.dismiss();
        else returnPending = true;
    }

    // runs a frame after the custom dialog is gone, rules must not fire from inside its dismiss
    private void returnToRules() {
        dialog.setPlugin(originalPlugin);
        dialog.showTextPanel();
        dialog.showVisualPanel();

        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        memoryMap.get(MemKeys.LOCAL).set("$option", returnOption, 0f);
        FireAll.fire(null, dialog, memoryMap, "DialogOptionSelected");
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().getCampaignUI().isShowingDialog())
            Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);

        if (returnPending) {
            returnPending = false;
            returnToRules();
            return;
        }

        if (showPanelPending) {
            showPanelPending = false;
            showPanel();
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
