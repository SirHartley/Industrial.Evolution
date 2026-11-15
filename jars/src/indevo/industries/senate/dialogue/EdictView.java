package indevo.industries.senate.dialogue;

import indevo.utils.ui.UiScroll;

// view state of the Edict panel: selected colony market id and Edict condition id, null picks the default
public class EdictView {

    public static final String COLONIES = "colonies";
    public static final String EDICTS = "edicts";
    public static final String DETAIL = "detail";

    public String colony = null;
    public String edict = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public EdictView() {
    }

    public EdictView(String colony, String edict) {
        this.colony = colony;
        this.edict = edict;
    }
}
