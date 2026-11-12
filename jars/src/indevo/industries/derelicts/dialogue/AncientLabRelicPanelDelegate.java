package indevo.industries.derelicts.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.hullmods.Automated;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.items.ForgeTemplateItemPlugin;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AncientLabRelicPanelDelegate extends BaseCustomDialogDelegate {

    public static final int RELICS_PER_TEMPLATE_FP = 2;
    public static final int RELICS_PER_SHIP_DP = 10;

    public static final String REFINE_LORE = "Centuries of copying have left gaps in the template. Domain reference cores give the laboratory an intact pattern to work from.";
    public static final String INTEGRATE_LORE = "The automated hulls still expect commands from a Domain control network. Your technicians use relic components to build an interface their systems will recognise.";

    public static final float BOARD_W = 1004f;
    public static final float BOARD_H = 560f;
    static final float DETAIL_W = 384f;
    static final float COLUMN_GAP = 20f;
    static final float BODY_Y = 64f;
    static final float TAB_W = 150f;
    static final float ROW_H = 48f;
    static final float ROW_GAP = 4f;
    static final float MESSAGE_H = 24f;
    static final float THUMB = 44f;
    static final float COST_ICON = 20f;
    static final float PICTURE_H = 96f;
    static final float PICTURE = 88f;
    static final float PIP = 9f;
    static final float PIP_GAP = 3f;
    static final float BUTTON_H = 28f;
    static final float GRID_ROW = 19f;
    static final float GAP = 8f;

    private final Industry lab;
    private final MarketAPI market;
    private final AncientLabRelicView view;
    private final Runnable onLeave;
    private UiRoot root;
    private UiScroll.Column column;
    private float cw;
    private CustomPanelAPI pinned;
    private float pinY;
    private CustomPanelAPI detailP;
    private float detailX;
    private float detailH;
    private float detailActionH;

    static class Entry {
        String key;
        String name;
        String sub;
        int cost;
        ShipHullSpecAPI hull;
        CargoStackAPI stack;
        int level;
        String location;
        FleetMemberAPI member;
    }

    public AncientLabRelicPanelDelegate(Industry lab, AncientLabRelicView view, Runnable onLeave) {
        this.lab = lab;
        this.market = lab.getMarket();
        this.view = view;
        this.onLeave = onLeave;
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    public static void show(DialogCreatorUI ui, Industry lab, AncientLabRelicView view) {
        ui.showDialog(getWidth(), getHeight(), new AncientLabRelicPanelDelegate(lab, view, null));
    }

    // opens the panel for the market's lab in the given view, for code and tests
    public static boolean show(MarketAPI market, AncientLabRelicView view) {
        Industry lab = market.getIndustry(Ids.LAB);
        if (lab == null) return false;
        return UiHost.show(market.getPrimaryEntity(), dialog -> dialog.showCustomDialog(getWidth(), getHeight(), new AncientLabRelicPanelDelegate(lab, view, dialog::dismiss)));
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

    private void build(CustomPanelAPI p, float w, float h) {
        List<Entry> entries = view.ships ? getShipEntries() : getTemplateEntries();
        Entry selected = resolveSelection(entries);
        float lw = w - DETAIL_W - COLUMN_GAP;
        float rx = lw + COLUMN_GAP;

        addHeader(p, w);
        addList(p, lw, h, entries);
        Ui.vrule(p, rx - 10f, BODY_Y, h - BODY_Y);
        addDetail(p, rx, w - rx, h - BODY_Y, selected);
    }

    private Entry resolveSelection(List<Entry> entries) {
        if (AncientLabRelicView.FIRST.equals(view.selected)) view.selected = entries.isEmpty() ? null : entries.get(0).key;
        for (Entry entry : entries) {
            if (entry.key.equals(view.selected)) return entry;
        }

        view.selected = null;
        return null;
    }

    private void addHeader(CustomPanelAPI p, float w) {
        UiFrame.title(p, w, "Relic services", lab.getCurrentName() + ", " + market.getName(), this::addHelp);
        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, TAB_W, UiFrame.HEADER_H, new String[]{"Templates", "Ships"}, view.ships ? 1 : 0, null, i -> switchTab(i == 1));
        UiFrame.counter(p, w, UiFrame.HEADER_Y, RelicHelper.getName(), UiImages.relicSprite(), Ui.num(RelicHelper.getAvailable(market)));
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();

        if (!view.ships) {
            tooltip.addPara("Lower quality levels mean fewer defects and D-mods. The minimum is %s.", 0f, hl, "0");
            tooltip.addPara("Cost: %s %s per fleet point and quality level.", 10f, hl, RELICS_PER_TEMPLATE_FP + "", RelicHelper.getName());
            tooltip.addPara("Templates in the Industrial Supply Store and in storage are listed.", 10f);
        } else {
            tooltip.addPara("Permanently removes the automated ship CR penalty and exempts the ship from the Automated Ships skill limit.", 0f);
            tooltip.addPara("Cost: %s %s per deployment point.", 10f, hl, RELICS_PER_SHIP_DP + "", RelicHelper.getName());
        }

        tooltip.addPara("%s are taken from storage first, then from your cargo.", 10f, hl, RelicHelper.getName());
    }

    private void switchTab(boolean ships) {
        if (view.ships != ships) {
            view.ships = ships;
            view.selected = null;
            view.message = null;
            view.scroll.reset(AncientLabRelicView.LIST);
            view.scroll.reset(AncientLabRelicView.DETAIL);
        }

        root.refresh();
    }

    private void select(Entry entry) {
        if (!entry.key.equals(view.selected)) {
            view.selected = entry.key;
            view.scroll.reset(AncientLabRelicView.DETAIL);
        }

        view.message = null;
        root.refresh();
    }

    private void addList(CustomPanelAPI p, float lw, float h, List<Entry> entries) {
        Ui.heading(p, 0f, BODY_Y, lw, view.ships ? "Automated ships" : "Forge Templates", Ui.LEFT);

        if (entries.isEmpty()) {
            String empty = view.ships ? "No automated ships in your fleet or local storage." : "No Forge Templates in local or industrial storage.";
            Ui.text(p, 8f, BODY_Y + Ui.HEADING_H + 6f, lw - 16f, empty, Ui.gray());
        } else {
            float rw = lw - UiScroll.GUTTER;
            if (view.ships) Ui.thead(p, 0f, BODY_Y + 20f, lw, new String[]{"Ship", "Location", "Max CR", "Cost"},
                    new float[]{56f, rw - 280f, rw - 180f, rw - 100f}, new float[]{200f, 90f, 70f, 92f}, new Alignment[]{Ui.LEFT, Ui.LEFT, Ui.CENTER, Ui.RIGHT});
            else Ui.thead(p, 0f, BODY_Y + 20f, lw, new String[]{"Template", "Quality level", "Stack", "Cost"},
                    new float[]{104f, rw - 250f, rw - 150f, rw - 100f}, new float[]{200f, 90f, 50f, 92f}, new Alignment[]{Ui.LEFT, Ui.CENTER, Ui.CENTER, Ui.RIGHT});

            int available = RelicHelper.getAvailable(market);
            UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y + 42f, lw, UiScroll.wholeRows(h - BODY_Y - 42f - MESSAGE_H, 0f, ROW_H, ROW_GAP), view.scroll, AncientLabRelicView.LIST);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                boolean selected = entry.key.equals(view.selected);
                CustomPanelAPI row = UiButtons.row(list.block(ROW_H, i == 0 ? 0f : ROW_GAP), 0f, 0f, rw, ROW_H, selected, true, null, () -> select(entry));
                if (view.ships) addShipRow(row, rw, entry, selected);
                else addTemplateRow(row, rw, entry, selected);
                addCost(row, rw - 8f, 13f, entry.cost, entry.cost <= available);
            }

            list.end();
        }

        if (view.message != null) Ui.text(p, 0f, h - MESSAGE_H + 4f, lw, view.message, Ui.pos());
    }

    private void addTemplateRow(CustomPanelAPI row, float w, Entry entry, boolean selected) {
        float qx = w - 250f;
        String icon = Global.getSettings().getSpecialItemSpec(ItemIds.FORGETEMPLATE).getIconName();
        UiImages.icon(row, 3f, 2f, THUMB, icon, entry.stack.getDisplayName());
        UiImages.hull(row, 50f, 2f, THUMB, entry.hull, true, false);
        Ui.text(row, 104f, 5f, qx - 110f, entry.name, selected ? Ui.bright() : Ui.text());
        Ui.text(row, 104f, 23f, qx - 110f, entry.sub, Ui.gray());

        int total = pipCount(entry.level);
        UiData.pips(row, qx + Math.round((90f - pipWidth(total)) / 2f), 10f, total, entry.level, PIP, PIP_GAP, Ui.neg());
        Ui.text(row, qx, 23f, 90f, "level " + entry.level, Ui.gray(), Ui.CENTER);
        Ui.text(row, w - 150f, 13f, 50f, Ui.num(entry.stack.getSize()), Ui.text(), Ui.CENTER);
    }

    private void addShipRow(CustomPanelAPI row, float w, Entry entry, boolean selected) {
        float lx = w - 280f;
        UiImages.ship(row, 3f, 2f, THUMB, entry.member, true, false);
        Ui.text(row, 56f, 5f, lx - 62f, entry.name, selected ? Ui.bright() : Ui.text());
        Ui.text(row, 56f, 23f, lx - 62f, entry.sub, Ui.gray());
        Ui.text(row, lx, 13f, 94f, entry.location, Ui.gray());

        boolean penalty = entry.member.getVariant().hasHullMod(HullMods.AUTOMATED);
        Ui.text(row, w - 180f, 13f, 70f, penalty ? getCrPenalty() : "none", penalty ? Ui.neg() : Ui.gray(), Ui.CENTER);
    }

    // relic icon and amount, right aligned at right
    private static void addCost(CustomPanelAPI row, float right, float y, int cost, boolean ok) {
        String amount = Ui.num(cost);
        float aw = Math.min(Ui.width(amount, Ui.INS15) + 2f, 70f);
        Ui.text(row, right - aw, y, aw, amount, ok ? Ui.hl() : Ui.neg(), Ui.RIGHT);
        UiImages.img(row, right - aw - 4f - COST_ICON, y - 2f, COST_ICON, COST_ICON, UiImages.relicSprite());
    }

    private void addDetail(CustomPanelAPI p, float x, float w, float h, Entry entry) {
        cw = w - UiScroll.GUTTER;
        pinned = null;

        // the cost and the button stay pinned at the bottom, the information and the lore above scroll
        float actionH = 0f;
        if (entry != null) {
            String reason = entry.cost <= RelicHelper.getAvailable(market) ? null : "Not enough " + RelicHelper.getName() + ".";
            actionH = Ui.HEADING_H + 6f + UiData.COST_ROW_H + GAP + UiButtons.guardHeight(cw, BUTTON_H, reason);
        }

        detailP = p;
        detailX = x;
        detailH = h;
        detailActionH = actionH;
        column = UiScroll.list(p, x, BODY_Y, w, actionH > 0f ? h - actionH - 2f * GAP : h, view.scroll, AncientLabRelicView.DETAIL);
        if (entry == null) {
            heading(view.ships ? "Automated ships" : "Forge Templates", 0f);
            text(view.ships ? "Pick a ship to integrate." : "Pick a Forge Template to refine.", Ui.gray(), GAP);
        } else if (view.ships) addShipDetail(entry);
        else addTemplateDetail(entry);

        if (pinned == null) column.end();
    }

    // starts the pinned action block: ends the scroll column, later blocks stack below it
    private void pin() {
        column.end();
        pinned = Ui.panel(detailP, detailX, BODY_Y + detailH - detailActionH, cw, detailActionH);
        pinY = 0f;
    }

    // column blocks until pin, then blocks stack in the pinned panel
    private CustomPanelAPI block(float h, float gap) {
        if (pinned == null) return column.block(h, gap);
        if (pinY > 0f) pinY += gap;
        CustomPanelAPI b = Ui.panel(pinned, 0f, pinY, cw, h);
        pinY += h;
        return b;
    }

    private void addTemplateDetail(Entry entry) {
        SpecialItemData data = entry.stack.getSpecialDataIfSpecial();
        int after = entry.level - 1;

        heading(entry.hull.getHullNameWithDashClass() + " template", 0f);
        picture(entry.hull);

        CustomPanelAPI b = block(20f, 10f);
        int total = pipCount(entry.level);
        float pw = pipWidth(total);
        float ax = cw - 24f - 6f - pw;
        float gx = ax - 22f;
        float bx = gx - 4f - 20f;
        float px = bx - 6f - pw;
        Ui.text(b, 0f, 0f, px - 8f, "Quality level");
        UiData.pips(b, px, 4f, total, entry.level, PIP, PIP_GAP, Ui.neg());
        Ui.text(b, bx, 0f, 20f, entry.level + "", Ui.text(), Ui.RIGHT);
        Ui.text(b, gx, 0f, 16f, ">", Ui.hl(), Ui.CENTER);
        UiData.pips(b, ax, 4f, total, after, PIP, PIP_GAP, Ui.pos());
        Ui.text(b, cw - 24f, 0f, 24f, after + "", Ui.pos(), Ui.RIGHT);

        String before = getDefectId(entry.level);
        String afterId = getDefectId(after);
        CustomPanelAPI d = block(34f, 4f);
        UiImages.hullmod(d, 0f, 2f, 30f, before);
        Ui.text(d, 38f, 0f, cw - 38f, Global.getSettings().getHullModSpec(before).getDisplayName());
        String result = afterId == null ? "is removed" : "becomes " + Global.getSettings().getHullModSpec(afterId).getDisplayName();
        String part = afterId == null ? "removed" : Global.getSettings().getHullModSpec(afterId).getDisplayName();
        Ui.runs(d, 38f, 17f, cw - 38f, Ui.INS15, Ui.gray(), Ui.LEFT, result, new String[]{part}, new Color[]{Ui.pos()});

        grid(GAP, GridRow.of("Templates in this stack", Ui.num(entry.stack.getSize())),
                GridRow.of("Charges each", ForgeTemplateItemPlugin.getCharges(data) + ""),
                GridRow.of("Stored in", entry.location, Ui.text()));

        int per = RELICS_PER_TEMPLATE_FP * entry.hull.getFleetPoints();
        addCostAndAction(entry, RelicHelper.getName() + ", " + Ui.num(per) + " per template", "Refine", REFINE_LORE, () -> refine(entry));
    }

    private void addShipDetail(Entry entry) {
        heading(entry.member.getShipName(), 0f);
        picture(entry.member.getHullSpec());

        if (entry.member.getVariant().hasHullMod(HullMods.AUTOMATED)) {
            String value = getCrPenalty() + " > no penalty";
            CustomPanelAPI b = block(20f, 10f);
            UiData.value(b, 0f, 0f, cw, 20f, "Max CR", value, Ui.text(), new String[]{getCrPenalty(), ">", "no penalty"}, new Color[]{Ui.neg(), Ui.hl(), Ui.pos()});
        }

        CustomPanelAPI s = block(20f, 0f);
        UiImages.skill(s, 0f, 1f, 18f, Global.getSettings().getSkillSpec(Skills.AUTOMATED_SHIPS), false);
        UiData.value(s, 24f, 0f, cw - 24f, 20f, "Automated Ships limit", "counts > exempt", Ui.text(), new String[]{"counts", ">", "exempt"}, new Color[]{Ui.neg(), Ui.hl(), Ui.pos()});

        CustomPanelAPI perm = block(20f, 0f);
        Ui.text(perm, 0f, 0f, cw, "Permanent", Ui.hl());

        addCostAndAction(entry, RelicHelper.getName() + ", " + RELICS_PER_SHIP_DP + " per DP", "Integrate", INTEGRATE_LORE, () -> integrate(entry));
    }

    private void addCostAndAction(Entry entry, String label, String action, String lore, Runnable run) {
        int available = RelicHelper.getAvailable(market);
        boolean ok = entry.cost <= available;

        text(lore, Ui.gray(), 8f);
        pin();
        heading("Cost", GAP);
        CustomPanelAPI c = block(UiData.COST_ROW_H, 6f);
        UiData.costrow(c, 0f, 0f, cw, label, UiImages.relicSprite(), Ui.num(entry.cost), Ui.num(available), ok);

        String reason = ok ? null : "Not enough " + RelicHelper.getName() + ".";
        CustomPanelAPI b = block(UiButtons.guardHeight(cw, BUTTON_H, reason), GAP);
        UiButtons.guarded(b, 0f, 0f, cw, BUTTON_H, action, UiButtons.Kind.GREEN, reason, run);
    }

    private void picture(ShipHullSpecAPI hull) {
        CustomPanelAPI b = block(PICTURE_H, 4f);
        Ui.well(b, 0f, 0f, cw, PICTURE_H);
        UiImages.hull(b, Math.round((cw - PICTURE) / 2f), 4f, PICTURE, hull, false, false);
    }

    private void heading(String title, float gap) {
        Ui.heading(block(Ui.HEADING_H, gap), 0f, 0f, cw, title);
    }

    private void text(String text, Color color, float gap) {
        CustomPanelAPI b = block(Ui.wrapHeight(text, Ui.INS15, cw), gap);
        Ui.wrap(b, 0f, 0f, cw, text, color);
    }

    private void grid(float gap, GridRow... rows) {
        CustomPanelAPI b = block(rows.length * GRID_ROW, gap);
        UiData.grid(b, 0f, 0f, cw, GRID_ROW, List.of(rows));
    }

    private void refine(Entry entry) {
        SpecialItemData data = entry.stack.getSpecialDataIfSpecial();
        RelicHelper.take(market, entry.cost);
        ForgeTemplateItemPlugin.incrementForgeTemplateQualityLevel(data, -1);
        view.message = entry.name + " refined to quality level " + ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(data) + ", used " + entry.cost + " " + RelicHelper.getName() + ".";
        root.refresh();
    }

    private void integrate(Entry entry) {
        RelicHelper.take(market, entry.cost);

        FleetMemberAPI member = entry.member;
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setOriginalVariant(null);
        variant.setHullVariantId(Misc.genUID());
        variant.addTag(Tags.TAG_AUTOMATED_NO_PENALTY);
        member.setVariant(variant, false, true);
        member.updateStats();

        view.selected = null;
        view.message = entry.name + " integrated, the automated ship penalty no longer applies. Used " + entry.cost + " " + RelicHelper.getName() + ".";
        root.refresh();
    }

    private static String getCrPenalty() {
        return "-" + Math.round(Automated.MAX_CR_PENALTY * 100f) + "%";
    }

    // printed hulls get the defect of the template level, capped at the third (ForgeTemplateItemPlugin.addPrintDefectDMods)
    private static String getDefectId(int level) {
        if (level <= 0) return null;
        if (level == 1) return Ids.DEFECTS_LOW;
        if (level == 2) return Ids.DEFECTS_MED;
        return Ids.DEFECTS_HIGH;
    }

    private static int pipCount(int level) {
        return Math.max(3, level);
    }

    private static float pipWidth(int total) {
        return total * (PIP + PIP_GAP) - PIP_GAP;
    }

    private List<Entry> getTemplateEntries() {
        List<Entry> entries = new ArrayList<>();
        addTemplateEntries(entries, MiscIE.getIndustrialStorageCargo(market), market.getSubmarket(Ids.SHAREDSTORAGE));
        addTemplateEntries(entries, Misc.getStorageCargo(market), market.getSubmarket(Submarkets.SUBMARKET_STORAGE));
        return entries;
    }

    private void addTemplateEntries(List<Entry> entries, CargoAPI cargo, SubmarketAPI submarket) {
        if (cargo == null || submarket == null) return;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!ForgeTemplateItemPlugin.isForgeTemplate(data)) continue;

            ShipVariantAPI variant = ForgeTemplateItemPlugin.getVariant(data);
            if (variant == null) continue;

            int level = ForgeTemplateItemPlugin.getForgeTemplateQualityLevel(data);
            if (level <= 0) continue;

            Entry entry = new Entry();
            entry.key = submarket.getSpecId() + "|" + data.getData();
            entry.stack = stack;
            entry.hull = variant.getHullSpec();
            entry.level = level;
            entry.name = entry.hull.getHullNameWithDashClass();
            entry.sub = Misc.getHullSizeStr(entry.hull.getHullSize());
            entry.location = submarket.getNameOneLine();
            entry.cost = RELICS_PER_TEMPLATE_FP * entry.hull.getFleetPoints() * (int) stack.getSize();
            entries.add(entry);
        }
    }

    private List<Entry> getShipEntries() {
        List<Entry> entries = new ArrayList<>();
        addShipEntries(entries, Global.getSector().getPlayerFleet().getFleetData(), "Fleet");

        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage != null) addShipEntries(entries, storage.getCargo().getMothballedShips(), storage.getNameOneLine());
        return entries;
    }

    private void addShipEntries(List<Entry> entries, FleetDataAPI fleetData, String location) {
        if (fleetData == null) return;

        for (FleetMemberAPI member : fleetData.getMembersListCopy()) {
            if (!Misc.isAutomated(member) || Automated.isAutomatedNoPenalty(member)) continue;

            ShipHullSpecAPI hull = member.getHullSpec();
            int dp = Math.round(hull.getSuppliesToRecover());
            Entry entry = new Entry();
            entry.key = member.getId();
            entry.member = member;
            entry.hull = hull;
            entry.name = member.getShipName();
            entry.sub = hull.getHullNameWithDashClass() + " " + Misc.getHullSizeStr(hull.getHullSize()).toLowerCase();
            entry.location = location;
            entry.cost = RELICS_PER_SHIP_DP * dp;
            entries.add(entry);
        }
    }
}
