package indevo.dialogue.research.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.research.ResearchProjectTemplateRepo;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ResearchProjectPanel {

    public static final float WIDTH = 600f;
    public static final float HEIGHT = 600f;
    static final float LIST_Y = 30f;
    static final float CARD_GAP = 8f;
    static final float HEAD_H = 20f;
    static final float CARD_H = 92f;
    static final float NOTE_H = 20f;
    static final float DESC_Y = 28f;
    static final float TOGGLE_Y = 25f;
    static final float TOGGLE_H = 20f;
    static final float INFO_RIGHT = 146f;
    static final float INFO_W = 52f;
    static final float STOCK_RIGHT = 84f;
    static final float STOCK_W = 74f;
    static final float ROW_Y = 56f;
    static final float BAR_W = 150f;
    static final float BAR_H = 20f;
    static final float ITEMS_X = 172f;
    static final float ITEM_ICON = 24f;
    static final float MORE_W = 30f;
    static final float BUTTONS_RIGHT = 198f;
    static final float BUTTON_W = 92f;
    static final float BUTTON_H = 26f;
    static final float NOTE_ICON = 22f;
    static final float TABLE_ROW = 24f;
    static final float TABLE_ICON = 22f;
    static final float REDEEM_DIALOG_W = 500f;
    static final float REDEEM_DIALOG_H = 60f;
    static final float TOOLTIP_W = 400f;
    static final String OVERFLOW_LABEL = "Stock";

    private final ResearchProjectDialoguePlugin plugin;
    private final ResearchView view;
    private UiRoot root;

    public ResearchProjectPanel(ResearchProjectDialoguePlugin plugin, ResearchView view) {
        this.plugin = plugin;
        this.view = view;
    }

    public void show(InteractionDialogAPI dialog) {
        CustomPanelAPI panel = dialog.getVisualPanel().showCustomPanel(WIDTH, HEIGHT, new UiCanvas());
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    public void refresh() {
        if (root != null) root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        UiFrame.title(p, w, "Research Tracts", null, this::addHelp);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        List<ResearchProject> projects = new ArrayList<>();
        for (ResearchProject project : ResearchProjectTemplateRepo.RESEARCH_PROJECTS.values()) {
            if (project.display() && !project.getProgress().redeemed) projects.add(project);
        }

        projects.sort(Comparator.comparing((ResearchProject project) -> !isReady(project, cargo))
                .thenComparing(Comparator.comparingDouble((ResearchProject project) -> project.getProgress().points).reversed()));

        UiScroll.Column list = UiScroll.list(p, 0f, LIST_Y, w, h - LIST_Y, view.scroll, ResearchView.CARDS);
        float cw = list.width();

        if (projects.isEmpty()) Ui.text(list.block(Ui.LINE_H + 4f, 0f), 8f, 2f, cw - 16f, "No open research projects.", Ui.gray());

        float gap = 0f;
        for (ResearchProject project : projects) {
            addCard(list.block(getCardHeight(project, cw, cargo), gap), cw, project, cargo);
            gap = CARD_GAP;
        }

        list.end();
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        tooltip.addPara("Donate accepted items to advance a project. A finished project is redeemed for its reward.", 0f);
        tooltip.addPara("Repeatable projects can be finished again. With %s on, donations past %s count toward the next reward.", 10f,
                Misc.getHighlightColor(), OVERFLOW_LABEL, "100%");
        tooltip.addPara("You will be notified when new projects become available.", Misc.getGrayColor(), 10f);
    }

    private static boolean isFinished(ResearchProject project) {
        return project.getProgress().points >= project.getRequiredPoints();
    }

    private static boolean isReady(ResearchProject project, CargoAPI cargo) {
        RedeemRequirement requirement = project.getRedeemRequirement();
        return isFinished(project) && (requirement == null || requirement.isMet(cargo));
    }

    private static boolean hasNote(ResearchProject project, CargoAPI cargo) {
        return isFinished(project) && !isReady(project, cargo);
    }

    private float getCardHeight(ResearchProject project, float cw, CargoAPI cargo) {
        float h = CARD_H + getDescExtra(project, cw) + (hasNote(project, cargo) ? NOTE_H : 0f);
        if (!view.info.contains(project.getId())) return h;

        return h + getInfoHeight(project, cw);
    }

    private static float getInfoHeight(ResearchProject project, float cw) {
        return 8f + Ui.wrapHeight(getLongDesc(project), Ui.INS15, cw - 20f) + 8f + Ui.HEADING_H + 4f + project.getRequiredItems().size() * TABLE_ROW + 6f;
    }

    // whole sentences on one line; a first sentence longer than the line wraps to a second one
    private static String getDesc(ResearchProject project, float cw) {
        String s = project.getShortDesc() == null ? "" : project.getShortDesc().trim();
        float w = cw - INFO_RIGHT - 20f;
        String one = Ui.sentences(s, Ui.INS15, w);
        if (!one.endsWith(Ui.ELLIPSIS)) return one;

        float twoLines = 2f * Ui.wrapHeight("Ag", Ui.INS15, w) + 1f;
        String out = "";
        for (String sentence : s.split("(?<=[.!?])\\s+")) {
            String next = out.isEmpty() ? sentence : out + " " + sentence;
            if (Ui.wrapHeight(next, Ui.INS15, w) > twoLines) break;
            out = next;
        }
        if (!out.isEmpty()) return out;

        String words = "";
        for (String word : s.split("\\s+")) {
            String next = words.isEmpty() ? word : words + " " + word;
            if (Ui.wrapHeight(next + Ui.ELLIPSIS, Ui.INS15, w) > twoLines) break;
            words = next;
        }
        return words.replaceAll("[,;:.]+$", "") + Ui.ELLIPSIS;
    }

    private static float getDescExtra(ResearchProject project, float cw) {
        float w = cw - INFO_RIGHT - 20f;
        float line = Ui.wrapHeight("Ag", Ui.INS15, w);
        return Ui.wrapHeight(getDesc(project, cw), Ui.INS15, w) > line + 1f ? line : 0f;
    }

    private static String getLongDesc(ResearchProject project) {
        return project.getLongDesc() == null ? "" : project.getLongDesc();
    }

    private void addCard(CustomPanelAPI b, float cw, ResearchProject project, CargoAPI cargo) {
        String id = project.getId();
        ResearchProject.Progress progress = project.getProgress();
        float points = progress.points;
        int required = project.getRequiredPoints();
        boolean finished = isFinished(project);
        boolean ready = isReady(project, cargo);
        boolean info = view.info.contains(id);
        float ch = b.getPosition().getHeight();

        Ui.box(b, 0f, 0f, cw, ch, Misc.scaleAlpha(ready ? Ui.pos() : Ui.base(), ready ? 0.55f : 0.3f), Misc.scaleAlpha(Ui.dark(), 0.3f));
        Ui.box(b, 1f, 1f, cw - 2f, HEAD_H, null, ready ? Misc.scaleAlpha(Ui.GREEN, 0.75f) : Ui.dark());

        String state = ready ? "Ready" : Math.round(required <= 0 ? 100f : points / required * 100f) + "%";
        String right = project.isRepeatable() ? "Repeatable, " + state : state;
        float rw = Math.min(Ui.width(right, Ui.ORB12) + 2f, cw / 2f);
        Color headColor = ready ? Ui.text() : Ui.base();
        Ui.textIn(b, cw - 10f - rw, 1f, rw, HEAD_H, right, Ui.ORB12, ready ? Ui.text() : Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
        Ui.textIn(b, 10f, 1f, cw - 30f - rw, HEAD_H, project.getName(), Ui.ORB12, headColor, Ui.LEFT, new String[0], new Color[0]);

        float dy = getDescExtra(project, cw);
        Ui.wrap(b, 10f, DESC_Y, cw - INFO_RIGHT - 20f, getDesc(project, cw), Ui.gray());
        UiButtons.area(b, cw - INFO_RIGHT, TOGGLE_Y, INFO_W, TOGGLE_H, "Info", info, true, null, Ui.CENTER, () -> toggleInfo(id));
        if (project.isRepeatable()) addStockToggle(b, cw, progress);

        UiData.bar(b, 10f, ROW_Y + dy, BAR_W, BAR_H, required <= 0 ? 1f : Math.min(1f, points / required), finished ? Ui.pos() : Ui.barBlue(),
                format(points) + " / " + format(required));
        addItems(b, ITEMS_X, ROW_Y + dy - 2f, cw - BUTTONS_RIGHT - 8f - ITEMS_X, project, cargo);
        addButtons(b, cw, ROW_Y + dy - 3f, project, cargo, finished, ready);

        float y = CARD_H + dy - 4f;
        if (hasNote(project, cargo)) {
            RedeemRequirement requirement = project.getRedeemRequirement();
            String note = "Redeem needs " + requirement.count + " " + requirement.getName() + " in your cargo. You have " + requirement.getAvailable(cargo) + ".";
            UiImages.img(b, 10f, CARD_H + dy - 8f, NOTE_ICON, NOTE_ICON, requirement.getSprite());
            Ui.textIn(b, 38f, CARD_H + dy - 8f, cw - 48f, NOTE_ICON, note, Ui.INS15, Ui.neg(), Ui.LEFT, new String[0], new Color[0]);
            y += NOTE_H;
        }

        if (info) addInfo(b, cw, y, project, cargo);
    }

    private void addStockToggle(CustomPanelAPI b, float cw, ResearchProject.Progress progress) {
        ButtonAPI toggle = UiButtons.check(b, cw - STOCK_RIGHT, TOGGLE_Y, STOCK_W, OVERFLOW_LABEL, progress.overflow, true, () -> {
            progress.overflow = !progress.overflow;
            refresh();
        });

        Ui.tooltip(b, toggle, TOOLTIP_W, TooltipMakerAPI.TooltipLocation.LEFT,
                t -> t.addPara("Accept excess donations toward future rewards. When disabled, excess items stay in your cargo.", 0f));
    }

    private float itemsWidth(List<RequiredItem> items, int shown, CargoAPI cargo, boolean owned, float icon, float pad, float gap) {
        float total = 0f;
        for (int i = 0; i < shown; i++) {
            RequiredItem item = items.get(i);
            String s = owned ? format(item.points) + " (" + Ui.num(ResearchProjectDialoguePlugin.getQuantity(cargo, item)) + ")" : format(item.points);
            total += icon + pad + Ui.width(s, Ui.INS15) + 2f + gap;
        }
        return total;
    }

    private void addItems(CustomPanelAPI b, float x, float y, float w, ResearchProject project, CargoAPI cargo) {
        List<RequiredItem> items = project.getRequiredItems();
        int n = items.size();
        if (n == 0) return;

        int shown = n <= 5 ? n : 4;
        float room = w - (n > 5 ? MORE_W : 0f);
        // cells take their measured width; owned counts go first, then spacing and icons shrink
        boolean owned = n <= 3 && itemsWidth(items, shown, cargo, true, ITEM_ICON, 4f, 6f) <= room;
        boolean loose = itemsWidth(items, shown, cargo, owned, ITEM_ICON, 4f, 6f) <= room;
        float pad = loose ? 4f : 2f;
        float gap = loose ? 6f : 2f;
        float size = loose ? ITEM_ICON : 20f;
        boolean fits = itemsWidth(items, shown, cargo, owned, size, pad, gap) <= room;
        float per = room / shown;

        for (int i = 0; i < shown; i++) {
            RequiredItem item = items.get(i);
            String pointsText = format(item.points);
            String own = Ui.num(ResearchProjectDialoguePlugin.getQuantity(cargo, item));
            String s = owned ? pointsText + " (" + own + ")" : pointsText;
            float tw = Ui.width(s, Ui.INS15) + 2f;
            if (!fits) tw = Math.max(0f, Math.min(tw, per - size - pad - gap));

            CustomPanelAPI icon = UiImages.img(b, x, y + (ITEM_ICON - size) / 2f, size, size, item.getSprite());
            Ui.tooltip(b, icon, item.getName(), format(item.points) + " points each.", "You have " + own + ".");
            Ui.textIn(b, x + size + pad, y, tw, ITEM_ICON, s, Ui.INS15, Ui.gray(), Ui.LEFT, new String[]{pointsText}, new Color[]{Ui.hl()});
            x += size + pad + tw + gap;
        }

        if (n > 5) {
            List<String> rest = new ArrayList<>();
            for (RequiredItem item : items.subList(shown, n)) rest.add(item.getName() + ": " + format(item.points) + " points each");
            UiImages.morecell(b, x, y, MORE_W, ITEM_ICON, rest);
        }
    }

    private void addButtons(CustomPanelAPI b, float cw, float y, ResearchProject project, CargoAPI cargo, boolean finished, boolean ready) {
        float available = 0f;
        for (RequiredItem item : project.getRequiredItems()) available += Math.max(0f, ResearchProjectDialoguePlugin.getQuantity(cargo, item));

        boolean canDonate = (!finished || project.allowsOverflow()) && available > 0f;
        String id = project.getId();
        float x = cw - BUTTONS_RIGHT;

        UiButtons.button(b, x, y, BUTTON_W, BUTTON_H, "Donate...", canDonate ? UiButtons.Kind.STD : UiButtons.Kind.OFF, CutStyle.TL_BR,
                canDonate ? () -> ResearchProjectDonationCargoPicker.init(plugin, id) : null);

        ButtonAPI redeem = UiButtons.button(b, x + BUTTON_W + 4f, y, BUTTON_W, BUTTON_H, "Redeem", ready ? UiButtons.Kind.GREEN : UiButtons.Kind.OFF, CutStyle.TL_BR,
                ready ? () -> onRedeem(project) : null);
        if (project.getRedeemRequirement() != null) addRedeemTooltip(b, redeem, project, cargo);
    }

    private void addInfo(CustomPanelAPI b, float cw, float y, ResearchProject project, CargoAPI cargo) {
        float tw = cw - 20f;
        Ui.rule(b, 10f, y, tw);
        y += 8f;

        String desc = getLongDesc(project);
        Ui.wrap(b, 10f, y, tw, desc, Ui.text());
        y += Ui.wrapHeight(desc, Ui.INS15, tw) + 8f;

        float nameX = 34f;
        float pointsX = tw - 320f;
        float haveX = tw - 220f;
        float completeX = tw - 110f;
        Ui.thead(b, 10f, y, tw, new String[]{"Accepted item", "Points each", "You have", "To complete"},
                new float[]{nameX, pointsX, haveX, completeX}, new float[]{pointsX - nameX - 8f, 90f, 100f, 104f},
                new com.fs.starfarer.api.ui.Alignment[]{Ui.LEFT, Ui.RIGHT, Ui.RIGHT, Ui.RIGHT});
        y += Ui.HEADING_H + 4f;

        float left = Math.max(0f, project.getRequiredPoints() - project.getProgress().points);
        for (RequiredItem item : project.getRequiredItems()) {
            String have = Ui.num(ResearchProjectDialoguePlugin.getQuantity(cargo, item));
            String complete = Ui.num(item.points <= 0f ? 0f : (float) Math.ceil(left / item.points));

            UiImages.icon(b, 14f, y + 1f, TABLE_ICON, item.getSprite(), item.getName());
            Ui.textIn(b, 10f + nameX, y, pointsX - nameX - 8f, TABLE_ROW, item.getName(), Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            Ui.textIn(b, 10f + pointsX, y, 90f, TABLE_ROW, format(item.points), Ui.INS15, Ui.hl(), Ui.RIGHT, new String[0], new Color[0]);
            Ui.textIn(b, 10f + haveX, y, 100f, TABLE_ROW, have, Ui.INS15, Ui.text(), Ui.RIGHT, new String[0], new Color[0]);
            Ui.textIn(b, 10f + completeX, y, 104f, TABLE_ROW, complete, Ui.INS15, Ui.hl(), Ui.RIGHT, new String[0], new Color[0]);
            y += TABLE_ROW;
        }
    }

    private void toggleInfo(String id) {
        if (!view.info.remove(id)) view.info.add(id);
        refresh();
    }

    static String format(float value) {
        if (value == Math.floor(value)) return Ui.num(value);
        return Misc.getRoundedValueMaxOneAfterDecimal(value);
    }

    private void addRedeemTooltip(CustomPanelAPI b, ButtonAPI button, ResearchProject project, CargoAPI cargo) {
        Ui.tooltip(b, button, TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, tooltip -> {
            RedeemRequirement req = project.getRedeemRequirement();
            int available = req.getAvailable(cargo);
            tooltip.addPara("Redeeming consumes %s from your fleet cargo (Available: %s).", 0f,
                    new Color[]{Misc.getHighlightColor(), req.isMet(cargo) ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor()},
                    req.count + " " + req.getName(), available + "");

            String lore = project.getRedeemLore();
            if (lore != null) tooltip.addPara(lore, Misc.getGrayColor(), 10f);
        });
    }

    private void onRedeem(ResearchProject project) {
        if (project.getRedeemRequirement() == null) redeem(project, null);
        else showRedeemConfirm(project);
    }

    private void showRedeemConfirm(ResearchProject project) {
        RedeemRequirement req = project.getRedeemRequirement();
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        boolean hasItem = req.isMet(cargo);
        int available = req.getAvailable(cargo);

        CustomDialogDelegate delegate = new BaseCustomDialogDelegate() {
            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                TooltipMakerAPI info = panel.createUIElement(REDEEM_DIALOG_W, REDEEM_DIALOG_H, false);
                info.setParaInsigniaLarge();
                info.addPara("Redeeming consumes %s from your fleet cargo (Available: %s).", 0f,
                        new Color[]{Misc.getHighlightColor(), hasItem ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor()},
                        req.count + " " + req.getName(), available + "");
                panel.addUIElement(info).inTL(0, 0);
            }

            @Override
            public boolean hasCancelButton() {
                return hasItem;
            }

            @Override
            public String getConfirmText() {
                return hasItem ? "Confirm" : "Return";
            }

            @Override
            public String getCancelText() {
                return "Cancel";
            }

            @Override
            public void customDialogConfirm() {
                if (!hasItem) return;
                redeem(project, req.take(cargo));
            }
        };

        plugin.getDialog().showCustomDialog(REDEEM_DIALOG_W, REDEEM_DIALOG_H, delegate);
    }

    private void redeem(ResearchProject project, CargoAPI consumed) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        CargoAPI rewards = project.getRewards();
        cargo.addAll(rewards.createCopy());

        if (project.isRepeatable()) {
            project.getProgress().points = Math.max(0f, project.getProgress().points - project.getRequiredPoints());
            project.getProgress().redeemed = false;
        } else project.getProgress().redeemed = true;

        plugin.setProjectIdForRewards(project.getId(), rewards, consumed);
        refresh();

        Global.getSoundPlayer().playUISound("ui_rep_raise", 1f, 1f);
    }
}
