package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiButtons;
import indevo.utils.ui.UiDraw;

import java.awt.*;
import java.util.List;
import java.util.function.IntConsumer;

// value slider: step buttons at both ends, a track that takes a click or a drag, the value on the track; reports on release
public class YardsSlider extends BaseCustomUIPanelPlugin {

    public static final float STEP_W = 20f;
    public static final float HANDLE_W = 6f;

    private final int max;
    private final Color fill;
    private final IntConsumer onSet;
    private int value;
    private boolean dragging = false;
    private PositionAPI pos;
    private LabelAPI label;

    private YardsSlider(int value, int max, Color fill, IntConsumer onSet) {
        this.value = value;
        this.max = max;
        this.fill = fill;
        this.onSet = onSet;
    }

    public static void create(CustomPanelAPI p, float x, float y, float w, float h, int value, int max, Color fill, IntConsumer onSet) {
        boolean on = max > 0;
        UiButtons.button(p, x, y, STEP_W, h, "-", on ? UiButtons.Kind.STD : UiButtons.Kind.OFF, CutStyle.ALL, on && value > 0 ? () -> onSet.accept(value - 1) : null);
        UiButtons.button(p, x + w - STEP_W, y, STEP_W, h, "+", on ? UiButtons.Kind.STD : UiButtons.Kind.OFF, CutStyle.ALL, on && value < max ? () -> onSet.accept(value + 1) : null);

        float tw = w - 2f * STEP_W - 8f;
        YardsSlider slider = new YardsSlider(value, max, fill, onSet);
        CustomPanelAPI track = p.createCustomPanel(tw, h, slider);
        p.addComponent(track).inTL(x + STEP_W + 4f, y);
        slider.label = Ui.textIn(track, 4f, 0f, tw - 8f, h, Ui.num(value), Ui.INS15, Ui.text(), Ui.CENTER, new String[0], new Color[0]);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();
        float f = max <= 0 ? 0f : value / (float) max;
        float fw = Math.round((w - 6f) * f);

        UiDraw.quad(x, y, w, h, Color.BLACK, alphaMult);
        UiDraw.quad(x + 3f, y + 1f, fw, h - 2f, fill, alphaMult);
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();
        Color edge = Misc.scaleAlpha(Ui.base(), 0.9f);

        UiDraw.quad(x, y, 1f, h, edge, alphaMult);
        UiDraw.quad(x + w - 1f, y, 1f, h, edge, alphaMult);
        if (max <= 0) return;

        float hx = x + 3f + Math.round((w - 6f) * value / (float) max) - HANDLE_W / 2f;
        UiDraw.quad(hx - 1f, y - 4f, HANDLE_W + 2f, h + 8f, Color.BLACK, alphaMult);
        UiDraw.quad(hx, y - 3f, HANDLE_W, h + 6f, Ui.bright(), alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null || max <= 0) return;

        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;

            if (e.isLMBDownEvent() && pos.containsEvent(e)) {
                dragging = true;
                setFromX(e.getX());
                e.consume();
            } else if (dragging && e.isMouseMoveEvent()) {
                setFromX(e.getX());
                e.consume();
            } else if (dragging && e.isLMBUpEvent()) {
                dragging = false;
                e.consume();
                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 0.6f);
                // rebuilds the panel this slider sits in, nothing may run after it
                onSet.accept(value);
                return;
            }
        }
    }

    private void setFromX(float mouseX) {
        float f = (mouseX - pos.getX() - 3f) / (pos.getWidth() - 6f);
        value = Math.round(Math.max(0f, Math.min(1f, f)) * max);
        if (label != null) label.setText(Ui.num(value));
    }
}
