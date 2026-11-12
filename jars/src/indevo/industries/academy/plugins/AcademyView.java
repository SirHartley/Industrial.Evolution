package indevo.industries.academy.plugins;

import indevo.utils.ui.UiScroll;

// view state of the Academy panel; null selection picks the trainee, else the first row
public class AcademyView {

    public static final String LIST = "list";
    public static final String DETAIL = "detail";

    public boolean admins = false;
    public String selected = null;
    public String rung = null;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public AcademyView() {
    }

    public AcademyView(boolean admins, String selected, String rung) {
        this.admins = admins;
        this.selected = selected;
        this.rung = rung;
    }
}
