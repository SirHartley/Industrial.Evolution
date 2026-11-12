package indevo.industries.engineeringhub.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.economy.RelicSpeedUpTarget;
import indevo.economy.plugins.RelicSpeedUpDialog;
import indevo.ids.Ids;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.industries.engineeringhub.industry.EngineeringHub.BlueprintSource;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static indevo.utils.helper.StringHelper.getDayOrDays;

// reverse engineering tab of the hub panel: ship in work, drop off, progress table, finishing a hull at 100%
class ReverseEngineeringTab {

    static final float DETAIL_W = 404f;
    static final float COLUMN_GAP = 20f;
    static final float BODY_Y = 64f;
    static final float CARD_Y = 88f;
    static final float CARD_H = 60f;
    static final float DROP_Y = 154f;
    static final float TABLE_Y = 196f;
    static final float ROW_H = 46f;
    static final float ROW_GAP = 4f;
    static final float BAR_X = 240f;
    static final float BAR_W = 160f;
    static final float BAR_H = 20f;
    static final float DROP_CELL = 32f;
    static final float PICTURE_H = 84f;
    static final float PICTURE = 76f;
    static final float PAY_Y = 178f;
    static final float PAY_ROW_H = 34f;
    static final float PAY_ROW_GAP = 3f;
    static final float PAY_ICON = 30f;
    static final float BUTTON_H = 28f;
    static final float CHECK_H = 20f;
    static final float GRID_ROW = 19f;

    private final EngineeringHubPanelDelegate panel;
    private final EngineeringHub hub;
    private final MarketAPI market;
    private final EngineeringHubView view;

    record Hull(String id, ShipHullSpecAPI spec, float progress) {
    }

    ReverseEngineeringTab(EngineeringHubPanelDelegate panel) {
        this.panel = panel;
        this.hub = panel.hub;
        this.market = panel.market;
        this.view = panel.view;
    }

    void build(CustomPanelAPI p, float w, float h) {
        List<Hull> hulls = getHulls();
        Hull selected = null;
        for (Hull hull : hulls) {
            if (hull.id().equals(view.hull)) selected = hull;
        }

        if (selected == null && !hulls.isEmpty()) selected = hulls.get(0);
        view.hull = selected != null ? selected.id() : null;

        float lw = w - DETAIL_W - COLUMN_GAP;
        float rx = lw + COLUMN_GAP;
        addInWork(p, lw);
        addDropOff(p, lw);
        addProgress(p, lw, h, hulls);
        Ui.vrule(p, rx - 10f, BODY_Y, h - BODY_Y);
        addDetail(p, rx, w - rx, h, selected);
    }

    static void addHelp(TooltipMakerAPI tooltip, EngineeringHub hub) {
        Color hl = Misc.getHighlightColor();
        float pad = 10f;
        String frigate = Math.round(hub.getCleanResearchValue(HullSize.FRIGATE) * 100f) + "%";
        String capital = Math.round(hub.getCleanResearchValue(HullSize.CAPITAL_SHIP) * 100f) + "%";
        String days = hub.getBaseDays(HullSize.FRIGATE) + " days";
        String capitalDays = hub.getBaseDays(HullSize.CAPITAL_SHIP) + " days";

        tooltip.addPara("The hub takes one ship a day from the drop off and takes it apart over %s (%s for capital ships), faster with an AI core.", 0f, hl, days, capitalDays);
        tooltip.addPara("Each ship adds %s to the progress of its hull (%s for capital ships). Every D-mod cuts that, more so the more D-mods the ship has.", pad, hl, frigate, capital);
        tooltip.addPara("At %s, overwrite a ship blueprint of the same hull size with the new data, or pay %s per deployment point of the hull instead.", pad, hl,
                "100%", EngineeringHub.RELICS_PER_DP_FOR_BP_CREATION + " " + RelicHelper.getName());
        tooltip.addPara("Blueprints come from the hub's slot and the %s, Relic Components from the %s. Tiandong and Roider hulls take their retrofit item and give a retrofit template.", pad, hl,
                getSharedName(), getSharedName());
        tooltip.addPara("Accelerate halves the days left of the ship in work for %s per day left, once every %s.", pad, hl,
                RelicSpeedUpTarget.RELICS_PER_DAY + " " + RelicHelper.getName(), Math.round(RelicSpeedUpTarget.COOLDOWN_DAYS) + " days");
    }

