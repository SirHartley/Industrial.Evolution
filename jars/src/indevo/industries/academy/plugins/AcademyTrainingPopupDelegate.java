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
import static indevo.industries.academy.plugins.AcademyPanelDelegate.HEADING_EXTRA;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.STORED_BG;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.TOOLTIP_INSET;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.getPersonalityColor;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.getPersonalityName;

public class AcademyTrainingPopupDelegate extends ButtonReportingDialogueDelegate {

    public static final float WIDTH = 700f;
    public static final float HEIGHT = 400f;

    private static final float PAD = 10f;
    private static final float SQUARE = 124f;
    private static final float SQUARE_GAP = 12f;
    private static final float BAR_H = 4f;
    private static final float HEADER_H = 50f;

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

        TooltipMakerAPI header = panel.createUIElement(w, HEADER_H, false);
        header.addSectionHeading("Personality training - " + officer.getNameString(), Alignment.MID, 0f);
        header.addPara("Level %s, currently %s. Training steps left: %s.", PAD, new Color[]{hl, getPersonalityColor(current), hl},
                officer.getStats().getLevel() + "", getPersonalityName(current), academy.getOfficerStepsLeft(officer) + "");
        panel.addUIElement(header).inTL(0f, 0f);

        float squaresY = HEADER_H + PAD;
        float x = (w - 5 * SQUARE - 4 * SQUARE_GAP) / 2f;
        for (String id : Academy.COLOURS_BY_PERSONALITY.keySet()) {
            addSquare(id, x, squaresY);
            x += SQUARE + SQUARE_GAP;
        }

        if (selected != null) {
            int steps = Academy.getPersonalitySteps(current, selected);
            float fee = academy.getOfficerTrainingFee(steps);
            String days = academy.getOfficerTrainingDays(steps) + " days";

            TooltipMakerAPI summary = panel.createUIElement(w, h - squaresY - SQUARE - PAD, false);
            if (fee > 0) summary.addPara("%s to %s: %s, %s.", 0f, new Color[]{getPersonalityColor(current), getPersonalityColor(selected), hl, hl},
                    getPersonalityName(current), getPersonalityName(selected), days, Misc.getDGSCredits(fee));
            else summary.addPara("%s to %s: %s.", 0f, new Color[]{getPersonalityColor(current), getPersonalityColor(selected), hl},
                    getPersonalityName(current), getPersonalityName(selected), days);

            summary.addPara(Global.getSettings().getPersonaltySpec(selected).getDescription(), Misc.getGrayColor(), PAD);
            panel.addUIElement(summary).inTL(0f, squaresY + SQUARE + PAD * 1.5f);
        } else {
            TooltipMakerAPI none = panel.createUIElement(w, 20f, false);
            none.addPara("No personality can be trained right now.", Misc.getGrayColor(), 0f);
            panel.addUIElement(none).inTL(0f, squaresY + SQUARE + PAD * 1.5f);
        }

        basePanel.addComponent(panel);
    }

    private void addSquare(final String id, float x, float y) {
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
        Color color = live ? getPersonalityColor(id) : Misc.scaleColorOnly(getPersonalityColor(id), 0.35f);

        TooltipMakerAPI background = panel.createUIElement(SQUARE, SQUARE, false);
        ButtonAPI button = background.addAreaCheckbox("", new ButtonAction(this) {
            @Override
            public void execute() {
                selected = id;
                recreatePanel();
            }
        }, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), SQUARE, SQUARE, 0f);
        button.setEnabled(selectable);
        button.setChecked(id.equals(selected));
        panel.addUIElement(background).inTL(x - TOOLTIP_INSET, y);

        TooltipMakerAPI header = panel.createUIElement(SQUARE, CARD_HEADER_H, false);
        header.addSectionHeading(getPersonalityName(id), live ? Misc.getBasePlayerColor() : gray, live ? Misc.getDarkPlayerColor() : STORED_BG,
                Alignment.MID, SQUARE - HEADING_EXTRA, 0f);
        panel.addUIElement(header).inTL(x, y);
        panel.addComponent(createBar(color)).inTL(x + 1f, y + CARD_HEADER_H);

        float bodyY = y + CARD_HEADER_H + BAR_H;
        float textW = SQUARE - TOOLTIP_INSET * 2;
        TooltipMakerAPI text = panel.createUIElement(textW, SQUARE - CARD_HEADER_H - BAR_H, false);

        if (isCurrent) {
            text.addPara("Current", gray, 0f).setAlignment(Alignment.MID);
        } else if (!isReachable) {
            boolean needsAlpha = !academy.hasAlphaCore() && steps <= 2 - academy.getTrainedSteps(officer) && steps > academy.getOfficerStepsLeft(officer);
            String reason = needsAlpha ? "Needs an alpha core" : "Out of reach";
            text.addPara(reason, gray, 0f).setAlignment(Alignment.MID);
        } else {
            text.addPara("%s days", 0f, hl, academy.getOfficerTrainingDays(steps) + "").setAlignment(Alignment.MID);
            if (fee > 0) text.addPara(Misc.getDGSCredits(fee), affordable ? hl : Misc.getNegativeHighlightColor(), 2f).setAlignment(Alignment.MID);
        }

        float textY = isReachable && fee > 0 ? 35f : 43f;
        panel.addUIElement(text).inTL(x, bodyY + textY);
    }

    private CustomPanelAPI createBar(final Color color) {
        return panel.createCustomPanel(SQUARE - 2f, BAR_H, new BaseCustomUIPanelPlugin() {
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
