package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.ids.Ids;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;

import java.util.Map;
import java.util.function.Consumer;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

// hosts the Salvage Yards panel; vanilla pickers need the panel closed, it comes back in the same view after them
public class SalvageYardsDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    public static final String RETURN_TRIGGER = "IndEvo_YardsBaseMenu";

    private final InteractionDialogPlugin originalPlugin;
    private final Map<String, MemoryAPI> memoryMap;
    private final SalvageYardsPanel panel;

    private InteractionDialogAPI dialog;
    private Consumer<InteractionDialogAPI> pendingPicker = null;
    private boolean reopen = false;
    private boolean returnPending = false;

    public SalvageYardsDialogPlugin(MarketAPI market, SalvageYardsView view) {
        this(market, view, null, null);
    }

    public SalvageYardsDialogPlugin(MarketAPI market, SalvageYardsView view, InteractionDialogPlugin originalPlugin, Map<String, MemoryAPI> memoryMap) {
        this.originalPlugin = originalPlugin;
        this.memoryMap = memoryMap;
        this.panel = new SalvageYardsPanel(market, view, this);
    }

    // opens the panel for the market's yards in the given view, for code and tests
    public static boolean show(MarketAPI market, SalvageYardsView view) {
        if (market == null || !market.hasIndustry(Ids.SCRAPYARD)) return false;
        return Global.getSector().getCampaignUI().showInteractionDialog(new SalvageYardsDialogPlugin(market, view), market.getPrimaryEntity());
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
        dialog.showCustomDialog(SalvageYardsPanel.getWidth(), SalvageYardsPanel.getHeight(), panel);
    }

    // the custom dialog clears the dialog's sub dialog slot only after its delegate callback, so the picker opens next frame
    void openPicker(Consumer<InteractionDialogAPI> picker) {
        pendingPicker = picker;
        panel.dismissForPicker();
    }

    @Override
    public void close() {
        if (panel.isOpen()) panel.cancel();
        else if (!reopen && pendingPicker == null) leave();
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
        dialog.setPromptText("You decide to...");

        new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
        FireAll.fire(null, dialog, memoryMap, RETURN_TRIGGER);
    }

    void panelShown() {
        reopen = false;
    }

    @Override
    public void advance(float amount) {
        // a vanilla picker takes escape itself
        if (Global.getSector().getCampaignUI().isShowingDialog() && !reopen)
            Global.getSector().getMemoryWithoutUpdate().set(BLOCK_ESC, this, 1f);

        if (returnPending) {
            returnPending = false;
            returnToRules();
            return;
        }

        if (pendingPicker != null) {
            Consumer<InteractionDialogAPI> picker = pendingPicker;
            pendingPicker = null;
            picker.accept(dialog);
            reopen = true;
        }

        // showCustomDialog does nothing while a picker is open (InteractionDialogAPI impl keeps one sub dialog), so this waits for it
        if (reopen && !panel.isOpen()) showPanel();
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
