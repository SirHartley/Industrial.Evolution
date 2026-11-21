package indevo.industries.ratsettlement.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.industries.engineeringhub.ReverseEngineering;
import indevo.industries.ratsettlement.facility.EngineeringAnnex;
import indevo.industries.ratsettlement.facility.EngineeringAnnex.Payment;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;

import static indevo.utils.helper.StringHelper.getDayOrDays;

// engineering annex panel, two phases: collecting (class card, in work, drop off) and at 100% (blueprint to overwrite, Finish)
public class AnnexPanelDelegate extends BaseCustomDialogDelegate {

    static final float BOARD_W = 540f;
    static final float BOARD_H = 371f;
    static final float BOARD_H_READY = 423f;
    static final float TOP = 30f;
    static final float CARD_H = 80f;
    static final float HEAD_GAP = 20f;
    static final float ROW_GAP = 4f;
    static final float GAP = 8f;
    static final float SECTION = 16f;
    static final float SECOND_HEAD = TOP + CARD_H + HEAD_GAP;
    static final float SECOND_Y = SECOND_HEAD + Ui.HEADING_H + ROW_GAP;
    static final float WORK_H = 50f;
    static final float DROP_HEAD = SECOND_Y + WORK_H + HEAD_GAP;
    static final float DROP_Y = DROP_HEAD + Ui.HEADING_H + ROW_GAP;
    static final float DROP_H = 66f;
    static final float CELL = 36f;
    static final float CELL_GAP = 4f;
    static final float PAY_ROW = 32f;
    static final int PAY_ROWS = 3;
    static final float BUTTON_H = 24f;
    static final float AMOUNT_TIP_W = 220f;

    final EngineeringAnnex annex;
    final AnnexView view;
    final MarketAPI market;
    private final Runnable onLeave;
    private final Runnable onReopen;
    private final float shownH;
    private CustomDialogCallback callback;
    private UiRoot root;
    private boolean open = false;
    private boolean reopening = false;

