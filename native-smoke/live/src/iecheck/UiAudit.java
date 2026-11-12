package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import indevo.utils.helper.ReflectionUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// layout audit of an open panel: walks the component tree and reports overlapping or cut off text and components
// positions are screen space, y up; findings print x and y from the panel's top left
public final class UiAudit {
    private static final float TOL = 1f;

    public static String stats = "";

    private UiAudit() {
    }

    record Rect(float x, float y, float w, float h) {
        float r() {
            return x + w;
        }

        float t() {
            return y + h;
        }

        boolean overlaps(Rect o) {
            return Math.min(r(), o.r()) - Math.max(x, o.x) > TOL && Math.min(t(), o.t()) - Math.max(y, o.y) > TOL;
        }

        boolean contains(Rect o) {
            return o.x >= x - TOL && o.r() <= r() + TOL && o.y >= y - TOL && o.t() <= t() + TOL;
        }

        Rect clip(Rect o) {
            float nx = Math.max(x, o.x);
            float ny = Math.max(y, o.y);
            return new Rect(nx, ny, Math.min(r(), o.r()) - nx, Math.min(t(), o.t()) - ny);
        }

        boolean empty() {
            return w <= TOL || h <= TOL;
        }

        static Rect of(PositionAPI p) {
            return new Rect(p.getX(), p.getY(), p.getWidth(), p.getHeight());
        }
    }

    record Text(LabelAPI label, String text, Rect rect, Rect clip) {
    }

    record Btn(ButtonAPI button, Rect rect, Rect clip) {
    }

    // the custom dialog window (frame and Return) and its content panel
    public record Found(UIComponentAPI dialog, CustomPanelAPI content) {
    }

