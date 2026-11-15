package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import indevo.dialogue.sidepanel.HelpIcon;

import java.awt.*;

public final class UiFrame {

    public static final float MARGIN_W = 100f;
    public static final float MARGIN_H = 100f;
    // showCustomDialog adds a 10 px frame and the 45 px button row (obf/ui/newui/super.java)
    public static final float DIALOG_PAD_W = 20f;
    public static final float DIALOG_PAD_H = 55f;
    public static final float TITLE_GAP = 6f;
    public static final float HEADER_Y = 30f;
    public static final float HEADER_H = 24f;
    public static final float SLOT_GAP = 16f;
    public static final float SLOT_ICON = 24f;

    private UiFrame() {
    }

    // content width of a custom dialog drawn as a board of boardW, never wider than the screen minus the margin
    public static float width(float boardW) {
        return Math.min(boardW, Global.getSettings().getScreenWidth() - MARGIN_W) - DIALOG_PAD_W;
    }

    public static float height(float boardH) {
        return Math.min(boardH, Global.getSettings().getScreenHeight() - MARGIN_H) - DIALOG_PAD_H;
    }

    // title strip at y 0: heading with the sub text in grey, the ? icon at the right end when help is set
    public static LabelAPI title(CustomPanelAPI p, float w, String title, String sub, HelpIcon.Content help) {
        float hw = help == null ? w : w - HelpIcon.SIZE - TITLE_GAP;
        String text = sub == null ? title : title + "  " + sub;
        LabelAPI label = Ui.heading(p, 0f, 0f, hw, text);
        if (sub != null && label.getText().contains(sub)) {
            label.setHighlight(sub);
            label.setHighlightColors(Ui.gray());
        }

        if (help != null) p.addUIElement(HelpIcon.create(p, help)).inTL(w - HelpIcon.SIZE - Ui.INSET, 0f);
        return label;
    }

    // header item right to left: icon then grey text ending at right; returns the right edge for the next slot
    public static float slot(CustomPanelAPI p, float right, float y, String sprite, String text, String title, String... lines) {
        float tw = Ui.width(text, Ui.INS15) + 2f;
        float left = right - tw;
        LabelAPI label = Ui.textIn(p, left, y, tw, HEADER_H, text, Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        CustomPanelAPI icon = UiImages.img(p, left - 4f - SLOT_ICON, y, SLOT_ICON, SLOT_ICON, sprite);
        Ui.tooltip(p, icon, title, lines);
        Ui.tooltip(p, label, title, lines);
        return left - 4f - SLOT_ICON - SLOT_GAP;
    }

    // grey label, icon, value in highlight, ending at right; returns the right edge for the next slot
    public static float counter(CustomPanelAPI p, float right, float y, String label, String sprite, String value) {
        float vw = Ui.width(value, Ui.INS15) + 2f;
        float iconX = right - vw - 4f - UiData.COST_ICON;
        Ui.textIn(p, right - vw, y, vw, HEADER_H, value, Ui.INS15, Ui.hl(), Ui.LEFT, new String[0], new Color[0]);
        UiImages.img(p, iconX, y + 1f, UiData.COST_ICON, UiData.COST_ICON, sprite);
        float lw = Ui.width(label, Ui.INS15) + 2f;
        Ui.textIn(p, iconX - 6f - lw, y, lw, HEADER_H, label, Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        return iconX - 6f - lw - SLOT_GAP;
    }

    public static float credits(CustomPanelAPI p, float right, float y) {
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        return counter(p, right, y, "Credits", UiImages.creditsSprite(), Ui.credits(credits));
    }
}
