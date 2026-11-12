package indevo.utils.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    // blocks are kept until end(): the viewport height is fixed when the element is created, and at offset 0 it ends on
    // the last block that fits whole, so nothing is cut in half at rest; the rest of the column stays empty
    public static final class Column {
        private final CustomPanelAPI holder;
        private final float w;
        private final float h;
        private final String id;
        private final Offsets offsets;
        private final List<CustomPanelAPI> blocks = new ArrayList<>();
        private final List<Float> gaps = new ArrayList<>();
        private final List<Boolean> headings = new ArrayList<>();
        private float content = 0f;

        private Column(CustomPanelAPI holder, float w, float h, String id, Offsets offsets) {
            this.holder = holder;
            this.w = w;
            this.h = h;
            this.id = id;
            this.offsets = offsets;
        }

        public float width() {
            return w - GUTTER;
        }

        public CustomPanelAPI block(float height, float gapBefore) {
            CustomPanelAPI block = holder.createCustomPanel(width(), height, new UiCanvas());
            blocks.add(block);
            gaps.add(gapBefore);
            headings.add(false);
            content += gapBefore + height;
            return block;
        }

        // a Ui.HEADING_H block the view never ends on at rest, it stays with the block below it
        public CustomPanelAPI heading(float gapBefore) {
            CustomPanelAPI block = block(Ui.HEADING_H, gapBefore);
            headings.set(headings.size() - 1, true);
            return block;
        }

        public float contentHeight() {
            return content;
        }

        public TooltipMakerAPI end() {
            float viewH = h;
            if (content > h + 0.5f) {
                float y = 0f;
                float fit = 0f;
                for (int i = 0; i < blocks.size(); i++) {
                    float bottom = y + gaps.get(i) + blocks.get(i).getPosition().getHeight();
                    if (bottom > h + 0.5f) break;
                    if (!headings.get(i)) fit = bottom;
                    y = bottom;
                }
                if (fit > 0f) viewH = fit;
            }

            TooltipMakerAPI element = holder.createUIElement(w + Ui.INSET, viewH, true);
            for (int i = 0; i < blocks.size(); i++) element.addCustom(blocks.get(i), gaps.get(i));
            holder.addUIElement(element).inTL(-Ui.INSET, 0f);
            offsets.track(id, element, viewH);
            return element;
        }
    }

    private UiScroll() {
    }

    // tallest height up to h that ends on a whole row: lead (blocks above the first row), then rows of rowH with gap between
    public static float wholeRows(float h, float lead, float rowH, float gap) {
        int n = Math.max(1, (int) Math.floor((h - lead + gap) / (rowH + gap)));
        return Math.min(h, lead + n * rowH + (n - 1) * gap);
    }

    // the element is built Ui.INSET wider and shifted left so blocks start at x 0 of the rect
    public static Column list(CustomPanelAPI p, float x, float y, float w, float h, Offsets offsets, String id) {
        CustomPanelAPI holder = Ui.panel(p, x, y, w, h);
        return new Column(holder, w, h, id, offsets);
    }
}
