package indevo.industries.ratsettlement.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.engineeringhub.ReverseEngineering;
import indevo.industries.ratsettlement.facility.EngineeringAnnex;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static indevo.industries.ratsettlement.dialogue.AnnexPanelDelegate.*;

// the ship list behind Add ships... and Change...: a tick puts a ship in the drop off or takes it back at once
class AnnexShipList {

    static final float ROW_H = 44f;
    static final float BACK_W = 96f;
    static final float CHANGE_W = 260f;
    static final List<UiFilter.Item> KEYS = List.of(new UiFilter.Item(AnnexView.SORT_ADDS, 62f, "What the ship adds to the blueprint data."),
            new UiFilter.Item(AnnexView.SORT_NAME, 70f));

    private final AnnexPanelDelegate panel;
    private final EngineeringAnnex annex;
    private final AnnexView view;

    AnnexShipList(AnnexPanelDelegate panel) {
        this.panel = panel;
        this.annex = panel.annex;
        this.view = panel.view;
    }

    void build(CustomPanelAPI p, float w, float h) {
        boolean classes = view.classes;
        List<FleetMemberAPI> all = annex.getCandidates(classes);
        Set<FleetMemberAPI> drop = new HashSet<>(annex.getDropOff());
        Set<FleetMemberAPI> fleet = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

        int enabled = 0;
        for (FleetMemberAPI ship : all) if (isEnabled(ship)) enabled++;
        Ui.heading(p, 0f, TOP, w, "Drop off", drop.size() + " of " + enabled);

        float y = TOP + Ui.HEADING_H + ROW_GAP;
        y += UiFilter.bar(p, 0f, y, view.filter, classes ? "Name or hull class" : "Name", "Filters and sort order of this list.", List.of(), null, KEYS,
                this::filtered, panel::refresh) + GAP;

        float by = h - BUTTON_H;
        float listBottom = by - 13f - 6f;
        List<FleetMemberAPI> shown = filter(all, drop, classes);

        if (all.isEmpty()) {
            ShipHullSpecAPI spec = annex.getHullSpec();
            String none = classes || spec == null ? "No ship in the fleet or in storage the annex can take." : "No " + spec.getHullNameWithDashClass() + " ships in the fleet or in storage.";
            Ui.text(p, 8f, y + 7f, w - 16f, none, Ui.gray());
        } else if (shown.isEmpty()) Ui.text(p, 8f, y + 7f, w - 16f, "No ship matches.", Ui.gray());
        else {
            float listH = UiScroll.wholeRows(listBottom - y, 0f, ROW_H, ROW_GAP);
            boolean scroll = shown.size() * (ROW_H + ROW_GAP) - ROW_GAP > listH + 0.5f;
            if (scroll) {
                UiScroll.Column list = UiScroll.list(p, 0f, y, w, listH, view.scroll, AnnexView.SHIPS);
                for (int i = 0; i < shown.size(); i++) row(list.block(ROW_H, i == 0 ? 0f : ROW_GAP), 0f, list.width(), shown.get(i), drop, fleet, classes);
                list.end();
            } else {
                for (int i = 0; i < shown.size(); i++) row(p, y + i * (ROW_H + ROW_GAP), w, shown.get(i), drop, fleet, classes);
            }
        }

        Ui.rule(p, 0f, by - 13f, w);
        UiButtons.button(p, 0f, by, BACK_W, BUTTON_H, "Back", UiButtons.Kind.STD, CutStyle.ALL, panel::closeShips);

        ShipHullSpecAPI spec = annex.getHullSpec();
        if (classes && spec == null) Ui.text(p, BACK_W + 14f, by + 3f, w - BACK_W - 14f, "The first ship sets the hull class.", Ui.gray(), Ui.RIGHT);
        else if (spec != null && !all.isEmpty()) {
            float now = annex.getProgress();
            float then = Math.min(1f, now + annex.getInWorkValue() + annex.getDropOffValue());
            String label = classes ? spec.getHullNameWithDashClass() + " data" : "Blueprint data";
            UiData.change(p, w - CHANGE_W, by, CHANGE_W, BUTTON_H, label, pct(now, false), pct(then, false));
        }
    }

