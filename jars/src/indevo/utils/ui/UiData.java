package indevo.utils.ui;

import com.fs.starfarer.api.Global;
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
    // its fill com.fs.starfarer.renderers.O and its lines com.fs.starfarer.renderers.E (untextured here); no easing, no overflow part
    public static CustomPanelAPI bar(CustomPanelAPI p, float x, float y, float w, float h, float fraction, Color fill, String label) {
        float f = Math.max(0f, Math.min(1f, fraction));
        Color frame = Ui.base();
        Color soft = Misc.interpolateColor(fill, Color.white, 0.15f);
        Color shade = Misc.setAlpha(Color.BLACK, 127);

        CustomPanelAPI bar = Ui.painted(p, x, y, w, h, (pos, a) -> {
            float bx = pos.getX();
            float by = pos.getY();
            float bw = pos.getWidth();
            float bh = pos.getHeight();
            float ux = bx + 3f;
            float uw = bw - 8f;
            if (bh <= 5f) {
                ux -= 1f;
                uw += 2f;
            }

            UiDraw.quad(bx, by, bw, bh, Color.BLACK, a);
            float fw = uw * f + 1f;
            filled(ux, by + 1f, fw, bh - 2f, soft, fill, a);

            float rw = uw - fw - 4f;
            if (rw > 0f && bh - 6f > 1f) {
                UiDraw.quad(ux + fw + 5f, by + 3f, rw, bh - 6f, soft, a * 0.65f * 0.75f);
                UiDraw.vgradient(ux + fw + 5f, by + 3f, rw, bh - 6f, shade, 0f, 0.65f, a, false);
            }
        }, (pos, a) -> {
            float bx = pos.getX();
            float by = pos.getY();
            float bw = pos.getWidth();
            float bh = pos.getHeight();

            UiDraw.line(bx, by, bx, by + bh, 2f, frame, 0.5f, 0.5f, 0.5f, a, false);
            UiDraw.line(bx + 1f, by, bx + 1f, by + bh, 2f, frame, 1f, 1f, 1f, a, true);
            UiDraw.line(bx + bw, by, bx + bw, by + bh, 2f, frame, 0.5f, 0.5f, 0.5f, a, false);
            UiDraw.line(bx + bw - 1f, by, bx + bw - 1f, by + bh, 2f, frame, 1f, 1f, 1f, a, true);
            if (bh <= 5f) return;
            UiDraw.line(bx + 1f, by, bx + 15f, by, 1f, frame, 1f, 0.5f, 0f, a, false);
            UiDraw.line(bx + 1f, by + bh - 1f, bx + 15f, by + bh, 1f, frame, 1f, 0.5f, 0f, a, false);
            UiDraw.line(bx + bw - 1f, by, bx + bw - 15f, by, 1f, frame, 1f, 0.5f, 0f, a, false);
            UiDraw.line(bx + bw - 1f, by + bh - 1f, bx + bw - 15f, by + bh, 1f, frame, 1f, 0.5f, 0f, a, false);
        });

        ((UiCanvas) bar.getPlugin()).shown = f;
        if (label != null) Ui.textIn(bar, 4f, 0f, w - 8f, h, label, Ui.INS15, Ui.text(), Ui.CENTER, new String[0], new Color[0]);
        return bar;
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

    public static float grid(CustomPanelAPI p, float x, float y, float w, float rowH, List<GridRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            GridRow row = rows.get(i);
            Color c = row.color() != null ? row.color() : Ui.hl();
            value(p, x, y + i * rowH, w, rowH, row.label(), row.value(), c, new String[0], new Color[0]);
        }

        return rows.size() * rowH;
    }
}
