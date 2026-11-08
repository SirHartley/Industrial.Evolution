package indevo.industries.senate.dialogue;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;

import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class EdictDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private final InteractionDialogPlugin originalPlugin;
    private final EdictPanelDelegate panel;

    private InteractionDialogAPI dialog;
    private EdictConfirmDialog popup;
    private boolean showPanelPending = false;
    private boolean returnPending = false;

    private EdictDialogPlugin(MarketAPI market, InteractionDialogPlugin originalPlugin) {
        this.originalPlugin = originalPlugin;
        this.panel = new EdictPanelDelegate(market, this);
    }

    public static void show(DialogCreatorUI ui, MarketAPI market) {
        ui.showDialog(null, new EdictDialogPlugin(market, null));
    }

    public static void show(InteractionDialogAPI dialog, MarketAPI market) {
        EdictDialogPlugin plugin = new EdictDialogPlugin(market, dialog.getPlugin());
        dialog.setPlugin(plugin);
        plugin.init(dialog);
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

        // opened from the colony screen there is nothing behind the panel, from rules the market dialog stays visible
        if (originalPlugin == null) {
            dialog.setPromptText("");
            dialog.getOptionPanel().clearOptions();
            dialog.hideVisualPanel();
            dialog.hideTextPanel();
        }

        showPanel();
    }

    public void showPanel() {
        popup = null;
        dialog.showCustomDialog(EdictPanelDelegate.getPanelWidth(), EdictPanelDelegate.getPanelHeight(), panel);
    }

    public void showPanelNextFrame() {
        popup = null;
        showPanelPending = true;
    }

    // the panel must be dismissed first, a dialog only shows custom dialogs one at a time
    public void showConfirm(EdictConfirmDialog confirm) {
        popup = confirm;
        dialog.showCustomDialog(EdictConfirmDialog.WIDTH, confirm.getHeight(), confirm);
    }

    @Override
    public void close() {
        if (popup != null && popup.isOpen()) popup.cancel();
        else if (panel.isOpen()) panel.cancel();
        else leave();
    }

    public void leave() {
        if (originalPlugin == null) dialog.dismiss();
        else returnPending = true;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().getCampaignUI().isShowingDialog())
            Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);

        if (returnPending) {
            returnPending = false;
            dialog.setPlugin(originalPlugin);
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
        return originalPlugin == null ? null : originalPlugin.getMemoryMap();
    }
}
