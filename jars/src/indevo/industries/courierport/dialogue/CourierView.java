package indevo.industries.courierport.dialogue;

import indevo.industries.courierport.ShippingContract;
import indevo.utils.ui.UiButtons;
import indevo.utils.ui.UiScroll;

// view state of the Courier Port panel; draft is the contract in the editor, never the stored object
public class CourierView {

    public static final String LIST = "list";
    public static final String EDITOR = "editor";
    public static final String FROM = "from";
    public static final String TO = "to";

    public String selected = null;
    public boolean creating = false;
    public boolean goodsChosen = true;
    public ShippingContract draft = null;
    public String expanded = null;
    public final UiButtons.Confirm confirm = new UiButtons.Confirm();
    public final UiScroll.Offsets scroll = new UiScroll.Offsets();

    public CourierView() {
    }

    // a stored contract in the editor
    public static CourierView contract(String id) {
        CourierView view = new CourierView();
        view.selected = id;
        return view;
    }

    // a new contract at any step: null market ids leave that step open, a null scope leaves the goods open
    public static CourierView newContract(String fromMarketId, String fromSubmarketId, String toMarketId, String toSubmarketId, ShippingContract.Scope scope) {
        CourierView view = new CourierView();
        view.creating = true;
        view.draft = new ShippingContract();
        view.draft.fromMarketId = fromMarketId;
        if (fromSubmarketId != null) view.draft.fromSubmarketId = fromSubmarketId;
        view.draft.toMarketId = toMarketId;
        if (toSubmarketId != null) view.draft.toSubmarketId = toSubmarketId;
        view.goodsChosen = scope != null;
        if (scope != null) view.draft.scope = scope;
        return view;
    }

    void startNew() {
        selected = null;
        creating = true;
        goodsChosen = false;
        draft = new ShippingContract();
        expanded = null;
        confirm.clear();
        scroll.reset(EDITOR);
    }

    void select(String id) {
        if (!id.equals(selected) || creating) scroll.reset(EDITOR);
        selected = id;
        creating = false;
        goodsChosen = true;
        draft = null;
        expanded = null;
        confirm.clear();
    }
}
