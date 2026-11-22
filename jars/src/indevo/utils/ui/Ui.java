package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;

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
    // letter pairs that are one sound, never hyphenated between
    private static final Set<String> PAIRS = Set.of("ch", "ck", "gh", "ph", "sh", "th", "wh");

    public interface Content {
        void create(TooltipMakerAPI tooltip);
    }

    // a line of breakLines, hyphen when the line ends inside a word that had none
    private record Cut(String text, boolean hyphen) {
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

    // black behind panels that sit on the dialog picture or starscape, so text stays readable
    public static Color backdrop() {
        return Misc.setAlpha(Color.BLACK, 200);
    }

    // vanilla progress bar colour by settings key (progressBarStandardColor, ...WarningColor, ...OverflowColor, ...CargoColor)
    public static Color barColor(String key) {
        return Global.getSettings().getColor(key);
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

    // name on one line and the grey second line below; a name too long for one line wraps to two and the second line drops
    public static LabelAPI nameLines(CustomPanelAPI p, float x, float y, float pitch, float w, String name, Color c, String second, float secondW) {
        if (width(name, INS15) + 2f <= w) {
            LabelAPI label = text(p, x, y, w, name, c);
            if (second != null) text(p, x, y + pitch, secondW, second, gray());
            return label;
        }

        String shown = lines(name, INS15, w, 2);
        LabelAPI label = wrap(p, x, y, w, shown, c);
        if (!shown.equals(name)) tooltip(p, label, null, name);
        return label;
    }

    // the leading words that wrap into n lines at w, with "..." when words are left out
    public static String lines(String s, String font, float w, int n) {
        if (s == null) return "";
        float max = n * wrapHeight("Ag", font, w) + 1f;
        if (wrapHeight(s, font, w) <= max) return s;

        String words = "";
        for (String word : s.trim().split("\\s+")) {
            String next = words.isEmpty() ? word : words + " " + word;
            if (wrapHeight(next + ELLIPSIS, font, w) > max) break;
            words = next;
        }
        return words.isEmpty() ? fit(s, font, w) : words.replaceAll("[,;:.]+$", "") + ELLIPSIS;
    }

    // words over at most n lines: several words on a line stay within soft, a lone word may reach hard and is hyphenated past it, "..." when it still runs out
    public static List<String> breakLines(String s, String font, float soft, float hard, int n) {
        String text = s == null ? "" : s;
        List<Cut> cuts = cuts(text, font, soft, hard);
        if (cuts.size() > n) cuts = cuts(text, font, hard, hard);

        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, cuts.size()); i++) {
            String line = cuts.get(i).text() + (cuts.get(i).hyphen() ? "-" : "");
            if (i == n - 1 && cuts.size() > n) {
                StringBuilder rest = new StringBuilder();
                for (int j = i; j < cuts.size(); j++) rest.append(j > i && !cuts.get(j - 1).hyphen() && !cuts.get(j - 1).text().endsWith("-") ? " " : "").append(cuts.get(j).text());
                line = rest.toString();
            }
            out.add(fit(line, font, hard + 2f));
        }
        return out;
    }

    private static List<Cut> cuts(String s, String font, float soft, float hard) {
        List<Cut> out = new ArrayList<>();
        Deque<String> words = new ArrayDeque<>(Arrays.asList(s.trim().split("\\s+")));
        String line = "";
        while (!words.isEmpty()) {
            String word = words.pop();
            String next = line.isEmpty() ? word : line + " " + word;
            if (width(next, font) <= soft || line.isEmpty() && width(word, font) <= hard) {
                line = next;
            } else if (!line.isEmpty()) {
                out.add(new Cut(line, false));
                line = "";
                words.push(word);
            } else {
                int at = hyphenAt(word, font, hard);
                if (at < 0) {
                    line = word;
                    continue;
                }
                out.add(new Cut(word.substring(0, at), word.charAt(at - 1) != '-'));
                words.push(word.substring(at));
            }
        }
        if (!line.isEmpty()) out.add(new Cut(line, false));
        return out;
    }

    // latest break whose part fits hard with its hyphen: after a hyphen, before one consonant between vowels, between two consonants with vowels around; two letters stay before, three after
    private static int hyphenAt(String word, String font, float hard) {
        for (int i = word.length() - 3; i >= 2; i--) {
            char a = Character.toLowerCase(word.charAt(i - 1));
            char b = Character.toLowerCase(word.charAt(i));
            char c = Character.toLowerCase(word.charAt(i + 1));
            boolean at = a == '-'
                    || vowel(a) && consonant(b) && vowel(c)
                    || vowel(Character.toLowerCase(word.charAt(i - 2))) && consonant(a) && consonant(b) && vowel(c) && !PAIRS.contains("" + a + b);
            if (at && width(word.substring(0, i) + (a == '-' ? "" : "-"), font) <= hard) return i;
        }
        return -1;
    }

    private static boolean vowel(char c) {
        return "aeiouy".indexOf(c) >= 0;
    }

    private static boolean consonant(char c) {
        return Character.isLetter(c) && !vowel(c);
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

        return heading(p, x, y, w, title, ORB12, align, c, bg);
    }

    // flat bar with the text 8 px in; ORB12 runs about a sixth wider than INS15, a narrow tile takes INS15
    public static LabelAPI heading(CustomPanelAPI p, float x, float y, float w, String title, String font, Alignment align, Color c, Color bg) {
        bar(p, x, y, w, bg);
        return textIn(p, x + 8f, y, w - 16f, HEADING_H, title, font, c, align, new String[0], new Color[0]);
    }

    // left heading with a grey note at its right end, the note gets what the title leaves; hl parts of the note in the highlight colour
    public static LabelAPI heading(CustomPanelAPI p, float x, float y, float w, String title, String note, String... hl) {
        float tw = Math.min(width(title, ORB12) + 2f, w - 16f);
        bar(p, x, y, w, dark());
        LabelAPI label = textIn(p, x + 8f, y, tw, HEADING_H, title, ORB12, base(), LEFT, new String[0], new Color[0]);

        float nx = x + 8f + tw + 16f;
        float nw = x + w - 8f - nx;
        Color[] colors = new Color[hl.length];
        Arrays.fill(colors, hl());
        if (note != null && nw > 20f) textIn(p, nx, y, nw, HEADING_H, note, ORB12, gray(), RIGHT, hl, colors);
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

    // framed card: base frame at 0.3, no fill (vanilla keeps interiors black)
    public static CustomPanelAPI card(CustomPanelAPI p, float x, float y, float w, float h) {
        return box(p, x, y, w, h, Misc.scaleAlpha(base(), 0.3f), null);
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
        tooltip(p, target, TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> textTip(t, title, lines));
    }

    // the default 0.85 background lets busy content under it show through; vanilla ship tooltips use 1 too
    public static void solidTooltip(CustomPanelAPI p, UIComponentAPI target, String title, String... lines) {
        tooltip(p, target, TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> {
            t.setBgAlpha(1f);
            textTip(t, title, lines);
        });
    }

    private static void textTip(TooltipMakerAPI t, String title, String... lines) {
        float pad = 0f;
        if (title != null) {
            t.addPara(title, hl(), 0f);
            pad = 10f;
        }

        for (String line : lines) {
            t.addPara(line, pad);
            pad = 10f;
        }
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
