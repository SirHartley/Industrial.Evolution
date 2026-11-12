package indevo.industries.derelicts.dialogue;

import indevo.utils.ui.UiScroll;

// view state of the relic services panel; selected null shows nothing picked, FIRST picks the first row
public class AncientLabRelicView {

    public static final String LIST = "list";
    public static final String DETAIL = "detail";
    public static final String FIRST = "first";

    public boolean ships = false;
    public String selected = null;
    public String message = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public AncientLabRelicView() {
    }

    public AncientLabRelicView(boolean ships, String selected, String message) {
        this.ships = ships;
        this.selected = selected;
        this.message = message;
    }
}
