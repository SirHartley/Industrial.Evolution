package indevo.industries.senate.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.senate.EdictEffect;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.EdictRepo;
import indevo.industries.senate.EdictRequirement;
import indevo.industries.senate.EdictSupplyChange;
import indevo.industries.senate.conditions.BaseEdict;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static indevo.utils.helper.StringHelper.getDayOrDays;

// detail column of the Edict panel: one Edict on one colony, active or before enacting
class EdictDetail {

    static final float GAP = 6f;
    static final float SECTION_GAP = 10f;
    static final float LINE = 20f;
    static final float GRID_ROW = 19f;
    static final float BOX_PAD = 6f;
    static final float BOX_MIN_H = 30f;
    static final float CELL_W = 43f;
    static final float CELL_H = 46f;
    static final float CELL_GAP = 4f;
    static final float CELL_ICON = 28f;
    static final float BAR_H = 20f;
    static final float BUTTON_H = 28f;
    static final float MARK_W = 18f;
    static final float COST_X = 250f;
    static final float COST_W = 140f;
    static final float UNREST_W = 100f;
    static final float SMALL_ICON = 18f;
    static final float BRIBE_CONFIRM_H = 200f;

    static final String RELIC_LORE = "Several senators have districts waiting on machinery that nobody makes anymore. A few crates of relic components should settle their objections to an early repeal.";

    private final EdictPanelDelegate panel;
    private final EdictView view;
    private UiScroll.Column column;
    private float w;
    private MarketAPI colony;

    EdictDetail(EdictPanelDelegate panel, EdictView view) {
        this.panel = panel;
        this.view = view;
    }

    void build(CustomPanelAPI p, float x, float y, float width, float h, MarketAPI colony, String edictId) {
        this.colony = colony;
        column = UiScroll.list(p, x, y, width, h, view.scroll, EdictView.DETAIL);
        w = column.width();

        MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(edictId);
        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(colony);
        EdictAPI active = activeCondition == null ? null : EdictHelper.asEdict(activeCondition);
        boolean isActive = active != null && activeCondition.getId().equals(edictId);
        EdictAPI edict = isActive ? active : panel.getPreview(edictId);
        List<EdictRequirement> requirements = edict.getRequirements(colony);
        boolean met = panel.isMet(edictId, colony);

        addTitle(EdictRepo.getShortName(spec.getName()), isActive, met);
        addBox(edict.getShortDesc());
        if (spec.getDesc() != null && !spec.getDesc().isEmpty()) text(spec.getDesc(), Ui.gray(), GAP);

        List<EdictEffect> own = new ArrayList<>();
        List<EdictEffect> others = new ArrayList<>();
        for (EdictEffect effect : edict.getEffects(colony)) (effect.otherColonies() ? others : own).add(effect);

        heading("Effects on " + colony.getName(), SECTION_GAP);
        addEffects(own);
        addSupply(edict.getSupplyChanges(colony));

        if (!others.isEmpty()) {
            heading("Effects on other colonies", SECTION_GAP);
            addEffects(others);
        }

        heading("Term", SECTION_GAP);
        if (isActive) addActiveTerm(active);
        else addTerm(edict);

        heading("Requirements", SECTION_GAP);
        addRequirements(requirements, !isActive, activeCondition);

        if (!isActive) addEnact(edictId, met && activeCondition == null);
        else if (active.getRemainingDays() > 0) addEndEarly(active, activeCondition);
        else addRemove(active, activeCondition);

        column.end();
    }

