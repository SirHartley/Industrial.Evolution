package indevo.industries.senate.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import indevo.ids.Ids;
import indevo.ids.ItemIds;
import indevo.industries.senate.EdictHelper;
import indevo.industries.senate.EdictRepo;
import indevo.industries.senate.EdictRequirement;
import indevo.industries.senate.conditions.BaseEdict;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.utils.helper.RelicHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static indevo.items.installable.SpecialItemEffectsRepo.RANGE_LY_TWELVE;

public class EdictPanelDelegate extends BaseCustomDialogDelegate {

    private enum Row {
        ACTIVE, MET, UNMET
    }

    public static final float BOARD_W = 1004f;
    public static final float SINGLE_BOARD_W = 744f;
    public static final float BOARD_H = 712f;
    static final float BODY_Y = 64f;
    static final float LIST_Y = 88f;
    static final float COLONY_W = 260f;
    static final float EDICT_W = 280f;
    static final float COLUMN_GAP = 10f;
    static final float COLONY_ROW_H = 74f;
    static final float EDICT_ROW_H = 32f;
    static final float ROW_GAP = 4f;
    static final float EDICT_ROW_GAP = 2f;
    static final float GROUP_H = 18f;
    static final float GROUP_GAP = 6f;
    static final float PLANET = 40f;
    static final float EDICT_ICON = 26f;
    static final float LINE_ICON = 18f;

    private final MarketAPI origin;
    private final boolean senateScope;
    private final List<MarketAPI> colonies;
    private final List<MarketConditionSpecAPI> specs = new ArrayList<>();
    private final Map<String, EdictAPI> previews = new LinkedHashMap<>();
    private final EdictView view;
    private final Runnable onLeave;
    private UiRoot root;

    public EdictPanelDelegate(MarketAPI origin, EdictView view, Runnable onLeave) {
        this.origin = origin;
        this.view = view;
        this.onLeave = onLeave;

        Industry senate = origin.getIndustry(Ids.SENATE);
        senateScope = senate != null && senate.isFunctional();
        colonies = senateScope ? EdictHelper.getColoniesCoveredBy(origin) : new ArrayList<>(List.of(origin));

        // previews read the target colony from their parameters, the dummy never applies anything
        MarketAPI dummy = Global.getFactory().createMarket("IndEvo_edictPreview", "Edict preview", 3);
        for (MarketConditionSpecAPI spec : EdictRepo.getAll()) {
            dummy.addCondition(spec.getId());
            EdictAPI edict = EdictHelper.asEdict(dummy.getCondition(spec.getId()));
            if (edict == null) continue;

            specs.add(spec);
            previews.put(spec.getId(), edict);
        }
    }

