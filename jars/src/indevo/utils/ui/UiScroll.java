package indevo.utils.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.HashMap;
import java.util.Map;

public final class UiScroll {

    // the scrollbar sits in the last 3 px and widens on hover (com.fs.starfarer.ui.g), blocks stay clear of it
    public static final float GUTTER = 10f;

    // scroll offsets per list id, part of the panel's view state; capture before a rebuild, restore after it
    public static final class Offsets {
        private final Map<String, Float> saved = new HashMap<>();
        private final Map<String, Track> live = new HashMap<>();

        public float get(String id) {
            return saved.getOrDefault(id, 0f);
        }

        public void set(String id, float offset) {
            saved.put(id, offset);
        }

        public void reset(String id) {
            saved.remove(id);
        }

        public void capture() {
            for (Map.Entry<String, Track> e : live.entrySet()) {
                ScrollPanelAPI scroller = e.getValue().element.getExternalScroller();
                if (scroller != null) saved.put(e.getKey(), scroller.getYOffset());
            }

            live.clear();
        }

        public void restore() {
            for (Map.Entry<String, Track> e : live.entrySet()) apply(e.getKey(), e.getValue());
        }

        private void track(String id, TooltipMakerAPI element, float h) {
            Track track = new Track(element, h);
            live.put(id, track);
            apply(id, track);
        }

        private void apply(String id, Track track) {
            ScrollPanelAPI scroller = track.element.getExternalScroller();
            if (scroller == null) return;

            float max = Math.max(0f, track.element.getHeightSoFar() - track.h);
            scroller.setYOffset(Math.max(0f, Math.min(max, get(id))));
        }

        private record Track(TooltipMakerAPI element, float h) {
        }
    }

    // a scrolling column of fixed outer size; add blocks top to bottom, then end()
    public static final class Column {
        private final CustomPanelAPI holder;
        private final TooltipMakerAPI element;
        private final float w;
        private final float h;
        private final String id;
        private final Offsets offsets;

        private Column(CustomPanelAPI holder, float w, float h, String id, Offsets offsets) {
            this.holder = holder;
            this.w = w;
            this.h = h;
            this.id = id;
            this.offsets = offsets;
            element = holder.createUIElement(w + Ui.INSET, h, true);
        }

        public float width() {
            return w - GUTTER;
        }

        public CustomPanelAPI block(float height, float gapBefore) {
            CustomPanelAPI block = holder.createCustomPanel(width(), height, new UiCanvas());
            element.addCustom(block, gapBefore);
            return block;
        }

        public float contentHeight() {
            return element.getHeightSoFar();
        }

        public TooltipMakerAPI end() {
            holder.addUIElement(element).inTL(-Ui.INSET, 0f);
            offsets.track(id, element, h);
            return element;
        }
    }

    private UiScroll() {
    }

    // the element is built Ui.INSET wider and shifted left so blocks start at x 0 of the rect
    public static Column list(CustomPanelAPI p, float x, float y, float w, float h, Offsets offsets, String id) {
        CustomPanelAPI holder = Ui.panel(p, x, y, w, h);
        return new Column(holder, w, h, id, offsets);
    }
}