    private void addInWork(CustomPanelAPI p, float lw) {
        Ui.heading(p, 0f, BODY_Y, lw, "In work", Ui.LEFT);
        CustomPanelAPI card = Ui.card(p, 0f, CARD_Y, lw, CARD_H);

        ShipVariantAPI variant = hub.getCurrentDeconShipVar();
        if (variant == null) {
            Ui.dashbox(card, 4f, 4f, 52f, 52f);
            Ui.text(card, 64f, 20f, lw - 72f, "No ship is currently being reverse engineered.", Ui.gray());
            return;
        }

        ShipHullSpecAPI spec = variant.getHullSpec();
        String adds = "+" + Math.round(hub.getResearchValue(variant) * 100f) + "%";
        UiImages.hull(card, 4f, 4f, 52f, spec, true, false);
        Ui.text(card, 64f, 8f, BAR_X - 72f, spec.getHullNameWithDashClass());
        Ui.runs(card, 64f, 27f, BAR_X - 72f, Ui.INS15, Ui.gray(), Ui.LEFT, Misc.getHullSizeStr(spec.getHullSize()) + " · adds " + adds,
                new String[]{adds}, new Color[]{Ui.pos()});

        int total = hub.getSpeedUpDaysTotal();
        int left = hub.getSpeedUpDaysLeft();
        float fraction = total <= 0 ? 1f : Math.max(0f, Math.min(1f, (total - left) / (float) total));
        UiData.bar(card, BAR_X, 10f, BAR_W, BAR_H, fraction, Ui.barBlue(), "Day " + Math.max(0, total - left) + " of " + total);

        RelicSpeedUpTarget target = hub;
        int cost = target.getSpeedUpCost();
        String reason = null;
        float cooldown = target.getSpeedUpCooldownLeft();
        if (cooldown > 0f) {
            int days = (int) Math.ceil(cooldown);
            reason = "Unavailable for another " + days + " " + getDayOrDays(days) + ".";
        } else if (!target.canSpeedUp()) reason = "Too few days left.";
        else if (!RelicHelper.canAfford(market, cost)) reason = "Not enough " + RelicHelper.getName() + ".";

        if (reason != null) Ui.text(card, BAR_X, 37f, lw - 10f - BAR_X, reason, Ui.neg(), Ui.RIGHT);
        else Ui.runs(card, BAR_X, 37f, BAR_W, Ui.INS15, Ui.gray(), Ui.CENTER, left + " " + getDayOrDays(left) + " left", new String[]{left + ""}, new Color[]{Ui.hl()});

        boolean blocked = reason != null;
        ButtonAPI button = UiButtons.button(card, lw - 154f, 9f, 146f, 26f, "Accelerate", blocked ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.ALL,
                UiImages.relicSprite(), Ui.num(cost), Ui.ORB12, blocked ? null : () -> {
                    RelicSpeedUpDialog.apply(target);
                    panel.refresh();
                });
        Ui.tooltip(card, button, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.LEFT, t -> RelicSpeedUpDialog.addTooltip(t, target));
    }

    private void addDropOff(CustomPanelAPI p, float lw) {
        Ui.text(p, 4f, DROP_Y + 8f, 168f, "Waiting in the drop off", Ui.gray());
        List<FleetMemberAPI> ships = getDropOff();
        if (ships.isEmpty()) {
            Ui.text(p, 176f, DROP_Y + 8f, lw - 184f, "None", Ui.gray());
            return;
        }

        String count = Ui.num(ships.size());
        String line = count + (ships.size() == 1 ? " ship" : " ships") + ", one is taken each day";
        float tw = Math.min(Ui.width(line, Ui.INS15) + 2f, lw - 8f - 176f - DROP_CELL - 8f);
        Ui.runs(p, lw - 8f - tw, DROP_Y + 8f, tw, Ui.INS15, Ui.gray(), Ui.RIGHT, line, new String[]{count}, new Color[]{Ui.hl()});
        UiImages.iconrow(p, 176f, DROP_Y, lw - 8f - tw - 8f - 176f, ships, DROP_CELL, DROP_CELL, 2f, 1,
                (c, x, y, cw, ch, member) -> UiImages.ship(c, x, y, cw, member, true, false),
                member -> member.getShipName() + ", " + member.getHullSpec().getHullNameWithDashClass());
    }

