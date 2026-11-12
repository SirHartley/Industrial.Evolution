package indevo.industries.engineeringhub.dialogue;

import indevo.utils.ui.UiScroll;

// view state of the Engineering Hub panel; null selections pick the first row, a null payment the first that can pay
public class EngineeringHubView {

    public static final String LIST = "list";
    public static final String DETAIL = "detail";
    public static final String PAYMENTS = "payments";
    public static final String INSTALLED = "installed";
    public static final String RELICS = "relics";

    public boolean hullmods = false;
    public String hullmod = null;
    public String hull = null;
    public String payment = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public EngineeringHubView() {
    }

    public EngineeringHubView(boolean hullmods, String hullmod, String hull, String payment) {
        this.hullmods = hullmods;
        this.hullmod = hullmod;
        this.hull = hull;
        this.payment = payment;
    }
}
