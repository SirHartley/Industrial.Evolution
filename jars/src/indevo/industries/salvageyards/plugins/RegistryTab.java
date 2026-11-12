package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionProductionAPI.ProductionItemType;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import indevo.industries.salvageyards.memory.WreckRegistry;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;

import static indevo.items.installable.SpecialItemEffectsRepo.SALVAGE_DRONES_SP_PER_FP;

// board 5: registry hulls, weapons and wings on the left, the selected hull's fit on the right
class RegistryTab {

    static final float HULL_ROW_H = 46f;
    static final float HULL_GAP = 4f;
    static final float CELL = 52f;
    static final float CELL_GAP = 4f;
    static final float WING_ROW_H = 66f;
    static final float RECORDED_X = 300f;
    static final float SU_X = 390f;
    static final float COL_W = 80f;
    static final float COST_X = 470f;
    static final float COST_W = 110f;
    static final float STEPPER_W = 72f;
    static final float DETAIL_SPRITE = 124f;
    static final float FIT_CELL = 36f;
    static final float MOD_ICON = 28f;
    static final float MIN_HULL_LIST = 3f * (HULL_ROW_H + HULL_GAP);

    private final SalvageYardsPanel panel;
    private final SalvageYardsView view;
    private final YardsContract contract;
    private final WreckRegistry registry;

    private record FitCell(String id, int count, boolean wing) {
        String sprite() {
            return wing ? Global.getSettings().getFighterWingSpec(id).getVariant().getHullSpec().getSpriteName()
                    : Global.getSettings().getWeaponSpec(id).getHardpointSpriteName();
        }

        String name() {
            return wing ? AssemblyTab.wingName(Global.getSettings().getFighterWingSpec(id)) : Global.getSettings().getWeaponSpec(id).getWeaponName();
        }
    }

    RegistryTab(SalvageYardsPanel panel, YardsContract contract) {
        this.panel = panel;
        this.view = panel.getView();
        this.contract = contract;
        this.registry = contract.getRegistry();
    }

    void build(CustomPanelAPI p, float w, float h) {
        float lw = w - SalvageYardsPanel.SIDE_W - SalvageYardsPanel.SIDE_GAP;
        float rx = lw + SalvageYardsPanel.SIDE_GAP;
        float top = SalvageYardsPanel.BODY_Y;

        if (registry == null) {
            Ui.text(p, 0f, top + 6f, w, "The salvage drones have recorded nothing yet.", Ui.gray(), Ui.CENTER);
            return;
        }

        List<String> hulls = getHulls();
        if (view.registryHull == null || !hulls.contains(view.registryHull)) view.registryHull = hulls.isEmpty() ? null : hulls.get(0);

        // two rows of wings while the hull list keeps three rows, one otherwise
        int wingRows = h - top - bottomHeight(2) - 44f >= MIN_HULL_LIST ? 2 : 1;
        float bottom = bottomHeight(wingRows);

        addHulls(p, lw, top, h - top - bottom, hulls);
        float y = h - bottom + 8f;
        y = addWeapons(p, lw, y);
        addWings(p, lw, y + 8f, wingRows);

        Ui.vrule(p, rx - 10f, top, h - top);
        addDetail(p, rx, w - rx, h - top);
    }

    private static float bottomHeight(int wingRows) {
        return 8f + 24f + 2f * (CELL + CELL_GAP) + 8f + 24f + wingRows * (WING_ROW_H + CELL_GAP);
    }

    private List<String> getHulls() {
        Set<String> known = new HashSet<>(Global.getSector().getPlayerFaction().getKnownShips());
        List<String> hulls = new ArrayList<>();
        for (String id : registry.getHullIds()) {
            if (!known.contains(id) && registry.getHullCount(id) > 0) hulls.add(id);
        }

        hulls.sort(Comparator.comparing(id -> Global.getSettings().getHullSpec(id).getHullName()));
        return hulls;
    }