    private void addTitle(String name, boolean active, boolean met) {
        CustomPanelAPI b = column.block(Ui.HEADING_H, 0f);
        if (!active) {
            if (met) Ui.heading(b, 0f, 0f, w, name);
            else Ui.heading(b, 0f, 0f, w, name, Ui.CENTER, Ui.gray(), Misc.scaleAlpha(Ui.gray(), 0.3f));
            return;
        }

        String note = "Active";
        float nw = Ui.width(note, Ui.ORB12) + 2f;
        String shown = Ui.fit(name, Ui.ORB12, w - 2f * (nw + 16f));
        Ui.heading(b, 0f, 0f, w, shown, Ui.CENTER, Ui.text(), Misc.scaleAlpha(Ui.AMBER, 0.6f));
        Ui.textIn(b, w - 8f - nw, 0f, nw, Ui.HEADING_H, note, Ui.ORB12, Ui.text(), Ui.RIGHT, new String[0], new Color[0]);
        if (!shown.equals(name)) Ui.tooltip(b, b, null, name);
    }

    private void addBox(String desc) {
        float th = Ui.wrapHeight(desc, Ui.INS15, w - 20f);
        float bh = Math.max(BOX_MIN_H, th + 2f * BOX_PAD);
        CustomPanelAPI b = column.block(bh, 8f);
        Ui.box(b, 0f, 0f, w, bh, Misc.scaleAlpha(Ui.base(), 0.35f), Misc.scaleAlpha(Ui.dark(), 0.35f));
        Ui.wrap(b, 10f, Math.round((bh - th) / 2f), w - 20f, desc, Ui.text());
    }

    private void addEffects(List<EdictEffect> effects) {
        CustomPanelAPI b = column.block(Math.max(1, effects.size()) * LINE, GAP);
        if (effects.isEmpty()) Ui.text(b, 0f, 1f, w, "No effect.", Ui.gray());

        for (int i = 0; i < effects.size(); i++) {
            EdictEffect effect = effects.get(i);
            Color color = BaseEdict.getToneColor(effect.tone());
            if (effect.note() == null) {
                UiData.value(b, 0f, i * LINE, w, LINE, effect.label(), effect.value(), color, new String[0], new Color[0]);
                continue;
            }

            String value = effect.value() + "  " + effect.note();
            UiData.value(b, 0f, i * LINE, w, LINE, effect.label(), value, Ui.gray(), new String[]{effect.value()}, new Color[]{color});
        }
    }

    private void addSupply(List<EdictSupplyChange> changes) {
        if (changes.isEmpty()) return;

        int per = Math.max(1, (int) ((w + CELL_GAP) / (CELL_W + CELL_GAP)));
        int rows = Math.min(2, (changes.size() + per - 1) / per);
        CustomPanelAPI b = column.block(rows * (CELL_H + CELL_GAP) - CELL_GAP, GAP);
        UiImages.iconrow(b, 0f, 0f, w, changes, CELL_W, CELL_H, CELL_GAP, 2, this::supplyCell, this::supplyName);
    }

    private void supplyCell(CustomPanelAPI p, float x, float y, float cw, float ch, EdictSupplyChange change) {
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(change.commodityId());
        String now = Ui.num(getSupply(change.commodityId()));
        String delta = (change.amount() > 0 ? "+" : "") + change.amount();
        Color color = change.amount() > 0 ? Ui.pos() : Ui.neg();

        CustomPanelAPI cell = Ui.well(p, x, y, cw, ch);
        UiImages.img(cell, Math.round((cw - CELL_ICON) / 2f), 1f, CELL_ICON, CELL_ICON, spec.getIconName());
        Ui.runs(cell, 1f, CELL_ICON, cw - 2f, Ui.INS15, Ui.text(), Ui.CENTER, now + " " + delta, new String[]{delta}, new Color[]{color});
        Ui.tooltip(p, cell, spec.getName(), "Supply " + now + ", " + delta + " from this Edict.");
    }

    private String supplyName(EdictSupplyChange change) {
        String delta = (change.amount() > 0 ? "+" : "") + change.amount();
        return Global.getSettings().getCommoditySpec(change.commodityId()).getName() + " " + delta;
    }

    private int getSupply(String commodityId) {
        int max = 0;
        for (Industry industry : colony.getIndustries()) {
            for (MutableCommodityQuantity quantity : industry.getAllSupply()) {
                if (commodityId.equals(quantity.getCommodityId())) max = Math.max(max, quantity.getQuantity().getModifiedInt());
            }
        }

        return max;
    }