    public AnnexPanelDelegate(EngineeringAnnex annex, AnnexView view, Runnable onLeave, Runnable onReopen) {
        this.annex = annex;
        this.view = view;
        this.market = annex.getMarket();
        this.onLeave = onLeave;
        this.onReopen = onReopen;
        this.shownH = getHeight();
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public float getHeight() {
        return UiFrame.height(annex.isReady() && !view.ships ? BOARD_H_READY : BOARD_H);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        open = true;
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return new BaseCustomUIPanelPlugin() {
            @Override
            public void advance(float amount) {
                if (root != null && view.ships && view.filter.poll()) {
                    view.scroll.reset(AnnexView.SHIPS);
                    root.refresh();
                }
            }
        };
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
        closed();
    }

    @Override
    public void customDialogCancel() {
        closed();
    }

    private void closed() {
        open = false;
        if (reopening) onReopen.run();
        else onLeave.run();
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    void refresh() {
        root.refresh();
    }

    // the window height belongs to the phase, a phase change shows the panel again
    void afterChange() {
        if (Math.abs(getHeight() - shownH) < 0.5f) {
            refresh();
            return;
        }

        reopening = true;
        cancel();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        UiFrame.title(p, w, "Engineering Annex", annex.getSettlementName(), this::addHelp);
        if (view.ships) {
            new AnnexShipList(this).build(p, w, h);
            return;
        }

        addClassCard(p, w);
        if (annex.isReady()) addPayment(p, w);
        else {
            addInWork(p, w);
            addDropOff(p, w);
        }
    }

    private void addHelp(TooltipMakerAPI t) {
        Color hl = Misc.getHighlightColor();
        t.addPara("Works on one hull class at a time. The first ship put in sets the class. Blueprint data is shared with your Engineering Hubs.", 0f);
        t.addPara("One ship is taken apart at a time, over %s (%s for capital ships). Each adds %s (%s for capital ships), less with D-mods.", 10f, hl,
                ReverseEngineering.getBaseDays(HullSize.CRUISER) + " days", ReverseEngineering.getBaseDays(HullSize.CAPITAL_SHIP) + "",
                pct(ReverseEngineering.getCleanValue(HullSize.CRUISER)), pct(ReverseEngineering.getCleanValue(HullSize.CAPITAL_SHIP)));
        t.addPara("At %s, overwrite a ship blueprint of the same hull size or pay " + RelicHelper.getName() + ". Then the next class can start.", 10f, hl, "100%");
    }

    private void addClassCard(CustomPanelAPI p, float w) {
        ShipHullSpecAPI spec = annex.getHullSpec();
        if (spec == null) {
            CustomPanelAPI box = Ui.dashbox(p, 0f, TOP, w, CARD_H);
            Ui.text(box, 0f, 31f, w, "No hull class", Ui.gray(), Ui.CENTER);
            return;
        }

        boolean ready = annex.isReady();
        float right = w - 8f;
        float tx = 82f;
        float sw = 120f;
        float nw = right - sw - 8f - tx;
        float bw = 22f;

        CustomPanelAPI card = Ui.card(p, 0f, TOP, w, CARD_H);
        UiImages.hull(card, 8f, 8f, 64f, spec, true, false);
        nameText(card, tx, 9f, nw, spec.getHullNameWithDashClass(), Ui.text());
        Ui.text(card, tx, 28f, nw, Misc.getHullSizeStr(spec.getHullSize()), Ui.gray());

        if (ready) Ui.text(card, right - sw, 9f, sw, "Ready to finish", Ui.pos(), Ui.RIGHT);
        else {
            boolean working = annex.getInWork() != null;
            Ui.text(card, right - sw, 9f, sw, working ? "In work" : "Waiting for ships", working ? Ui.base() : Ui.gray(), Ui.RIGHT);
            int need = annex.getShipsNeeded();
            if (need <= 0) Ui.text(card, right - sw, 28f, sw, "Enough ships", Ui.gray(), Ui.RIGHT);
            else Ui.runs(card, right - sw, 28f, sw, Ui.INS15, Ui.gray(), Ui.RIGHT, need + (need == 1 ? " more ship" : " more ships"),
                    new String[]{need + ""}, new Color[]{Ui.hl()});
        }

        float progress = annex.getProgress();
        CustomPanelAPI bar = UiData.bar(card, tx, 50f, right - bw - 8f - tx, 20f, progress, ready ? Ui.GREEN : Ui.barBlue(), pct(progress, false));
        Ui.tooltip(card, bar, 280f, TooltipLocation.BELOW, this::addDataTooltip);

        ButtonAPI abandon = UiButtons.button(card, right - bw, 49f, bw, bw, "X", UiButtons.Kind.RED, CutStyle.ALL, () -> askAbandon(spec));
        Ui.tooltip(card, abandon, Ui.TOOLTIP_W, TooltipLocation.BELOW, this::addAbandonTooltip);
    }

    private void addDataTooltip(TooltipMakerAPI t) {
        List<FleetMemberAPI> drop = annex.getDropOff();
        float work = annex.getInWorkValue();
        float dropValue = annex.getDropOffValue();
        ShipHullSpecAPI spec = annex.getHullSpec();

        t.addPara("Blueprint data", Ui.hl(), 0f);
        t.beginGrid(268f, 1);
        t.addToGrid(0, 0, "Done", pct(annex.getProgress(), false), Ui.hl());
        t.addToGrid(0, 1, "In work", pct(work), work > 0f ? Ui.pos() : Ui.neg());
        t.addToGrid(0, 2, "Drop off, " + drop.size() + (drop.size() == 1 ? " ship" : " ships"), pct(dropValue), dropValue > 0f ? Ui.pos() : Ui.neg());
        t.addGrid(10f);
        if (spec != null) t.addPara("A ship without D-mods adds %s.", 10f, Ui.hl(), pct(ReverseEngineering.getCleanValue(spec.getHullSize()), false));
    }

    private void addAbandonTooltip(TooltipMakerAPI t) {
        String lost = EngineeringAnnex.ownsHub() ? "The ship in work is lost. The blueprint data stays with your Engineering Hub."
                : "The ship in work and the blueprint data are lost.";
        t.addPara("Abandon", Ui.hl(), 0f);
        t.addPara("Clears the annex for another hull class.", 10f);
        t.addPara(lost + " Ships in the drop off go back to storage.", 10f);
    }

    private void askAbandon(ShipHullSpecAPI spec) {
        String name = spec.getHullNameWithDashClass();
        boolean hub = EngineeringAnnex.ownsHub();
        String data = pct(annex.getProgress(), false);

        new UiConfirm("Abandon", t -> {
            t.addPara("Abandon the %s?", 0f, Ui.hl(), name);
            if (hub) t.addPara("The ship in work is lost. The blueprint data stays with your Engineering Hub.", 10f);
            else t.addPara("The ship in work and %s blueprint data are lost.", 10f, Ui.hl(), data);
        }, () -> {
            annex.abandon();
            view.payment = null;
            view.scroll.reset(AnnexView.PAYMENTS);
        }).show(this::afterChange);
    }

    private void addInWork(CustomPanelAPI p, float w) {
        Ui.heading(p, 0f, SECOND_HEAD, w, "In work", Ui.LEFT);
        CustomPanelAPI card = Ui.card(p, 0f, SECOND_Y, w, WORK_H);

        ShipVariantAPI variant = annex.getInWork();
        if (variant == null) {
            Ui.dashbox(card, 3f, 3f, 44f, 44f);
            Ui.text(card, 56f, 16f, w - 64f, "No ship in work", Ui.gray());
            return;
        }

        float aw = 140f;
        float bw = 130f;
        float ax = w - 8f - aw;
        float bx = ax - 8f - bw;
        float tw = bx - 56f - 12f;

        FleetMemberAPI shown = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        if (annex.getInWorkName() != null) shown.setShipName(annex.getInWorkName());
        UiImages.shipIcon(card, 3f, 3f, 44f, shown);
        nameText(card, 56f, 7f, tw, shown.getShipName(), Ui.text());

        int dMods = ReverseEngineering.getNumNonBuiltInDMods(variant);
        float value = annex.getInWorkValue();
        String adds = pct(value);
        LabelAPI line = Ui.runs(card, 56f, 26f, tw, Ui.INS15, Ui.gray(), Ui.LEFT, (dMods > 0 ? dModWords(dMods) + ", " : "") + "adds " + adds,
                new String[]{adds}, new Color[]{value > 0f ? Ui.pos() : Ui.neg()});
        Ui.tooltip(card, (UIComponentAPI) line, AMOUNT_TIP_W, TooltipLocation.BELOW, t -> addAmountTooltip(t, shown.getShipName(), variant));

        int total = annex.getSpeedUpDaysTotal();
        int left = annex.getSpeedUpDaysLeft();
        float fraction = total <= 0 ? 1f : (total - left) / (float) total;
        UiData.bar(card, bx, 6f, bw, 20f, fraction, Ui.barBlue(), left + " " + getDayOrDays(left) + " left");

        String reason = null;
        float cooldown = annex.getSpeedUpCooldownLeft();
        if (cooldown > 0f) {
            int days = (int) Math.ceil(cooldown);
            reason = "Unavailable for another " + days + " " + getDayOrDays(days) + ".";
        } else if (!annex.canSpeedUp()) reason = "Too few days left.";

        int cost = annex.getSpeedUpCost();
        ButtonAPI accelerate = UiButtons.relic(card, ax, 6f, aw, BUTTON_H, "Accelerate", cost, RelicHelper.canAfford(market, cost), CutStyle.ALL, reason != null ? null : () -> {
            if (RelicSpeedUpDialog.apply(annex)) refresh();
        });
        Ui.tooltip(card, accelerate, Ui.TOOLTIP_W, TooltipLocation.BELOW, t -> RelicSpeedUpDialog.addTooltip(t, annex));
        if (reason != null) Ui.text(card, bx, 30f, w - 8f - bx, reason, Ui.neg(), Ui.RIGHT);
    }

    private void addDropOff(CustomPanelAPI p, float w) {
        List<FleetMemberAPI> drop = annex.getDropOff();
        String count = drop.size() + (drop.size() == 1 ? " ship" : " ships");
        Ui.heading(p, 0f, DROP_HEAD, w, "Drop off", drop.isEmpty() ? null : count);

        float bw = 110f;
        float tw = w - bw - 28f;
        CustomPanelAPI strip = Ui.card(p, 0f, DROP_Y, w, DROP_H);
        if (!drop.isEmpty()) {
            UiImages.iconrow(strip, 8f, 7f, tw, drop, CELL, CELL, CELL_GAP, 1, this::dropCell,
                    ship -> ship.getShipName() + ", " + ship.getHullSpec().getHullNameWithDashClass());
        } else if (annex.getHullId() != null) Ui.text(strip, 8f, 24f, tw, "No ships", Ui.gray());
        else {
            Ui.text(strip, 8f, 15f, tw, "No ships", Ui.gray());
            Ui.text(strip, 8f, 34f, tw, "The first ship sets the hull class.", Ui.gray());
        }

        UiButtons.button(strip, w - 8f - bw, Math.round((DROP_H - BUTTON_H) / 2f), bw, BUTTON_H, drop.isEmpty() ? "Add ships..." : "Change...",
                UiButtons.Kind.STD, CutStyle.ALL, this::openShips);
    }

    private void dropCell(CustomPanelAPI c, float x, float y, float cw, float ch, FleetMemberAPI ship) {
        UiImages.shipIcon(c, x, y, cw, ship);
        float value = EngineeringAnnex.getValue(ship);
        LabelAPI amount = Ui.text(c, x - 2f, y + ch + 2f, cw + 4f, pct(value), value > 0f ? Ui.pos() : Ui.neg(), Ui.CENTER);
        Ui.tooltip(c, (UIComponentAPI) amount, AMOUNT_TIP_W, TooltipLocation.ABOVE, t -> addAmountTooltip(t, ship.getShipName(), ship.getVariant()));
    }

    private void openShips() {
        view.ships = true;
        view.classes = annex.getHullId() == null;
        view.scroll.reset(AnnexView.SHIPS);
        refresh();
    }

    void closeShips() {
        view.ships = false;
        afterChange();
    }

    // the hull size's share, what the d-mods take off, the result
    static void addAmountTooltip(TooltipMakerAPI t, String name, ShipVariantAPI variant) {
        float base = ReverseEngineering.getCleanValue(variant.getHullSize());
        float value = ReverseEngineering.getValue(variant, 0);
        int dMods = ReverseEngineering.getNumNonBuiltInDMods(variant);

        t.addPara(name, Ui.hl(), 0f);
        t.beginGrid(208f, 1);
        int row = 0;
        t.addToGrid(0, row++, Misc.getHullSizeStr(variant.getHullSize()), pct(base), Ui.hl());
        if (dMods > 0) t.addToGrid(0, row++, dModWords(dMods), "-" + Math.round((base - value) * 100f) + "%", Ui.neg());
        t.addToGrid(0, row, "Adds", pct(value), value > 0f ? Ui.pos() : Ui.neg());
        t.addGrid(10f);
    }

    private void addPayment(CustomPanelAPI p, float w) {
        String hullId = annex.getHullId();
        ShipHullSpecAPI spec = annex.getHullSpec();
        boolean blueprint = Items.SHIP_BP.equals(ReverseEngineering.getPaymentItemId(hullId));
        String size = Misc.getHullSizeStr(spec.getHullSize()).toLowerCase();
        Ui.heading(p, 0f, SECOND_HEAD, w, blueprint ? "Blueprint to overwrite" : "Retrofit item to overwrite", blueprint ? size + " blueprints" : "retrofit items");

        List<Payment> payments = annex.getPayments();
        int cost = annex.getRelicCost();
        int stock = RelicHelper.getAvailable(market);
        boolean relicsOk = stock >= cost;
        Payment picked = resolvePayment(payments);
        boolean relics = AnnexView.RELICS.equals(view.payment);

        float listH = PAY_ROWS * (PAY_ROW + ROW_GAP) - ROW_GAP;
        float rw = w;
        if (payments.isEmpty()) {
            Ui.text(p, 8f, SECOND_Y + 7f, w - 16f, blueprint ? "No " + size + " blueprint in storage or fleet cargo." : "No retrofit item in storage or fleet cargo.", Ui.gray());
        } else if (payments.size() > PAY_ROWS) {
            UiScroll.Column list = UiScroll.list(p, 0f, SECOND_Y, w, listH, view.scroll, AnnexView.PAYMENTS);
            rw = list.width();
            for (int i = 0; i < payments.size(); i++) payRow(list.block(PAY_ROW, i == 0 ? 0f : ROW_GAP), 0f, rw, payments.get(i), picked);
            list.end();
        } else {
            for (int i = 0; i < payments.size(); i++) payRow(p, SECOND_Y + i * (PAY_ROW + ROW_GAP), rw, payments.get(i), picked);
        }

        float ry = SECOND_Y + listH + GAP;
        CustomPanelAPI relicRow = UiButtons.row(p, 0f, ry, rw, PAY_ROW, relics, true, null, () -> pick(AnnexView.RELICS));
        addRadio(relicRow, relics);
        UiImages.icon(relicRow, 32f, 2f, 28f, UiImages.relicSprite(), RelicHelper.getName());
        Ui.text(relicRow, 68f, 7f, rw - 68f - 130f, RelicHelper.getName() + " instead", relics ? Ui.bright() : Ui.text());
        String amount = Ui.num(cost);
        Ui.runs(relicRow, rw - 8f - 120f, 7f, 120f, Ui.INS15, Ui.gray(), Ui.RIGHT, amount + " (" + Ui.num(stock) + ")", new String[]{amount}, new Color[]{relicsOk ? Ui.hl() : Ui.neg()});
        Ui.tooltip(p, relicRow, RelicHelper.getName() + " instead", "Taken from settlement storage first, then from your cargo.");

        String reason = null;
        if (payments.isEmpty() && !relicsOk) reason = blueprint ? "No blueprint and not enough " + RelicHelper.getName() + "." : "No retrofit item and not enough " + RelicHelper.getName() + ".";
        else if (relics && !relicsOk) reason = "Not enough " + RelicHelper.getName() + ".";

        String uses;
        if (relics) uses = "Uses " + Ui.num(cost) + " " + RelicHelper.getName();
        else if (picked != null) uses = "Uses the " + writtenName(picked) + (blueprint ? " blueprint" : " retrofit item");
        else uses = "";

        float fy = ry + PAY_ROW + SECTION;
        float tw = w - 56f - 110f - 20f;
        CustomPanelAPI strip = Ui.card(p, 0f, fy, w, WORK_H);
        String outputId = ReverseEngineering.getPaymentItemId(hullId);
        String sprite = getItemSprite(outputId);
        if (sprite != null) UiImages.icon(strip, 5f, 5f, 40f, sprite, getItemName(outputId));
        nameText(strip, 56f, 7f, tw, spec.getHullNameWithDashClass() + (blueprint ? " blueprint" : " retrofit template"), Ui.text());
        if (reason != null) Ui.text(strip, 56f, 26f, tw, Ui.fit(reason, Ui.INS15, tw), Ui.neg());
        else Ui.text(strip, 56f, 26f, tw, Ui.fit(uses, Ui.INS15, tw), Ui.gray());

        Payment pay = relics ? null : picked;
        UiButtons.button(strip, w - 8f - 110f, Math.round((WORK_H - BUTTON_H) / 2f), 110f, BUTTON_H, "Finish", reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.GREEN,
                CutStyle.ALL, reason != null ? null : () -> finish(pay));
    }

    private void payRow(CustomPanelAPI p, float y, float rw, Payment payment, Payment picked) {
        boolean on = payment == picked;
        CustomPanelAPI row = UiButtons.row(p, 0f, y, rw, PAY_ROW, on, true, null, () -> pick(getKey(payment)));
        addRadio(row, on);
        String sprite = getItemSprite(payment.item().getId());
        if (sprite != null) UiImages.icon(row, 32f, 2f, 28f, sprite, getItemName(payment.item().getId()));
        ShipHullSpecAPI written = ReverseEngineering.getHullSpecOrNull(payment.item().getData());
        if (written != null) UiImages.hull(row, 64f, 2f, 28f, written, false, false);

        String where = (payment.fleet() ? "Fleet cargo" : "Storage") + (payment.count() > 1 ? ", " + Ui.num(payment.count()) : "");
        float ww = Ui.width(where, Ui.INS15) + 4f;
        float nw = rw - 100f - ww - 20f;
        nameText(row, 100f, 7f, nw, writtenName(payment), on ? Ui.bright() : Ui.text());
        Ui.text(row, rw - 8f - ww, 7f, ww, where, Ui.gray(), Ui.RIGHT);
    }

    // keeps a valid pick in view.payment: the first blueprint, else relic components
    private Payment resolvePayment(List<Payment> payments) {
        if (AnnexView.RELICS.equals(view.payment)) return null;
        for (Payment payment : payments) {
            if (getKey(payment).equals(view.payment)) return payment;
        }

        if (!payments.isEmpty()) {
            view.payment = getKey(payments.get(0));
            return payments.get(0);
        }

        view.payment = AnnexView.RELICS;
        return null;
    }

    private void pick(String key) {
        view.payment = key;
        refresh();
    }

    private void finish(Payment payment) {
        if (!annex.finish(payment)) return;

        view.payment = null;
        view.scroll.reset(AnnexView.PAYMENTS);
        afterChange();
    }

    private static String getKey(Payment payment) {
        return (payment.fleet() ? "fleet|" : "storage|") + payment.item().getId() + "|" + payment.item().getData();
    }

    private static String writtenName(Payment payment) {
        ShipHullSpecAPI written = ReverseEngineering.getHullSpecOrNull(payment.item().getData());
        return written != null ? written.getHullNameWithDashClass() : "Unknown hull";
    }

    static void addRadio(CustomPanelAPI row, boolean on) {
        Ui.box(row, 10f, 10f, 12f, 12f, Misc.scaleAlpha(Ui.base(), 0.8f), Misc.setAlpha(Color.BLACK, 120));
        if (on) Ui.box(row, 13f, 13f, 6f, 6f, null, Ui.bright());
    }

    // a cut name keeps the full name as tooltip
    static void nameText(CustomPanelAPI p, float x, float y, float w, String name, Color c) {
        String shown = Ui.fit(name, Ui.INS15, w);
        LabelAPI label = Ui.text(p, x, y, w, shown, c);
        if (!shown.equals(name)) Ui.tooltip(p, label, null, name);
    }

    static String pct(float value) {
        return "+" + pct(value, false);
    }

    static String pct(float value, boolean sign) {
        return (sign ? "+" : "") + Math.round(Math.min(value, 1f) * 100f) + "%";
    }

    static String dModWords(int n) {
        return n + (n == 1 ? " D-mod" : " D-mods");
    }

    // retrofit item specs only exist with their mod loaded
    private static String getItemSprite(String id) {
        try {
            return Global.getSettings().getSpecialItemSpec(id).getIconName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String getItemName(String id) {
        try {
            return Global.getSettings().getSpecialItemSpec(id).getName();
        } catch (RuntimeException e) {
            return id;
        }
    }
}
