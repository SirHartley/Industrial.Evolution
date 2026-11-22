package indevo.utils.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

// a guided tour step over a whole panel: dims everything but one lit rect, a 1 px bright line with a pulsing glow around it,
// and a box beside it with the step text, the step count and Next / Skip, or Done on the last step
public final class UiTour {

    public static final float DIM = 0.7f;
    public static final float PAD = 4f;
    public static final float GAP = 8f;
    public static final float BOX_W = 360f;
    public static final float FADE = 0.2f;
    static final float PULSE_UP = 0.4f;
    static final float PULSE_DOWN = 0.9f;
    static final float GLOW_REACH = 9f;
    static final float GLOW_ALPHA = 0.6f;
    static final float GLOW_LOW = 0.35f;
    static final float TEXT_X = 9f;
    static final float TEXT_Y = 8f;
    static final float BUTTON_H = 24f;
    static final float NEXT_W = 80f;
    static final float SKIP_W = 64f;
    static final float BUTTON_GAP = 6f;
    static final float EDGE = 11f;
    static final float BOTTOM = 10f;
    static final float TEXT_GAP = 12f;

    private UiTour() {
    }

    // the timing state the owner keeps across rebuilds
    public interface State {
        // 0 to 1: fades in at the start and out at the end
        float alpha();

        // 0 to 1, the glow of the lit line; constant when motion is off
        float glow();

        // clicks outside the lit rect and the box are eaten while true
        boolean blocking();
    }

    // one step; x, y, w, h is the target in panel coordinates from the top left, the lit rect is PAD larger; text null draws no box
    public record Step(float x, float y, float w, float h, String text, String number, int index, int count,
                       Runnable next, Runnable skip) {
    }

    // 0.4 s up, 0.9 s down, again; the ? glow of the Pets panel uses it too
    public static float pulse(float time) {
        float t = time % (PULSE_UP + PULSE_DOWN);
        return t < PULSE_UP ? t / PULSE_UP : 1f - (t - PULSE_UP) / PULSE_DOWN;
    }

    // adds the overlay as the last child of p, so it draws over and takes input before everything built before it; step null dims all
    public static void add(CustomPanelAPI p, float w, float h, Step step, State state) {
        float[] lit = step == null ? null : new float[]{step.x - PAD, step.y - PAD, step.w + 2 * PAD, step.h + 2 * PAD};
        Overlay overlay = new Overlay(state, lit, h);
        CustomPanelAPI panel = p.createCustomPanel(w, h, overlay);
        overlay.panel = panel;
        p.addComponent(panel).inTL(0f, 0f);
        if (step != null && step.text != null) overlay.box = box(panel, w, h, lit, step);
        panel.setOpacity(state.alpha());
    }

    private static float[] box(CustomPanelAPI p, float w, float h, float[] lit, Step step) {
        float textW = BOX_W - 2 * (TEXT_X + 1f);
        float textH = Ui.wrapHeight(step.text, Ui.INS15, textW);
        float bh = BOTTOM + textH + TEXT_GAP + BUTTON_H + BOTTOM;
        float bx = w - (lit[0] + lit[2]) >= BOX_W + GAP ? lit[0] + lit[2] + GAP : lit[0] - GAP - BOX_W;
        bx = Math.max(0f, Math.min(bx, w - BOX_W));
        float by = Math.max(0f, Math.min(lit[1], h - bh));

        CustomPanelAPI box = Ui.painted(p, bx, by, BOX_W, bh, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Color.BLACK, a),
                (pos, a) -> UiDraw.outline(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Ui.base(), a));
        Ui.wrap(box, TEXT_X, TEXT_Y, textW, step.text, Ui.INS15, Ui.text(), Alignment.TL,
                new String[]{step.number}, new Color[]{Ui.hl()});

        float y = bh - BOTTOM - BUTTON_H - 1f;
        Ui.textIn(box, TEXT_X, y, 60f, BUTTON_H, step.index + " / " + step.count, Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        if (step.index < step.count) {
            UiButtons.button(box, BOX_W - EDGE - SKIP_W - BUTTON_GAP - NEXT_W, y, NEXT_W, BUTTON_H, "Next", UiButtons.Kind.STD, CutStyle.ALL, step.next);
            UiButtons.button(box, BOX_W - EDGE - SKIP_W, y, SKIP_W, BUTTON_H, "Skip", UiButtons.Kind.STD, CutStyle.ALL, step.skip);
        } else {
            UiButtons.button(box, BOX_W - EDGE - NEXT_W, y, NEXT_W, BUTTON_H, "Done", UiButtons.Kind.STD, CutStyle.ALL, step.next);
        }

        return new float[]{bx, by, BOX_W, bh};
    }

    private static class Overlay extends BaseCustomUIPanelPlugin {
        private final State state;
        private final float[] lit;
        private final float height;
        private CustomPanelAPI panel;
        private float[] box;
        private PositionAPI pos;

        Overlay(State state, float[] lit, float height) {
            this.state = state;
            this.lit = lit;
            this.height = height;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void advance(float amount) {
            if (panel != null) panel.setOpacity(state.alpha());
        }

        // alphaMult already holds the panel opacity
        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null) return;

            float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
            if (lit == null) {
                UiDraw.quad(x, y, w, h, Color.BLACK, DIM * alphaMult);
                return;
            }

            float lx = x + lit[0], ly = y + height - lit[1] - lit[3], lw = lit[2], lh = lit[3];
            UiDraw.quad(x, ly + lh, w, y + h - ly - lh, Color.BLACK, DIM * alphaMult);
            UiDraw.quad(x, y, w, ly - y, Color.BLACK, DIM * alphaMult);
            UiDraw.quad(x, ly, lx - x, lh, Color.BLACK, DIM * alphaMult);
            UiDraw.quad(lx + lw, ly, x + w - lx - lw, lh, Color.BLACK, DIM * alphaMult);

            float glow = GLOW_LOW + (1f - GLOW_LOW) * state.glow();
            UiDraw.glow(lx, ly, lw, lh, GLOW_REACH, Misc.getBasePlayerColor(), GLOW_ALPHA * glow * alphaMult);
            UiDraw.outline(lx, ly, lw, lh, Misc.getBrightPlayerColor(), alphaMult);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null || !state.blocking()) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !(event.isMouseDownEvent() || event.isMouseUpEvent() || event.isMouseScrollEvent())) continue;
                if (inside(event, lit) || inside(event, box)) continue;
                event.consume();
            }
        }

        private boolean inside(InputEventAPI event, float[] r) {
            if (r == null) return false;
            float ex = event.getX() - pos.getX();
            float ey = pos.getY() + height - event.getY();
            return ex >= r[0] && ex < r[0] + r[2] && ey >= r[1] && ey < r[1] + r[3];
        }
    }
}
