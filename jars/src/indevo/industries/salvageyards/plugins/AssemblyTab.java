package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI;
import com.fs.starfarer.api.campaign.BaseCustomProductionPickerDelegateImpl;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.ids.ItemIds;
import indevo.industries.salvageyards.intel.YardsCustomProductionIntel;
import indevo.industries.salvageyards.memory.WreckRegistry;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;

// board 4: order table, scrapped hulls and running orders on the left, the contract column on the right
class AssemblyTab {

    static final float ROW_H = 46f;
    static final float RUN_GAP = 4f;
    static final float RUN_H = 2f * ROW_H + RUN_GAP;
    static final float SPRITE = 42f;
    static final float X_SIZE = 14f;
    static final float NAME_X = 54f;
    static final float SOURCE_X = 270f;
    static final float QTY_X = 430f;
    static final float DMOD_X = 480f;
    static final float DMOD_W = 70f;
    static final float COST_W = 112f;
    static final float BUTTON_W = 150f;
    static final float BUTTON_H = 26f;
    static final float SCRAP_CELLS_W = 140f;
    static final float SLIDER_ICON = 28f;
    static final float ACCEL_W = 150f;
    // rule gap, buttons, scrapped heading and row, running heading, two running rows
    static final float BOTTOM_H = 10f + 40f + 24f + 54f + 24f + RUN_H;

    private final SalvageYardsPanel panel;
    private final SalvageYardsView view;
    private final YardsContract contract;

    AssemblyTab(SalvageYardsPanel panel, YardsContract contract) {
        this.panel = panel;
        this.view = panel.getView();
        this.contract = contract;
    }

    void build(CustomPanelAPI p, float w, float h) {
        float lw = w - SalvageYardsPanel.SIDE_W - SalvageYardsPanel.SIDE_GAP;
        float rx = lw + SalvageYardsPanel.SIDE_GAP;
        float y = SalvageYardsPanel.BODY_Y;

        addOrder(p, lw, y, h - y - BOTTOM_H);
        y = h - BOTTOM_H;
        Ui.rule(p, 0f, y + 2f, lw);
        y += 10f;
        addButtons(p, lw, y);
        y += 40f;
        addScrapped(p, lw, y);
        y += 24f + 54f;
        addRunning(p, lw, y, h - y);

        Ui.vrule(p, rx - 10f, SalvageYardsPanel.BODY_Y, h - SalvageYardsPanel.BODY_Y);
        addContract(p, rx, w - rx, h - SalvageYardsPanel.BODY_Y);
    }

    private void addOrder(CustomPanelAPI p, float lw, float y, float h) {
        Ui.heading(p, 0f, y, lw, "Order", Ui.LEFT);
        y += 20f;

        float cw = lw - UiScroll.GUTTER;
        float costX = cw - COST_W - 2f;
        Ui.thead(p, 0f, y, lw, new String[]{"Item", "Source", "Qty", "D-mods", "Cost"},
                new float[]{NAME_X, SOURCE_X, QTY_X, DMOD_X, costX}, new float[]{200f, 150f, 40f, DMOD_W, COST_W},
                new Alignment[]{Ui.LEFT, Ui.LEFT, Ui.CENTER, Ui.CENTER, Ui.RIGHT});
        y += 20f;

        UiScroll.Column list = UiScroll.list(p, 0f, y, lw, h - 40f, view.scroll, SalvageYardsView.ORDER);
        List<YardsContract.Line> lines = contract.getLines();
        int dmods = contract.getDModForecast();

        for (int i = 0; i < lines.size(); i++) addLine(list.block(ROW_H, 0f), list.width(), lines.get(i), i, dmods);
        if (lines.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "Nothing is in the order yet.", Ui.gray());
        list.end();
    }