    public static float getWidth(MarketAPI market) {
        Industry senate = market.getIndustry(Ids.SENATE);
        boolean scope = senate != null && senate.isFunctional();
        return UiFrame.width(scope ? BOARD_W : SINGLE_BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    public static void show(DialogCreatorUI ui, MarketAPI market, EdictView view) {
        ui.showDialog(getWidth(market), getHeight(), new EdictPanelDelegate(market, view, null));
    }

    public static void show(InteractionDialogAPI dialog, MarketAPI market, EdictView view) {
        dialog.showCustomDialog(getWidth(market), getHeight(), new EdictPanelDelegate(market, view, null));
    }

    // opens the panel in its own interaction dialog, for code and tests
    public static boolean show(MarketAPI market, EdictView view) {
        return UiHost.show(market.getPrimaryEntity(), dialog -> dialog.showCustomDialog(getWidth(market), getHeight(), new EdictPanelDelegate(market, view, dialog::dismiss)));
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
        float right = addHeader(p, w);
        addHeaderLine(p, right);
        Ui.rule(p, 0f, UiFrame.HEADER_Y + UiFrame.HEADER_H, w, Misc.scaleAlpha(Ui.base(), 0.55f));

        if (colonies.isEmpty() || specs.isEmpty()) {
            Ui.text(p, 0f, BODY_Y + 2f, w, "No colony is in range of this Senate.", Ui.gray());
            return;
        }

        MarketAPI colony = resolveColony();
        String edict = resolveEdict(colony);

        float x = 0f;
        if (senateScope) {
            addColonies(p, x, h, colony);
            x += COLONY_W + COLUMN_GAP;
            Ui.vrule(p, x - 5f, BODY_Y, h - BODY_Y);
        }

        addEdicts(p, x, h, colony, edict);
        x += EDICT_W + COLUMN_GAP;
        Ui.vrule(p, x - 5f, BODY_Y, h - BODY_Y);

        new EdictDetail(this, view).build(p, x, BODY_Y, w - x, h - BODY_Y, colony, edict);
    }

    private MarketAPI resolveColony() {
        for (MarketAPI colony : colonies) {
            if (colony.getId().equals(view.colony)) return colony;
        }

        MarketAPI colony = colonies.contains(origin) ? origin : colonies.get(0);
        view.colony = colony.getId();
        return colony;
    }

    private String resolveEdict(MarketAPI colony) {
        if (view.edict != null && previews.containsKey(view.edict)) return view.edict;

        MarketConditionAPI active = EdictHelper.getActiveEdict(colony);
        if (active != null && previews.containsKey(active.getId())) view.edict = active.getId();
        else {
            view.edict = specs.get(0).getId();
            for (MarketConditionSpecAPI spec : specs) {
                if (isMet(spec.getId(), colony)) {
                    view.edict = spec.getId();
                    break;
                }
            }
        }

        return view.edict;
    }

    private float addHeader(CustomPanelAPI p, float w) {
        UiFrame.title(p, w, "Edicts", senateScope ? "Senate on " + origin.getName() : origin.getName(), this::addHelp);

        float right = UiFrame.counter(p, w, UiFrame.HEADER_Y, "", UiImages.relicSprite(), Ui.num(RelicHelper.getAvailable(origin)));
        if (getCompoundsSenate() != null) {
            String name = Global.getSettings().getSpecialItemSpec(ItemIds.NEUAL_COMPOUNDS).getName();
            right = UiFrame.slot(p, right, UiFrame.HEADER_Y, Global.getSettings().getSpecialItemSpec(ItemIds.NEUAL_COMPOUNDS).getIconName(), name, name,
                    "The Senate covers your colonies within " + RANGE_LY_TWELVE + " LY.",
                    "Unrest from removing an Edict early is halved.");
        }

        return right;
    }

    private MarketAPI getCompoundsSenate() {
        if (senateScope) {
            Industry senate = origin.getIndustry(Ids.SENATE);
            return senate.getSpecialItem() != null ? origin : null;
        }

        Pair<MarketAPI, Float> compounds = EdictHelper.getCompoundsSenateInRange(origin);
        return compounds == null ? null : compounds.one;
    }

    private void addHeaderLine(CustomPanelAPI p, float right) {
        float y = UiFrame.HEADER_Y;
        float lw = right;
        String line;
        String[] parts;

        if (senateScope) {
            String count = colonies.size() + "";
            String noun = colonies.size() == 1 ? " colony" : " colonies";
            if (getCompoundsSenate() != null) {
                line = "Covers " + count + noun + ": " + EdictHelper.getSystemName(origin) + " and colonies within " + RANGE_LY_TWELVE + " LY";
                parts = new String[]{count, RANGE_LY_TWELVE + " LY"};
            } else {
                line = "Covers " + count + noun + " in " + EdictHelper.getSystemName(origin);
                parts = new String[]{count};
            }
        } else {
            MarketAPI systemSenate = EdictHelper.getSystemSenate(origin);
            Pair<MarketAPI, Float> compounds = EdictHelper.getCompoundsSenateInRange(origin);

            if (systemSenate != null) {
                line = "Granted by the Senate at " + systemSenate.getName();
                parts = new String[]{systemSenate.getName()};
            } else if (compounds != null) {
                String dist = Misc.getRoundedValueMaxOneAfterDecimal(compounds.two) + " LY";
                line = "Granted by the Senate at " + compounds.one.getName() + ", " + dist + " away";
                parts = new String[]{compounds.one.getName(), dist};
            } else return;
        }

        Color[] colors = new Color[parts.length];
        java.util.Arrays.fill(colors, Ui.hl());
        Ui.textIn(p, 0f, y, lw, UiFrame.HEADER_H, line, Ui.INS15, Ui.gray(), Ui.LEFT, parts, colors);
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();
        float pad = 10f;

        tooltip.addPara("A Senate allows one Edict per colony in its star system.", 0f);
        tooltip.addPara("Installing %s extends coverage to %s and halves unrest from early removal.", pad, hl,
                EdictHelper.COMPOUNDS_NAME, RANGE_LY_TWELVE + " LY");
        tooltip.addPara("Removing an Edict before its minimum term causes %s.", pad, Misc.getNegativeHighlightColor(), "1 unrest per remaining month");
        tooltip.addPara("Bribing the senators costs %s per remaining day and waives that unrest.", pad, hl,
                BaseEdict.RELICS_PER_REMAINING_DAY + " " + RelicHelper.getName());
        if (!senateScope) tooltip.addPara("Visit the Senate to manage the Edicts of every colony in its range.", Misc.getGrayColor(), pad);
    }

    private void addColonies(CustomPanelAPI p, float x, float h, MarketAPI selected) {
        Ui.heading(p, x, BODY_Y, COLONY_W, "Colonies");
        UiScroll.Column list = UiScroll.list(p, x, LIST_Y, COLONY_W, h - LIST_Y, view.scroll, EdictView.COLONIES);
        float rw = list.width();

        float gap = 0f;
        for (MarketAPI colony : colonies) {
            addColonyRow(list.block(COLONY_ROW_H, gap), rw, colony, colony == selected);
            gap = ROW_GAP;
        }

        list.end();
    }

    private void addColonyRow(CustomPanelAPI block, float w, MarketAPI colony, boolean selected) {
        CustomPanelAPI row = UiButtons.row(block, 0f, 0f, w, COLONY_ROW_H, selected, true, null, () -> selectColony(colony));
        UiImages.planet(row, 6f, 6f, PLANET, colony);

        float textX = 56f;
        float tagW = colony.hasIndustry(Ids.SENATE) ? Ui.width("Senate", Ui.INS15) + 2f : 0f;
        Ui.text(row, textX, 5f, w - textX - 8f - (tagW > 0f ? tagW + 8f : 0f), colony.getName(), selected ? Ui.bright() : Ui.text());
        if (tagW > 0f) Ui.text(row, w - 8f - tagW, 5f, tagW, "Senate", Ui.hl(), Ui.RIGHT);
        Ui.text(row, textX, 23f, w - textX - 8f, getLocationLine(colony), Ui.gray());

        MarketConditionAPI condition = EdictHelper.getActiveEdict(colony);
        EdictAPI active = condition == null ? null : EdictHelper.asEdict(condition);
        if (active == null) {
            Ui.text(row, textX, 44f, w - textX - 8f, "No Edict", Ui.gray());
            return;
        }

        int min = active.getMinRuntime(colony);
        String days = getDayCount(active, colony);
        float dw = Math.min(Ui.width(days, Ui.INS15) + 2f, 80f);
        Color accent = getRuntimeColor(active);

        UiImages.img(row, textX, 43f, LINE_ICON, LINE_ICON, condition.getSpec().getIcon());
        Ui.text(row, textX + 24f, 44f, w - textX - 24f - 16f - dw, EdictRepo.getShortName(condition.getName()), accent);
        Ui.text(row, w - 8f - dw, 44f, dw, days, Ui.gray(), Ui.RIGHT);
        UiData.thinbar(row, textX, 64f, w - textX - 8f, getRuntimeFraction(active, colony, min), accent);
    }

    private String getLocationLine(MarketAPI colony) {
        String line = "Size " + colony.getSize() + " · " + EdictHelper.getSystemName(colony);
        if (colony.getStarSystem() != null && colony.getStarSystem() == origin.getStarSystem()) return line;

        float dist = Misc.getDistanceLY(origin.getLocationInHyperspace(), colony.getLocationInHyperspace());
        return line + ", " + Misc.getRoundedValueMaxOneAfterDecimal(dist) + " LY";
    }

    private void addEdicts(CustomPanelAPI p, float x, float h, MarketAPI colony, String selected) {
        Ui.heading(p, x, BODY_Y, EDICT_W, "Edicts for " + colony.getName());
        UiScroll.Column list = UiScroll.list(p, x, LIST_Y, EDICT_W, h - LIST_Y, view.scroll, EdictView.EDICTS);
        float rw = list.width();

        MarketConditionAPI activeCondition = EdictHelper.getActiveEdict(colony);
        String activeId = activeCondition == null ? null : activeCondition.getId();
        List<MarketConditionSpecAPI> met = new ArrayList<>();
        List<MarketConditionSpecAPI> unmet = new ArrayList<>();
        MarketConditionSpecAPI active = null;

        for (MarketConditionSpecAPI spec : specs) {
            if (spec.getId().equals(activeId)) active = spec;
            else if (isMet(spec.getId(), colony)) met.add(spec);
            else unmet.add(spec);
        }

        float gap = 0f;
        if (active != null) {
            group(list, rw, "Active", null, gap);
            addEdictRow(list, rw, colony, active, Row.ACTIVE, selected);
            gap = GROUP_GAP;
        }

        if (!met.isEmpty()) {
            group(list, rw, "Requirements met", activeId != null ? "one Edict per colony" : null, gap);
            for (MarketConditionSpecAPI spec : met) addEdictRow(list, rw, colony, spec, Row.MET, selected);
            gap = GROUP_GAP;
        }

        if (!unmet.isEmpty()) {
            group(list, rw, "Requirements not met", null, gap);
            for (MarketConditionSpecAPI spec : unmet) addEdictRow(list, rw, colony, spec, Row.UNMET, selected);
        }

        list.end();
    }

    private static void group(UiScroll.Column list, float w, String title, String note, float gap) {
        CustomPanelAPI block = list.block(GROUP_H, gap);
        float nw = note == null ? 0f : Math.min(Ui.width(note, Ui.ORB12) + 2f, w / 2f);
        Ui.text(block, 0f, 1f, w - nw - (nw > 0f ? 8f : 0f), title, Ui.ORB12, Ui.gray(), Ui.LEFT);
        if (note != null) Ui.text(block, w - nw, 1f, nw, note, Ui.ORB12, Ui.gray(), Ui.RIGHT);
    }

    private void addEdictRow(UiScroll.Column list, float w, MarketAPI colony, MarketConditionSpecAPI spec, Row state, String selectedId) {
        boolean selected = spec.getId().equals(selectedId);
        boolean live = state != Row.UNMET;
        CustomPanelAPI block = list.block(EDICT_ROW_H, EDICT_ROW_GAP);
        CustomPanelAPI row = UiButtons.row(block, 0f, 0f, w, EDICT_ROW_H, selected, true, state == Row.ACTIVE ? Ui.AMBER : null, () -> selectEdict(spec.getId()));
        Ui.tooltip(block, row, null, previews.get(spec.getId()).getShortDesc());

        UiImages.img(row, 4f + (state == Row.ACTIVE ? UiButtons.STRIP_W : 0f), 3f, EDICT_ICON, EDICT_ICON, spec.getIcon(), null, live ? 1f : UiImages.DIM);
        float nameX = 38f + (state == Row.ACTIVE ? UiButtons.STRIP_W : 0f);
        float rightW;

        if (state == Row.ACTIVE) {
            EdictAPI active = EdictHelper.getActiveEdictPlugin(colony);
            String days = active == null ? "" : getDayCount(active, colony);
            rightW = Math.min(Ui.width(days, Ui.INS15) + 2f, 90f);
            Ui.textIn(row, w - 8f - rightW, 0f, rightW, EDICT_ROW_H, days, Ui.INS15, active != null ? getRuntimeColor(active) : Ui.AMBER, Ui.RIGHT, new String[0], new Color[0]);
        } else if (state == Row.MET) {
            rightW = UiData.MARK;
            UiData.mark(row, w - 10f - UiData.MARK, 10f, true);
        } else {
            String missing = getFirstUnmet(spec.getId(), colony);
            rightW = Math.min(Ui.width(missing, Ui.INS15) + 2f, 130f);
            Ui.textIn(row, w - 8f - rightW, 0f, rightW, EDICT_ROW_H, missing, Ui.INS15, Ui.neg(), Ui.RIGHT, new String[0], new Color[0]);
        }

        Color nameColor = !live ? Ui.gray() : selected ? Ui.bright() : Ui.text();
        Ui.textIn(row, nameX, 0f, w - nameX - 16f - rightW, EDICT_ROW_H, EdictRepo.getShortName(spec.getName()), Ui.INS15, nameColor, Ui.LEFT, new String[0], new Color[0]);
    }

    private void selectColony(MarketAPI colony) {
        if (!colony.getId().equals(view.colony)) {
            view.colony = colony.getId();
            view.edict = null;
            view.scroll.reset(EdictView.EDICTS);
            view.scroll.reset(EdictView.DETAIL);
        }

        refresh();
    }

    private void selectEdict(String id) {
        if (!id.equals(view.edict)) {
            view.edict = id;
            view.scroll.reset(EdictView.DETAIL);
        }

        refresh();
    }

    EdictAPI getPreview(String id) {
        return previews.get(id);
    }

    boolean isMet(String id, MarketAPI colony) {
        for (EdictRequirement requirement : previews.get(id).getRequirements(colony)) {
            if (!requirement.met) return false;
        }

        return true;
    }

    private String getFirstUnmet(String id, MarketAPI colony) {
        for (EdictRequirement requirement : previews.get(id).getRequirements(colony)) {
            if (!requirement.met) return requirement.shortText;
        }

        return "";
    }

    static String getDayCount(EdictAPI edict, MarketAPI colony) {
        int min = edict.getMinRuntime(colony);
        return Math.min(edict.getDaysPassed(), min) + " / " + min;
    }

    static float getRuntimeFraction(EdictAPI edict, MarketAPI colony, int min) {
        return min <= 0 ? 1f : Math.min(1f, edict.getDaysPassed() / (float) min);
    }

    static Color getRuntimeColor(EdictAPI edict) {
        return edict.getRemainingDays() > 0 ? Ui.AMBER : Ui.pos();
    }
}
