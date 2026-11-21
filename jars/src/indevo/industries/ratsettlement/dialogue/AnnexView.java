package indevo.industries.ratsettlement.dialogue;

import indevo.utils.ui.UiFilter;
import indevo.utils.ui.UiScroll;

// view state of the Engineering Annex panel; a null payment picks the first blueprint, else relic components
public class AnnexView {

    public static final String PAYMENTS = "payments";
    public static final String SHIPS = "ships";
    public static final String RELICS = "relics";
    public static final String SORT_ADDS = "Adds";
    public static final String SORT_NAME = "Name";

    public boolean ships = false;
    // the ship list was opened with no class set, it shows every class
    public boolean classes = false;
    public String payment = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();
    public final UiFilter.State filter = new UiFilter.State(SORT_ADDS);
}
