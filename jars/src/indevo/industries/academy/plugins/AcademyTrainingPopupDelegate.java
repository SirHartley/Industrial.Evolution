package indevo.industries.academy.plugins;

import com.fs.starfarer.api.Global;
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

import static indevo.industries.academy.plugins.AcademyPanelDelegate.getPersonalityColor;
import static indevo.industries.academy.plugins.AcademyPanelDelegate.getPersonalityName;

public class AcademyTrainingPopupDelegate extends ButtonReportingDialogueDelegate {

    public static final float WIDTH = 700f;
    public static final float HEIGHT = 380f;

    private static final float PAD = 10f;
    private static final float SQUARE_W = 124f;
    private static final float SQUARE_H = 30f;
    private static final float SQUARE_GAP = 9f;
    private static final float SQUARE_TEXT_H = 60f;

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
        panel = Global.getSettings().createCustom(w, h, null);

        Color hl = Misc.getHighlightColor();
        String current = officer.getPersonalityAPI().getId();

        TooltipMakerAPI main = panel.createUIElement(w, h, false);
        main.addSectionHeading("Personality training - " + officer.getNameString(), Alignment.MID, 0f);
        main.addPara("Level %s, currently %s. Training steps left: %s.", PAD, new Color[]{hl, getPersonalityColor(current), hl},
                officer.getStats().getLevel() + "", getPersonalityName(current), academy.getOfficerStepsLeft(officer) + "");

        main.addCustom(createSquares(w), PAD);

        if (selected != null) {
            int steps = Academy.getPersonalitySteps(current, selected);
            float fee = academy.getOfficerTrainingFee(steps);
            String days = academy.getOfficerTrainingDays(steps) + "";

            if (fee > 0) main.addPara("%s to %s: %s, %s days, %s.", PAD, new Color[]{getPersonalityColor(current), getPersonalityColor(selected), hl, hl, hl},
                    getPersonalityName(current), getPersonalityName(selected), getStepsText(steps), days, Misc.getDGSCredits(fee));
            else main.addPara("%s to %s: %s, %s days.", PAD, new Color[]{getPersonalityColor(current), getPersonalityColor(selected), hl, hl},
                    getPersonalityName(current), getPersonalityName(selected), getStepsText(steps), days);

            main.addPara(Global.getSettings().getPersonaltySpec(selected).getDescription(), Misc.getGrayColor(), PAD);
        }

        panel.addUIElement(main).inTL(0f, 0f);
        basePanel.addComponent(panel);
    }

    private CustomPanelAPI createSquares(float w) {
        Color dark = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();
        Color gray = Misc.getGrayColor();
        Color hl = Misc.getHighlightColor();

        String current = officer.getPersonalityAPI().getId();
        List<String> reachable = academy.getReachablePersonalities(officer);
        int stepsLeft = academy.getOfficerStepsLeft(officer);
        int trained = academy.getTrainedSteps(officer);

        CustomPanelAPI row = panel.createCustomPanel(w, SQUARE_H + SQUARE_TEXT_H, new ButtonReportingCustomPanel(this));
        float x = (w - 5 * SQUARE_W - 4 * SQUARE_GAP) / 2f;

        for (final String id : Academy.COLOURS_BY_PERSONALITY.keySet()) {
            int steps = Academy.getPersonalitySteps(current, id);
            float fee = academy.getOfficerTrainingFee(steps);
            boolean isCurrent = id.equals(current);
            boolean affordable = BillingHelper.canAfford(fee);
            boolean selectable = reachable.contains(id) && affordable;

            TooltipMakerAPI square = row.createUIElement(SQUARE_W, SQUARE_H + SQUARE_TEXT_H, false);
            Color color = selectable || isCurrent ? getPersonalityColor(id) : gray;
            ButtonAPI button = square.addAreaCheckbox(getPersonalityName(id), new ButtonAction(this) {
                @Override
                public void execute() {
                    selected = id;
                    recreatePanel();
                }
            }, color, dark, bright, SQUARE_W, SQUARE_H, 0f);
            button.setEnabled(selectable);
            button.setChecked(id.equals(selected));

            if (isCurrent) {
                square.addPara("Current", gray, 5f).setAlignment(Alignment.MID);
            } else {
                square.addPara(getStepsText(steps), selectable ? Misc.getTextColor() : gray, 5f).setAlignment(Alignment.MID);

                if (!reachable.contains(id)) {
                    String reason = !academy.hasAlphaCore() && steps <= 2 - trained && steps > stepsLeft ? "Needs an alpha core" : "Unavailable";
                    square.addPara(reason, gray, 1f).setAlignment(Alignment.MID);
                } else if (!affordable) {
                    square.addPara("Not enough credits", Misc.getNegativeHighlightColor(), 1f).setAlignment(Alignment.MID);
                } else {
                    square.addPara("%s days", 1f, hl, academy.getOfficerTrainingDays(steps) + "").setAlignment(Alignment.MID);
                    if (fee > 0) square.addPara("%s", 1f, hl, Misc.getDGSCredits(fee)).setAlignment(Alignment.MID);
                }
            }

            row.addUIElement(square).inTL(x, 0f);
            x += SQUARE_W + SQUARE_GAP;
        }

        return row;
    }

    private static String getStepsText(int steps) {
        return steps + (steps == 1 ? " step" : " steps");
    }
}