    // with no class at the start every class shows; once the first tick sets one, the others grey out
    private boolean isEnabled(FleetMemberAPI ship) {
        String hullId = annex.getHullId();
        return hullId == null || hullId.equals(ReverseEngineering.getBaseHullId(ship.getHullSpec()));
    }

    // ticked first, then by the picked key
    private List<FleetMemberAPI> filter(List<FleetMemberAPI> all, Set<FleetMemberAPI> drop, boolean classes) {
        List<FleetMemberAPI> shown = new ArrayList<>();
        for (FleetMemberAPI ship : all) {
            String hull = ship.getHullSpec().getHullNameWithDashClass();
            if (classes ? view.filter.matches(ship.getShipName(), hull) : view.filter.matches(ship.getShipName())) shown.add(ship);
        }

        Comparator<FleetMemberAPI> key = AnnexView.SORT_NAME.equals(view.filter.sortKey())
                ? Comparator.comparing(FleetMemberAPI::getShipName, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingDouble((FleetMemberAPI ship) -> -EngineeringAnnex.getValue(ship));
        view.filter.sort(shown, key);
        shown.sort(Comparator.comparing((FleetMemberAPI ship) -> !drop.contains(ship)).thenComparing(ship -> !isEnabled(ship)));
        return shown;
    }

    private void filtered() {
        view.scroll.reset(AnnexView.SHIPS);
        panel.refresh();
    }

    private void row(CustomPanelAPI p, float y, float w, FleetMemberAPI ship, Set<FleetMemberAPI> drop, Set<FleetMemberAPI> fleet, boolean classes) {
        boolean on = drop.contains(ship);
        boolean enabled = isEnabled(ship);
        CustomPanelAPI row = UiButtons.row(p, 0f, y, w, ROW_H, on && enabled, enabled, null, enabled ? () -> toggle(ship, on) : null);

        addTick(row, on, enabled);
        if (enabled) UiImages.shipIcon(row, 34f, 3f, 38f, ship);
        else UiImages.ship(row, 34f, 3f, 38f, ship, true, true);

        boolean inFleet = on ? annex.isFromFleet(ship) : fleet.contains(ship);
        String where = inFleet ? "Fleet" : "Storage";
        String second = classes ? ship.getHullSpec().getHullNameWithDashClass() + ", " + where : where;
        int dMods = ReverseEngineering.getNumNonBuiltInDMods(ship.getVariant());
        if (dMods > 0) second += ", " + dModWords(dMods);

        float nw = w - 86f - 100f;
        nameText(row, 80f, 5f, nw, ship.getShipName(), !enabled ? Ui.gray() : on ? Ui.bright() : Ui.text());
        Ui.text(row, 80f, 23f, nw, Ui.fit(second, Ui.INS15, nw), Ui.gray());

        if (!enabled) return;
        float value = EngineeringAnnex.getValue(ship);
        String adds = pct(value);
        LabelAPI amount = Ui.runs(row, w - 8f - 84f, 13f, 84f, Ui.INS15, Ui.gray(), Ui.RIGHT, "adds " + adds, new String[]{adds}, new Color[]{value > 0f ? Ui.pos() : Ui.neg()});
        Ui.tooltip(row, (UIComponentAPI) amount, AMOUNT_TIP_W, TooltipLocation.LEFT, t -> addAmountTooltip(t, ship.getShipName(), ship.getVariant()));
    }

    private static void addTick(CustomPanelAPI row, boolean on, boolean enabled) {
        Color edge = enabled ? Ui.base() : Ui.gray();
        Ui.box(row, 12f, 16f, 12f, 12f, Misc.scaleAlpha(edge, 0.8f), Misc.setAlpha(Color.BLACK, 120));
        if (on) Ui.box(row, 15f, 19f, 6f, 6f, null, Ui.bright());
    }

    private void toggle(FleetMemberAPI ship, boolean on) {
        if (on) annex.takeBack(ship);
        else if (annex.accepts(ship) && !annex.isReady()) annex.put(ship);
        panel.refresh();
    }
}
