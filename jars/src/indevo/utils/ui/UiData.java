package indevo.utils.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.helper.RelicHelper;

import java.awt.*;
import java.util.List;

public final class UiData {

    public static final float GRID_ROW_H = 18f;
    public static final float COST_ROW_H = 22f;
    public static final float COST_ICON = 22f;
    public static final float MARK = 12f;
    public static final float THIN_H = 3f;
    public static final float VALUE_SHARE = 0.6f;
    public static final float PIP_OFF = 0.18f;
    private static SpriteAPI line;

    public record GridRow(String label, String value, Color color) {
        public static GridRow of(String label, String value) {
            return new GridRow(label, value, null);
        }

        public static GridRow of(String label, String value, Color color) {
            return new GridRow(label, value, color);
        }
    }

    private UiData() {
    }

    // vanilla bar chrome: black track, inset fill, doubled end lines, fading corner stubs, label centred on top
    // translation of obfuscated code, 0.98a-RC8: the vanilla progress bar com.fs.starfarer.campaign.ui.N (renderImpl) at rest,
    // its fill com.fs.starfarer.renderers.O and its lines com.fs.starfarer.renderers.E; no easing, no overflow part, no hover
    public static CustomPanelAPI bar(CustomPanelAPI p, float x, float y, float w, float h, float fraction, Color fill, String label) {
        float f = Math.max(0f, Math.min(1f, fraction));
        Color frame = Ui.barColor("widgetBorderColorBright");
        Color soft = Misc.interpolateColor(fill, Color.white, 0.15f);
        Color shade = Misc.setAlpha(Color.BLACK, 127);

        CustomPanelAPI bar = Ui.painted(p, x, y, w, h, (pos, a) -> {
            float bx = pos.getX();
            float by = pos.getY();
            float bw = pos.getWidth();
            float bh = pos.getHeight();
            boolean thin = bh <= 5f;
            float ux = bx + (thin ? 2f : 3f);
            float uw = bw - (thin ? 6f : 8f);
            SpriteAPI tex = lineTexture();

            UiDraw.quad(bx, by, bw, bh, Color.BLACK, a);
            UiDraw.line(tex, bx, by, bx, by + bh, 2f, frame, 0.5f, 0.5f, 0.5f, a, false);
            UiDraw.line(tex, bx + 1f, by, bx + 1f, by + bh, 2f, frame, 1f, 1f, 1f, a, true);
            UiDraw.line(tex, bx + bw, by, bx + bw, by + bh, 2f, frame, 0.5f, 0.5f, 0.5f, a, false);
            UiDraw.line(tex, bx + bw - 1f, by, bx + bw - 1f, by + bh, 2f, frame, 1f, 1f, 1f, a, true);
            if (!thin) {
                UiDraw.line(tex, bx + 1f, by, bx + 15f, by, 1f, frame, 1f, 0.5f, 0f, a, false);
                UiDraw.line(tex, bx + 1f, by + bh - 1f, bx + 15f, by + bh, 1f, frame, 1f, 0.5f, 0f, a, false);
                UiDraw.line(tex, bx + bw - 1f, by, bx + bw - 15f, by, 1f, frame, 1f, 0.5f, 0f, a, false);
                UiDraw.line(tex, bx + bw - 1f, by + bh - 1f, bx + bw - 15f, by + bh, 1f, frame, 1f, 0.5f, 0f, a, false);
            }

            // the empty part: edge lines along it, fading in over 10 px at the right end, and a divider where it starts
            float end = bx + 6f + uw;
            float empty = uw * (1f - f);
            if (empty > 0f) {
                float fade = Math.min(10f, empty);
                float start = end - empty + 1.5f;
                if (thin) {
                    UiDraw.line(tex, end, by + 1f, end - empty - 1f, by + 1f, 1f, frame, 1f, 1f, 1f, a, false);
                    UiDraw.line(tex, end, by + bh - 2f, end - empty - 1f, by + bh - 2f, 1f, frame, 1f, 1f, 1f, a, false);
                } else {
                    for (float ly : new float[]{by + 1f, by + bh - 2f}) {
                        UiDraw.line(tex, end + 2f, ly, end + 2f - fade, ly, 1f, frame, 0f, 0.5f, 1f, a, false);
                        UiDraw.line(tex, end + 2f - fade, ly, start, ly, 1f, frame, 1f, 1f, 1f, a, false);
                    }
                    UiDraw.line(tex, start, by + 1f, start, by + bh - 1f, 1f, frame, 1f, 1f, 1f, a, false);
                    UiDraw.line(tex, start, by + 1f, start, by + bh - 1f, 1f, Color.white, 0f, 1f, 0f, a, true);
                }
            }

            float fw = uw * f + 1f;
            filled(ux, by + 1f, fw, bh - 2f, soft, fill, a);
            float rw = uw - fw - 4f;
            if (rw > 0f && bh - 6f > 1f) {
                UiDraw.quad(ux + fw + 5f, by + 3f, rw, bh - 6f, soft, a * 0.65f * 0.75f);
                UiDraw.vgradient(ux + fw + 5f, by + 3f, rw, bh - 6f, shade, 0f, 0.65f, a, false);
            }
            UiDraw.line(tex, bx + uw * f + 5.5f, by, bx + uw * f + 5.5f, by + bh, 1f, frame, 0.5f, 1f, 0.5f, a, false);
        }, null);

        ((UiCanvas) bar.getPlugin()).shown = f;
        if (label != null) Ui.textIn(bar, 4f, 0f, w - 8f, h, label, Ui.INS15, Ui.text(), Ui.CENTER, new String[0], new Color[0]);
        return bar;
    }

