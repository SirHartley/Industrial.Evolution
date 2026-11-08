package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.dialogue.sidepanel.ButtonAction;
import indevo.dialogue.sidepanel.ButtonReportingCustomPanel;
import indevo.dialogue.sidepanel.ButtonReportingDialogueDelegate;
import indevo.industries.academy.industry.Academy;
import indevo.utils.helper.BillingHelper;

import java.awt.*;
import java.util.List;

import static indevo.industries.academy.plugins.AcademyPanelDelegate.CARD_HEADER_H;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.STORED_BG;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.TOOLTIP_INSET;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.addHeading;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.getPersonalityColor;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.getPersonalityName;

public class AcademyTrainingPopupDelegate extends ButtonReportingDialogueDelegate {

    private static final float PAD = 10f;
    private static final float TILE_W = 120f;
    private static final float TILE_H = 66f;
    private static final float TILE_GAP = 10f;
    private static final float BAR_H = 4f;
    private static final float LINE_H = 20f;

    public static final float WIDTH = 5 * TILE_W + 4 * TILE_GAP;

    private final Academy academy;
    private final AcademyDialogPlugin plugin;
    private final PersonAPI officer;
    private String selected;

    private CustomPanelAPI basePanel;
    private CustomPanelAPI panel;
    private CustomDialogCallback callback;
    private boolean open = false;

    public AcademyTrainingPopupDelegate(Academy academy, AcademyDialogPlugin plugin, PersonAPI officer) {
        this.academy = academy;
        this.plugin = plugin;
        this.officer = officer;
        this.selected = getNearestSelectable();
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.basePanel = panel;
        this.callback = callback;
        this.open = true;
        recreatePanel();
    }

    public boolean isOpen() {
        return open;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return "Train";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    @Override
    public void customDialogConfirm() {
        open = false;
        if (selected != null) academy.setOfficerForTraining(officer, selected);
        plugin.showPanelNextFrame();
    }

    @Override
    public void customDialogCancel() {
        open = false;
        plugin.showPanelNextFrame();
    }

    // fits the longest personality description so the dialog never grows
    public float getHeight() {
        CustomPanelAPI scratch = Global.getSettings().createCustom(WIDTH, 1000f, null);
        float text = 0f;

        for (String id : Academy.COLOURS_BY_PERSONALITY.keySet()) {
            TooltipMakerAPI summary = scratch.createUIElement(WIDTH, 1000f, false);
            addSummary(summary, officer.getPersonalityAPI().getId(), id);
            text = Math.max(text, summary.getHeightSoFar());
        }

        return getTilesY() + TILE_H + PAD + text + PAD;
    }

    private static float getTilesY() {
        return CARD_HEADER_H + PAD + LINE_H + PAD;
    }

    private String getNearestSelectable() {
        String current = officer.getPersonalityAPI().getId();
        String nearest = null;
        int nearestSteps = Integer.MAX_VALUE;

        for (String id : academy.getReachablePersonalities(officer)) {
            int steps = Academy.getPersonalitySteps(current, id);
            if (steps < nearestSteps && BillingHelper.canAfford(academy.getOfficerTrainingFee(steps))) {
                nearest = id;
                nearestSteps = steps;
            }
        }

        return nearest;
    }

    private void recreatePanel() {
        if (panel != null) basePanel.removeComponent(panel);

        float w = basePanel.getPosition().getWidth();
        float h = basePanel.getPosition().getHeight();
        panel = Global.getSettings().createCustom(w, h, new ButtonReportingCustomPanel(this));

        Color hl = Misc.getHighlightColor();
        String current = officer.getPersonalityAPI().getId();

        addHeading(panel, "Personality training - " + officer.getNameString(), Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), 0f, 0f, w);

        TooltipMakerAPI header = panel.createUIElement(w, LINE_H, false);
        header.addPara("Level %s, currently %s. Training steps left: %s.", 0f, new Color[]{hl, getPersonalityColor(current), hl},
                officer.getStats().getLevel() + "", getPersonalityName(current), academy.getOfficerStepsLeft(officer) + "");
        panel.addUIElement(header).inTL(-TOOLTIP_INSET, CARD_HEADER_H + PAD);

        float tilesY = getTilesY();
        float x = (w - WIDTH) / 2f;
        for (String id : Academy.COLOURS_BY_PERSONALITY.keySet()) {
            addTile(id, x, tilesY);
            x += TILE_W + TILE_GAP;
        }

        TooltipMakerAPI summary = panel.createUIElement(w, h - tilesY - TILE_H - PAD, false);
        addSummary(summary, current, selected);
        panel.addUIElement(summary).inTL(-TOOLTIP_INSET, tilesY + TILE_H + PAD);

        basePanel.addComponent(panel);
    }

