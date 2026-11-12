package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class Ui {

    public static final String INS15 = Fonts.DEFAULT_SMALL;
    public static final String INS21 = Fonts.INSIGNIA_LARGE;
    public static final String ORB12 = Fonts.ORBITRON_12;
    public static final String ORB20 = Fonts.ORBITRON_20AA;
    public static final String VIC10 = Fonts.VICTOR_10;

    public static final Alignment LEFT = Alignment.LMID;
    public static final Alignment RIGHT = Alignment.RMID;
    public static final Alignment CENTER = Alignment.MID;

    public static final Color GREEN = new Color(50, 130, 0);
    public static final Color RED = new Color(160, 30, 20);
    public static final Color AMBER = new Color(200, 160, 10);

    public static final float HEADING_H = 18f;
    public static final float LINE_H = 20f;
    public static final float TOOLTIP_W = 360f;
    // first item of a tooltip element sits 5 px right of the element (StandardTooltipV2Expandable.addCustom)
    public static final float INSET = 5f;
    public static final String ELLIPSIS = "...";

    public interface Content {
        void create(TooltipMakerAPI tooltip);
    }

    private Ui() {
    }

    public static Color base() {
        return Misc.getBasePlayerColor();
    }

    public static Color bright() {
        return Misc.getBrightPlayerColor();
    }

    public static Color dark() {
        return Misc.getDarkPlayerColor();
    }

    public static Color hl() {
        return Misc.getHighlightColor();
    }

    public static Color text() {
        return Misc.getTextColor();
    }

    public static Color gray() {
        return Misc.getGrayColor();
    }

    public static Color pos() {
        return Misc.getPositiveHighlightColor();
    }

    public static Color neg() {
        return Misc.getNegativeHighlightColor();
    }

    public static Color story() {
        return Misc.getStoryOptionColor();
    }

    public static Color storyDark() {
        return Misc.getStoryDarkColor();
    }

    public static Color line() {
        return Misc.scaleAlpha(base(), 0.3f);
    }

    public static Color barBlue() {
        return Global.getSettings().getColor("progressBarStandardColor");
    }

    public static String credits(float amount) {
        return Misc.getDGSCredits(amount);
    }

    public static String num(float amount) {
        return Misc.getWithDGS(amount);
    }

    public static CustomPanelAPI panel(CustomPanelAPI parent, float x, float y, float w, float h) {
        return painted(parent, x, y, w, h, null, null);
    }

    public static CustomPanelAPI painted(CustomPanelAPI parent, float x, float y, float w, float h, UiCanvas.Painter below, UiCanvas.Painter above) {
        CustomPanelAPI panel = parent.createCustomPanel(w, h, new UiCanvas(below, above));
        parent.addComponent(panel).inTL(x, y);
        return panel;
    }

    // vanilla widgets at a rect: items start at x, the first item's pad is its y offset, the element grows past h and is not clipped
    public static TooltipMakerAPI element(CustomPanelAPI p, float x, float y, float w, float h, Content content) {
        TooltipMakerAPI element = p.createUIElement(w + INSET, h, false);
        content.create(element);
        p.addUIElement(element).inTL(x - INSET, y);
        return element;
    }

    public static float width(String s, String font) {
        return Global.getSettings().computeStringWidth(s, font);
    }

    public static String fit(String s, String font, float w) {
        if (s == null) return "";
        float max = w - 2f;
        if (width(s, font) <= max) return s;

        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(s.substring(0, mid).stripTrailing() + ELLIPSIS, font) <= max) lo = mid;
            else hi = mid - 1;
        }

        return lo == 0 ? ELLIPSIS : s.substring(0, lo).stripTrailing() + ELLIPSIS;
    }

    public static String sentences(String s, String font, float w) {
        if (s == null) return "";
        float max = w - 2f;
        if (width(s, font) <= max) return s;

        String out = "";
        for (String sentence : s.trim().split("(?<=[.!?])\\s+")) {
            String next = out.isEmpty() ? sentence : out + " " + sentence;
            if (width(next, font) > max) break;
            out = next;
        }
        if (!out.isEmpty()) return out;

        String words = "";
        for (String word : s.trim().split("\\s+")) {
            String next = words.isEmpty() ? word : words + " " + word;
            if (width(next + ELLIPSIS, font) > max) break;
            words = next;
        }
        return words.isEmpty() ? fit(s, font, w) : words.replaceAll("[,;:.]+$", "") + ELLIPSIS;
    }

    public static LabelAPI text(CustomPanelAPI p, float x, float y, float w, String s) {
        return text(p, x, y, w, s, INS15, text(), LEFT);
    }

    public static LabelAPI text(CustomPanelAPI p, float x, float y, float w, String s, Color c) {
        return text(p, x, y, w, s, INS15, c, LEFT);
    }

    public static LabelAPI text(CustomPanelAPI p, float x, float y, float w, String s, Color c, Alignment align) {
        return text(p, x, y, w, s, INS15, c, align);
    }

    public static LabelAPI text(CustomPanelAPI p, float x, float y, float w, String s, String font, Color c, Alignment align) {
        return runs(p, x, y, w, font, c, align, s, new String[0], new Color[0]);
    }

    // one line with coloured runs, each part is highlighted at its first occurrence
    public static LabelAPI runs(CustomPanelAPI p, float x, float y, float w, String font, Color c, Alignment align, String s, String[] parts, Color[] colors) {
        String shown = fit(s, font, w);
        LabelAPI label = Global.getSettings().createLabel(shown, font);
        label.setColor(c);
        label.setAlignment(align);
        highlight(label, shown, parts, colors);

        float h = label.computeTextHeight("Ag");
        p.addComponent((UIComponentAPI) label).setSize(w, h).inTL(x, y);
        if (!shown.equals(s)) tooltip(p, (UIComponentAPI) label, null, s);
        return label;
    }

    // one line centred vertically in a box of height h
    public static LabelAPI textIn(CustomPanelAPI p, float x, float y, float w, float h, String s, String font, Color c, Alignment align,
                                  String[] parts, Color[] colors) {
        LabelAPI label = runs(p, x, y, w, font, c, align, s, parts, colors);
        // the engine centres a label with the odd pixel below, as vanilla section headings show
        label.getPosition().inTL(x, y + (float) Math.floor((h - height(label)) / 2f));
        return label;
    }

    public static LabelAPI wrap(CustomPanelAPI p, float x, float y, float w, String s, Color c) {
        return wrap(p, x, y, w, s, INS15, c, Alignment.TL, new String[0], new Color[0]);
    }

    // wrapped at w, the label height is the measured text height
    public static LabelAPI wrap(CustomPanelAPI p, float x, float y, float w, String s, String font, Color c, Alignment align, String[] parts, Color[] colors) {
        LabelAPI label = Global.getSettings().createLabel(s, font);
        label.setColor(c);
        label.setAlignment(align);
        highlight(label, s, parts, colors);

        PositionAPI pos = p.addComponent((UIComponentAPI) label);
        label.autoSizeToWidth(w);
        pos.inTL(x, y);
        return label;
    }

    public static float wrapHeight(String s, String font, float w) {
        LabelAPI label = Global.getSettings().createLabel(s, font);
        label.autoSizeToWidth(w);
        return label.getPosition().getHeight();
    }

    public static float height(LabelAPI label) {
        return label.getPosition().getHeight();
    }

    private static void highlight(LabelAPI label, String shown, String[] parts, Color[] colors) {
        List<String> keep = new ArrayList<>();
        List<Color> keepColors = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || parts[i].isEmpty() || !shown.contains(parts[i])) continue;
            keep.add(parts[i]);
            keepColors.add(colors[Math.min(i, colors.length - 1)]);
        }

        if (keep.isEmpty()) return;
        label.setHighlight(keep.toArray(new String[0]));
        label.setHighlightColors(keepColors.toArray(new Color[0]));
    }

    public static LabelAPI heading(CustomPanelAPI p, float x, float y, float w, String title) {
        return heading(p, x, y, w, title, CENTER, base(), dark());
    }

    public static LabelAPI heading(CustomPanelAPI p, float x, float y, float w, String title, Alignment align) {
        return heading(p, x, y, w, title, align, base(), dark());
    }

    // centred text is the vanilla heading label; left or right text sits 8 px in on a flat bar of the same colour and height
    public static LabelAPI heading(CustomPanelAPI p, float x, float y, float w, String title, Alignment align, Color c, Color bg) {
        if (align == CENTER) {
            TooltipMakerAPI element = p.createUIElement(w, HEADING_H, false);
            // the heading label draws 10 px wider than the width passed
            String shown = fit(title, ORB12, w - 16f);
            LabelAPI label = element.addSectionHeading(shown, c, bg, Alignment.MID, w - 10f, 0f);
            p.addUIElement(element).inTL(x, y);
            if (!shown.equals(title)) tooltip(p, label, null, title);
            return label;
        }

        bar(p, x, y, w, bg);
        return textIn(p, x + 8f, y, w - 16f, HEADING_H, title, ORB12, c, align, new String[0], new Color[0]);
    }

    // left heading with a grey note at its right end, the note gets what the title leaves
    public static LabelAPI heading(CustomPanelAPI p, float x, float y, float w, String title, String note) {
        float tw = Math.min(width(title, ORB12) + 2f, w - 16f);
        bar(p, x, y, w, dark());
        LabelAPI label = textIn(p, x + 8f, y, tw, HEADING_H, title, ORB12, base(), LEFT, new String[0], new Color[0]);

        float nx = x + 8f + tw + 16f;
        float nw = x + w - 8f - nx;
        if (note != null && nw > 20f) textIn(p, nx, y, nw, HEADING_H, note, ORB12, gray(), RIGHT, new String[0], new Color[0]);
        return label;
    }

    private static void bar(CustomPanelAPI p, float x, float y, float w, Color bg) {
        painted(p, x, y, w, HEADING_H, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), bg, a), null);
    }

    public static CustomPanelAPI rule(CustomPanelAPI p, float x, float y, float w) {
        return rule(p, x, y, w, line());
    }

    public static CustomPanelAPI rule(CustomPanelAPI p, float x, float y, float w, Color c) {
        return painted(p, x, y, w, 1f, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), 1f, c, a), null);
    }

    public static CustomPanelAPI vrule(CustomPanelAPI p, float x, float y, float h) {
        Color c = line();
        return painted(p, x, y, 1f, h, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), 1f, pos.getHeight(), c, a), null);
    }

    public static CustomPanelAPI box(CustomPanelAPI p, float x, float y, float w, float h, Color border, Color fill) {
        return painted(p, x, y, w, h, (pos, a) -> {
            if (fill != null) UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), fill, a);
        }, (pos, a) -> {
            if (border != null) UiDraw.outline(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), border, a);
        });
    }

    // framed card: base frame at 0.3, dark fill
    public static CustomPanelAPI card(CustomPanelAPI p, float x, float y, float w, float h) {
        return box(p, x, y, w, h, Misc.scaleAlpha(base(), 0.3f), Misc.scaleAlpha(dark(), 0.5f));
    }

    // recessed cell behind a ship or item picture
    public static CustomPanelAPI well(CustomPanelAPI p, float x, float y, float w, float h) {
        return box(p, x, y, w, h, Misc.scaleAlpha(base(), 0.2f), Misc.setAlpha(Color.BLACK, 90));
    }

    // table header strip: dark bar, orb12 column labels at the given x, width and alignment
    public static CustomPanelAPI thead(CustomPanelAPI p, float x, float y, float w, String[] labels, float[] xs, float[] ws, Alignment[] aligns) {
        CustomPanelAPI head = box(p, x, y, w, HEADING_H, null, Misc.scaleAlpha(dark(), 0.5f));
        for (int i = 0; i < labels.length; i++) {
            textIn(head, xs[i], 0f, ws[i], HEADING_H, labels[i], ORB12, base(), aligns[i], new String[0], new Color[0]);
        }

        return head;
    }

    public static CustomPanelAPI dashbox(CustomPanelAPI p, float x, float y, float w, float h) {
        Color c = Misc.scaleAlpha(base(), 0.2f);
        return painted(p, x, y, w, h, null, (pos, a) -> UiDraw.dashed(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), c, a));
    }

    public static void tooltip(CustomPanelAPI p, UIComponentAPI target, String title, String... lines) {
        tooltip(p, target, TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> {
            float pad = 0f;
            if (title != null) {
                t.addPara(title, hl(), 0f);
                pad = 10f;
            }

            for (String line : lines) {
                t.addPara(line, pad);
                pad = 10f;
            }
        });
    }

    public static void tooltip(CustomPanelAPI p, LabelAPI target, String title, String... lines) {
        tooltip(p, (UIComponentAPI) target, title, lines);
    }

    // works on any panel, label or button; buttons also show it while disabled
    public static void tooltip(CustomPanelAPI p, UIComponentAPI target, float width, TooltipMakerAPI.TooltipLocation loc, Content content) {
        if (target instanceof ButtonAPI button) button.setShowTooltipWhileInactive(true);

        p.createUIElement(1f, 1f, false).addTooltipTo(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return width;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                content.create(tooltip);
            }
        }, target, loc);
    }
}
