package indevo.industries.engineeringhub.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.industries.engineeringhub.memory.RecoveredHullmods;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EngineeringHubPanelDelegate extends BaseCustomDialogDelegate {

    public static final String LORE = "The dismantled ship's control modules still contain its modification firmware. Domain decoding hardware can recover a specification your engineers can use.";

    public static final float BOARD_W = 1004f;
    public static final float BOARD_H = 640f;
    static final float DETAIL_W = 384f;
    static final float COLUMN_GAP = 20f;
    static final float BODY_Y = 64f;
    static final float TAB_W = 180f;
    static final float ROW_H = 48f;
    static final float ROW_GAP = 4f;
    static final float ICON = 40f;
    static final float HULL = 34f;
    static final float COST_ICON = 20f;
    static final float COST_W = 86f;
    static final float DETAIL_ICON = 64f;
    static final float OP_H = 40f;
    static final float BUTTON_H = 28f;
    static final float GAP = 8f;
    static final HullSize[] SIZES = {HullSize.FRIGATE, HullSize.DESTROYER, HullSize.CRUISER, HullSize.CAPITAL_SHIP};

    final EngineeringHub hub;
    final MarketAPI market;
    final EngineeringHubView view;
    private final Runnable onLeave;
    private UiRoot root;

    record Hullmod(HullModSpecAPI spec, ShipHullSpecAPI source) {
    }

    public EngineeringHubPanelDelegate(EngineeringHub hub, EngineeringHubView view, Runnable onLeave) {
        this.hub = hub;
        this.market = hub.getMarket();
        this.view = view;
        this.onLeave = onLeave;
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    public static void show(DialogCreatorUI ui, EngineeringHub hub, EngineeringHubView view) {
        ui.showDialog(getWidth(), getHeight(), new EngineeringHubPanelDelegate(hub, view, null));
    }

    // opens the panel for the market's hub in the given view, for code and tests
    public static boolean show(MarketAPI market, EngineeringHubView view) {
        if (!(market.getIndustry(Ids.ENGHUB) instanceof EngineeringHub hub)) return false;
        return UiHost.show(market.getPrimaryEntity(), dialog -> dialog.showCustomDialog(getWidth(), getHeight(), new EngineeringHubPanelDelegate(hub, view, dialog::dismiss)));
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
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
        if (onLeave != null) onLeave.run();
    }

    @Override
    public void customDialogCancel() {
        if (onLeave != null) onLeave.run();
    }

    void refresh() {
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        List<Hullmod> hullmods = getHullmods();
        addHeader(p, w, hullmods.size());
        if (view.hullmods) addHullmodTab(p, w, h, hullmods);
        else new ReverseEngineeringTab(this).build(p, w, h);
    }

    private void addHeader(CustomPanelAPI p, float w, int recovered) {
        UiFrame.title(p, w, hub.getCurrentName(), market.getName(), this::addHelp);
        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, TAB_W, UiFrame.HEADER_H, new String[]{"Reverse engineering", "Hullmod data (" + recovered + ")"},
                view.hullmods ? 1 : 0, null, i -> switchTab(i == 1));
        UiFrame.counter(p, w, UiFrame.HEADER_Y, RelicHelper.getName(), UiImages.relicSprite(), Ui.num(RelicHelper.getAvailable(market)));
    }

    private void switchTab(boolean hullmods) {
        if (view.hullmods != hullmods) {
            view.hullmods = hullmods;
            view.scroll.reset(EngineeringHubView.LIST);
            view.scroll.reset(EngineeringHubView.DETAIL);
            view.scroll.reset(EngineeringHubView.PAYMENTS);
        }

        refresh();
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        if (!view.hullmods) {
            ReverseEngineeringTab.addHelp(tooltip, hub);
            return;
        }

        Color hl = Misc.getHighlightColor();
        tooltip.addPara("Ships reverse engineered here leave data on the hullmods they had installed that your faction does not know.", 0f);
        tooltip.addPara("Learning one costs at least %s %s, more for valuable hullmods.", 10f, hl, RecoveredHullmods.MIN_COST + "", RelicHelper.getName());
        tooltip.addPara("%s are taken from storage first, then from your cargo.", 10f, hl, RelicHelper.getName());
    }

    private void addHullmodTab(CustomPanelAPI p, float w, float h, List<Hullmod> hullmods) {
        Hullmod selected = null;
        for (Hullmod hullmod : hullmods) {
            if (hullmod.spec().getId().equals(view.hullmod)) selected = hullmod;
        }

        if (selected == null && !hullmods.isEmpty()) selected = hullmods.get(0);
        view.hullmod = selected != null ? selected.spec().getId() : null;

        float lw = w - DETAIL_W - COLUMN_GAP;
        float rx = lw + COLUMN_GAP;
        addHullmodList(p, lw, h, hullmods);
        Ui.vrule(p, rx - 10f, BODY_Y, h - BODY_Y);
        addHullmodDetail(p, rx, w - rx, h - BODY_Y, selected);
    }

    private void addHullmodList(CustomPanelAPI p, float lw, float h, List<Hullmod> hullmods) {
        Ui.heading(p, 0f, BODY_Y, lw, "Recovered hullmod data", Ui.LEFT);
        if (hullmods.isEmpty()) {
            Ui.text(p, 8f, BODY_Y + Ui.HEADING_H + 6f, lw - 16f, "No recovered hullmod data.", Ui.gray());
            return;
        }

        float rw = lw - UiScroll.GUTTER;
        float hx = rw - 280f;
        float cx = rw - 8f - COST_W;
        Ui.thead(p, 0f, BODY_Y + 20f, lw, new String[]{"Hullmod", "Recovered from", "Cost"}, new float[]{52f, hx, rw - 100f},
                new float[]{200f, 160f, 92f}, new Alignment[]{Ui.LEFT, Ui.LEFT, Ui.RIGHT});

        int available = RelicHelper.getAvailable(market);
        UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y + 42f, lw, h - BODY_Y - 42f, view.scroll, EngineeringHubView.LIST);
        for (int i = 0; i < hullmods.size(); i++) {
            Hullmod hullmod = hullmods.get(i);
            HullModSpecAPI spec = hullmod.spec();
            boolean selected = spec.getId().equals(view.hullmod);
            CustomPanelAPI row = UiButtons.row(list.block(ROW_H, i == 0 ? 0f : ROW_GAP), 0f, 0f, rw, ROW_H, selected, true, null, () -> selectHullmod(spec.getId()));

            UiImages.hullmod(row, 3f, 4f, ICON, spec.getId());
            Ui.text(row, 52f, 5f, hx - 60f, spec.getDisplayName(), selected ? Ui.bright() : Ui.text());
            Ui.text(row, 52f, 23f, hx - 60f, getOpLine(spec), Ui.gray());
            if (hullmod.source() != null) {
                UiImages.hull(row, hx, 7f, HULL, hullmod.source(), true, false);
                Ui.text(row, hx + HULL + 8f, 13f, cx - hx - HULL - 14f, hullmod.source().getHullNameWithDashClass(), Ui.gray());
            } else Ui.text(row, hx, 13f, cx - hx - 6f, "Unknown hull", Ui.gray());

            int cost = RecoveredHullmods.getCost(spec);
            addCost(row, rw - 8f, 13f, cost, cost <= available);
        }

        list.end();
    }

    private void selectHullmod(String id) {
        if (!id.equals(view.hullmod)) {
            view.hullmod = id;
            view.scroll.reset(EngineeringHubView.DETAIL);
        }

        refresh();
    }

    private void addHullmodDetail(CustomPanelAPI p, float x, float w, float h, Hullmod hullmod) {
        UiScroll.Column column = UiScroll.list(p, x, BODY_Y, w, h, view.scroll, EngineeringHubView.DETAIL);
        float cw = column.width();

        if (hullmod == null) {
            Ui.heading(column.block(Ui.HEADING_H, 0f), 0f, 0f, cw, "Recovered hullmod data");
            CustomPanelAPI b = column.block(Ui.LINE_H, GAP);
            Ui.text(b, 0f, 0f, cw, "No hullmod to show.", Ui.gray());
            column.end();
            return;
        }

        HullModSpecAPI spec = hullmod.spec();
        Ui.heading(column.block(Ui.HEADING_H, 0f), 0f, 0f, cw, spec.getDisplayName());

        String description = getDescription(spec, hullmod.source() != null ? hullmod.source().getHullSize() : HullSize.CRUISER);
        float tw = cw - DETAIL_ICON - 12f;
        CustomPanelAPI d = column.block(Math.max(DETAIL_ICON, Ui.wrapHeight(description, Ui.INS15, tw) + 2f), GAP);
        UiImages.hullmod(d, 0f, 0f, DETAIL_ICON, spec.getId());
        Ui.wrap(d, DETAIL_ICON + 12f, 2f, tw, description, Ui.text());

        Ui.text(column.block(Ui.LINE_H, 10f), 0f, 0f, cw, "Ordnance points", Ui.gray());
        CustomPanelAPI op = column.block(OP_H, 0f);
        float cell = cw / SIZES.length;
        for (int i = 0; i < SIZES.length; i++) {
            float cx = Math.round(i * cell);
            Ui.box(op, cx, 0f, cell - 4f, OP_H, Misc.scaleAlpha(Ui.base(), 0.18f), Misc.setAlpha(Color.BLACK, 90));
            Ui.text(op, cx, 3f, cell - 4f, Misc.getHullSizeStr(SIZES[i]), Ui.gray(), Ui.CENTER);
            Ui.text(op, cx, 20f, cell - 4f, spec.getCostFor(SIZES[i]) + "", Ui.hl(), Ui.CENTER);
        }

        CustomPanelAPI from = column.block(HULL, 10f);
        Ui.text(from, 0f, 8f, 140f, "Recovered from", Ui.gray());
        String source = hullmod.source() != null ? hullmod.source().getHullNameWithDashClass() : "Unknown hull";
        float nw = Math.min(Ui.width(source, Ui.INS15) + 2f, cw - 140f - HULL - 16f);
        Ui.text(from, cw - nw, 8f, nw, source, Ui.text(), Ui.RIGHT);
        if (hullmod.source() != null) UiImages.hull(from, cw - nw - 8f - HULL, 0f, HULL, hullmod.source(), true, false);

        int cost = RecoveredHullmods.getCost(spec);
        int available = RelicHelper.getAvailable(market);
        Ui.heading(column.block(Ui.HEADING_H, 10f), 0f, 0f, cw, "Cost");
        UiData.costrow(column.block(UiData.COST_ROW_H, 6f), 0f, 0f, cw, RelicHelper.getName(), UiImages.relicSprite(), Ui.num(cost), Ui.num(available), cost <= available);

        String reason = cost <= available ? null : "Not enough " + RelicHelper.getName() + ".";
        CustomPanelAPI b = column.block(UiButtons.guardHeight(cw, BUTTON_H, reason), GAP);
        UiButtons.guarded(b, 0f, 0f, cw, BUTTON_H, "Learn", UiButtons.Kind.STD, reason, () -> {
            RecoveredHullmods.learn(market, spec.getId());
            view.hullmod = null;
            view.scroll.reset(EngineeringHubView.DETAIL);
            refresh();
        });

        CustomPanelAPI lore = column.block(Ui.wrapHeight(LORE, Ui.INS15, cw), 0f);
        Ui.wrap(lore, 0f, 0f, cw, LORE, Ui.gray());
        column.end();
    }

    private static String getOpLine(HullModSpecAPI spec) {
        StringBuilder s = new StringBuilder();
        for (HullSize size : SIZES) {
            if (s.length() > 0) s.append(" / ");
            s.append(spec.getCostFor(size));
        }

        return s.append(" OP").toString();
    }

    // getDescription fills the params from the effect with a null ship, some mod effects throw there
    private static String getDescription(HullModSpecAPI spec, HullSize size) {
        try {
            return spec.getDescription(size);
        } catch (RuntimeException e) {
            return "";
        }
    }

    // relic icon and amount, right aligned at right
    static void addCost(CustomPanelAPI row, float right, float y, int cost, boolean ok) {
        String amount = Ui.num(cost);
        float aw = Math.min(Ui.width(amount, Ui.INS15) + 2f, COST_W - COST_ICON - 4f);
        Ui.text(row, right - aw, y, aw, amount, ok ? Ui.hl() : Ui.neg(), Ui.RIGHT);
        UiImages.img(row, right - aw - 4f - COST_ICON, y - 2f, COST_ICON, COST_ICON, UiImages.relicSprite());
    }

    private List<Hullmod> getHullmods() {
        List<Hullmod> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : RecoveredHullmods.getLearnable(market).entrySet()) {
            result.add(new Hullmod(Global.getSettings().getHullModSpec(entry.getKey()), getHullSpec(entry.getValue())));
        }

        return result;
    }

    // getHullSpec throws on an id whose mod is gone
    static ShipHullSpecAPI getHullSpec(String id) {
        if (id == null) return null;
        try {
            return Global.getSettings().getHullSpec(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
