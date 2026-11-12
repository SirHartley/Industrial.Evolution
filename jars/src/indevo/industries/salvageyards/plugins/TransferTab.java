package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.BillingHelper;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

// boards 6 and 18: pick the ship to move from and the one to move to, then the mods; costs and the transfer at the bottom
class TransferTab {

    enum SlotState {
        BUILT, HAS, INCOMING, FREE
    }

    static final float MID_GAP = 64f;
    static final float STRIP_H = 50f;
    static final float SHIP_CELL = 48f;
    static final float SHIP_STEP = 52f;
    static final float CARD_H = 100f;
    static final float CARD_SPRITE = 88f;
    static final float MOD_ROW_H = 38f;
    static final float MOD_STEP = 42f;
    static final float MOD_ICON = 28f;
    static final float SLOT_H = 44f;
    static final float SLOT_GAP = 6f;
    static final float SLOT_MAX_W = 230f;
    static final float CHECK_H = 20f;
    static final float COST_H = 24f;
    static final float BUTTON_H = 30f;
    static final int MAX_DMOD_PIPS = 5;

    private final SalvageYardsPanel panel;
    private final SalvageYardsView view;
    private final ModTransfer.Kind kind;
    private final List<FleetMemberAPI> members;
    private final float costMult;

    private FleetMemberAPI origin;
    private FleetMemberAPI target;

    private record Slot(String id, SlotState state) {
    }

    private interface Reason {
        String get(FleetMemberAPI m);
    }

    TransferTab(SalvageYardsPanel panel, ModTransfer.Kind kind) {
        this.panel = panel;
        this.view = panel.getView();
        this.kind = kind;
        this.members = ModTransfer.getMembers(panel.getMarket());
        this.costMult = ModTransfer.getCostMult(panel.getMarket());
    }