    private void addSummary(TooltipMakerAPI summary, String current, String target) {
        Color hl = Misc.getHighlightColor();

        if (target == null) {
            summary.addPara("No personality can be trained right now.", Misc.getGrayColor(), 0f);
            return;
        }

        int steps = Academy.getPersonalitySteps(current, target);
        float fee = academy.getOfficerTrainingFee(steps);
        String days = academy.getOfficerTrainingDays(steps) + " days";

        if (fee > 0) summary.addPara("%s to %s: %s, %s.", 0f, new Color[]{getPersonalityColor(current), getPersonalityColor(target), hl, hl},
                getPersonalityName(current), getPersonalityName(target), days, Misc.getDGSCredits(fee));
        else summary.addPara("%s to %s: %s.", 0f, new Color[]{getPersonalityColor(current), getPersonalityColor(target), hl},
                getPersonalityName(current), getPersonalityName(target), days);

        summary.addPara(Global.getSettings().getPersonaltySpec(target).getDescription(), Misc.getGrayColor(), PAD);
    }

    private void addTile(final String id, float x, float y) {
        Color gray = Misc.getGrayColor();
        Color hl = Misc.getHighlightColor();
        String current = officer.getPersonalityAPI().getId();
        List<String> reachable = academy.getReachablePersonalities(officer);

        int steps = Academy.getPersonalitySteps(current, id);
        float fee = academy.getOfficerTrainingFee(steps);
        boolean isCurrent = id.equals(current);
        boolean isReachable = reachable.contains(id);
        boolean affordable = BillingHelper.canAfford(fee);
        boolean selectable = isReachable && affordable;
        boolean live = isCurrent || isReachable;
        Color color = live ? getPersonalityColor(id) : Misc.scaleColorOnly(getPersonalityColor(id), 0.5f);

        TooltipMakerAPI background = panel.createUIElement(TILE_W, TILE_H, false);
        ButtonAPI button = background.addAreaCheckbox("", new ButtonAction(this) {
            @Override
            public void execute() {
                selected = id;
                recreatePanel();
            }
        }, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), TILE_W, TILE_H, 0f);
        button.setEnabled(selectable);
        button.setChecked(id.equals(selected));
        panel.addUIElement(background).inTL(x - TOOLTIP_INSET, y);

        Color headerText = isCurrent ? hl : live ? Misc.getBasePlayerColor() : gray;
        addHeading(panel, getPersonalityName(id), headerText, live ? Misc.getDarkPlayerColor() : STORED_BG, x, y, TILE_W);
        panel.addComponent(createBar(color)).inTL(x + 1f, y + CARD_HEADER_H);
        if (isCurrent) panel.addComponent(createFrame(hl)).inTL(x, y);

        float bodyY = y + CARD_HEADER_H + BAR_H;
        float bodyH = TILE_H - CARD_HEADER_H - BAR_H;
        TooltipMakerAPI text = panel.createUIElement(TILE_W, bodyH, false);

        if (isCurrent) {
            text.addPara("Current", hl, 0f).setAlignment(Alignment.MID);
        } else if (!isReachable) {
            boolean needsAlpha = !academy.hasAlphaCore() && steps <= academy.getMaxOfficerSteps(true) - academy.getTrainedSteps(officer) && steps > academy.getOfficerStepsLeft(officer);
            String reason = needsAlpha ? "Needs an alpha core" : "Out of reach";
            text.addPara(reason, gray, 0f).setAlignment(Alignment.MID);
        } else {
            text.addPara("%s days", 0f, hl, academy.getOfficerTrainingDays(steps) + "").setAlignment(Alignment.MID);
            if (fee > 0) text.addPara(Misc.getDGSCredits(fee), affordable ? hl : Misc.getNegativeHighlightColor(), 0f).setAlignment(Alignment.MID);
        }

        panel.addUIElement(text).inTL(x - TOOLTIP_INSET, bodyY + Math.max(0f, (bodyH - text.getHeightSoFar()) / 2f));
    }

    private CustomPanelAPI createFrame(final Color color) {
        return panel.createCustomPanel(TILE_W, TILE_H, new BaseCustomUIPanelPlugin() {
            private PositionAPI pos;

            @Override
            public void positionChanged(PositionAPI position) {
                pos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (pos == null) return;
                float x = pos.getX();
                float y = pos.getY();
                float w = pos.getWidth();
                float h = pos.getHeight();
                Misc.renderQuad(x, y, w, 1f, color, alphaMult);
                Misc.renderQuad(x, y + h - 1f, w, 1f, color, alphaMult);
                Misc.renderQuad(x, y, 1f, h, color, alphaMult);
                Misc.renderQuad(x + w - 1f, y, 1f, h, color, alphaMult);
            }
        });
    }

    private CustomPanelAPI createBar(final Color color) {
        return panel.createCustomPanel(TILE_W - 2f, BAR_H, new BaseCustomUIPanelPlugin() {
            private PositionAPI pos;

            @Override
            public void positionChanged(PositionAPI position) {
                pos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (pos == null) return;
                Misc.renderQuad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), color, alphaMult);
            }
        });
    }
}
