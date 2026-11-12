package indevo.industries.courierport.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;
import indevo.industries.courierport.ShippingCostCalculator;
import indevo.industries.courierport.ShippingTargetHelper;
import indevo.industries.courierport.ShippingTooltipHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;

public class CourierPanelDelegate extends BaseCustomDialogDelegate {

    public static final float BOARD_W = 1104f;
    public static final float BOARD_H = 709f;
    static final float BODY_Y = 64f;
    static final float LIST_W = 360f;
    static final float EDITOR_X = 372f;
    static final float ROW_H = 70f;
    static final float ROW_GAP = 4f;
    static final float FIRST_GAP = 6f;
    static final float EMPTY_H = 24f;
    static final float BUTTON_H = 28f;
    static final float ROUTE_ICON = 14f;
    static final float COST_W = 110f;
    static final float NEXT_W = 130f;

    private final CourierView view;
    private final CourierPortDialoguePlugin host;
    private CustomDialogCallback callback;
    private UiRoot root;
    private boolean open = false;
    private boolean picking = false;

    public CourierPanelDelegate(CourierView view, CourierPortDialoguePlugin host) {
        this.view = view;
        this.host = host;
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        open = true;
        picking = false;
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    @Override
    public void customDialogConfirm() {
        open = false;
        host.leave();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        if (!picking) host.leave();
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    void closeForPicker() {
        view.scroll.capture();
        picking = true;
        callback.dismissCustomDialog(1);
    }

    void refresh() {
        root.refresh();
    }

    CourierPortDialoguePlugin getHost() {
        return host;
    }

    private void build(CustomPanelAPI p, float w, float h) {
        ShippingContract stored = resolve();

        addHeader(p, w);
        addList(p, h - BODY_Y - BUTTON_H - 8f);
        UiButtons.button(p, 0f, h - BUTTON_H, LIST_W, BUTTON_H, "New contract", UiButtons.Kind.STD, () -> {
            view.startNew();
            refresh();
        });

        Ui.vrule(p, EDITOR_X - 6f, BODY_Y, h - BODY_Y);
        new CourierEditor(this, view, stored).build(p, EDITOR_X, BODY_Y, w - EDITOR_X, h - BODY_Y);
    }

    // returns the stored contract behind the draft, null while a new contract is edited
    private ShippingContract resolve() {
        List<ShippingContract> contracts = ShippingContractMemory.getContractList();
        if (view.creating) {
            if (view.draft == null) view.startNew();
            return null;
        }

        ShippingContract stored = view.selected != null ? ShippingContractMemory.getContract(view.selected) : null;
        if (stored == null && !contracts.isEmpty()) stored = contracts.get(0);
        if (stored == null) {
            view.startNew();
            return null;
        }

        view.selected = stored.getId();
        if (view.draft == null || !view.draft.getId().equals(stored.getId())) {
            view.draft = stored.getCopy();
            view.draft.elapsedDays = stored.elapsedDays;
        }

        return stored;
    }

    private void addHeader(CustomPanelAPI p, float w) {
        UiFrame.title(p, w, "Courier Port", null, this::addHelp);
        float right = UiFrame.credits(p, w, UiFrame.HEADER_Y);

        List<ShippingContract> contracts = ShippingContractMemory.getContractList();
        int active = 0;
        for (ShippingContract c : contracts) if (c.isActive && c.isValid()) active++;
        String count = contracts.size() + "";
        String text = count + (contracts.size() == 1 ? " contract, " : " contracts, ") + active + " active.";
        float textW = Ui.width(text, Ui.INS15) + 2f;

        String core = ShippingTargetHelper.getMemoryAICoreId();
        String effect = getCoreEffect(core);
        if (effect != null) {
            String name = Global.getSettings().getCommoditySpec(core).getName();
            float slotW = Ui.width(name, Ui.INS15) + 2f + 4f + UiFrame.SLOT_ICON;
            if (right - slotW > textW + UiFrame.SLOT_GAP) right = UiFrame.slot(p, right, UiFrame.HEADER_Y, UiImages.commoditySprite(core), name, name, effect);
        }

        Ui.textIn(p, 0f, UiFrame.HEADER_Y, Math.min(textW, right), UiFrame.HEADER_H, text, Ui.INS15, Ui.gray(), Ui.LEFT,
                new String[]{count, " " + active + " "}, new Color[]{Ui.hl(), Ui.hl()});
        Ui.rule(p, 0f, UiFrame.HEADER_Y + UiFrame.HEADER_H, w, Misc.scaleAlpha(Ui.base(), 0.55f));
    }

    static String getCoreEffect(String core) {
        if (Commodities.ALPHA_CORE.equals(core)) return "Cuts the cargo and ship cost by " + percent(ShippingCostCalculator.TOTAL_FEE_REDUCTION) + ".";
        if (Commodities.BETA_CORE.equals(core)) return "Cuts the distance multiplier by " + percent(ShippingCostCalculator.DISTANCE_MULT_REDUCTION) + ".";
        if (Commodities.GAMMA_CORE.equals(core)) return "Comm relays can reach the Courier Port.";
        return null;
    }

    static String percent(float mult) {
        return Math.round((1f - mult) * 100f) + "%";
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();
        float pad = 10f;

        tooltip.addPara("Contracts move goods between the storages of any two markets that are not hostile to you, as long as you own a functional Courier Port.", 0f);
        tooltip.addPara("Each run costs a base fee of %s, plus %s per unit of cargo space and %s per deployment point of each ship. Fuel and crew take %s space per unit.", pad, hl,
                Misc.getDGSCredits(Setting.COURIER_BASE_FEE.value()), Misc.getDGSCredits(Setting.COURIER_COST_PER_CARGO_SPACE.value()),
                Misc.getDGSCredits(Setting.COURIER_COST_PER_SHIP_DP.value()), "0.75");
        tooltip.addPara("Cargo and ship costs are multiplied by the distance multiplier. Items taken from the Resource Stockpiles also cost their value.", pad);
        tooltip.addPara("Contracts without selected goods are billed by load: the cost is known on the day the courier leaves.", pad);
        tooltip.addPara("An Alpha Core cuts the cargo and ship cost by %s, a Beta Core cuts the distance multiplier by %s, a Gamma Core lets comm relays reach the Courier Port.", pad, hl,
                percent(ShippingCostCalculator.TOTAL_FEE_REDUCTION), percent(ShippingCostCalculator.DISTANCE_MULT_REDUCTION));
        tooltip.addPara("Ship now sends a contract the next day, also when it is inactive.", pad);
        tooltip.addPara("Courier fleets always reach their destination. No one dares attack them, for they would never be able to ship anything, ever again.", pad, Misc.getGrayColor());
    }

    private void addList(CustomPanelAPI p, float h) {
        UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y, LIST_W, UiScroll.wholeRows(h, Ui.HEADING_H + FIRST_GAP, ROW_H, ROW_GAP), view.scroll, CourierView.LIST);
        float rw = list.width();
        List<ShippingContract> contracts = ShippingContractMemory.getContractList();

        Ui.heading(list.heading(0f), 0f, 0f, rw, "Contracts", Ui.LEFT);
        if (contracts.isEmpty()) Ui.text(list.block(EMPTY_H, FIRST_GAP), 8f, 2f, rw - 16f, "No shipping contracts.", Ui.gray());

        for (int i = 0; i < contracts.size(); i++) addRow(list.block(ROW_H, i == 0 ? FIRST_GAP : ROW_GAP), rw, contracts.get(i));
        list.end();
    }

    private void addRow(CustomPanelAPI block, float w, ShippingContract c) {
        boolean valid = c.isValid();
        boolean selected = !view.creating && c.getId().equals(view.selected);
        Color strip = !valid ? Ui.neg() : c.isActive ? Ui.GREEN : Ui.gray();
        CustomPanelAPI row = UiButtons.row(block, 0f, 0f, w, ROW_H, selected, true, strip, () -> {
            view.select(c.getId());
            refresh();
        });

        boolean byLoad = !CourierEditor.isSpecific(c.scope);
        String cost = !valid ? "" : byLoad ? "by load" : Misc.getDGSCredits(CourierEditor.getSpecificTotal(c));
        float costW = cost.isEmpty() ? 0f : Math.min(Ui.width(cost, Ui.INS15) + 2f, COST_W);
        if (costW > 0f) Ui.text(row, w - 10f - costW, 5f, costW, cost, byLoad ? Ui.gray() : Ui.hl(), Ui.RIGHT);

        Color nameColor = selected ? Ui.bright() : valid && c.isActive ? Ui.text() : Ui.gray();
        Ui.text(row, 14f, 5f, w - 24f - costW - (costW > 0f ? 8f : 0f), c.name, nameColor);
        addRoute(row, 14f, 23f, w - 24f, c);

        String next = valid ? getNextRun(c) : "";
        float nextW = next.isEmpty() ? 0f : Math.min(Ui.width(next, Ui.INS15) + 2f, NEXT_W);
        if (nextW > 0f) Ui.text(row, w - 10f - nextW, 42f, nextW, next, c.isActive ? Ui.text() : Ui.gray(), Ui.RIGHT);

        String line = valid ? getScopeText(c) + " · " + ShippingTooltipHelper.getCadenceString(c.getRecurrentDays()) : "Invalid: " + c.getInvalidReason();
        Ui.text(row, 14f, 42f, w - 24f - nextW - (nextW > 0f ? 8f : 0f), line, valid ? Ui.gray() : Ui.neg());

        if (valid && c.isActive && c.getRecurrentDays() > 0) {
            float f = Math.min(1f, c.elapsedDays / (float) c.getRecurrentDays());
            UiData.thinbar(row, 14f, 62f, w - 26f, f, Ui.barBlue());
        }
    }

    private static void addRoute(CustomPanelAPI row, float x, float y, float w, ShippingContract c) {
        MarketAPI from = c.getFromMarket();
        MarketAPI to = c.getToMarket();
        String a = from != null ? from.getName() : "Not available";
        String b = to != null ? to.getName() : "Not available";
        float arrow = 16f;
        float names = w - 2f * (ROUTE_ICON + 6f) - arrow - 4f;
        float half = Math.round(names / 2f);
        // the origin takes what the destination name leaves, at least half
        float room = Math.max(half, names - Math.min(Ui.width(b, Ui.INS15) + 2f, half));

        float cx = x;
        routeIcon(row, cx, y + 2f, from);
        cx += ROUTE_ICON + 6f;
        float aw = Math.min(Ui.width(a, Ui.INS15) + 2f, room);
        Ui.text(row, cx, y, aw, a, from != null ? Ui.text() : Ui.gray());
        cx += aw + 4f;
        Ui.text(row, cx, y, arrow - 4f, ">", Ui.hl());
        cx += arrow;
        routeIcon(row, cx, y + 2f, to);
        cx += ROUTE_ICON + 6f;
        Ui.text(row, cx, y, x + w - cx, b, to != null ? Ui.text() : Ui.gray());
    }

    private static void routeIcon(CustomPanelAPI row, float x, float y, MarketAPI market) {
        if (market == null || market.getPrimaryEntity() == null) Ui.dashbox(row, x, y, ROUTE_ICON, ROUTE_ICON);
        else UiImages.planet(row, x, y, ROUTE_ICON, market.getPrimaryEntity());
    }

    // a typed name of this length fits the row beside the widest cost, even in the widest glyph
    public static int getNameMaxChars() {
        float w = LIST_W - UiScroll.GUTTER - 24f - COST_W - 8f;
        return Math.max(8, (int) ((w - 2f) / Ui.width("W", Ui.INS15)));
    }

    static String getScopeText(ShippingContract c) {
        return switch (c.scope) {
            case EVERYTHING -> "Everything";
            case ALL_CARGO -> "All cargo";
            case ALL_WEAPONS -> "All weapons";
            case ALL_SHIPS -> "All ships";
            default -> {
                int types = c.targetCargo.getStacksCopy().size();
                int ships = c.variantList.size();
                String shipText = ships + (ships == 1 ? " ship" : " ships");
                if (types > 0 && ships > 0) yield types + (types == 1 ? " type, " : " types, ") + shipText;
                if (types > 0) yield types + (types == 1 ? " cargo type" : " cargo types");
                yield ships > 0 ? shipText : "Selected goods";
            }
        };
    }

    // same wording as the scheduler: ships once elapsedDays passes the cadence, Ship now ships inactive contracts too
    static String getNextRun(ShippingContract c) {
        if (!c.isActive && c.elapsedDays <= c.getRecurrentDays()) return "on demand";
        String days = ShippingTooltipHelper.getDaysMonthString(c.getRecurrentDays() - c.elapsedDays);
        return days.equals("today") ? days : "in " + days;
    }
}
