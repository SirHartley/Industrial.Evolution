package indevo.utils.ui;

import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.function.IntConsumer;

public final class UiButtons {

    public enum Kind {
        STD, GREEN, RED, AMBER, STORY, HOT, OFF
    }

    public static final float REASON_GAP = 4f;
    // green and amber fills at 60 % read like vanilla buttons; the full state colours stay for strips, bars and marks
    public static final float FILL = 0.6f;
    public static final float TAB_GAP = 4f;
    public static final float STRIP_W = 4f;
    public static final float ICON_GAP = 5f;

    // which inline confirm is armed, kept in the panel's view state
    public static final class Confirm {
        private String armed;

        public boolean isArmed(String id) {
            return id != null && id.equals(armed);
        }

        public void arm(String id) {
            armed = id;
        }

        public void clear() {
            armed = null;
        }
    }

    private UiButtons() {
    }

    public static ButtonAPI button(CustomPanelAPI p, float x, float y, float w, float h, String label, Kind kind, Runnable action) {
        return button(p, x, y, w, h, label, kind, CutStyle.ALL, null, null, Ui.ORB12, action);
    }

    public static ButtonAPI button(CustomPanelAPI p, float x, float y, float w, float h, String label, Kind kind, CutStyle cut, Runnable action) {
        return button(p, x, y, w, h, label, kind, cut, null, null, Ui.ORB12, action);
    }

    // icon and sub text are drawn over an empty button face, labels never take the click
    public static ButtonAPI button(CustomPanelAPI p, float x, float y, float w, float h, String label, Kind kind, CutStyle cut,
                                   String icon, String sub, String font, Runnable action) {
        boolean off = kind == Kind.OFF || action == null;
        boolean face = icon == null && sub == null;

        TooltipMakerAPI element = p.createUIElement(w + Ui.INSET, h, false);
        if (Ui.ORB20.equals(font)) element.setButtonFontOrbitron20();
        else if (Ui.VIC10.equals(font)) element.setButtonFontVictor10();

        String text = face ? Ui.fit(label, font, w - h / 2f) : "";
        ButtonAPI button = element.addButton(text, action, fg(kind), bg(kind), Alignment.MID, cut, w, h, 0f);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        if (off) button.setEnabled(false);
        if (face) {
            if (!text.equals(label)) Ui.tooltip(p, button, null, label);
            return button;
        }

        Color fg = off ? Ui.gray() : fg(kind);
        String line = sub == null ? label : label + " " + sub;
        float iconSize = icon == null ? 0f : Math.min(h - 6f, 22f);
        float iconW = icon == null ? 0f : iconSize + ICON_GAP;
        float textW = Math.min(Ui.width(line, font) + 2f, w - h / 2f - iconW);
        float left = x + Math.round((w - iconW - textW) / 2f);

        if (icon != null) UiImages.img(p, left, y + Math.round((h - iconSize) / 2f), iconSize, iconSize, icon, null, off ? 0.4f : 1f);
        Ui.textIn(p, left + iconW, y, textW, h, line, font, fg, Ui.LEFT, new String[]{sub}, new Color[]{off ? Ui.gray() : Ui.hl()});
        return button;
    }

    // a blocked action: grey button and the reason in red below; the reason line is reserved either way
    // takes guardHeight(w, h, reason)
    public static ButtonAPI guarded(CustomPanelAPI p, float x, float y, float w, float h, String label, Kind kind, String reason, Runnable action) {
        boolean blocked = reason != null;
        ButtonAPI button = button(p, x, y, w, h, label, blocked ? Kind.OFF : kind, blocked ? null : action);
        if (blocked) Ui.wrap(p, x, y + h + REASON_GAP, w, reason, Ui.neg());
        return button;
    }

    public static float guardHeight(float w, float h, String reason) {
        return h + REASON_GAP + reasonHeight(w, reason);
    }

    public static float reasonHeight(float w, String reason) {
        if (reason == null) return Ui.LINE_H;
        return Math.max(Ui.LINE_H, Ui.wrapHeight(reason, Ui.INS15, w) + 4f);
    }

    // armed: Confirm and Cancel (or the given labels) stand where the button was
    public static ButtonAPI confirm(CustomPanelAPI p, float x, float y, float w, float h, String label, Kind kind,
                                    Confirm state, String id, Runnable action, Runnable refresh) {
        return confirm(p, x, y, w, h, label, kind, state, id, action, refresh, "Confirm", "Cancel");
    }

    public static ButtonAPI confirm(CustomPanelAPI p, float x, float y, float w, float h, String label, Kind kind,
                                    Confirm state, String id, Runnable action, Runnable refresh, String okLabel, String cancelLabel) {
        if (!state.isArmed(id)) return button(p, x, y, w, h, label, kind, action == null ? null : () -> {
            state.arm(id);
            refresh.run();
        });

        float half = Math.round((w - 6f) / 2f);
        ButtonAPI ok = button(p, x, y, half, h, okLabel, kind == Kind.RED ? Kind.RED : Kind.GREEN, () -> {
            state.clear();
            action.run();
            refresh.run();
        });
        button(p, x + w - half, y, half, h, cancelLabel, Kind.STD, () -> {
            state.clear();
            refresh.run();
        });
        return ok;
    }