    private void addHulls(CustomPanelAPI p, float lw, float y, float h, List<String> hulls) {
        Ui.heading(p, 0f, y, lw, "Hulls", SALVAGE_DRONES_SP_PER_FP + " salvage units per fleet point, delivered as fitted");
        y += 24f;

        float cw = lw - UiScroll.GUTTER;
        float stepX = cw - STEPPER_W - 4f;
        Ui.thead(p, 0f, y, lw, new String[]{"Hull", "Recorded", "Salvage units", "Hull cost", "Order"},
                new float[]{AssemblyTab.NAME_X, RECORDED_X, SU_X, COST_X, stepX}, new float[]{RECORDED_X - AssemblyTab.NAME_X - 6f, COL_W, COL_W, COST_W, STEPPER_W},
                new Alignment[]{Ui.LEFT, Ui.CENTER, Ui.CENTER, Ui.RIGHT, Ui.CENTER});
        y += 20f;

        UiScroll.Column list = UiScroll.list(p, 0f, y, lw, h - 44f, view.scroll, SalvageYardsView.HULLS);
        for (int i = 0; i < hulls.size(); i++) addHullRow(list.block(HULL_ROW_H, i == 0 ? 0f : HULL_GAP), list.width(), hulls.get(i));
        if (hulls.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "No hulls without a blueprint are recorded.", Ui.gray());
        list.end();
    }

    private void addHullRow(CustomPanelAPI block, float cw, String id) {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
        CustomPanelAPI row = UiButtons.row(block, 0f, 0f, cw, HULL_ROW_H, id.equals(view.registryHull), true, null, () -> {
            view.registryHull = id;
            view.scroll.reset(SalvageYardsView.DETAIL);
            panel.refresh();
        });

        UiImages.hull(row, 4f, 2f, AssemblyTab.SPRITE, spec, true, false);
        Ui.text(row, AssemblyTab.NAME_X, 6f, RECORDED_X - AssemblyTab.NAME_X - 6f, spec.getHullNameWithDashClass());
        Ui.text(row, AssemblyTab.NAME_X, 24f, RECORDED_X - AssemblyTab.NAME_X - 6f, spec.getDesignation(), Ui.gray());

        Ui.text(row, RECORDED_X, 14f, COL_W, Ui.num(registry.getHullCount(id)), Ui.hl(), Ui.CENTER);
        Ui.text(row, SU_X, 14f, COL_W, Ui.num(YardsContract.getSalvageUnitCost(id)), Ui.text(), Ui.CENTER);

        float fit = getNextFitValue(id, 0);
        Ui.text(row, COST_X, fit > 0f ? 6f : 14f, COST_W, Ui.credits(contract.getLineCost(ProductionItemType.SHIP, id, 1)), Ui.hl(), Ui.RIGHT);
        if (fit > 0f) Ui.text(row, COST_X - 40f, 24f, COST_W + 40f, "+ fit " + Ui.credits(fit), Ui.gray(), Ui.RIGHT);

        int inOrder = view.order.registryHulls.getOrDefault(id, 0);
        boolean canAdd = contract.canAdd(YardsContract.Type.REGISTRY_HULL, id);
        UiButtons.stepper(row, cw - STEPPER_W - 4f, (HULL_ROW_H - 22f) / 2f, inOrder, true,
                inOrder > 0 ? () -> change(view.order.registryHulls, id, -1) : null,
                canAdd ? () -> change(view.order.registryHulls, id, 1) : null);
    }

    // fit value of the hull the next order step would take, oldest record first
    private float getNextFitValue(String id, int offset) {
        WreckRegistry.WreckFit fit = getNextFit(id, offset);
        return fit == null ? 0f : Math.round(fit.getValue() * contract.getPriceMult());
    }

    private WreckRegistry.WreckFit getNextFit(String id, int offset) {
        int inOrder = view.order.registryHulls.getOrDefault(id, 0) + offset;
        List<WreckRegistry.WreckFit> fits = registry.getOldestFits(id, inOrder + 1);
        if (fits.isEmpty()) return null;
        return fits.get(Math.min(inOrder, fits.size() - 1));
    }