    private void addLine(CustomPanelAPI row, float cw, YardsContract.Line line, int index, int dmods) {
        if (index % 2 == 0) Ui.box(row, 0f, 0f, cw, ROW_H, null, Misc.scaleAlpha(Ui.base(), 0.05f));

        String name;
        String sub;
        switch (line.type()) {
            case HULL, REGISTRY_HULL -> {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(line.id());
                UiImages.hull(row, 4f, 2f, SPRITE, spec, true, false);
                name = spec.getHullNameWithDashClass();
                sub = spec.getDesignation();
            }
            case WEAPON -> {
                WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(line.id());
                weaponCell(row, 4f, 2f, SPRITE, spec, -1, 0);
                name = spec.getWeaponName();
                sub = spec.getSize().getDisplayName() + " " + spec.getType().getDisplayName().toLowerCase();
            }
            default -> {
                FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(line.id());
                UiImages.wing(row, 4f, 2f, SPRITE, line.id(), true, false);
                name = wingName(spec);
                sub = spec.getRoleDesc() != null ? spec.getRoleDesc() : "Fighter wing";
            }
        }

        xButton(row, 4f + SPRITE - X_SIZE - 1f, 3f, "Remove from the order", () -> remove(line));
        Ui.text(row, NAME_X, 6f, SOURCE_X - NAME_X - 6f, name);
        Ui.text(row, NAME_X, 24f, SOURCE_X - NAME_X - 6f, sub, Ui.gray());

        boolean registry = line.type() != YardsContract.Type.HULL;
        String sourceSub = null;
        Color sourceSubColor = Ui.gray();
        if (line.capped()) {
            sourceSub = "Registry has only " + Ui.num(line.limit());
            sourceSubColor = Ui.neg();
        } else if (line.type() == YardsContract.Type.REGISTRY_HULL) {
            sourceSub = "as fitted · " + Ui.num(line.salvageUnits()) + " salvage units";
        }

        float sourceW = QTY_X - SOURCE_X - 4f;
        Ui.text(row, SOURCE_X, sourceSub != null ? 6f : 14f, sourceW, registry ? "Wreck Registry" : "Blueprint", registry ? Ui.hl() : Ui.text());
        if (sourceSub != null) Ui.text(row, SOURCE_X, 24f, sourceW, sourceSub, sourceSubColor);

        Ui.text(row, QTY_X, 14f, 40f, Ui.num(line.qty()), line.qty() > 0 ? Ui.hl() : Ui.gray(), Ui.CENTER);

        if (line.isHull()) dmodPips(row, DMOD_X, ROW_H / 2f - 4f, DMOD_W, dmods);
        else Ui.text(row, DMOD_X, 14f, DMOD_W, "none", Ui.gray(), Ui.CENTER);

        float total = line.cost() + line.fitCost();
        String costSub = null;
        if (line.type() == YardsContract.Type.REGISTRY_HULL && line.fitCost() > 0f) costSub = "fit " + Ui.credits(line.fitCost());
        else if (line.qty() > 1) costSub = Ui.credits(line.cost() / line.qty()) + " each";

        float costX = cw - COST_W - 2f;
        Ui.text(row, costX, costSub != null ? 6f : 14f, COST_W, Ui.credits(total), Ui.hl(), Ui.RIGHT);
        float subX = DMOD_X + DMOD_W + 2f;
        if (costSub != null) Ui.text(row, subX, 24f, cw - 2f - subX, costSub, Ui.gray(), Ui.RIGHT);
    }

    // D-mod forecast as pips, shrunk to the column when the base D-mod setting is high
    static void dmodPips(CustomPanelAPI p, float x, float y, float w, int filled) {
        int total = Math.max(1, Setting.SY_BASE_DMODS.intValue());
        float gap = 3f;
        float size = Math.max(3f, Math.min(9f, (float) Math.floor((w + gap) / total - gap)));
        float pw = total * (size + gap) - gap;
        UiData.pips(p, x + Math.round((w - pw) / 2f), y + Math.round((9f - size) / 2f), total, Math.min(filled, total), size, gap, Ui.neg());
    }

    static void weaponCell(CustomPanelAPI p, float x, float y, float size, WeaponSpecAPI spec, int count, int extra) {
        if (count < 0 && extra <= 0) {
            Ui.well(p, x, y, size, size);
            UiImages.icon(p, x + 4f, y + 4f, size - 8f, spec.getHardpointSpriteName(), spec.getWeaponName());
            return;
        }

        UiImages.stack(p, x, y, size, spec.getHardpointSpriteName(), count, extra, spec.getWeaponName());
    }

