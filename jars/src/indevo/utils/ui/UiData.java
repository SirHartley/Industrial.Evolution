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
    // fills at the button level, the empty part a dim tint of the same colour, like the vanilla campaign bars
    public static Color barFill(Color c) {
        return UiButtons.fill(c);
    }

    public static Color barTrack(Color c) {
        return Misc.setAlpha(Misc.scaleColorOnly(c, 0.22f), 255);
    }

    public static CustomPanelAPI bar(CustomPanelAPI p, float x, float y, float w, float h, float fraction, Color fill, String label) {
        float f = Math.max(0f, Math.min(1f, fraction));
        Color edge = Misc.scaleAlpha(Ui.base(), 0.9f);
        Color inner = Misc.scaleAlpha(Ui.base(), 0.4f);
        Color tick = Misc.scaleAlpha(Ui.bright(), 0.8f);

        CustomPanelAPI bar = Ui.painted(p, x, y, w, h, (pos, a) -> {
            float bx = pos.getX();
            float by = pos.getY();
            float bw = pos.getWidth();
            float bh = pos.getHeight();
            float fw = Math.round((bw - 6f) * f);

            UiDraw.quad(bx, by, bw, bh, Color.BLACK, a);
            UiDraw.quad(bx + 3f, by + 1f, bw - 6f, bh - 2f, barTrack(fill), a);
            UiDraw.quad(bx + 3f, by + 1f, fw, bh - 2f, barFill(fill), a);
            if (f > 0f && f < 1f) UiDraw.quad(bx + 3f + fw - 1f, by + 1f, 1f, bh - 2f, tick, a);
        }, (pos, a) -> {
            float bx = pos.getX();
            float by = pos.getY();
            float bw = pos.getWidth();
            float bh = pos.getHeight();

            UiDraw.quad(bx, by, 1f, bh, edge, a);
            UiDraw.quad(bx + bw - 1f, by, 1f, bh, edge, a);
            UiDraw.quad(bx + 1f, by, 1f, bh, inner, a);
            UiDraw.quad(bx + bw - 2f, by, 1f, bh, inner, a);
            UiDraw.fadeRight(bx, by, 14f, edge, a);
            UiDraw.fadeRight(bx, by + bh - 1f, 14f, edge, a);
            UiDraw.fadeLeft(bx + bw - 14f, by, 14f, edge, a);
            UiDraw.fadeLeft(bx + bw - 14f, by + bh - 1f, 14f, edge, a);
        });

        if (label != null) Ui.textIn(bar, 4f, 0f, w - 8f, h, label, Ui.INS15, Ui.text(), Ui.CENTER, new String[0], new Color[0]);
        return bar;
    }

    public static CustomPanelAPI thinbar(CustomPanelAPI p, float x, float y, float w, float fraction, Color fill) {
        float f = Math.max(0f, Math.min(1f, fraction));
        Color track = barTrack(fill);
        Color done = barFill(fill);

        return Ui.painted(p, x, y, w, THIN_H, (pos, a) -> {
            UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), track, a);
            UiDraw.quad(pos.getX(), pos.getY(), Math.round(pos.getWidth() * f), pos.getHeight(), done, a);
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