    // the texture vanilla loads at startup for every ui line (com.fs.starfarer.renderers.H); untextured if it is missing
    private static SpriteAPI lineTexture() {
        if (line == null) line = Global.getSettings().getSprite("graphics/hud/line4x4.png");
        return line.getTextureId() != 0 ? line : null;
    }

    // the vanilla fill: the colour 15 % toward white at 0.75, then an additive vertical gradient of the colour, 0 at the edges, 0.75 in the middle
    private static void filled(float x, float y, float w, float h, Color soft, Color fill, float a) {
        if (h <= 1f) return;
        UiDraw.quad(x, y, w, h, soft, a * 0.75f);
        UiDraw.vgradient(x, y, w, h, fill, 0f, 0.75f, a, true);
    }

    // vanilla draws no fill under 2 px of height, so the 3 px row bar is the vanilla fill colour flat, the empty part at 0.65
    public static CustomPanelAPI thinbar(CustomPanelAPI p, float x, float y, float w, float fraction, Color fill) {
        float f = Math.max(0f, Math.min(1f, fraction));
        Color soft = Misc.interpolateColor(fill, Color.white, 0.15f);

        return Ui.painted(p, x, y, w, THIN_H, (pos, a) -> {
            float fw = Math.round(pos.getWidth() * f);
            UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Color.BLACK, a);
            UiDraw.quad(pos.getX(), pos.getY(), fw, pos.getHeight(), soft, a * 0.75f);
            UiDraw.quad(pos.getX() + fw, pos.getY(), pos.getWidth() - fw, pos.getHeight(), soft, a * 0.65f * 0.75f * 0.4f);
        }, null);
    }

    public static CustomPanelAPI pips(CustomPanelAPI p, float x, float y, int total, int filled, float size, float gap, Color color) {
        return pips(p, x, y, total, 0, filled, size, size, gap, color);
    }

    // lights pips [from, to), the rest in the track colour
    public static CustomPanelAPI pips(CustomPanelAPI p, float x, float y, int total, int from, int to, float w, float h, float gap, Color color) {
        Color off = Misc.scaleAlpha(Ui.base(), PIP_OFF);
        float pw = Math.max(0f, total * (w + gap) - gap);

        return Ui.painted(p, x, y, pw, h, (pos, a) -> {
            for (int i = 0; i < total; i++) {
                UiDraw.quad(pos.getX() + i * (w + gap), pos.getY(), w, h, i >= from && i < to ? color : off, a);
            }
        }, null);
    }

    public static CustomPanelAPI mark(CustomPanelAPI p, float x, float y, boolean ok) {
        Color c = ok ? Ui.pos() : Ui.neg();
        return Ui.painted(p, x, y, MARK, MARK, null, (pos, a) -> UiDraw.mark(pos.getX(), pos.getY(), MARK, ok, c, a));
    }

    // requirement line: mark and text, red text while unmet
    public static LabelAPI checkrow(CustomPanelAPI p, float x, float y, float w, boolean ok, String text) {
        mark(p, x, y + 3f, ok);
        return Ui.text(p, x + MARK + 6f, y, w - MARK - 6f, text, ok ? Ui.text() : Ui.neg());
    }

    // icon, amount in highlight (negative when short), stock in grey brackets
    public static LabelAPI cost(CustomPanelAPI p, float x, float y, float w, String sprite, String amount, String stock, boolean ok, float size) {
        UiImages.img(p, x, y, size, size, sprite);
        String s = withStock(amount, stock, w - size - 5f);
        return Ui.textIn(p, x + size + 5f, y, w - size - 5f, size, s, Ui.INS15, Ui.gray(), Ui.LEFT,
                new String[]{amount}, new Color[]{ok ? Ui.hl() : Ui.neg()});
    }

    // the stock in brackets only while both fit, the header counters show it too
    public static String withStock(String amount, String stock, float w) {
        if (stock == null) return amount;
        String s = amount + " (" + stock + ")";
        return Ui.width(s, Ui.INS15) + 2f <= w ? s : amount;
    }

    // label left, icon and amount right aligned in a measured column
    public static float costrow(CustomPanelAPI p, float x, float y, float w, String label, String sprite, String amount, String stock, boolean ok) {
        float iconW = sprite == null ? 0f : COST_ICON + 4f;
        String value = withStock(amount, stock, w * VALUE_SHARE - iconW);
        float vw = Ui.width(value, Ui.INS15) + 2f;

        if (sprite != null) UiImages.img(p, x + w - vw - iconW, y, COST_ICON, COST_ICON, sprite);
        Ui.textIn(p, x + w - vw, y, vw, COST_ROW_H, value, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[]{amount}, new Color[]{ok ? Ui.hl() : Ui.neg()});
        Ui.textIn(p, x, y, w - vw - iconW - 8f, COST_ROW_H, label, Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        return COST_ROW_H;
    }

    public static float creditsRow(CustomPanelAPI p, float x, float y, float w, String label, float amount) {
        float have = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        return costrow(p, x, y, w, label, UiImages.creditsSprite(), Ui.credits(amount), Ui.credits(have), have >= amount);
    }

    public static float relicRow(CustomPanelAPI p, float x, float y, float w, String label, int amount, MarketAPI market) {
        int have = RelicHelper.getAvailable(market);
        return costrow(p, x, y, w, label, UiImages.relicSprite(), Ui.num(amount), Ui.num(have), have >= amount);
    }

    // label left, value right in its own measured column; parts colour runs inside the value
    public static float value(CustomPanelAPI p, float x, float y, float w, float h, String label, String value, Color color, String[] parts, Color[] colors) {
        float vw = Math.min(Ui.width(value, Ui.INS15) + 2f, w * VALUE_SHARE);
        Ui.textIn(p, x + w - vw, y, vw, h, value, Ui.INS15, color, Ui.RIGHT, parts, colors);
        Ui.textIn(p, x, y, w - vw - 8f, h, label, Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        return h;
    }

    // label left; right the old value struck through in grey, an arrow and the new value in highlight
    public static float change(CustomPanelAPI p, float x, float y, float w, float h, String label, String from, String to) {
        float toW = Ui.width(to, Ui.INS15) + 2f;
        float arrowW = Ui.width("->", Ui.INS15) + 2f;
        float fromW = Ui.width(from, Ui.INS15) + 2f;
        float toX = x + w - toW;
        float arrowX = toX - 3f - arrowW;
        // the strike line runs 2 px past the old value, 7 leaves the same gap on both sides of the arrow
        float fromX = arrowX - 7f - fromW;

        Ui.textIn(p, toX, y, toW, h, to, Ui.INS15, Ui.hl(), Ui.RIGHT, new String[0], new Color[0]);
        Ui.textIn(p, arrowX, y, arrowW, h, "->", Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
        LabelAPI old = Ui.textIn(p, fromX, y, fromW, h, from, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
        // through the middle of the digits and 2 px past them on both sides (right aligned, the box has 2 px slack on the left)
        float lh = Ui.height(old);
        Ui.rule(p, fromX, y + (float) Math.floor((h - lh) / 2f) + Math.round(lh / 2f), fromW + 2f, Ui.text());
        Ui.textIn(p, x, y, fromX - 8f - x, h, label, Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        return h;
    }

    public static float grid(CustomPanelAPI p, float x, float y, float w, float rowH, List<GridRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            GridRow row = rows.get(i);
            Color c = row.color() != null ? row.color() : Ui.hl();
            value(p, x, y + i * rowH, w, rowH, row.label(), row.value(), c, new String[0], new Color[0]);
        }

        return rows.size() * rowH;
    }
}
