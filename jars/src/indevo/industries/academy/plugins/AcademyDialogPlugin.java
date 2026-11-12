package indevo.industries.academy.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.EscapeBlockingDialoguePluginAPI;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.listener.EscapeInterceptListener;
import indevo.industries.petshop.script.EscBlockerMemoryIterator;

import java.util.Map;

import static indevo.industries.petshop.listener.EscapeInterceptListener.BLOCK_ESC;

public class AcademyDialogPlugin implements InteractionDialogPlugin, EscapeBlockingDialoguePluginAPI {

    private final InteractionDialogPlugin originalPlugin;
    private final Map<String, MemoryAPI> memoryMap;
    private final String returnOption;

    private final AcademyPanelDelegate panel;
    private InteractionDialogAPI dialog;
    private boolean returnPending = false;

    public AcademyDialogPlugin(Academy academy, AcademyView view) {
        this(academy, view, null, null, null);
    }

    public AcademyDialogPlugin(Academy academy, AcademyView view, InteractionDialogPlugin originalPlugin, Map<String, MemoryAPI> memoryMap, String returnOption) {
        this.originalPlugin = originalPlugin;
        this.memoryMap = memoryMap;
        this.returnOption = returnOption;
        this.panel = new AcademyPanelDelegate(academy, view, this::leave);
    }

    // opens the panel for the market's Academy in the given view, for code and tests
    public static boolean show(MarketAPI market, AcademyView view) {
        Academy academy = Academy.get(market);
        if (academy == null) return false;
        return Global.getSector().getCampaignUI().showInteractionDialog(new AcademyDialogPlugin(academy, view), market.getPrimaryEntity());
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
        dialog.showCustomDialog(AcademyPanelDelegate.getWidth(), AcademyPanelDelegate.getHeight(), panel);
    }

    @Override
    public void close() {
        if (panel.isOpen()) panel.cancel();
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