    private void change(Map<String, Integer> map, String id, int delta) {
        int n = map.getOrDefault(id, 0) + delta;
        if (n > 0) map.put(id, n);
        else map.remove(id);
        panel.refresh();
    }

    private float addWeapons(CustomPanelAPI p, float lw, float y) {
        List<String> ids = new ArrayList<>(registry.getWeaponIds());
        ids.removeIf(id -> registry.getWeaponCount(id) <= 0);
        ids.sort(Comparator.comparing(id -> Global.getSettings().getWeaponSpec(id).getWeaponName()));

        Ui.heading(p, 0f, y, lw, "Weapons", ids.size() + (ids.size() == 1 ? " type" : " types") + ", no salvage units");
        y += 24f;

        float h = 2f * (CELL + CELL_GAP);
        UiScroll.Column list = UiScroll.list(p, 0f, y, lw, h, view.scroll, SalvageYardsView.WEAPONS);
        int per = perRow(list.width());

        for (int i = 0; i < ids.size(); i += per) {
            CustomPanelAPI row = list.block(CELL, i == 0 ? 0f : CELL_GAP);
            for (int j = i; j < Math.min(ids.size(), i + per); j++) addWeaponCell(row, (j - i) * (CELL + CELL_GAP), ids.get(j));
        }

        if (ids.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "No weapons are recorded.", Ui.gray());
        list.end();
        return y + h;
    }

    private void addWeaponCell(CustomPanelAPI row, float x, String id) {
        WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
        int inOrder = view.order.weapons.getOrDefault(id, 0);
        int count = registry.getWeaponCount(id);
        String reason = getItemReason(YardsContract.Type.WEAPON, id, inOrder, count);

        ButtonAPI area = UiButtons.area(row, x, 0f, CELL, CELL, null, false, reason == null, null, Ui.CENTER,
                () -> change(view.order.weapons, id, 1));
        UiImages.stack(row, x, 0f, CELL, spec.getHardpointSpriteName(), count, inOrder, null);
        if (inOrder > 0) AssemblyTab.xButton(row, x + CELL - AssemblyTab.X_SIZE - 1f, 1f, "Remove one from the order", () -> change(view.order.weapons, id, -1));

        Ui.tooltip(row, area, spec.getWeaponName(), itemLines(spec.getSize().getDisplayName() + " " + spec.getType().getDisplayName().toLowerCase(),
                count, inOrder, ProductionItemType.WEAPON, id, reason));
    }

    private void addWings(CustomPanelAPI p, float lw, float y, int rows) {
        List<String> ids = new ArrayList<>(registry.getWingIds());
        ids.removeIf(id -> registry.getWingCount(id) <= 0);
        ids.sort(Comparator.comparing(id -> Global.getSettings().getFighterWingSpec(id).getWingName()));

        Ui.heading(p, 0f, y, lw, "Fighter wings", "no salvage units");
        y += 24f;

        UiScroll.Column list = UiScroll.list(p, 0f, y, lw, rows * (WING_ROW_H + CELL_GAP), view.scroll, SalvageYardsView.WINGS);
        int per = perRow(list.width());

        for (int i = 0; i < ids.size(); i += per) {
            CustomPanelAPI row = list.block(WING_ROW_H, i == 0 ? 0f : CELL_GAP);
            for (int j = i; j < Math.min(ids.size(), i + per); j++) addWingCell(row, (j - i) * (CELL + CELL_GAP), ids.get(j));
        }

        if (ids.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "No fighter wings are recorded.", Ui.gray());
        list.end();
    }