    private List<FleetMemberAPI> getDropOff() {
        SubmarketAPI drop = market.getSubmarket(Ids.ENGSTORAGE);
        if (drop == null) return new ArrayList<>();

        CargoAPI cargo = drop.getCargo();
        cargo.initMothballedShips(Global.getSector().getPlayerFaction().getId());
        return cargo.getMothballedShips().getMembersListCopy();
    }

    private void addProgress(CustomPanelAPI p, float lw, float h, List<Hull> hulls) {
        Ui.heading(p, 0f, TABLE_Y, lw, "Blueprint progress", Ui.LEFT);
        if (hulls.isEmpty()) {
            Ui.text(p, 8f, TABLE_Y + Ui.HEADING_H + 6f, lw - 16f, "You have not reverse engineered any ships yet.", Ui.gray());
            return;
        }

        float rw = lw - UiScroll.GUTTER;
        float nx = BAR_X + BAR_W + 10f;
        Ui.thead(p, 0f, TABLE_Y + 20f, lw, new String[]{"Hull", "Progress", "Next"}, new float[]{52f, BAR_X, nx},
                new float[]{180f, BAR_W, rw - 8f - nx}, new Alignment[]{Ui.LEFT, Ui.CENTER, Ui.RIGHT});

        String inWork = hub.getCurrentDeconShipVar() != null ? hub.getBaseHullId(hub.getCurrentDeconShipVar().getHullSpec()) : null;
        UiScroll.Column list = UiScroll.list(p, 0f, TABLE_Y + 42f, lw, h - TABLE_Y - 42f, view.scroll, EngineeringHubView.LIST);
        for (int i = 0; i < hulls.size(); i++) {
            Hull hull = hulls.get(i);
            boolean selected = hull.id().equals(view.hull);
            CustomPanelAPI row = UiButtons.row(list.block(ROW_H, i == 0 ? 0f : ROW_GAP), 0f, 0f, rw, ROW_H, selected, true, null, () -> selectHull(hull.id()));

            boolean ready = hull.progress() >= 1f;
            UiImages.hull(row, 3f, 2f, 42f, hull.spec(), true, false);
            Ui.text(row, 52f, 5f, BAR_X - 60f, hull.spec().getHullNameWithDashClass(), selected ? Ui.bright() : Ui.text());
            Ui.text(row, 52f, 23f, BAR_X - 60f, Misc.getHullSizeStr(hull.spec().getHullSize()), Ui.gray());
            UiData.bar(row, BAR_X, 12f, BAR_W, BAR_H, hull.progress(), ready ? Ui.pos() : Ui.barBlue(), Math.round(Math.min(1f, hull.progress()) * 100f) + "%");

            String next;
            Color color;
            if (ready) {
                next = "Ready to finish";
                color = Ui.pos();
            } else if (hull.id().equals(inWork)) {
                next = "In work";
                color = Ui.text();
            } else {
                int more = getShipsNeeded(hull);
                next = more + (more == 1 ? " more ship" : " more ships");
                color = more == 1 ? Ui.text() : Ui.gray();
            }

            Ui.text(row, nx, 13f, rw - 8f - nx, next, color, Ui.RIGHT);
        }

        list.end();
    }

    // counts ships without D-mods
    private int getShipsNeeded(Hull hull) {
        float clean = hub.getCleanResearchValue(hull.spec().getHullSize());
        if (clean <= 0f) return 0;
        return Math.max(1, (int) Math.ceil((1f - hull.progress()) / clean - 0.001f));
    }