    private void addActiveTerm(EdictAPI active) {
        int min = active.getMinRuntime(colony);
        int day = Math.min(active.getDaysPassed(), min);
        CustomPanelAPI bar = column.block(BAR_H, GAP);
        UiData.bar(bar, 0f, 0f, w, BAR_H, EdictPanelDelegate.getRuntimeFraction(active, colony, min), EdictPanelDelegate.getRuntimeColor(active), "Day " + day + " of " + min);

        int left = active.getRemainingDays();
        CustomPanelAPI b = column.block(LINE, GAP);
        if (left > 0) {
            String label = active.endsOnItsOwn() ? "Ends on its own in" : "Free to remove in";
            UiData.grid(b, 0f, 0f, w, LINE, List.of(GridRow.of(label, left + " " + getDayOrDays(left))));
        } else {
            UiData.value(b, 0f, 0f, w, LINE, "Minimum term served.", "Can end without penalty", Ui.pos(), new String[0], new Color[0]);
        }
    }

    private void addTerm(EdictAPI edict) {
        int min = edict.getMinRuntime(colony);
        int finish = edict.getFinishUnrest(colony);
        boolean halved = EdictHelper.getCompoundsSenateInRange(colony) != null;
        String early = edict.getMaxEarlyRemovalUnrest(colony) + "";

        int rows = edict.endsOnItsOwn() && finish > 0 ? 3 : 2;
        CustomPanelAPI b = column.block(rows * GRID_ROW, GAP);
        float y = 0f;
        UiData.value(b, 0f, y, w, GRID_ROW, "Runs for", min + " " + getDayOrDays(min), Ui.hl(), new String[0], new Color[0]);
        y += GRID_ROW;

        if (rows == 3) {
            UiData.value(b, 0f, y, w, GRID_ROW, "Ends on its own with unrest", finish + "", Ui.neg(), new String[0], new Color[0]);
            y += GRID_ROW;
        }

        String value = halved ? early + "  halved" : early;
        UiData.value(b, 0f, y, w, GRID_ROW, "Extra unrest if removed early", value, Ui.gray(), new String[]{early}, new Color[]{Ui.neg()});
    }

    private void addRequirements(List<EdictRequirement> requirements, boolean oneEdictRow, MarketConditionAPI otherActive) {
        List<EdictRequirement> rows = new ArrayList<>(requirements);
        if (oneEdictRow) rows.add(new EdictRequirement("No other Edict on " + colony.getName(), "One Edict per colony", otherActive == null));

        float tw = w - MARK_W;
        float h = 0f;
        for (EdictRequirement requirement : rows) h += rowHeight(requirement.text, tw);

        CustomPanelAPI b = column.block(h, GAP);
        float y = 0f;
        for (EdictRequirement requirement : rows) {
            UiData.mark(b, 2f, y + 4f, requirement.met);
            Ui.wrap(b, MARK_W, y + 1f, tw, requirement.text, requirement.met ? Ui.text() : Ui.neg());
            y += rowHeight(requirement.text, tw);
        }
    }

    private static float rowHeight(String text, float w) {
        return Math.max(LINE, Ui.wrapHeight(text, Ui.INS15, w) + 3f);
    }

    private void addEnact(String edictId, boolean allowed) {
        CustomPanelAPI b = column.block(BUTTON_H, SECTION_GAP);
        UiButtons.button(b, 0f, 0f, w, BUTTON_H, "Enact", allowed ? UiButtons.Kind.GREEN : UiButtons.Kind.OFF,
                allowed ? () -> createEnactConfirm(edictId).show(panel::refresh) : null);
    }

