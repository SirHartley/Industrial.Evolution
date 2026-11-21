package indevo.industries.changeling.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.changeling.industry.ChangelingTarget;
import indevo.industries.changeling.industry.EffectLine;
import indevo.industries.changeling.industry.SubIndustry;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.compatibility.AotdManager;
import indevo.utils.helper.BillingHelper;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiButtons;
import indevo.utils.ui.UiData;
import indevo.utils.ui.UiFrame;
import indevo.utils.ui.UiHost;
import indevo.utils.ui.UiImages;
import indevo.utils.ui.UiRoot;
import indevo.utils.ui.UiScroll;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// variant picker for governments and specializations: picture tiles, the picked variant as a two column card
public class ChangelingIndustryDialogueDelegate extends BaseCustomDialogDelegate {

    public static final float BOARD_W = 840f;
    public static final String RESEARCH = "Requires research";
    public static final String NO_CREDITS = "Not enough credits";
    static final int PER_ROW = 4;
    static final float GRID_X = 2f;
    static final float GRID_Y = 26f;
    static final float TILE_W = 198f;
    static final float TILE_H = 142f;
    static final float TILE_GAP = 8f;
    static final float TILE_PAD = 8f;
    static final float ART_X = 4f;
    static final float HEAD_Y = 4f;
    static final float ART_Y = 22f;
    static final float ART_W = 190f;
    static final float ART_H = 95f;
    static final float TILE_LINE_Y = 122f;
    static final float DIM = 0.45f;
    static final float SECTION_GAP = 20f;
    static final float COLUMN_GAP = 24f;
    static final float CARD_TOP = Ui.HEADING_H + 6f;
    static final float FACT_PITCH = 18f;
    static final float FACT_LABEL_W = 90f;
    static final float LORE_GAP = 10f;
    static final float TIP_W = 400f;

    private final Industry industry;
    private final ChangelingTarget target;
    private final List<Option> options = new ArrayList<>();
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private final Runnable onLeave;
    private String selected;
    private UiRoot root;

    private static class Option {
        SubIndustryData data;
        List<EffectLine> effects;
        String sprite;
        String lore;
        String research;
        String blocked;
        String permanence;
        float cost;
        boolean afford;
        boolean current;

        // reason the tile can not be picked, null when it can
        String why() {
            if (research != null) return research;
            if (blocked != null) return blocked;
            return !current && !afford ? NO_CREDITS : null;
        }
    }

    public ChangelingIndustryDialogueDelegate(Industry industry, ChangelingTarget target, Runnable onLeave) {
        this.industry = industry;
        this.target = target;
        this.onLeave = onLeave;

        MarketAPI market = industry.getMarket();
        selected = target.getCurrentId(market);

        for (SubIndustryData data : target.getOptions()) {
            SubIndustry sub = data.newInstance();
            sub.init(industry);

            Option o = new Option();
            o.data = data;
            o.effects = target.getEffects(data);
            o.sprite = sub.getImageName(market);
            o.lore = Global.getSettings().getDescription(data.descriptionID, Description.Type.CUSTOM).getText1();
            o.current = data.id.equals(selected);
            o.research = o.current ? null : AotdManager.getMissingResearchReason(data.getUnlockId(), market);
            o.blocked = o.current || sub.isAvailableToBuild() ? null : sub.getUnavailableReason();
            o.permanence = target.getPermanence(data);
            o.cost = sub.getCost();
            o.afford = BillingHelper.canAfford(o.cost);
            SubIndustry.ensureTexture(o.sprite);
            options.add(o);
        }
    }

    public static void show(DialogCreatorUI ui, Industry industry, ChangelingTarget target) {
        ChangelingIndustryDialogueDelegate delegate = new ChangelingIndustryDialogueDelegate(industry, target, null);
        float w = UiFrame.width(BOARD_W);
        ui.showDialog(w, delegate.height(w), delegate);
    }