    private void selectHull(String id) {
        if (!id.equals(view.hull)) {
            view.hull = id;
            view.payment = null;
            view.scroll.reset(EngineeringHubView.PAYMENTS);
        }

        panel.refresh();
    }

    private void addDetail(CustomPanelAPI p, float x, float w, float h, Hull hull) {
        if (hull == null) {
            Ui.heading(p, x, BODY_Y, w, "Blueprint progress");
            Ui.text(p, x, BODY_Y + 26f, w, "No hull to show.", Ui.gray());
            addFinishOnOwn(p, x, h - CHECK_H, w, null);
            return;
        }

        Ui.heading(p, x, BODY_Y, w, hull.spec().getHullNameWithDashClass());
        Ui.well(p, x, BODY_Y + 22f, w, PICTURE_H);
        UiImages.hull(p, x + Math.round((w - PICTURE) / 2f), BODY_Y + 26f, PICTURE, hull.spec(), false, false);

        if (hull.progress() >= 1f) addPayment(p, x, w, h, hull);
        else addPending(p, x, w, h, hull);
    }

    private void addPending(CustomPanelAPI p, float x, float w, float h, Hull hull) {
        boolean inWork = hub.getCurrentDeconShipVar() != null && hull.id().equals(hub.getBaseHullId(hub.getCurrentDeconShipVar().getHullSpec()));
        int more = getShipsNeeded(hull);
        Ui.heading(p, x, PAY_Y, w, "Blueprint progress", Ui.LEFT);
        UiData.grid(p, x, PAY_Y + 26f, w, GRID_ROW, List.of(
                GridRow.of("Progress", Math.round(hull.progress() * 100f) + "%"),
                GridRow.of("Each ship without D-mods adds", "+" + Math.round(hub.getCleanResearchValue(hull.spec().getHullSize()) * 100f) + "%", Ui.pos()),
                GridRow.of("Ships needed", more + ""),
                GridRow.of("Status", inWork ? "In work" : "Waiting for ships", Ui.text())));
        addFinishOnOwn(p, x, h - CHECK_H, w, hull);
    }

