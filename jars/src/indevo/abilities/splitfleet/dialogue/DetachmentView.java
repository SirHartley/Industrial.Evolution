package indevo.abilities.splitfleet.dialogue;

import indevo.utils.ui.UiScroll;

// view state of the detachment panel; met is the slot of the detachment the player meets in space, 0 from the ability
public class DetachmentView {

    public int met = 0;
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    String notice = null;
    int noticeSlot = 0;

    public DetachmentView() {
    }

    public DetachmentView(int met) {
        this.met = met;
    }

    public static String listId(int slot) {
        return "slot" + slot;
    }
}
