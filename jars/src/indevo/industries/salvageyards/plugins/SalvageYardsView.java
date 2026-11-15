package indevo.industries.salvageyards.plugins;

import indevo.utils.ui.UiButtons;
import indevo.utils.ui.UiScroll;

import java.util.LinkedHashSet;
import java.util.Set;

// view state of the Salvage Yards panel; null ids pick the first valid entry
public class SalvageYardsView {

    public enum Tab {
        ASSEMBLY, REGISTRY, DMODS, SMODS
    }

    public static final String ORDER = "order";
    public static final String CONTRACT = "contract";
    public static final String RUNNING = "running";
    public static final String HULLS = "hulls";
    public static final String WEAPONS = "weapons";
    public static final String WINGS = "wings";
    public static final String DETAIL = "detail";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String MODS = "mods";
    public static final String SLOTS = "slots";

    public Tab tab = Tab.ASSEMBLY;
    public String registryHull = null;
    public String origin = null;
    public String target = null;
    public final Set<String> mods = new LinkedHashSet<>();
    public final YardsOrder order = new YardsOrder();
    public final UiButtons.Confirm confirm = new UiButtons.Confirm();
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public SalvageYardsView() {
    }

    public SalvageYardsView(Tab tab) {
        this.tab = tab;
    }
}
