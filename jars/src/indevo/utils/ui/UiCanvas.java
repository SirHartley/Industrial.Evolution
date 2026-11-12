package indevo.utils.ui;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;

// plugin of every kit panel: runs a Runnable button id on press, paints below and above the children
public class UiCanvas extends BaseCustomUIPanelPlugin {

    public interface Painter {
        void paint(PositionAPI pos, float alpha);
    }

    private final Painter below;
    private final Painter above;
    private PositionAPI pos;

    public UiCanvas() {
        this(null, null);
    }

    public UiCanvas(Painter below, Painter above) {
        this.below = below;
        this.above = above;
    }

    public static UiCanvas below(Painter painter) {
        return new UiCanvas(painter, null);
    }

    public static UiCanvas above(Painter painter) {
        return new UiCanvas(null, painter);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos != null && below != null) below.paint(pos, alphaMult);
    }

    @Override
    public void render(float alphaMult) {
        if (pos != null && above != null) above.paint(pos, alphaMult);
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId instanceof Runnable r) r.run();
    }
}
