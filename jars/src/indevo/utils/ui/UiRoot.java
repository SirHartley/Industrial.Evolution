package indevo.utils.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;

// rebuild host: a click changes the view state, refresh() drops the body, builds it again and restores the scroll offsets
public final class UiRoot {

    public interface Builder {
        void build(CustomPanelAPI body, float w, float h);
    }

    private final CustomPanelAPI root;
    private final UiScroll.Offsets offsets;
    private final Builder builder;
    private CustomPanelAPI body;

    public UiRoot(CustomPanelAPI root, UiScroll.Offsets offsets, Builder builder) {
        this.root = root;
        this.offsets = offsets;
        this.builder = builder;
    }

    public void refresh() {
        offsets.capture();
        if (body != null) root.removeComponent(body);

        float w = root.getPosition().getWidth();
        float h = root.getPosition().getHeight();
        body = root.createCustomPanel(w, h, new UiCanvas());
        builder.build(body, w, h);
        root.addComponent(body).inTL(0f, 0f);
        offsets.restore();
    }
}