    public static List<?> children(Object node) {
        List<Object> out = new ArrayList<>();
        try {
            MethodHandle getter = MethodHandles.privateLookupIn(node.getClass(), MethodHandles.lookup())
                    .findVirtual(node.getClass(), "getChildrenCopy", MethodType.methodType(List.class));
            out.addAll((List<?>) getter.invoke(node));
        } catch (Throwable ignored) {
        }
        // dialogs (obf ui.impl.o0OO) keep their content in an inner panel that is not in the child list
        try {
            MethodHandle inner = MethodHandles.privateLookupIn(node.getClass(), MethodHandles.lookup())
                    .findVirtual(node.getClass(), "getInnerPanel", MethodType.methodType(Class.forName("com.fs.starfarer.ui.interfacenew")));
            Object panel = inner.invoke(node);
            if (panel != null && !out.contains(panel)) out.add(panel);
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static Object screen() {
        Object state = Global.getSector().getCampaignUI();
        try {
            MethodHandle getter = MethodHandles.privateLookupIn(state.getClass(), MethodHandles.lookup())
                    .findVirtual(state.getClass(), "getScreenPanel", MethodType.methodType(Class.forName("com.fs.starfarer.ui.interfacenew")));
            return getter.invoke(state);
        } catch (Throwable t) {
            throw new IllegalStateException("no screen panel", t);
        }
    }

    public static Found find() {
        InteractionDialogAPI interaction = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        Object dialog = interaction == null ? null : findDialog(interaction);
        if (dialog == null) dialog = findDialog(screen());
        if (dialog == null) return null;
        CustomPanelAPI content = firstCustom(dialog);
        return content == null ? null : new Found((UIComponentAPI) dialog, content);
    }

    // a side panel in the dialog's visual area (VisualPanelAPI.showCustomPanel) of the given size
    public static Found findSide(float w, float h) {
        InteractionDialogAPI interaction = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (interaction == null) return null;
        List<Object> queue = new ArrayList<>(children(interaction));
        while (!queue.isEmpty()) {
            Object node = queue.remove(0);
            if (node instanceof CustomPanelAPI panel && Math.abs(panel.getPosition().getWidth() - w) < 1f && Math.abs(panel.getPosition().getHeight() - h) < 1f) return new Found(panel, panel);
            queue.addAll(children(node));
        }
        return null;
    }

    private static Object findDialog(Object node) {
        if (node instanceof CustomDialogDelegate.CustomDialogCallback) return node;
        for (Object child : children(node)) {
            Object found = findDialog(child);
            if (found != null) return found;
        }
        return null;
    }

    private static CustomPanelAPI firstCustom(Object root) {
        List<Object> queue = new ArrayList<>(children(root));
        while (!queue.isEmpty()) {
            Object node = queue.remove(0);
            if (node instanceof CustomPanelAPI panel) return panel;
            queue.addAll(children(node));
        }
        return null;
    }

    // closes the custom dialog the way its Return button does
    public static void close(Found found) {
        ((CustomDialogDelegate.CustomDialogCallback) found.dialog()).dismissCustomDialog(1);
    }

    public static void dismissDialog() {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        if (dialog != null) dialog.dismiss();
    }

    // runs the Runnable the kit stores as button data, the same call UiCanvas.buttonPressed makes
    public static boolean click(Object root, String text) {
        ButtonAPI button = button(root, text);
        if (button == null || !button.isEnabled() || !(button.getCustomData() instanceof Runnable action)) return false;
        action.run();
        return true;
    }

    // the enabled button under the centre of the label holding text: icon buttons and rows draw their text as separate labels
    public static boolean clickLabel(Object root, String text) {
        LabelAPI label = label(root, text);
        if (label == null) return false;
        float x = label.getPosition().getCenterX();
        float y = label.getPosition().getCenterY();
        for (Object node : all(root)) {
            if (!(node instanceof ButtonAPI b) || !b.isEnabled() || !(b.getCustomData() instanceof Runnable action)) continue;
            PositionAPI p = b.getPosition();
            if (x >= p.getX() && x <= p.getX() + p.getWidth() && y >= p.getY() && y <= p.getY() + p.getHeight()) {
                action.run();
                return true;
            }
        }
        return false;
    }

    public static boolean clickPrefix(Object root, String prefix) {
        ButtonAPI button = buttonPrefix(root, prefix);
        if (button == null || !button.isEnabled() || !(button.getCustomData() instanceof Runnable action)) return false;
        action.run();
        return true;
    }

    public static ButtonAPI buttonPrefix(Object node, String prefix) {
        if (node instanceof ButtonAPI b && b.getText() != null && b.getText().startsWith(prefix)) return b;
        for (Object child : children(node)) {
            ButtonAPI found = buttonPrefix(child, prefix);
            if (found != null) return found;
        }
        return null;
    }

    // the n-th enabled button without own text (toggles, rows, checkboxes draw their text elsewhere)
    public static boolean clickUntitled(Object root, int n) {
        for (Object node : all(root)) {
            if (node instanceof ButtonAPI b && b.getText() == null && b.isEnabled() && b.getCustomData() instanceof Runnable action && n-- == 0) {
                action.run();
                return true;
            }
        }
        return false;
    }

    public static String buttonTexts(Object node) {
        StringBuilder out = new StringBuilder();
        for (Object child : all(node)) if (child instanceof ButtonAPI b) out.append("[").append(b.getText()).append(b.getCustomData() instanceof Runnable ? " r" : "").append("]");
        return out.toString();
    }

    public static ButtonAPI button(Object node, String text) {
        if (node instanceof ButtonAPI b && text.equals(b.getText())) return b;
        for (Object child : children(node)) {
            ButtonAPI found = button(child, text);
            if (found != null) return found;
        }
        return null;
    }

    public static LabelAPI label(Object node, String text) {
        if (node instanceof LabelAPI l && l.getText() != null && l.getText().contains(text)) return l;
        for (Object child : children(node)) {
            LabelAPI found = label(child, text);
            if (found != null) return found;
        }
        return null;
    }

    // the row whose labels hold text: the area checkbox under them
    public static boolean clickRow(Object root, String text) {
        Object row = rowOf(root, text);
        if (row == null) return false;
        for (Object child : all(row)) {
            if (child instanceof ButtonAPI b && b.getCustomData() instanceof Runnable action) {
                action.run();
                return true;
            }
        }
        return false;
    }

    private static Object rowOf(Object node, String text) {
        for (Object child : children(node)) {
            if (child instanceof LabelAPI l && text.equals(l.getText()) && node instanceof CustomPanelAPI) {
                for (Object sibling : children(node)) {
                    if (sibling instanceof TooltipMakerAPI && !all(sibling).isEmpty()) {
                        for (Object b : all(sibling)) if (b instanceof ButtonAPI) return node;
                    }
                }
            }
            Object found = rowOf(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private static List<Object> all(Object node) {
        List<Object> out = new ArrayList<>();
        for (Object child : children(node)) {
            out.add(child);
            out.addAll(all(child));
        }
        return out;
    }

    public static String dump(Object node, int depth) {
        if (node == null) return "null";
        String name = node.getClass().getName();
        StringBuilder out = new StringBuilder(name.substring(name.lastIndexOf('.') + 1));
        if (depth < 3) {
            out.append("{");
            for (Object child : children(node)) out.append(dump(child, depth + 1)).append(",");
            out.append("}");
        }
        return out.toString();
    }

    // click and hover targets in screen pixels from the top left, the frame gamectl and the captures use
    public static List<String> targets(Found found) {
        List<String> out = new ArrayList<>();
        float sh = Global.getSettings().getScreenHeight();
        Rect panel = Rect.of(found.content().getPosition());
        out.add("HELP " + Math.round(panel.r() - 12f) + "," + Math.round(sh - panel.t() + 10f));
        for (Object node : all(found.content())) {
            if (node instanceof ButtonAPI b && b.getOpacity() > 0f) {
                PositionAPI p = b.getPosition();
                out.add("BTN " + (b.isEnabled() ? "on " : "off ") + Math.round(p.getCenterX()) + "," + Math.round(sh - p.getCenterY()) + " " + b.getText());
            } else if (node instanceof LabelAPI l && l.getText() != null && l.getText().endsWith("...")) {
                PositionAPI p = l.getPosition();
                out.add("CUT " + Math.round(p.getCenterX()) + "," + Math.round(sh - p.getCenterY()) + " " + l.getText());
            }
        }
        return out;
    }

    public static String rect(Found found) {
        Rect r = Rect.of(found.dialog().getPosition());
        float sh = Global.getSettings().getScreenHeight();
        return Math.round(r.x()) + " " + Math.round(sh - r.t()) + " " + Math.round(r.w()) + " " + Math.round(r.h());
    }

    public static List<String> run(Found found) {
        Rect panel = Rect.of(found.content().getPosition());
        List<Text> texts = new ArrayList<>();
        List<Btn> buttons = new ArrayList<>();
        List<String> out = new ArrayList<>();
        walk(found.content(), panel, panel, false, panel, texts, buttons, out);
        stats = texts.size() + " texts, " + buttons.size() + " buttons";

        for (int i = 0; i < texts.size(); i++) {
            for (int j = i + 1; j < texts.size(); j++) {
                Text a = texts.get(i);
                Text b = texts.get(j);
                Rect va = a.rect().clip(a.clip());
                Rect vb = b.rect().clip(b.clip());
                if (va.overlaps(vb)) out.add("text over text: '" + cut(a.text()) + "' " + at(a.rect(), panel) + " and '" + cut(b.text()) + "' " + at(b.rect(), panel));
            }
        }

        for (int i = 0; i < buttons.size(); i++) {
            for (int j = i + 1; j < buttons.size(); j++) {
                Btn a = buttons.get(i);
                Btn b = buttons.get(j);
                Rect va = a.rect().clip(a.clip());
                Rect vb = b.rect().clip(b.clip());
                if (!va.overlaps(vb) || a.rect().contains(b.rect()) || b.rect().contains(a.rect())) continue;
                out.add("buttons intersect: '" + cut(a.button().getText()) + "' " + at(a.rect(), panel) + " and '" + cut(b.button().getText()) + "' " + at(b.rect(), panel));
            }
        }

        // Ui.fit cuts with "...", labels drawn over a button face are button labels such as "Cargo..."
        for (Text t : texts) {
            if (!t.text().stripTrailing().endsWith("...")) continue;
            boolean onButton = false;
            for (Btn b : buttons) if (b.rect().contains(t.rect())) onButton = true;
            String s = t.text().strip();
            if (!onButton) out.add("text cut with an ellipsis: '" + (s.length() > 50 ? "..." + s.substring(s.length() - 47) : s) + "' " + at(t.rect(), panel));
        }
        return out;
    }

    // clip is the visible area (nearest scroll area or the panel); scrolled marks nodes inside a scroll area
    private static void walk(Object node, Rect panel, Rect clip, boolean scrolled, Rect scrollArea, List<Text> texts, List<Btn> buttons, List<String> out) {
        if (node instanceof UIComponentAPI c && c.getOpacity() <= 0f) return;
        if (node instanceof LabelAPI l && l.getOpacity() <= 0f) return;

        Rect rect = node instanceof UIComponentAPI c ? Rect.of(c.getPosition()) : node instanceof LabelAPI l ? Rect.of(l.getPosition()) : null;
        if (rect != null && !(node instanceof LabelAPI) && rect.clip(clip).empty() && !(node instanceof ScrollPanelAPI)) return;

        if (node instanceof LabelAPI label) {
            label(label, rect, panel, clip, scrolled, scrollArea, texts, out);
            return;
        }

        if (rect != null && !(node instanceof TooltipMakerAPI) && !(node instanceof ScrollPanelAPI)
                && (node instanceof ButtonAPI || node instanceof CustomPanelAPI || children(node).isEmpty())) {
            boolean outside = scrolled ? rect.x() < scrollArea.x() - TOL || rect.r() > scrollArea.r() + TOL : !panel.contains(rect);
            if (outside) out.add("outside " + (scrolled ? "its scroll area" : "the panel") + ": " + kind(node) + " " + at(rect, panel));
        }

        if (node instanceof ButtonAPI button) {
            buttons.add(new Btn(button, rect, clip));
            String text = button.getText();
            if (text != null && !text.isEmpty()) {
                float tw = Global.getSettings().computeStringWidth(text, "graphics/fonts/orbitron12condensed.fnt");
                if (tw > rect.w() - 4f) out.add("button text wider than the button: '" + cut(text) + "' " + Math.round(tw) + " in " + Math.round(rect.w()) + " " + at(rect, panel));
            }
        }

        Rect childClip = clip;
        boolean childScrolled = scrolled;
        Rect childArea = scrollArea;
        if (node instanceof ScrollPanelAPI) {
            childClip = rect.clip(clip);
            childScrolled = true;
            childArea = rect;
        }

        for (Object child : children(node)) walk(child, panel, childClip, childScrolled, childArea, texts, buttons, out);
    }

    private static void label(LabelAPI label, Rect box, Rect panel, Rect clip, boolean scrolled, Rect scrollArea, List<Text> texts, List<String> out) {
        String text = label.getText();
        if (text == null || text.isBlank()) return;

        float tw = label.computeTextWidth(text);
        float th = label.computeTextHeight(text);
        float lh = lineHeight(label);
        boolean multi = box.h() > lh * 1.5f;
        Alignment align = alignment(label);

        Rect drawn;
        if (multi) {
            drawn = new Rect(box.x(), box.t() - th, box.w(), th);
            if (th > box.h() + TOL) out.add("wrapped text taller than its box: '" + cut(text) + "' " + Math.round(th) + " in " + Math.round(box.h()) + " " + at(box, panel));
        } else {
            float w = Math.min(tw, Math.max(box.w(), tw));
            float x = switch (align) {
                case RMID, TR, BR -> box.r() - w;
                case MID, TMID, BMID -> box.x() + (box.w() - w) / 2f;
                default -> box.x();
            };
            float y = switch (align) {
                case TL, TR, TMID -> box.t() - lh;
                case BL, BR, BMID -> box.y();
                default -> box.y() + (box.h() - lh) / 2f;
            };
            drawn = new Rect(x, y, w, lh);
            if (tw > box.w() + 0.5f) out.add("text wider than its box: '" + cut(text) + "' " + Math.round(tw) + " in " + Math.round(box.w()) + " " + at(box, panel));
            if (th > lh * 1.5f) out.add("text wraps in a one line box: '" + cut(text) + "' " + at(box, panel));
        }

        if (drawn.clip(clip).empty()) return;
        boolean outside = scrolled ? drawn.x() < scrollArea.x() - TOL || drawn.r() > scrollArea.r() + TOL : !panel.contains(drawn);
        if (outside) out.add("text outside " + (scrolled ? "its scroll area" : "the panel") + ": '" + cut(text) + "' " + at(drawn, panel));
        texts.add(new Text(label, text, drawn, clip));
    }

    private static float lineHeight(LabelAPI label) {
        try {
            MethodHandle getter = MethodHandles.publicLookup().findVirtual(label.getClass(), "getLineHeight", MethodType.methodType(float.class));
            return (float) getter.invoke(label);
        } catch (Throwable t) {
            return label.computeTextHeight("Ag");
        }
    }

    private static Alignment alignment(LabelAPI label) {
        try {
            List<String> names = ReflectionUtils.getFieldsOfType(label, Alignment.class);
            if (!names.isEmpty()) return (Alignment) ReflectionUtils.get(names.get(0), label);
        } catch (Throwable ignored) {
        }
        return Alignment.LMID;
    }

    private static String kind(Object node) {
        if (node instanceof ButtonAPI b) return "button '" + cut(b.getText()) + "'";
        if (node instanceof CustomPanelAPI) return "panel";
        String name = node.getClass().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static String at(Rect r, Rect panel) {
        return String.format(Locale.ROOT, "[%d,%d %dx%d]", Math.round(r.x() - panel.x()), Math.round(panel.t() - r.t()), Math.round(r.w()), Math.round(r.h()));
    }

    private static String cut(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }
}