    private void addPayment(CustomPanelAPI p, float x, float w, float h, Hull hull) {
        List<BlueprintSource> sources = hub.getBlueprintSources(hull.id());
        int relicCost = hub.getRelicCost(hull.id());
        int relicStock = hub.getRelicStock();
        boolean relicsOk = relicStock >= relicCost;
        boolean blueprint = Items.SHIP_BP.equals(EngineeringHub.getPaymentItemId(hull.id()));
        String size = Misc.getHullSizeStr(hull.spec().getHullSize()).toLowerCase();
        String kind = blueprint ? size + " blueprints" : "retrofit items";

        BlueprintSource picked = resolvePayment(sources, relicsOk);
        boolean relics = EngineeringHubView.RELICS.equals(view.payment);

        String reason = null;
        if (sources.isEmpty() && !relicsOk) reason = blueprint ? "No " + size + " blueprint to overwrite and not enough " + RelicHelper.getName() + "."
                : "No retrofit item to overwrite and not enough " + RelicHelper.getName() + ".";
        else if (relics && !relicsOk) reason = "Not enough " + RelicHelper.getName() + ".";
        float guardH = UiButtons.guardHeight(w, BUTTON_H, reason);

        float listY = PAY_Y + 24f;
        float bottom = 4f + PAY_ROW_H + 14f + 28f + 22f + 28f + guardH + 6f + CHECK_H;
        float listH = Math.max(PAY_ROW_H + PAY_ROW_GAP, h - listY - bottom);

        Ui.heading(p, x, PAY_Y, w, blueprint ? "Blueprint to overwrite" : "Retrofit item to overwrite", kind);
        if (sources.isEmpty()) Ui.text(p, x + 8f, listY + 8f, w - 16f, blueprint ? "No " + size + " blueprint in the hub or the " + getSharedName() + "."
                : "No retrofit item in the hub or the " + getSharedName() + ".", Ui.gray());
        else {
            UiScroll.Column list = UiScroll.list(p, x, listY, w, listH, view.scroll, EngineeringHubView.PAYMENTS);
            float rw = list.width();
            for (int i = 0; i < sources.size(); i++) {
                BlueprintSource source = sources.get(i);
                boolean on = source == picked;
                CustomPanelAPI row = UiButtons.row(list.block(PAY_ROW_H, i == 0 ? 0f : PAY_ROW_GAP), 0f, 0f, rw, PAY_ROW_H, on, true, null, () -> pick(getKey(source)));
                addRadio(row, on);
                UiImages.icon(row, 36f, 2f, PAY_ICON, getItemSprite(source.item().getId()), getItemName(source.item().getId()));
                ShipHullSpecAPI written = EngineeringHub.getHullSpecOrNull(source.item().getData());
                if (written != null) UiImages.hull(row, 70f, 2f, PAY_ICON, written, true, false);

                String where = source.installed() ? "Installed in the hub" : source.count() > 1 ? getSharedName() + ", " + Ui.num(source.count()) : getSharedName();
                float nameX = 108f;
                float sw = Math.min(Ui.width(where, Ui.INS15) + 2f, (rw - 14f - nameX) * 0.6f);
                Ui.text(row, rw - 14f - sw, 8f, sw, where, Ui.gray(), Ui.RIGHT);
                Ui.text(row, nameX, 8f, rw - 14f - sw - 8f - nameX, written != null ? written.getHullNameWithDashClass() : "Unknown hull", on ? Ui.bright() : Ui.text());
            }

            list.end();
        }

        float y = listY + listH + 4f;
        float rw = w - UiScroll.GUTTER;
        CustomPanelAPI relicRow = UiButtons.row(p, x, y, rw, PAY_ROW_H, relics, true, null, () -> pick(EngineeringHubView.RELICS));
        addRadio(relicRow, relics);
        UiImages.icon(relicRow, 36f, 2f, PAY_ICON, UiImages.relicSprite(), RelicHelper.getName());
        String amount = Ui.num(relicCost);
        String value = UiData.withStock(amount, Ui.num(relicStock), (rw - 14f - 74f) * 0.45f);
        float vw = Ui.width(value, Ui.INS15) + 2f;
        Ui.runs(relicRow, rw - 14f - vw, 8f, vw, Ui.INS15, Ui.gray(), Ui.RIGHT, value, new String[]{amount}, new Color[]{relicsOk ? Ui.hl() : Ui.neg()});
        Ui.text(relicRow, 74f, 8f, rw - 14f - vw - 8f - 74f, RelicHelper.getName() + " instead", relics ? Ui.bright() : Ui.text());
        Ui.tooltip(p, relicRow, RelicHelper.getName() + " instead", "Taken from the " + getSharedName() + ".");
        y += PAY_ROW_H + 14f;

        String output = blueprint ? hull.spec().getHullNameWithDashClass() + " blueprint" : hull.spec().getHullNameWithDashClass() + " retrofit template";
        String outputId = EngineeringHub.getPaymentItemId(hull.id());
        float ow = Math.min(Ui.width(output, Ui.INS15) + 2f, w - 100f - PAY_ICON - 6f);
        Ui.text(p, x, y, 90f, "You get", Ui.gray());
        Ui.text(p, x + w - ow, y, ow, output, Ui.pos(), Ui.RIGHT);
        String outputSprite = getItemSprite(outputId);
        if (outputSprite != null) UiImages.icon(p, x + w - ow - 6f - PAY_ICON, y - 6f, PAY_ICON, outputSprite, getItemName(outputId));
        y += 28f;

        String lose;
        if (relics) lose = Ui.num(relicCost) + " " + RelicHelper.getName();
        else if (picked != null) {
            ShipHullSpecAPI written = EngineeringHub.getHullSpecOrNull(picked.item().getData());
            String name = written != null ? written.getHullNameWithDashClass() : "Unknown hull";
            lose = "1 " + name + (blueprint ? " blueprint" : " " + getItemName(picked.item().getId()));
        } else lose = "Nothing";
        UiData.value(p, x, y, w, 20f, "You lose", lose, picked != null || relics ? Ui.neg() : Ui.gray(), new String[0], new Color[0]);
        y += 22f;

        MarketAPI target = hub.getDeliveryMarket();
        SubmarketAPI storage = MiscIE.getStorage(target);
        String delivered = storage != null ? target.getName() + ", " + storage.getNameOneLine() : target.getName();
        UiData.value(p, x, y, w, 20f, "Delivered to", delivered, Ui.text(), new String[0], new Color[0]);
        y += 28f;

        BlueprintSource pay = picked;
        boolean useRelics = relics;
        UiButtons.guarded(p, x, y, w, BUTTON_H, "Finish", UiButtons.Kind.GREEN, reason, () -> {
            if (!hub.finish(hull.id(), useRelics ? null : pay)) return;
            view.hull = null;
            view.payment = null;
            view.scroll.reset(EngineeringHubView.PAYMENTS);
            panel.refresh();
        });
        y += guardH + 6f;

        addFinishOnOwn(p, x, y, w, hull);
    }