    private void addWingCell(CustomPanelAPI row, float x, String id) {
        FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
        int inOrder = view.order.wings.getOrDefault(id, 0);
        int count = registry.getWingCount(id);
        String reason = getItemReason(YardsContract.Type.WING, id, inOrder, count);

        ButtonAPI area = UiButtons.area(row, x, 0f, CELL, CELL, null, false, reason == null, null, Ui.CENTER,
                () -> change(view.order.wings, id, 1));
        UiImages.stack(row, x, 0f, CELL, spec.getVariant().getHullSpec().getSpriteName(), count, inOrder, null);
        if (inOrder > 0) AssemblyTab.xButton(row, x + CELL - AssemblyTab.X_SIZE - 1f, 1f, "Remove one from the order", () -> change(view.order.wings, id, -1));
        Ui.text(row, x - 1f, CELL + 1f, CELL + 2f, Ui.fit(spec.getWingName(), Ui.VIC10, CELL + 2f), Ui.VIC10, Ui.gray(), Ui.CENTER);

        String role = spec.getRoleDesc() != null ? spec.getRoleDesc() : "Fighter wing";
        Ui.tooltip(row, area, AssemblyTab.wingName(spec), itemLines(role, count, inOrder, ProductionItemType.FIGHTER, id, reason));
    }

    private int perRow(float w) {
        return Math.max(1, (int) ((w + CELL_GAP) / (CELL + CELL_GAP)));
    }

    private String getItemReason(YardsContract.Type type, String id, int inOrder, int count) {
        if (contract.canAdd(type, id)) return null;
        if (inOrder >= count) return "Every recorded one is in the order.";
        return "One more does not fit the budget.";
    }

    private String[] itemLines(String sub, int count, int inOrder, ProductionItemType type, String id, String reason) {
        List<String> lines = new ArrayList<>();
        lines.add(sub);
        lines.add("Recorded: " + Ui.num(count) + ", in the order: " + Ui.num(inOrder));
        lines.add("Cost: " + Ui.credits(contract.getLineCost(type, id, 1)) + " each");
        lines.add(reason != null ? reason : "Click to add one to the order.");
        return lines.toArray(new String[0]);
    }

    private void addDetail(CustomPanelAPI p, float x, float w, float h) {
        UiScroll.Column column = UiScroll.list(p, x, SalvageYardsPanel.BODY_Y, w + UiScroll.GUTTER, h, view.scroll, SalvageYardsView.DETAIL);
        float rw = column.width();
        String id = view.registryHull;

        if (id == null) {
            CustomPanelAPI b = column.block(48f, 0f);
            Ui.heading(b, 0f, 0f, rw, "Wreck Registry");
            Ui.wrap(b, 0f, 26f, rw, "Select a recorded hull to see its fit.", Ui.gray());
            column.end();
            return;
        }

        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
        WreckRegistry.WreckFit fit = getNextFit(id, 0);
        int inOrder = view.order.registryHulls.getOrDefault(id, 0);

        CustomPanelAPI head = column.block(26f + DETAIL_SPRITE, 0f);
        Ui.heading(head, 0f, 0f, rw, spec.getHullNameWithDashClass());
        Ui.well(head, 0f, 26f, DETAIL_SPRITE, DETAIL_SPRITE);
        UiImages.hull(head, 0f, 26f, DETAIL_SPRITE, spec, false, false);
        float gx = DETAIL_SPRITE + 10f;
        UiData.grid(head, gx, 30f, rw - gx, 19f, List.of(
                UiData.GridRow.of("Recorded", Ui.num(registry.getHullCount(id))),
                UiData.GridRow.of("In order", Ui.num(inOrder), inOrder > 0 ? Ui.pos() : Ui.gray())));
        Ui.wrap(head, gx, 76f, rw - gx, spec.getDesignation() + ", " + spec.getHullSize().name().toLowerCase().replace('_', ' '), Ui.gray());

        addFit(column, rw, fit);
        addHullmods(column, rw, fit);
        addCost(column, rw, id, fit);

        String note = "D-mods and discounts are set on the Ship assembly tab.";
        CustomPanelAPI n = column.block(Ui.wrapHeight(note, Ui.INS15, rw) + 4f, 10f);
        Ui.wrap(n, 0f, 0f, rw, note, Ui.gray());
        column.end();
    }

