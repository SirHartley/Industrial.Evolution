package indevo.industries.changeling.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.changeling.industry.EffectLine;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiCanvas;
import indevo.utils.ui.UiDraw;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

// gain and cost lines of a variant: sign column, then the line with its numbers in the sign's colour
public final class EffectLines {

    public static final float PITCH = 18f;
    public static final String NONE = "No special rules";
    public static final float TEXT_X = 16f;
    static final float SIGN_W = 16f;
    static final float MINUS_W = 7f;
    static final float MINUS_H = 2f;

    private EffectLines() {
    }

    public static float height(List<EffectLine> lines) {
        return Math.max(1, lines.size()) * PITCH;
    }

    public static float draw(CustomPanelAPI p, float x, float y, float w, List<EffectLine> lines) {
        if (lines.isEmpty()) {
            Ui.text(p, x, y, w, NONE, Ui.gray());
            return PITCH;
        }

        float ly = y;
        for (EffectLine line : lines) {
            Color c = line.gain() ? Ui.pos() : Ui.neg();
            // the sign column is 10 px, the label is wider so the fit never cuts the plus
            if (line.gain()) Ui.text(p, x - 3f, ly, SIGN_W, "+", c, Ui.CENTER);
            else Ui.painted(p, x + 2f, ly + 7f, MINUS_W, MINUS_H, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), c, a), null);

            Color[] colors = new Color[line.highlights().length];
            Arrays.fill(colors, c);
            Ui.runs(p, x + TEXT_X, ly, w - TEXT_X, Ui.INS15, Ui.text(), Ui.LEFT, line.shown(), line.highlights(), colors);
            ly += PITCH;
        }

        return ly - y;
    }

    public static void add(TooltipMakerAPI tooltip, float w, List<EffectLine> lines, float pad) {
        CustomPanelAPI panel = Global.getSettings().createCustom(w, height(lines), new UiCanvas());
        draw(panel, 0f, 0f, w, lines);
        tooltip.addCustom(panel, pad);
    }
}
