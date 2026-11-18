package indevo.utils.ui;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

// filter bar over a big list: a text field and Show filters, which folds open a row of toggles and a row of sort keys
public final class UiFilter {

    public static final float H = 26f;
    public static final float BELOW = 8f;
    static final float FIELD_W = 150f;
    static final float GAP = 6f;
    static final float ROW_PITCH = 30f;
    static final float SHOW_W = 108f;
    static final float SHOW_COUNT_W = 124f;
    static final float SORT_W = 42f;
    static final float TEXT_INSET = 4f;
    static final int MAX_CHARS = 30;

    // the sort arrow, 8 x 5 in the top of an 8 unit box, 18 px left of the key's right edge
    static final float[] DOWN = {0f, 0f, 8f, 0f, 4f, 5f};
    static final float[] UP = {0f, 5f, 8f, 5f, 4f, 0f};

    public record Item(String label, float w, String tip) {
        public Item(String label, float w) {
            this(label, w, null);
        }
    }

    // view state of one bar, kept while its screen is open
    public static final class State {
        public final Set<String> on = new HashSet<>();
        public String sort;
        public boolean reversed = false;
        public boolean open = false;
        private final String defaultSort;
        private final Set<String> defaultOn;
        private String text = "";
        private TextFieldAPI field;
        private boolean focused = false;

        public State(String defaultSort, String... defaultOn) {
            this.defaultSort = defaultSort;
            this.sort = defaultSort;
            this.defaultOn = Set.of(defaultOn);
            on.addAll(this.defaultOn);
        }

        public String query() {
            return text.trim().toLowerCase(Locale.ROOT);
        }

        public boolean matches(String... values) {
            String q = query();
            if (q.isEmpty()) return true;
            for (String v : values) if (v != null && v.toLowerCase(Locale.ROOT).contains(q)) return true;
            return false;
        }

        public boolean isFiltering() {
            return !query().isEmpty() || !on.equals(defaultOn);
        }

        // toggles lit beyond the default ones, plus one for a sort that is not the default
        public int changed() {
            int n = 0;
            for (String s : on) if (!defaultOn.contains(s)) n++;
            return n + (!sort.equals(defaultSort) || reversed ? 1 : 0);
        }

        public <T> void sort(List<T> list, Comparator<T> by) {
            if (by != null) list.sort(by);
            if (reversed) Collections.reverse(list);
        }

        // a text field has no change callback, so the panel plugin polls it every frame; true when the text changed
        public boolean poll() {
            if (field == null) return false;

            focused = field.hasFocus();
            String typed = field.getText();
            if (typed.equals(text)) return false;

            text = typed;
            return true;
        }
    }

    private UiFilter() {
    }

    public static float height(State s, boolean toggles, boolean keys) {
        return height(s.open, toggles, keys);
    }

    public static float height(boolean open, boolean toggles, boolean keys) {
        if (!open) return H;
        return H + (toggles ? ROW_PITCH : 0f) + (keys ? ROW_PITCH : 0f);
    }

    // onToggle null flips the toggle; changed runs after a filter or sort change, refresh after the fold opens or closes
    public static float bar(CustomPanelAPI p, float x, float y, State s, String hint, String showTip, List<Item> toggles, Consumer<String> onToggle,
                            List<Item> keys, Runnable changed, Runnable refresh) {
        addField(p, x, y, s, hint);
        addShow(p, x + FIELD_W + GAP, y, s, showTip, refresh);
        if (!s.open) return H;

        float ry = y;
        if (!toggles.isEmpty()) {
            ry += ROW_PITCH;
            float tx = x;
            for (Item t : toggles) {
                ButtonAPI b = UiButtons.area(p, tx, ry, t.w(), H, t.label(), s.on.contains(t.label()), true, null, Ui.CENTER, () -> {
                    if (onToggle != null) onToggle.accept(t.label());
                    else if (!s.on.remove(t.label())) s.on.add(t.label());
                    changed.run();
                });
                if (t.tip() != null) Ui.tooltip(p, b, null, t.tip());
                tx += t.w() + GAP;
            }
        }

        if (keys.isEmpty()) return height(s, !toggles.isEmpty(), false);

        ry += ROW_PITCH;
        Ui.textIn(p, x, ry, SORT_W - 2f, H, "Sort", Ui.ORB12, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        float kx = x + SORT_W;
        for (Item k : keys) {
            boolean lit = k.label().equals(s.sort);
            ButtonAPI b = UiButtons.area(p, kx, ry, k.w(), H, k.label(), lit, true, null, Ui.LEFT, () -> {
                if (k.label().equals(s.sort)) s.reversed = !s.reversed;
                else {
                    s.sort = k.label();
                    s.reversed = false;
                }
                changed.run();
            });
            if (k.tip() != null) Ui.tooltip(p, b, null, k.tip());
            if (lit) {
                float[] arrow = s.reversed ? UP : DOWN;
                float ax = kx + k.w() - 18f;
                Ui.painted(p, ax, ry + 10f, 8f, 5f, null, (pos, a) -> UiDraw.fan(UiDraw.left(pos), UiDraw.bottom(pos) - 3f, 8f, 8f, arrow, 4f, 2.5f, Ui.bright(), a));
            }
            kx += k.w() + GAP;
        }

        return height(s, !toggles.isEmpty(), true);
    }

    // the field is made again on every rebuild and takes the focus back when it had it
    private static void addField(CustomPanelAPI p, float x, float y, State s, String hint) {
        TextFieldAPI field = UiButtons.field(p, x, y, FIELD_W, H, s.text, MAX_CHARS);
        s.field = field;
        if (s.text.isEmpty() && hint != null) Ui.textIn(p, x + TEXT_INSET, y, FIELD_W - 2f * TEXT_INSET, H, hint, Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        if (s.focused) field.grabFocus(false);
    }

    private static void addShow(CustomPanelAPI p, float x, float y, State s, String tip, Runnable refresh) {
        int count = s.open ? 0 : s.changed();
        float w = count > 0 ? SHOW_COUNT_W : SHOW_W;
        ButtonAPI b = UiButtons.area(p, x, y, w, H, null, s.open, true, null, Ui.CENTER, () -> {
            s.open = !s.open;
            refresh.run();
        });
        if (tip != null) Ui.tooltip(p, b, null, tip);

        String n = String.valueOf(count);
        String label = s.open ? "Hide filters" : count > 0 ? "Show filters " + n : "Show filters";
        Ui.textIn(p, x + 6f, y, w - 12f, H, label, Ui.ORB12, s.open ? Ui.bright() : Ui.base(), Ui.CENTER,
                count > 0 ? new String[]{n} : new String[0], new Color[]{Ui.hl()});
    }
}