    private void addFit(UiScroll.Column column, float rw, WreckRegistry.WreckFit fit) {
        Map<String, Integer> weapons = new LinkedHashMap<>();
        Map<String, Integer> wings = new LinkedHashMap<>();
        if (fit != null) {
            for (String w : fit.getWeapons()) weapons.merge(w, 1, Integer::sum);
            for (String w : fit.getWings()) wings.merge(w, 1, Integer::sum);
        }

        List<FitCell> cells = new ArrayList<>();
        for (Map.Entry<String, Integer> e : weapons.entrySet()) cells.add(new FitCell(e.getKey(), e.getValue(), false));
        for (Map.Entry<String, Integer> e : wings.entrySet()) cells.add(new FitCell(e.getKey(), e.getValue(), true));

        int per = Math.max(1, (int) ((rw + 4f) / (FIT_CELL + 4f)));
        int rows = Math.max(1, Math.min(3, (cells.size() + per - 1) / per));
        float h = cells.isEmpty() ? 20f : rows * (FIT_CELL + 4f);

        CustomPanelAPI b = column.block(24f + h, 10f);
        Ui.heading(b, 0f, 0f, rw, "Fit", fit == null ? null : "next hull, oldest record");
        if (cells.isEmpty()) {
            Ui.text(b, 0f, 24f, rw, "No weapons or fighter wings.", Ui.gray());
            return;
        }

        UiImages.iconrow(b, 0f, 24f, rw, cells, FIT_CELL, FIT_CELL, 4f, 3,
                (c, cx, cy, cw, ch, cell) -> UiImages.stack(c, cx, cy, cw, cell.sprite(), cell.count(), 0, cell.name()), FitCell::name);
    }

    private void addHullmods(UiScroll.Column column, float rw, WreckRegistry.WreckFit fit) {
        List<String> mods = new ArrayList<>();
        if (fit != null) {
            for (String id : fit.getHullmods()) {
                if (Global.getSettings().getHullModSpec(id) != null) mods.add(id);
            }
        }

        List<String> names = new ArrayList<>();
        for (String id : mods) names.add(Global.getSettings().getHullModSpec(id).getDisplayName());
        String text = mods.isEmpty() ? "None." : String.join(", ", names);

        int per = Math.max(1, (int) ((rw + 4f) / (MOD_ICON + 4f)));
        float iconsH = mods.isEmpty() ? 0f : Math.min(2, (mods.size() + per - 1) / per) * (MOD_ICON + 4f);
        float textH = Ui.wrapHeight(text, Ui.INS15, rw);

        CustomPanelAPI b = column.block(24f + iconsH + textH + 2f, 10f);
        Ui.heading(b, 0f, 0f, rw, "Hullmods");
        UiImages.iconrow(b, 0f, 24f, rw, mods, MOD_ICON, MOD_ICON, 4f, 2, (c, cx, cy, cw, ch, m) -> UiImages.hullmod(c, cx, cy, cw, m),
                m -> Global.getSettings().getHullModSpec(m).getDisplayName());
        Ui.wrap(b, 0f, 24f + iconsH, rw, text, mods.isEmpty() ? Ui.gray() : Ui.text());
    }

    private void addCost(UiScroll.Column column, float rw, String id, WreckRegistry.WreckFit fit) {
        CustomPanelAPI b = column.block(24f + 3f * UiData.COST_ROW_H + 8f, 10f);
        Ui.heading(b, 0f, 0f, rw, "Cost per hull");

        int su = YardsContract.getSalvageUnitCost(id);
        int available = contract.getAvailableSalvageUnits();
        String suText = Ui.num(su);
        UiData.value(b, 0f, 26f, rw, UiData.COST_ROW_H, "Salvage units", suText + " (" + Ui.num(available) + ")", Ui.gray(),
                new String[]{suText}, new Color[]{su <= available ? Ui.hl() : Ui.neg()});

        float fitCost = fit == null ? 0f : Math.round(fit.getValue() * contract.getPriceMult());
        UiData.creditsRow(b, 0f, 26f + UiData.COST_ROW_H + 2f, rw, "Hull", contract.getLineCost(ProductionItemType.SHIP, id, 1));
        UiData.creditsRow(b, 0f, 26f + 2f * (UiData.COST_ROW_H + 2f), rw, "Fit", fitCost);
    }
}