    private void addEndEarly(EdictAPI active, MarketConditionAPI condition) {
        heading("End early", SECTION_GAP);

        int cost = active.getRelicRemovalCost();
        int have = RelicHelper.getAvailable(colony);
        boolean affordable = have >= cost;
        int removeUnrest = active.getRemovalUnrest();
        int bribeUnrest = active.getRelicRemovalUnrest();
        boolean halved = EdictHelper.getCompoundsSenateInRange(colony) != null;
        float costRight = w - (UNREST_W + 10f);

        CustomPanelAPI t = column.block(17f + 2f * LINE + 2f, GAP);
        Ui.text(t, costRight - COST_W, 0f, COST_W, "Cost", Ui.ORB12, Ui.gray(), Ui.RIGHT);
        Ui.text(t, w - UNREST_W, 0f, UNREST_W, "Unrest", Ui.ORB12, Ui.gray(), Ui.RIGHT);

        float y = 17f;
        float labelW = costRight - COST_W - 8f;
        Ui.text(t, 0f, y + 1f, labelW, "Remove");
        Ui.text(t, costRight - COST_W, y + 1f, COST_W, "none", Ui.gray(), Ui.RIGHT);
        unrestCell(t, y, removeUnrest, halved);

        y += LINE;
        Ui.text(t, 0f, y + 1f, labelW, "Bribe senators");
        String amount = Ui.num(cost);
        String costText = amount + " (" + Ui.num(have) + ")";
        float cw = Math.min(Ui.width(costText, Ui.INS15) + 2f, COST_W - SMALL_ICON - 4f);
        UiImages.img(t, costRight - cw - SMALL_ICON - 4f, y + 1f, SMALL_ICON, SMALL_ICON, UiImages.relicSprite());
        Ui.runs(t, costRight - cw, y + 1f, cw, Ui.INS15, Ui.gray(), Ui.RIGHT, costText, new String[]{amount}, new Color[]{affordable ? Ui.hl() : Ui.neg()});
        unrestCell(t, y, bribeUnrest, false);

        float half = Math.round((w - 6f) / 2f);
        String reason = affordable ? null : "Not enough " + RelicHelper.getName() + ".";
        CustomPanelAPI b = column.block(UiButtons.guardHeight(half, BUTTON_H, reason), GAP);
        UiButtons.button(b, 0f, 0f, half, BUTTON_H, "Remove", UiButtons.Kind.RED, CutStyle.TL_BR, () -> createRemoveConfirm(active, condition).show(panel::refresh));

        ButtonAPI bribe = UiButtons.button(b, w - half, 0f, half, BUTTON_H, "Bribe senators", affordable ? UiButtons.Kind.STD : UiButtons.Kind.OFF, CutStyle.BL_TR,
                UiImages.relicSprite(), amount, Ui.ORB12, affordable ? () -> createBribeConfirm(active, condition).show(panel::refresh) : null);
        if (reason != null) Ui.wrap(b, w - half, BUTTON_H + UiButtons.REASON_GAP, half, reason, Ui.neg());

        MarketAPI market = colony;
        Ui.tooltip(b, bribe, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.ABOVE, tooltip -> {
            tooltip.addPara("Removes the Edict now without the early removal unrest.", 0f);
            if (bribeUnrest > 0) tooltip.addPara("Removal still causes %s.", 3f, Misc.getNegativeHighlightColor(), bribeUnrest + " unrest");
            RelicHelper.addCostLine(tooltip, market, cost, 10f);
            tooltip.addPara(RELIC_LORE, Misc.getGrayColor(), 10f);
        });
    }

    private void unrestCell(CustomPanelAPI t, float y, int unrest, boolean halved) {
        String n = unrest > 0 ? unrest + "" : "none";
        Color c = unrest > 0 ? Ui.neg() : Ui.gray();
        if (!halved) {
            Ui.text(t, w - UNREST_W, y + 1f, UNREST_W, n, c, Ui.RIGHT);
            return;
        }

        String s = "halved  " + n;
        float sw = Math.min(Ui.width(s, Ui.INS15) + 2f, UNREST_W - SMALL_ICON - 4f);
        UiImages.img(t, w - sw - SMALL_ICON - 4f, y + 1f, SMALL_ICON, SMALL_ICON, Global.getSettings().getSpecialItemSpec(ItemIds.NEUAL_COMPOUNDS).getIconName());
        Ui.runs(t, w - sw, y + 1f, sw, Ui.INS15, Ui.gray(), Ui.RIGHT, s, new String[]{n}, new Color[]{c});
    }

