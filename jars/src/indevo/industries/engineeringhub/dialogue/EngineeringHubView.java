package indevo.industries.engineeringhub.dialogue;

import indevo.utils.ui.UiScroll;

// view state of the Engineering Hub panel; null selections pick the first row
public class EngineeringHubView {

    public static final String LIST = "list";
    public static final String DETAIL = "detail";

    public boolean hullmods = true;
    public String hullmod = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public EngineeringHubView() {
    }

    public EngineeringHubView(String hullmod) {
        this.hullmod = hullmod;
    }
}