    public static ButtonAPI tab(CustomPanelAPI p, float x, float y, float w, float h, String label, boolean on, boolean enabled, Runnable action) {
        Color fg = on ? Ui.bright() : Ui.base();
        Color bg = on ? Misc.setAlpha(Ui.dark(), 255) : Misc.scaleAlpha(Ui.dark(), 0.6f);
        String text = Ui.fit(label, Ui.ORB12, w - 12f);

        TooltipMakerAPI element = p.createUIElement(w + Ui.INSET, h, false);
        ButtonAPI button = element.addButton(text, action, fg, bg, Alignment.MID, CutStyle.TOP, w, h, 0f);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        if (!enabled) button.setEnabled(false);
        if (!text.equals(label)) Ui.tooltip(p, button, null, label);
        return button;
    }

    // tabs left to right, tabW 0 splits w evenly, then a rule below; returns the y under the rule
    public static float tabs(CustomPanelAPI p, float x, float y, float w, float tabW, float h, String[] labels, int selected, boolean[] enabled, IntConsumer onSelect) {
        int n = labels.length;
        float each = tabW > 0f ? tabW : Math.round((w - TAB_GAP * (n - 1)) / n);

        for (int i = 0; i < n; i++) {
            int index = i;
            boolean on = i == selected;
            tab(p, x + i * (each + TAB_GAP), y, each, h, labels[i], on, enabled == null || enabled[i], () -> onSelect.accept(index));
        }

        Ui.rule(p, x, y + h, w, Misc.scaleAlpha(Ui.base(), 0.55f));
        return y + h + 1f;
    }

    // area checkbox look: checked is the bright frame and fill, strip is a colour bar at the left edge
    public static ButtonAPI area(CustomPanelAPI p, float x, float y, float w, float h, String label, boolean on, boolean enabled,
                                 Color strip, Alignment align, Runnable action) {
        boolean centred = align == Ui.CENTER;
        String text = centred && label != null ? Ui.fit(label, Ui.ORB12, w - 12f) : "";

        TooltipMakerAPI element = p.createUIElement(w + Ui.INSET, h, false);
        element.setAreaCheckboxFont(Ui.ORB12);
        ButtonAPI button = element.addAreaCheckbox(text, action, Ui.base(), Ui.dark(), Ui.bright(), w, h, 0f);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        button.setChecked(on);
        if (!enabled || action == null) button.setEnabled(false);

        if (strip != null) {
            float a = enabled ? 1f : 0.35f;
            Ui.painted(p, x + 1f, y + 1f, STRIP_W, h - 2f, (pos, alpha) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), strip, alpha * a), null);
        }

        if (!centred && label != null && !label.isEmpty()) {
            Color fg = !enabled ? Ui.gray() : on ? Ui.bright() : Ui.base();
            float inset = strip != null ? 14f : 10f;
            Ui.textIn(p, x + inset, y, w - inset - 10f, h, label, Ui.ORB12, fg, align, new String[0], new Color[0]);
        }

        return button;
    }

    // selectable list row: the area is the click target, content added to the returned panel sits on top and lets clicks through
    public static CustomPanelAPI row(CustomPanelAPI p, float x, float y, float w, float h, boolean selected, boolean enabled, Color strip, Runnable action) {
        CustomPanelAPI row = Ui.panel(p, x, y, w, h);
        area(row, 0f, 0f, w, h, null, selected, enabled, strip, Ui.CENTER, action);
        return row;
    }

    public static ButtonAPI check(CustomPanelAPI p, float x, float y, float w, String label, boolean on, boolean enabled, Runnable action) {
        TooltipMakerAPI element = p.createUIElement(w + Ui.INSET, Ui.LINE_H, false);
        String text = Ui.fit(label, Ui.INS15, w - 26f);
        ButtonAPI box = element.addCheckbox(w, Ui.LINE_H, text, action, ButtonAPI.UICheckboxSize.SMALL, 0f);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        box.setChecked(on);
        if (!enabled || action == null) box.setEnabled(false);
        if (!text.equals(label)) Ui.tooltip(p, box, null, label);
        return box;
    }

    // minus, value, plus; 72 wide
    public static float stepper(CustomPanelAPI p, float x, float y, int value, boolean enabled, Runnable minus, Runnable plus) {
        float s = 22f;
        Kind kind = enabled ? Kind.STD : Kind.OFF;
        button(p, x, y, s, s, "-", kind, enabled ? minus : null);
        Ui.textIn(p, x + s, y, 28f, s, Ui.num(value), Ui.INS15, value > 0 ? Ui.hl() : Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
        button(p, x + s + 28f, y, s, s, "+", kind, enabled ? plus : null);
        return 2f * s + 28f;
    }

    // vanilla text field; read getText() in the action that uses it and keep it in the view state before a refresh
    public static TextFieldAPI field(CustomPanelAPI p, float x, float y, float w, float h, String text, int maxChars) {
        TooltipMakerAPI element = p.createUIElement(w + Ui.INSET, h, false);
        TextFieldAPI field = element.addTextField(w, h, Ui.INS15, 0f);
        field.setMaxChars(maxChars);
        field.setText(text == null ? "" : text);
        p.addUIElement(element).inTL(x - Ui.INSET, y);
        return field;
    }

    public static Color fg(Kind kind) {
        return switch (kind) {
            case GREEN, RED, AMBER -> Ui.text();
            case STORY -> Ui.story();
            case HOT -> Ui.bright();
            default -> Ui.base();
        };
    }

    public static Color fill(Color state) {
        return Misc.setAlpha(Misc.scaleColorOnly(state, FILL), 255);
    }

    public static Color bg(Kind kind) {
        return switch (kind) {
            case GREEN -> fill(Ui.GREEN);
            case RED -> Ui.RED;
            case AMBER -> fill(Ui.AMBER);
            case STORY -> Ui.storyDark();
            case HOT -> Misc.setAlpha(Ui.dark(), 255);
            default -> Ui.dark();
        };
    }
}