    // small red x over a sprite corner, the label sits on an empty button face
    static void xButton(CustomPanelAPI p, float x, float y, String tip, Runnable action) {
        ButtonAPI b = UiButtons.button(p, x, y, X_SIZE, X_SIZE, "", UiButtons.Kind.RED, CutStyle.ALL, action);
        Ui.textIn(p, x, y, X_SIZE, X_SIZE, "x", Ui.VIC10, Ui.text(), Ui.CENTER, new String[0], new Color[0]);
        Ui.tooltip(p, b, tip);
    }

    private void remove(YardsContract.Line line) {
        YardsOrder order = view.order;
        switch (line.type()) {
            case HULL -> order.hulls.remove(line.id());
            case REGISTRY_HULL -> order.registryHulls.remove(line.id());
            case WEAPON -> order.weapons.remove(line.id());
            case WING -> order.wings.remove(line.id());
        }

        panel.refresh();
    }

    private void addButtons(CustomPanelAPI p, float lw, float y) {
        UiButtons.button(p, 0f, y, BUTTON_W, BUTTON_H, "Select hulls...", UiButtons.Kind.STD, CutStyle.TL_BR, this::selectHulls);
        UiButtons.button(p, BUTTON_W + 6f, y, BUTTON_W, BUTTON_H, "Scrap ships...", UiButtons.Kind.STD, CutStyle.TL_BR, this::scrapShips);

        String value = Ui.credits(contract.getOrderValue());
        float x = 2f * BUTTON_W + 20f;
        Ui.textIn(p, x, y, lw - x, BUTTON_H, "Order value  " + value, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[]{value}, new Color[]{Ui.hl()});
    }

    private void selectHulls() {
        panel.openPicker(d -> d.showCustomProductionPicker(new PickerDelegate()));
    }

    private class PickerDelegate extends BaseCustomProductionPickerDelegateImpl {
        @Override
        public Set<String> getAvailableShipHulls() {
            return new HashSet<>(Global.getSector().getPlayerFaction().getKnownShips());
        }

        @Override
        public Set<String> getAvailableWeapons() {
            WreckRegistry registry = contract.getRegistry();
            return registry == null ? new HashSet<>() : registry.getWeaponIds();
        }

        @Override
        public Set<String> getAvailableFighters() {
            WreckRegistry registry = contract.getRegistry();
            return registry == null ? new HashSet<>() : registry.getWingIds();
        }

        @Override
        public float getCostMult() {
            return contract.getPriceMult();
        }

        @Override
        public float getMaximumValue() {
            return Math.max(0f, contract.getBudgetLeft());
        }

        @Override
        public void notifyProductionSelected(FactionProductionAPI production) {
            for (FactionProductionAPI.ItemInProductionAPI item : production.getCurrent()) {
                int count = item.getQuantity();
                if (count <= 0) continue;

                switch (item.getType()) {
                    case SHIP -> YardsOrder.add(view.order.hulls, item.getSpecId(), count);
                    case WEAPON -> YardsOrder.add(view.order.weapons, item.getSpecId(), count);
                    case FIGHTER -> YardsOrder.add(view.order.wings, item.getSpecId(), count);
                }
            }
        }
    }

