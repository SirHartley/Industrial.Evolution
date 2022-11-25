package industrial_evolution.splinterFleet.dialogue.customPanelPlugins;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/**
 * @Author Histidine
 */

public class VisualCustomPanel {

    public static final float PANEL_WIDTH = 600;
    public static final float PANEL_HEIGHT = 600;

    protected static CustomPanelAPI panel;
    protected static TooltipMakerAPI tooltip;
    protected static InteractionDialogCustomPanelPlugin plugin;

    public static TooltipMakerAPI getTooltip() {
        return tooltip;
    }

    public static InteractionDialogCustomPanelPlugin getPlugin() {
        return plugin;
    }

    public static CustomPanelAPI getPanel() {
        return panel;
    }

    /**
     * If this is not called when closing the dialogue, causes a memory leak
     */
    public static void clearPanel() {
        panel = null;
        tooltip = null;
        plugin = null;
    }

    public static void createPanel(InteractionDialogAPI dialog, boolean replace) {
        createPanel(dialog, replace, PANEL_WIDTH, PANEL_HEIGHT);
    }

    public static void createPanel(InteractionDialogAPI dialog, boolean replace, float width, float height) {
        if (!replace && tooltip != null)
            return;

        VisualPanelAPI vp = dialog.getVisualPanel();
        plugin = new InteractionDialogCustomPanelPlugin();
        panel = vp.showCustomPanel(width, height, plugin);
        tooltip = panel.createUIElement(width, height, true);

        //tooltip.setForceProcessInput(true);

        //panel.addUIElement(tooltip);	// do this later, so the tooltip correctly gets its scrollbar
    }

    /**
     * Creates a new custom panel and readds the existing plugin and tooltip to it.<br/>
     * This resets the extent of the tooltip's scrollbar, for when new elements are added to the tooltip.
     *
     * @param dialog
     */
    public static void readdTooltip(InteractionDialogAPI dialog) {
        VisualPanelAPI vp = dialog.getVisualPanel();
        panel = vp.showCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, plugin);
        addTooltipToPanel();
    }

    /**
     * Call this after all desired elements have been added to the tooltip; otherwise the scrollbar may not cover all elements.
     */
    public static void addTooltipToPanel() {
        panel.addUIElement(tooltip);
    }
}