    void build(CustomPanelAPI p, float w, float h) {
        String unavailable = ModTransfer.getUnavailableReason(kind, panel.getMarket());
        if (unavailable != null) {
            Ui.wrap(p, 40f, 70f, w - 80f, unavailable, Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
            return;
        }

        List<FleetMemberAPI> origins = getOrigins();
        origin = select(origins, view.origin, m -> ModTransfer.getOriginReason(kind, m, members));
        view.origin = origin == null ? null : origin.getId();

        List<FleetMemberAPI> targets = getTargets();
        target = select(targets, view.target, m -> ModTransfer.getTargetReason(kind, origin, m));
        view.target = target == null ? null : target.getId();

        List<String> transferable = origin != null && target != null ? ModTransfer.getTransferable(kind, origin, target) : new ArrayList<>();
        view.mods.retainAll(transferable);
        if (kind == ModTransfer.Kind.SMOD && target != null) trimToFreeSlots();

        float sideW = (float) Math.floor((w - MID_GAP) / 2f);
        float toX = w - sideW;
        float y = SalvageYardsPanel.BODY_Y;

        String toNote = origin == null ? null : getTargetNote();
        Ui.heading(p, 0f, y, sideW, "From", kind == ModTransfer.Kind.SMOD ? "ships with S-mods" : "ships with D-mods");
        Ui.heading(p, toX, y, sideW, "To", toNote);
        y += 24f;

        addStrip(p, 0f, y, sideW, origins, origin, SalvageYardsView.FROM, m -> ModTransfer.getOriginReason(kind, m, members), m -> {
            view.origin = m.getId();
            view.target = null;
            view.mods.clear();
            view.scroll.reset(SalvageYardsView.TO);
            view.scroll.reset(SalvageYardsView.MODS);
        });
        if (origin == null) Ui.text(p, toX + 8f, y + 14f, sideW - 16f, "Select a ship to move from.", Ui.gray());
        else addStrip(p, toX, y, sideW, targets, target, SalvageYardsView.TO, m -> ModTransfer.getTargetReason(kind, origin, m), m -> {
            view.target = m.getId();
            view.mods.clear();
        });
        y += STRIP_H + 8f;

        addCard(p, 0f, y, sideW, origin);
        Ui.textIn(p, sideW, y, toX - sideW, CARD_H, ">", Ui.ORB20, Ui.hl(), Ui.CENTER, new String[0], new Color[0]);
        addCard(p, toX, y, sideW, target);
        y += CARD_H + 10f;

        float transferH = getTransferHeight(sideW);
        float midH = Math.max(MOD_STEP + 24f, h - y - transferH);
        addMods(p, 0f, y, sideW, midH);
        addSlots(p, toX, y, sideW, midH);

        addTransfer(p, w, sideW, toX, y + midH + 10f);
    }

    private List<FleetMemberAPI> getOrigins() {
        List<FleetMemberAPI> list = new ArrayList<>();
        for (FleetMemberAPI m : members) {
            if (!ModTransfer.getMovable(kind, m).isEmpty()) list.add(m);
        }
        return list;
    }

    private List<FleetMemberAPI> getTargets() {
        List<FleetMemberAPI> list = new ArrayList<>();
        if (origin == null) return list;

        for (FleetMemberAPI m : members) {
            if (m != origin && ModTransfer.isSameSizeAndTechtype(origin, m)) list.add(m);
        }
        return list;
    }

    // keeps the stored pick while it is still valid, else the first valid ship
    private static FleetMemberAPI select(List<FleetMemberAPI> list, String id, Reason reason) {
        for (FleetMemberAPI m : list) {
            if (m.getId().equals(id) && reason.get(m) == null) return m;
        }

        for (FleetMemberAPI m : list) {
            if (reason.get(m) == null) return m;
        }
        return null;
    }

    private void trimToFreeSlots() {
        int free = Math.max(0, ModTransfer.getFreeSlots(target));
        Iterator<String> it = view.mods.iterator();
        int n = 0;
        while (it.hasNext()) {
            it.next();
            if (++n > free) it.remove();
        }
    }

    private String getTargetNote() {
        ShipHullSpecAPI spec = origin.getHullSpec();
        String manufacturer = spec.getManufacturer() != null ? spec.getManufacturer() + " " : "";
        String note = manufacturer + getSizePlural(spec.getHullSize());
        return kind == ModTransfer.Kind.DMOD ? note + " that can be restored" : note;
    }

    private static String getSizePlural(ShipAPI.HullSize size) {
        return switch (size) {
            case FRIGATE -> "frigates";
            case DESTROYER -> "destroyers";
            case CRUISER -> "cruisers";
            case CAPITAL_SHIP -> "capital ships";
            default -> "ships";
        };
    }

    private void addStrip(CustomPanelAPI p, float x, float y, float w, List<FleetMemberAPI> ships, FleetMemberAPI selected, String id,
                          Reason reason, Consumer<FleetMemberAPI> onPick) {
        UiScroll.Column list = UiScroll.list(p, x, y, w, STRIP_H, view.scroll, id);
        int per = Math.max(1, (int) ((list.width() + SHIP_STEP - SHIP_CELL) / SHIP_STEP));

        for (int i = 0; i < ships.size(); i += per) {
            CustomPanelAPI row = list.block(SHIP_CELL, i == 0 ? 0f : SHIP_STEP - SHIP_CELL);
            for (int j = i; j < Math.min(ships.size(), i + per); j++) {
                FleetMemberAPI m = ships.get(j);
                String why = reason.get(m);
                float cx = (j - i) * SHIP_STEP;

                ButtonAPI area = UiButtons.area(row, cx, 0f, SHIP_CELL, SHIP_CELL, null, m == selected, why == null, null, Ui.CENTER, () -> {
                    onPick.accept(m);
                    panel.refresh();
                });
                UiImages.ship(row, cx + 2f, 2f, SHIP_CELL - 4f, m, false, why != null);
                Ui.tooltip(row, area, m.getShipName(), getClassText(m), why != null ? why : getPlaceText(m));
            }
        }

        if (ships.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "No matching ship is here.", Ui.gray());
        list.end();
    }

    private static String getClassText(FleetMemberAPI m) {
        return m.getHullSpec().getHullNameWithDashClass() + " " + m.getHullSpec().getDesignation().toLowerCase();
    }

    private static String getPlaceText(FleetMemberAPI m) {
        return ModTransfer.isInFleet(m) ? "in fleet" : "in storage";
    }

    private void addCard(CustomPanelAPI p, float x, float y, float w, FleetMemberAPI m) {
        if (m == null) {
            Ui.dashbox(p, x, y, w, CARD_H);
            Ui.textIn(p, x, y, w, CARD_H, "No ship selected", Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
            return;
        }

        CustomPanelAPI card = Ui.card(p, x, y, w, CARD_H);
        UiImages.ship(card, 6f, 6f, CARD_SPRITE, m, false, false);
        float tx = CARD_SPRITE + 16f;
        float tw = w - tx - 8f;

        Ui.text(card, tx, 6f, tw, Ui.fit(m.getShipName(), Ui.INS21, tw), Ui.INS21, Ui.text(), Ui.LEFT);
        Ui.text(card, tx, 32f, tw, Ui.fit(getClassText(m), Ui.INS15, tw));
        String manufacturer = m.getHullSpec().getManufacturer();
        String place = (manufacturer != null ? manufacturer + " · " : "") + getPlaceText(m);
        Ui.text(card, tx, 50f, tw, Ui.fit(place, Ui.INS15, tw), Ui.gray());

        if (kind == ModTransfer.Kind.SMOD) {
            int max = ModTransfer.getMaxSMods(m);
            int used = ModTransfer.getSModList(m).size();
            if (max > 0) UiData.pips(card, tx, 78f, max, Math.min(used, max), 9f, 3f, Ui.story());
            String count = used + " of " + max;
            float px = max > 0 ? tx + max * 12f + 6f : tx;
            Ui.runs(card, px, 72f, w - px - 8f, Ui.INS15, Ui.gray(), Ui.LEFT, count + " S-mods", new String[]{count}, new Color[]{Ui.story()});
        } else {
            int n = ModTransfer.getDModList(m).size();
            int pips = Math.max(MAX_DMOD_PIPS, n);
            UiData.pips(card, tx, 78f, pips, n, 9f, 3f, Ui.neg());
            String count = n + (n == 1 ? " D-mod" : " D-mods");
            float px = tx + pips * 12f + 6f;
            Ui.runs(card, px, 72f, w - px - 8f, Ui.INS15, Ui.gray(), Ui.LEFT, count, new String[]{n + ""}, new Color[]{Ui.neg()});
        }
    }

    private void addMods(CustomPanelAPI p, float x, float y, float w, float h) {
        String noun = kind == ModTransfer.Kind.SMOD ? "S-mods" : "D-mods";
        Ui.heading(p, x, y, w, noun + " to move", origin == null ? null : view.mods.size() + " selected");

        UiScroll.Column list = UiScroll.list(p, x, y + 24f, w, h - 24f, view.scroll, SalvageYardsView.MODS);
        List<String> mods = origin == null ? new ArrayList<>() : ModTransfer.getMovable(kind, origin);
        List<String> transferable = origin != null && target != null ? ModTransfer.getTransferable(kind, origin, target) : new ArrayList<>();
        int free = kind == ModTransfer.Kind.SMOD && target != null ? ModTransfer.getFreeSlots(target) : Integer.MAX_VALUE;

        for (int i = 0; i < mods.size(); i++) {
            addModRow(list.block(MOD_ROW_H, i == 0 ? 0f : MOD_STEP - MOD_ROW_H), list.width(), mods.get(i), transferable, free);
        }

        if (mods.isEmpty()) Ui.text(list.block(24f, 4f), 8f, 2f, list.width() - 16f, "Select a ship to move from.", Ui.gray());
        list.end();
    }

    private void addModRow(CustomPanelAPI row, float w, String id, List<String> transferable, int free) {
        boolean on = view.mods.contains(id);
        String tName = target == null ? null : target.getShipName();
        boolean ok;
        String status;

        if (target == null) {
            ok = false;
            status = "No ship to move it to.";
        } else if (!transferable.contains(id)) {
            ok = false;
            status = kind == ModTransfer.Kind.SMOD ? "Not installed on " + tName : tName + " has it already";
        } else if (!on && view.mods.size() >= free) {
            ok = false;
            status = tName + " has no free S-mod slot left.";
        } else {
            ok = true;
            status = kind == ModTransfer.Kind.SMOD ? "Installed on " + tName : "Moves to " + tName;
        }

        UiButtons.area(row, 0f, 0f, w, MOD_ROW_H, null, on, ok, null, Ui.CENTER, () -> {
            if (!view.mods.remove(id)) view.mods.add(id);
            panel.refresh();
        });

        UiImages.hullmod(row, 6f, (MOD_ROW_H - MOD_ICON) / 2f, MOD_ICON, id);
        float tx = MOD_ICON + 14f;
        Ui.text(row, tx, 1f, w - tx - 8f, Ui.fit(Global.getSettings().getHullModSpec(id).getDisplayName(), Ui.INS15, w - tx - 8f), on ? Ui.bright() : Ui.text());
        UiData.checkrow(row, tx, 18f, w - tx - 8f, ok, Ui.fit(status, Ui.INS15, w - tx - 26f));
    }

    private void addSlots(CustomPanelAPI p, float x, float y, float w, float h) {
        UiScroll.Column column = UiScroll.list(p, x, y, w, h, view.scroll, SalvageYardsView.SLOTS);
        float cw = column.width();

        if (target == null) {
            CustomPanelAPI b = column.block(48f, 0f);
            Ui.heading(b, 0f, 0f, cw, kind == ModTransfer.Kind.SMOD ? "S-mod slots" : "D-mods");
            Ui.text(b, 8f, 26f, cw - 16f, "No ship to move to.", Ui.gray());
            column.end();
            return;
        }

        List<Slot> slots = new ArrayList<>();
        int now;
        int after;
        int max = 0;

        if (kind == ModTransfer.Kind.SMOD) {
            max = ModTransfer.getMaxSMods(target);
            for (String id : ModTransfer.getSModList(target)) slots.add(new Slot(id, SlotState.BUILT));
            for (String id : view.mods) slots.add(new Slot(id, SlotState.INCOMING));
            now = ModTransfer.getSModList(target).size();
            after = now + view.mods.size();
            while (slots.size() < max) slots.add(new Slot(null, SlotState.FREE));
        } else {
            for (String id : ModTransfer.getDModList(target)) slots.add(new Slot(id, SlotState.HAS));
            for (String id : view.mods) slots.add(new Slot(id, SlotState.INCOMING));
            now = ModTransfer.getDModList(target).size();
            after = now + view.mods.size();
        }

        int count = Math.max(1, kind == ModTransfer.Kind.SMOD ? Math.max(max, slots.size()) : 3);
        float need = 0f;
        for (Slot s : slots) if (s.id() != null) need = Math.max(need, Ui.width(Global.getSettings().getHullModSpec(s.id()).getDisplayName(), Ui.INS15) + 44f);
        count = Math.max(1, Math.min(count, (int) ((cw + SLOT_GAP) / (need + SLOT_GAP))));
        float slotW = Math.min(SLOT_MAX_W, (float) Math.floor((cw - (count - 1) * SLOT_GAP) / count));
        int per = Math.max(1, (int) ((cw + SLOT_GAP) / (slotW + SLOT_GAP)));
        int rows = Math.max(1, Math.min(3, (slots.size() + per - 1) / per));
        float slotsH = slots.isEmpty() ? 20f : rows * (SLOT_H + SLOT_GAP);

        CustomPanelAPI b = column.block(24f + slotsH + 4f + 2f * 19f, 0f);
        String title = kind == ModTransfer.Kind.SMOD ? "S-mod slots" : "D-mods on " + target.getShipName();
        Ui.heading(b, 0f, 0f, cw, Ui.fit(title, Ui.ORB12, cw - 16f));

        if (slots.isEmpty()) Ui.text(b, 8f, 26f, cw - 16f, "It has no S-mod slots.", Ui.gray());
        else UiImages.iconrow(b, 0f, 24f, cw, slots, slotW, SLOT_H, SLOT_GAP, 3, this::addSlot, s -> s.id() == null ? "Free slot" :
                Global.getSettings().getHullModSpec(s.id()).getDisplayName());

        float gy = 24f + slotsH + 4f;
        if (kind == ModTransfer.Kind.SMOD) {
            UiData.grid(b, 0f, gy, cw, 19f, List.of(
                    UiData.GridRow.of("Used", now + " of " + max),
                    UiData.GridRow.of("After the transfer", after + " of " + max, view.mods.isEmpty() ? Ui.gray() : Ui.story())));
        } else {
            UiData.grid(b, 0f, gy, cw, 19f, List.of(
                    UiData.GridRow.of("Now", now + (now == 1 ? " D-mod" : " D-mods")),
                    UiData.GridRow.of("After the transfer", after + (after == 1 ? " D-mod" : " D-mods"), view.mods.isEmpty() ? Ui.gray() : Ui.neg())));
        }

        if (kind == ModTransfer.Kind.SMOD) addHullmods(column, cw);
        column.end();
    }

    private void addSlot(CustomPanelAPI p, float x, float y, float w, float h, Slot slot) {
        if (slot.state() == SlotState.FREE) {
            Ui.dashbox(p, x, y, w, h);
            Ui.textIn(p, x, y, w, h, "Free", Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
            return;
        }

        Color c = switch (slot.state()) {
            case BUILT -> Ui.story();
            case HAS -> Ui.neg();
            default -> Ui.hl();
        };
        String label = switch (slot.state()) {
            case BUILT -> "Built in";
            case HAS -> "Has it";
            default -> "Incoming";
        };

        CustomPanelAPI box = Ui.box(p, x, y, w, h, Misc.scaleAlpha(c, 0.8f), null);
        UiImages.hullmod(box, 6f, (h - 24f) / 2f, 24f, slot.id());
        float tw = w - 40f;
        Ui.text(box, 36f, 4f, tw, Ui.fit(Global.getSettings().getHullModSpec(slot.id()).getDisplayName(), Ui.INS15, tw));
        Ui.text(box, 36f, 22f, tw, label, c);
    }

    private void addHullmods(UiScroll.Column column, float cw) {
        List<String> mods = ModTransfer.getNormalMods(target);
        float icon = 28f;
        int per = Math.max(1, (int) ((cw + 4f) / (icon + 4f)));
        float rowsH = mods.isEmpty() ? 20f : Math.min(2, (mods.size() + per - 1) / per) * (icon + 4f);

        CustomPanelAPI b = column.block(24f + rowsH, 8f);
        Ui.heading(b, 0f, 0f, cw, "Hullmods", "on " + target.getShipName());
        if (mods.isEmpty()) {
            Ui.text(b, 8f, 26f, cw - 16f, "No hullmods are installed.", Ui.gray());
            return;
        }

        UiImages.iconrow(b, 0f, 24f, cw, mods, icon, icon, 4f, 2, (c, cx, cy, w, h, id) -> {
            if (view.mods.contains(id)) Ui.box(c, cx, cy, w, h, Ui.hl(), null);
            UiImages.hullmod(c, cx + 2f, cy + 2f, w - 4f, id);
        }, id -> Global.getSettings().getHullModSpec(id).getDisplayName());
    }

    private float getTransferHeight(float sideW) {
        float left = 3f * CHECK_H + 4f + Ui.wrapHeight(getSummary(), Ui.INS15, sideW);
        float right = (kind == ModTransfer.Kind.SMOD ? 2f * COST_H : COST_H + 20f) + 6f + UiButtons.guardHeight(sideW, BUTTON_H, getConfirmReason());
        return 10f + 24f + Math.max(left, right);
    }

    private String getSummary() {
        if (origin == null || target == null) return "Select a ship on both sides.";

        String o = origin.getShipName();
        String t = target.getShipName();
        if (kind == ModTransfer.Kind.SMOD) return o + " loses the selected S-mods and their hullmods. " + t + " gets them built in.";
        return o + " loses the selected D-mods and is restored once it has none left. " + t + " takes them on.";
    }

    private int getCredits() {
        if (origin == null) return 0;
        return Math.round(ModTransfer.getCreditsPerMod(kind, origin, costMult) * view.mods.size());
    }

    private int getRelics() {
        if (kind != ModTransfer.Kind.SMOD || target == null) return 0;
        return ModTransfer.getRelicsPerMod(target) * view.mods.size();
    }

    private String getConfirmReason() {
        if (origin == null || target == null || view.mods.isEmpty())
            return kind == ModTransfer.Kind.SMOD ? "Select the S-mods to transfer." : "Select the D-mods to transfer.";
        if (!BillingHelper.canAfford(getCredits())) return "You do not have enough credits.";
        if (!RelicHelper.canAfford(panel.getMarket(), getRelics())) return "Not enough " + RelicHelper.getName() + ".";
        return null;
    }

    private void addTransfer(CustomPanelAPI p, float w, float sideW, float toX, float y) {
        Ui.heading(p, 0f, y, w, "Transfer");
        y += 24f;

        int n = view.mods.size();
        String noun = kind == ModTransfer.Kind.SMOD ? "S-mods" : "D-mods";
        String oName = origin == null ? "the source ship" : origin.getShipName();
        String tName = target == null ? "the target ship" : target.getShipName();

        UiData.checkrow(p, 0f, y, sideW, origin != null && target != null, Ui.fit("Same size and manufacturer as " + oName, Ui.INS15, sideW - 20f));
        if (kind == ModTransfer.Kind.SMOD) {
            int free = target == null ? 0 : ModTransfer.getFreeSlots(target);
            UiData.checkrow(p, 0f, y + CHECK_H, sideW, free > 0 && free >= n, Ui.fit("Free S-mod slots on " + tName + ": " + Math.max(0, free), Ui.INS15, sideW - 20f));
        } else {
            boolean restorable = target != null && ModTransfer.isRestorable(target);
            UiData.checkrow(p, 0f, y + CHECK_H, sideW, restorable, Ui.fit(tName + " can be restored", Ui.INS15, sideW - 20f));
        }
        UiData.checkrow(p, 0f, y + 2f * CHECK_H, sideW, n > 0, noun + " selected: " + n);
        Ui.wrap(p, 0f, y + 3f * CHECK_H + 4f, sideW, getSummary(), Ui.gray());

        float perMod = origin == null ? 0f : ModTransfer.getCreditsPerMod(kind, origin, costMult);
        float cy = y;
        if (kind == ModTransfer.Kind.SMOD) {
            int relicsPer = target == null ? 0 : ModTransfer.getRelicsPerMod(target);
            UiData.relicRow(p, toX, cy, sideW, RelicHelper.getName() + ", " + Ui.num(relicsPer) + " per S-mod", getRelics(), panel.getMarket());
            cy += COST_H;
            UiData.creditsRow(p, toX, cy, sideW, "Fee, " + Ui.credits(perMod) + " per S-mod", getCredits());
            cy += COST_H;
        } else {
            UiData.creditsRow(p, toX, cy, sideW, "Cost per D-mod: " + Ui.credits(perMod), getCredits());
            cy += COST_H;
            AssemblyTab.priceLine(p, toX, cy, sideW, "Transfer at cost", panel.getMarket().getFaction(), costMult);
            cy += 20f;
        }

        UiButtons.guarded(p, toX, cy + 6f, sideW, BUTTON_H, "Transfer", UiButtons.Kind.GREEN, getConfirmReason(), this::apply);
    }

    private void apply() {
        int credits = getCredits();
        int relics = getRelics();
        List<String> mods = new ArrayList<>(view.mods);

        if (relics > 0) RelicHelper.take(panel.getMarket(), relics);
        BillingHelper.chargeNow(credits);
        if (kind == ModTransfer.Kind.SMOD) ModTransfer.moveSMods(origin, target, mods);
        else ModTransfer.moveDMods(origin, target, mods);

        String noun = kind == ModTransfer.Kind.SMOD ? (mods.size() == 1 ? " S-mod" : " S-mods") : (mods.size() == 1 ? " D-mod" : " D-mods");
        String cost = Misc.getDGSCredits(credits) + (relics > 0 ? " and " + relics + " " + RelicHelper.getName() : "");
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage("Moved " + mods.size() + noun + " from " + origin.getShipName()
                + " to " + target.getShipName() + " for " + cost, Misc.getHighlightColor());
        Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);

        view.mods.clear();
        panel.refresh();
    }
}