    private void scrapShips() {
        List<FleetMemberAPI> pool = new ArrayList<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
        CargoAPI storage = MiscIE.getStorageCargo(panel.getMarket());
        if (storage != null) {
            storage.initMothballedShips("player");
            pool.addAll(storage.getMothballedShips().getMembersListCopy());
        }
        pool.removeAll(view.order.scrapped);

        int perRow = Setting.SHIP_PICKER_ROW_COUNT.intValue();
        int rows = pool.size() > perRow ? (int) Math.ceil(pool.size() / (float) perRow) : 1;
        int cols = Math.max(Math.min(pool.size(), perRow), 4);

        panel.openPicker(d -> d.showFleetMemberPickerDialog("Select hulls to scrap", "Confirm", "Cancel", rows, cols, 88f, true, true, pool,
                new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members != null) view.order.scrapped.addAll(members);
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                    }
                }));
    }

    private void addScrapped(CustomPanelAPI p, float lw, float y) {
        Ui.heading(p, 0f, y, lw, "Scrapped for budget", Ui.LEFT);
        y += 24f;

        List<FleetMemberAPI> scrapped = view.order.scrapped;
        if (scrapped.isEmpty()) {
            Ui.text(p, 8f, y + 12f, lw - 16f, "No ships scrapped. Scrapped hulls add to the budget.", Ui.gray());
            return;
        }

        UiImages.iconrow(p, 0f, y, SCRAP_CELLS_W, scrapped, SPRITE, SPRITE, 4f, 1, (c, cx, cy, cw, ch, m) -> {
            UiImages.ship(c, cx, cy, cw, m, true, false);
            xButton(c, cx + cw - X_SIZE - 1f, cy + 1f, "Take back " + m.getShipName(), () -> {
                view.order.scrapped.remove(m);
                panel.refresh();
            });
        }, m -> m.getShipName() + ", " + m.getHullSpec().getHullNameWithDashClass());

        String budget = "+" + Ui.credits(contract.getScrapValue());
        float bw = Ui.width("Budget  " + budget, Ui.INS15) + 4f;
        float tx = SCRAP_CELLS_W + 10f;
        float tw = lw - tx - bw - 12f;

        List<String> names = new ArrayList<>();
        for (FleetMemberAPI m : scrapped) names.add(m.getHullSpec().getHullNameWithDashClass());
        Ui.text(p, tx, y + 4f, tw, String.join(", ", names));
        Ui.text(p, tx, y + 22f, tw, "30% of hull value, less per D-mod", Ui.gray());
        Ui.runs(p, lw - bw, y + 12f, bw, Ui.INS15, Ui.gray(), Ui.RIGHT, "Budget  " + budget, new String[]{budget}, new Color[]{Ui.pos()});
    }

    private void addRunning(CustomPanelAPI p, float lw, float y, float h) {
        Ui.heading(p, 0f, y, lw, "Orders in progress", Ui.LEFT);
        y += 24f;

        UiScroll.Column list = UiScroll.list(p, 0f, y, lw, h - 24f, view.scroll, SalvageYardsView.RUNNING);
        List<YardsCustomProductionIntel> orders = contract.getRunningOrders();
        for (int i = 0; i < orders.size(); i++) addRunningRow(list.block(ROW_H, i == 0 ? 0f : RUN_GAP), list.width(), orders.get(i), i);
        if (orders.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "No order is in progress here.", Ui.gray());
        list.end();
    }

    private void addRunningRow(CustomPanelAPI row, float cw, YardsCustomProductionIntel order, int index) {
        if (index % 2 == 0) Ui.box(row, 0f, 0f, cw, ROW_H, null, Misc.scaleAlpha(Ui.base(), 0.05f));

        List<ShipHullSpecAPI> hulls = new ArrayList<>();
        int items = 0;
        if (order.getData() != null) {
            for (Map.Entry<String, Integer> e : order.getData().productionList.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) hulls.add(Global.getSettings().getHullSpec(e.getKey()));
            }

            WreckRegistry.Order registry = order.getData().registryOrder;
            if (registry != null) {
                for (int n : registry.weapons.values()) items += n;
                for (int n : registry.wings.values()) items += n;
            }
        }

        UiImages.iconrow(row, 4f, 5f, 114f, hulls, 36f, 36f, 2f, 1, (c, cx, cy, cw2, ch, spec) -> UiImages.hull(c, cx, cy, cw2, spec, true, false),
                ShipHullSpecAPI::getHullNameWithDashClass);

        String title = !hulls.isEmpty() ? hulls.size() + (hulls.size() == 1 ? " hull" : " hulls") : items + (items == 1 ? " item" : " items");
        Ui.text(row, 126f, 6f, 118f, title);
        Ui.text(row, 126f, 24f, 118f, "to local storage", Ui.gray());

        int total = order.getSpeedUpDaysTotal();
        int left = order.getSpeedUpDaysLeft();
        float done = total <= 0 ? 1f : Math.max(0f, Math.min(1f, (total - left) / (float) total));
        UiData.bar(row, 250f, 13f, 170f, 20f, done, Ui.barBlue(), "Day " + Math.max(0, total - left) + " of " + total);

        String days = Ui.num(left);
        Ui.runs(row, 426f, 15f, 84f, Ui.INS15, Ui.gray(), Ui.LEFT, days + (left == 1 ? " day left" : " days left"), new String[]{days}, new Color[]{Ui.hl()});

        addAccelerate(row, cw, order);
    }

    // vanilla wing names already end in wing (talon_wing is "Talon Wing")
    static String wingName(FighterWingSpecAPI spec) {
        String name = spec.getWingName();
        return name.toLowerCase().endsWith("wing") ? name : name + " wing";
    }

    private void addAccelerate(CustomPanelAPI row, float cw, YardsCustomProductionIntel order) {
        float x = cw - ACCEL_W - 4f;
        String reason = getAccelerateReason(order);
        String id = "accelerate" + System.identityHashCode(order);
        Runnable apply = () -> RelicSpeedUpDialog.apply(order);

        ButtonAPI button;
        if (reason != null) {
            button = UiButtons.button(row, x, 3f, ACCEL_W, BUTTON_H, "Accelerate", UiButtons.Kind.OFF, CutStyle.ALL, UiImages.relicSprite(), null, Ui.ORB12, null);
            Ui.text(row, cw - 204f, 29f, 200f, reason, Ui.neg(), Ui.RIGHT);
        } else if (view.confirm.isArmed(id)) {
            button = UiButtons.confirm(row, x, 10f, ACCEL_W, BUTTON_H, "Accelerate", UiButtons.Kind.STD, view.confirm, id, apply, panel::refresh);
        } else {
            button = UiButtons.button(row, x, 10f, ACCEL_W, BUTTON_H, "Accelerate", UiButtons.Kind.STD, CutStyle.ALL, UiImages.relicSprite(),
                    Ui.num(order.getSpeedUpCost()), Ui.ORB12, () -> {
                        view.confirm.arm(id);
                        panel.refresh();
                    });
        }

        Ui.tooltip(row, button, 400f, TooltipMakerAPI.TooltipLocation.BELOW, t -> RelicSpeedUpDialog.addTooltip(t, order));
    }

    private String getAccelerateReason(YardsCustomProductionIntel order) {
        float cooldown = order.getSpeedUpCooldownLeft();
        if (cooldown > 0f) {
            int days = (int) Math.ceil(cooldown);
            return "again in " + days + (days == 1 ? " day" : " days");
        }

        if (!order.canSpeedUp()) return "Too few days left.";
        if (!RelicHelper.canAfford(panel.getMarket(), order.getSpeedUpCost())) return "Not enough " + RelicHelper.getName() + ".";
        return null;
    }

    private void addContract(CustomPanelAPI p, float x, float w, float h) {
        String reason = contract.getConfirmReason();
        float confirmH = UiButtons.guardHeight(w - UiScroll.GUTTER, 30f, reason);
        float blockH = 554f;

        // the confirm button stays pinned at the bottom, the contract above scrolls
        UiScroll.Column column = UiScroll.list(p, x, SalvageYardsPanel.BODY_Y, w, h - confirmH - 8f, view.scroll, SalvageYardsView.CONTRACT);
        CustomPanelAPI b = column.block(blockH, 0f);
        float rw = column.width();
        float y = 0f;

        Ui.heading(b, 0f, y, rw, "Contract");
        y += 26f;

        float capacity = contract.getCapacity();
        float value = contract.getOrderValue();
        float left = capacity - value;
        Ui.text(b, 0f, y, 80f, "Budget");
        String leftText = left >= 0f ? Ui.credits(left) : Ui.credits(-left);
        Ui.runs(b, 80f, y, rw - 80f, Ui.INS15, Ui.gray(), Ui.RIGHT, left >= 0f ? leftText + " left" : leftText + " over", new String[]{leftText},
                new Color[]{left >= 0f ? Ui.hl() : Ui.neg()});
        y += 20f;

        UiData.bar(b, 0f, y, rw, 20f, capacity <= 0f ? 1f : value / capacity, left >= 0f ? Ui.barBlue() : Ui.RED, Ui.num(value) + " / " + Ui.credits(capacity));
        y += 28f;

        UiData.grid(b, 0f, y, rw, 19f, List.of(
                UiData.GridRow.of("Yard capacity", Ui.credits(capacity - contract.getScrapValue())),
                UiData.GridRow.of("Scrapped ships", "+" + Ui.credits(contract.getScrapValue()), Ui.pos())));
        y += 44f;

        addStoryPoint(b, rw, y);
        y += 58f;

        Ui.heading(b, 0f, y, rw, Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName());
        y += 26f;
        int maxParts = contract.getMaxParts();
        UiImages.commodity(b, 0f, y - 2f, SLIDER_ICON, ItemIds.PARTS);
        YardsSlider.create(b, SLIDER_ICON + 8f, y + 2f, rw - SLIDER_ICON - 8f, 20f, view.order.parts, maxParts, Misc.scaleAlpha(Ui.base(), 0.55f), v -> {
            view.order.parts = v;
            panel.refresh();
        });
        y += 32f;

        float discount = contract.getBaseCost() <= 0f ? 0f : contract.getPartsDiscount() / contract.getBaseCost();
        UiData.grid(b, 0f, y, rw, 19f, List.of(
                UiData.GridRow.of("Used", Ui.num(view.order.parts) + " of " + Ui.num(maxParts)),
                UiData.GridRow.of("Cost reduction, up to 50%", Math.round(discount * 100f) + "%", Ui.pos())));
        y += 46f;

        Ui.heading(b, 0f, y, rw, RelicHelper.getName());
        y += 26f;
        int maxRelics = contract.getMaxRelics();
        UiImages.commodity(b, 0f, y - 2f, SLIDER_ICON, ItemIds.RARE_PARTS);
        YardsSlider.create(b, SLIDER_ICON + 8f, y + 2f, rw - SLIDER_ICON - 8f, 20f, view.order.relics, maxRelics, Misc.scaleAlpha(Ui.hl(), 0.6f), v -> {
            view.order.relics = v;
            panel.refresh();
        });
        y += 32f;

        int required = contract.toData().getRequiredParts();
        UiData.grid(b, 0f, y, rw, 19f, List.of(UiData.GridRow.of("Used", Ui.num(view.order.relics) + " of " + Ui.num(required))));
        y += 19f;

        int base = Setting.SY_BASE_DMODS.intValue();
        int dmods = contract.hasHulls() ? contract.getDModForecast() : base;
        String dmodText = dmods + " of " + base;
        float tw = Ui.width(dmodText, Ui.INS15) + 4f;
        Ui.text(b, 0f, y, rw - tw - 120f, "D-mods per hull");
        dmodPips(b, rw - tw - 112f, y + 4f, 104f, dmods);
        Ui.runs(b, rw - tw, y, tw, Ui.INS15, Ui.gray(), Ui.RIGHT, dmodText, new String[]{dmods + ""}, new Color[]{Ui.hl()});
        y += 28f;

        Ui.heading(b, 0f, y, rw, "Cost");
        y += 24f;
        float credits = contract.getTotalCredits();
        UiData.creditsRow(b, 0f, y, rw, "Credits", credits);
        y += 24f;
        UiData.costrow(b, 0f, y, rw, Global.getSettings().getCommoditySpec(ItemIds.PARTS).getName(), UiImages.partsSprite(),
                Ui.num(view.order.parts), Ui.num(contract.getPartsAvailable()), true);
        y += 24f;
        UiData.costrow(b, 0f, y, rw, RelicHelper.getName(), UiImages.relicSprite(), Ui.num(view.order.relics), Ui.num(contract.getRelicsAvailable()), true);
        y += 24f;

        int su = contract.getSalvageUnits();
        String suText = Ui.num(su);
        boolean suOk = su <= contract.getAvailableSalvageUnits();
        UiData.value(b, 0f, y + 2f, rw, 20f, "Salvage units", suText + " (" + Ui.num(contract.getAvailableSalvageUnits()) + ")", Ui.gray(),
                new String[]{suText}, new Color[]{suOk ? Ui.hl() : Ui.neg()});
        y += 28f;

        priceLine(b, 0f, y, rw, "Price", panel.getMarket().getFaction(), contract.getPriceMult());
        y += 19f;
        UiData.grid(b, 0f, y, rw, 19f, List.of(UiData.GridRow.of("Delivery", YardsContract.getDeliveryText())));
        column.end();

        CustomPanelAPI pinned = Ui.panel(p, x, SalvageYardsPanel.BODY_Y + h - confirmH, rw, confirmH);
        UiButtons.guarded(pinned, 0f, 0f, rw, 30f, "Confirm", UiButtons.Kind.GREEN, reason, () -> {
            contract.confirm();
            view.scroll.reset(SalvageYardsView.ORDER);
            panel.refresh();
        });
    }

    // "<label>, <standing>" left in the relation colour, the price percentage right
    static void priceLine(CustomPanelAPI p, float x, float y, float w, String label, FactionAPI faction, float mult) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        String standing = player.getRelationshipLevel(faction.getId()).getDisplayName().toLowerCase();
        String pct = Math.round(mult * 100f) + "%";
        float vw = Ui.width(pct, Ui.INS15) + 4f;

        Ui.runs(p, x, y, w - vw - 8f, Ui.INS15, Ui.text(), Ui.LEFT, label + ", " + standing, new String[]{standing}, new Color[]{faction.getRelColor(player.getId())});
        Ui.text(p, x + w - vw, y, vw, pct, Ui.hl(), Ui.RIGHT);
    }

    private void addStoryPoint(CustomPanelAPI b, float rw, float y) {
        if (contract.isStoryPointUsed()) {
            Ui.runs(b, 0f, y + 4f, rw, Ui.INS15, Ui.text(), Ui.CENTER, "Doubled by Story Point", new String[]{"Story Point"}, new Color[]{Ui.story()});
            return;
        }

        int points = Global.getSector().getPlayerStats().getStoryPoints();
        if (points <= 0) {
            UiButtons.button(b, 0f, y, rw, BUTTON_H, "Double yard capacity", UiButtons.Kind.OFF, null);
            Ui.text(b, 0f, y + 30f, rw, "You have no story points.", Ui.neg(), Ui.CENTER);
            return;
        }

        ButtonAPI button = UiButtons.confirm(b, 0f, y, rw, BUTTON_H, "Double yard capacity", UiButtons.Kind.STORY, view.confirm, "story", this::spendStoryPoint, panel::refresh);
        Ui.tooltip(b, button, "Double yard capacity",
                "Will double the production capacity of the Salvage Yards for a single order.",
                "Should you select this option, but not confirm the production contract at this moment, the bonus will be retained for the future.");
        Ui.runs(b, 0f, y + 30f, rw, Ui.INS15, Ui.gray(), Ui.CENTER, "Costs 1 story point", new String[]{"1 story point"}, new Color[]{Ui.story()});
    }

    private void spendStoryPoint() {
        float bonusXP = Global.getSettings().getBonusXP(YardsContract.BONUS_XP_KEY);
        Global.getSector().getPlayerStats().spendStoryPoints(1, true, null, true, bonusXP,
                "Temporarily increased the production capacity of the Salvage Yards on " + panel.getMarket().getName());
        Global.getSoundPlayer().playUISound(Sounds.STORY_POINT_SPEND, 1f, 1f);
        panel.getMarket().getMemoryWithoutUpdate().set(YardsContract.HAS_SP_SELECTED, true);
    }
}