    private void addRemove(EdictAPI active, MarketConditionAPI condition) {
        CustomPanelAPI b = column.block(BUTTON_H + 4f + LINE, SECTION_GAP);
        UiButtons.button(b, 0f, 0f, w, BUTTON_H, "Remove", UiButtons.Kind.RED, () -> createRemoveConfirm(active, condition).show(panel::refresh));

        int unrest = active.getRemovalUnrest();
        if (unrest > 0) Ui.runs(b, 0f, BUTTON_H + 4f, w, Ui.INS15, Ui.gray(), Ui.CENTER, "Removal causes " + unrest + " unrest", new String[]{unrest + " unrest"}, new Color[]{Ui.neg()});
        else Ui.text(b, 0f, BUTTON_H + 4f, w, "No penalty", Ui.pos(), Ui.CENTER);
    }

    private EdictConfirmDialog createEnactConfirm(String edictId) {
        String edictName = EdictRepo.getShortName(Global.getSettings().getMarketConditionSpec(edictId).getName());
        MarketAPI market = colony;
        EdictAPI edict = panel.getPreview(edictId);

        return new EdictConfirmDialog("Enact", info -> {
            info.addPara("Enact %s on %s?", 0f, Misc.getHighlightColor(), edictName, market.getName());
            edict.addRuntimeText(info, market, 10f);
        }, () -> EdictHelper.enact(market, edictId));
    }

    private EdictConfirmDialog createRemoveConfirm(EdictAPI active, MarketConditionAPI condition) {
        Color hl = Misc.getHighlightColor();
        String edictName = EdictRepo.getShortName(condition.getName());
        MarketAPI market = colony;
        int unrest = active.getRemovalUnrest();

        return new EdictConfirmDialog("Remove", info -> {
            if (unrest > 0) info.addPara("Remove %s from %s? This causes %s.", 0f, new Color[]{hl, hl, Misc.getNegativeHighlightColor()}, edictName, market.getName(), unrest + " unrest");
            else info.addPara("Remove %s from %s? There is %s.", 0f, new Color[]{hl, hl, Misc.getPositiveHighlightColor()}, edictName, market.getName(), "no penalty");
        }, active::removeWithPenalty);
    }

    private EdictConfirmDialog createBribeConfirm(EdictAPI active, MarketConditionAPI condition) {
        Color hl = Misc.getHighlightColor();
        String edictName = EdictRepo.getShortName(condition.getName());
        MarketAPI market = colony;
        int cost = active.getRelicRemovalCost();
        int relicUnrest = active.getRelicRemovalUnrest();

        return new EdictConfirmDialog("Bribe", BRIBE_CONFIRM_H, info -> {
            info.addPara("Bribe the senators to remove %s from %s now?", 0f, hl, edictName, market.getName());
            if (relicUnrest > 0) info.addPara("Removal still causes %s.", 10f, Misc.getNegativeHighlightColor(), relicUnrest + " unrest");
            else info.addPara("There is %s.", 10f, Misc.getPositiveHighlightColor(), "no unrest");
            RelicHelper.addCostLine(info, market, cost, 3f);
        }, () -> {
            RelicHelper.take(market, cost);
            active.removeEarlyWithRelics();
        });
    }

    private void heading(String title, float gap) {
        Ui.heading(column.block(Ui.HEADING_H, gap), 0f, 0f, w, title);
    }

    private void text(String text, Color color, float gap) {
        CustomPanelAPI b = column.block(Ui.wrapHeight(text, Ui.INS15, w), gap);
        Ui.wrap(b, 0f, 0f, w, text, color);
    }
}