    // opens the picker on its own with the given variant picked (null for the current one), for code and tests
    public static boolean show(Industry industry, ChangelingTarget target, String picked) {
        return UiHost.show(industry.getMarket().getPrimaryEntity(), dialog -> {
            ChangelingIndustryDialogueDelegate delegate = new ChangelingIndustryDialogueDelegate(industry, target, dialog::dismiss);
            Option o = delegate.option(picked);
            if (o != null && o.why() == null) delegate.selected = picked;
            float w = UiFrame.width(BOARD_W);
            dialog.showCustomDialog(w, delegate.height(w), delegate);
        });
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        root = new UiRoot(panel, scroll, this::build);
        root.refresh();
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return "Confirm";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    // the host confirm can not be disabled, so only a tile that can be picked is ever selected
    @Override
    public void customDialogConfirm() {
        confirm();
        if (onLeave != null) onLeave.run();
    }

    private void confirm() {
        Option o = option(selected);
        if (o == null || o.current || o.why() != null) return;

        MarketAPI market = industry.getMarket();
        if (o.data.id.equals(target.getCurrentId(market)) || !BillingHelper.canAfford(o.cost)) return;

        target.setCurrent(market, o.data);
        BillingHelper.chargeNow(o.cost);
    }

    @Override
    public void customDialogCancel() {
        if (onLeave != null) onLeave.run();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        String help = target.getHelp();
        UiFrame.title(p, w, target.getTitle(industry), null, help == null ? null : t -> t.addPara(help, 0f));

        for (int i = 0; i < options.size(); i++) {
            tile(p, GRID_X + (i % PER_ROW) * (TILE_W + TILE_GAP), GRID_Y + (i / PER_ROW) * (TILE_H + TILE_GAP), options.get(i));
        }

        Option shown = option(selected);
        float cardY = cardY();
        if (shown != null) card(p, cardY, w, Math.min(tallestCard(w), h - cardY), shown);
    }

    private void tile(CustomPanelAPI p, float x, float y, Option o) {
        String why = o.why();
        boolean picked = o.data.id.equals(selected);
        float tw = TILE_W - 2f * TILE_PAD;

        CustomPanelAPI tile = Ui.panel(p, x, y, TILE_W, TILE_H);
        ButtonAPI area = UiButtons.area(tile, 0f, 0f, TILE_W, TILE_H, null, picked, why == null, null, Ui.CENTER, () -> {
            selected = o.data.id;
            root.refresh();
        });

        Color name = picked ? Ui.bright() : o.research != null ? Ui.gray() : why != null ? Ui.neg() : Ui.base();
        Ui.heading(tile, ART_X, HEAD_Y, ART_W, o.data.name, Ui.LEFT, name, Ui.dark());

        if (o.research != null) UiImages.grey(tile, ART_X, ART_Y, ART_W, ART_H, o.sprite);
        else UiImages.img(tile, ART_X, ART_Y, ART_W, ART_H, o.sprite, null, why == null ? 1f : DIM);

        if (o.current) Ui.text(tile, TILE_PAD, TILE_LINE_Y, tw, "Current", Ui.gray(), Ui.RIGHT);
        else if (o.research != null) Ui.text(tile, TILE_PAD, TILE_LINE_Y, tw, RESEARCH, Ui.gray());
        else if (o.blocked != null) Ui.text(tile, TILE_PAD, TILE_LINE_Y, tw, o.blocked, Ui.neg());
        else Ui.text(tile, TILE_PAD, TILE_LINE_Y, tw, Ui.credits(o.cost), o.afford ? Ui.hl() : Ui.neg(), Ui.RIGHT);

        if (why != null) Ui.tooltip(tile, area, TIP_W, TooltipMakerAPI.TooltipLocation.RIGHT, t -> tileTip(t, o));
    }

    private void tileTip(TooltipMakerAPI t, Option o) {
        t.addPara(o.data.name, Ui.hl(), 0f);
        t.addPara(o.why(), o.research != null ? Ui.gray() : Ui.neg(), 10f);
        EffectLines.add(t, TIP_W - 10f, o.effects, 10f);
        t.addPara("Cost: %s", 10f, o.afford ? Ui.hl() : Ui.neg(), Ui.credits(o.cost));
    }

    private void card(CustomPanelAPI p, float y, float w, float maxH, Option o) {
        float half = half(w);
        float rx = half + COLUMN_GAP;
        Ui.heading(p, 0f, y, half, o.data.name, Ui.LEFT);
        Ui.heading(p, rx, y, half, "Effects", Ui.LEFT);

        float top = y + CARD_TOP;
        float fy = top + UiData.COST_ROW_H;
        float vw = half - FACT_LABEL_W;
        Ui.textIn(p, 0f, top, FACT_LABEL_W, UiData.COST_ROW_H, o.current ? "Status" : "Cost", Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        if (o.current) Ui.textIn(p, FACT_LABEL_W, top, vw, UiData.COST_ROW_H, "Current", Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        else UiData.cost(p, FACT_LABEL_W, top, vw, UiImages.creditsSprite(), Ui.credits(o.cost), Ui.credits(Global.getSector().getPlayerFleet().getCargo().getCredits().get()), o.afford, UiData.COST_ICON);
        if (o.data.requires != null) fy = fact(p, fy, half, "Requires", o.data.requires, o.blocked != null ? Ui.neg() : Ui.text());
        if (o.permanence != null) fy = fact(p, fy, half, "Permanent", o.permanence, Ui.text());

        // the window is sized for the full lore, it is only cut where the screen is too small for that
        float room = y + maxH - fy - LORE_GAP;
        int lines = Math.max(1, (int) Math.floor((room + 1f) / Ui.wrapHeight("Ag", Ui.INS15, half)));
        String lore = Ui.lines(o.lore, Ui.INS15, half, lines);
        LabelAPI label = Ui.wrap(p, 0f, fy + LORE_GAP, half, lore, Ui.gray());
        if (!lore.equals(o.lore)) Ui.tooltip(p, label, null, o.lore);

        EffectLines.draw(p, rx, top, half, o.effects);
    }

    private float fact(CustomPanelAPI p, float y, float w, String label, String value, Color c) {
        Ui.text(p, 0f, y, FACT_LABEL_W, label, Ui.gray());
        Ui.text(p, FACT_LABEL_W, y, w - FACT_LABEL_W, value, c);
        return y + FACT_PITCH;
    }

    private Option option(String id) {
        for (Option o : options) if (o.data.id.equals(id)) return o;
        return null;
    }

    private static float half(float w) {
        return (float) Math.floor((w - COLUMN_GAP) / 2f);
    }

    private float cardY() {
        int rows = (options.size() + PER_ROW - 1) / PER_ROW;
        return GRID_Y + rows * (TILE_H + TILE_GAP) - TILE_GAP + SECTION_GAP;
    }

    // one height for every card, so nothing moves when another tile is picked
    private float tallestCard(float w) {
        float half = half(w);
        float tallest = 0f;
        for (Option o : options) {
            int facts = 1 + (o.data.requires != null ? 1 : 0) + (o.permanence != null ? 1 : 0);
            float left = UiData.COST_ROW_H + (facts - 1) * FACT_PITCH + LORE_GAP + Ui.wrapHeight(o.lore, Ui.INS15, half);
            tallest = Math.max(tallest, CARD_TOP + Math.max(left, EffectLines.height(o.effects)));
        }

        return tallest;
    }

    private float height(float w) {
        return UiFrame.height(cardY() + tallestCard(w) + UiFrame.DIALOG_PAD_H);
    }
}