    // keeps a valid pick in view.payment: the first blueprint, else relic components
    private BlueprintSource resolvePayment(List<BlueprintSource> sources, boolean relicsOk) {
        if (EngineeringHubView.RELICS.equals(view.payment)) return null;
        for (BlueprintSource source : sources) {
            if (getKey(source).equals(view.payment)) return source;
        }

        if (!sources.isEmpty()) {
            view.payment = getKey(sources.get(0));
            return sources.get(0);
        }

        view.payment = EngineeringHubView.RELICS;
        return null;
    }

    private void pick(String key) {
        view.payment = key;
        panel.refresh();
    }

    private static String getKey(BlueprintSource source) {
        if (source.installed()) return EngineeringHubView.INSTALLED;
        return source.item().getId() + "|" + source.item().getData();
    }

    private static void addRadio(CustomPanelAPI row, boolean on) {
        Ui.box(row, 10f, 11f, 12f, 12f, Misc.scaleAlpha(Ui.base(), 0.8f), Misc.setAlpha(Color.BLACK, 120));
        if (on) Ui.box(row, 13f, 14f, 6f, 6f, null, Ui.bright());
    }

    private void addFinishOnOwn(CustomPanelAPI p, float x, float y, float w, Hull hull) {
        String with;
        if (hull == null) with = "any matching blueprint";
        else if (Items.SHIP_BP.equals(EngineeringHub.getPaymentItemId(hull.id()))) with = "any " + Misc.getHullSizeStr(hull.spec().getHullSize()).toLowerCase() + " blueprint";
        else with = "its retrofit item";

        boolean on = hub.isFinishOnOwn();
        ButtonAPI check = UiButtons.check(p, x, y, w, "Finish on its own with " + with, on, true, () -> {
            hub.setFinishOnOwn(!on);
            panel.refresh();
        });
        Ui.tooltip(p, check, "Finish on its own",
                "On: a hull at 100% is finished on the next day with the first matching blueprint, the one installed in the hub first, then those in the " + getSharedName() + ". " + RelicHelper.getName() + " are never used.",
                "Off: a hull stays at 100% until you finish it here.");
    }

    private List<Hull> getHulls() {
        hub.refreshResearchProgressList();
        List<Hull> hulls = new ArrayList<>();
        for (Map.Entry<String, Float> entry : hub.researchProgressList.entrySet()) {
            ShipHullSpecAPI spec = EngineeringHub.getHullSpecOrNull(entry.getKey());
            if (spec != null) hulls.add(new Hull(entry.getKey(), spec, Math.min(1f, entry.getValue())));
        }

        hulls.sort(Comparator.comparing((Hull hull) -> hull.progress() < 1f)
                .thenComparing(Hull::progress, Comparator.reverseOrder())
                .thenComparing(hull -> hull.spec().getHullNameWithDashClass()));
        return hulls;
    }

    static String getSharedName() {
        for (SubmarketSpecAPI spec : Global.getSettings().getAllSubmarketSpecs()) {
            // the submarket name holds a line break for the market tab
            if (Ids.SHAREDSTORAGE.equals(spec.getId())) return spec.getName().replace('\n', ' ');
        }

        return "storage";
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
