package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.petshop.plugins.PetTreeLayout.Brood;
import indevo.industries.petshop.plugins.PetTreeLayout.Node;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiDraw;
import indevo.utils.ui.UiScroll;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// a laid out tree in a scroll column: the owner draws the tiles, this draws the lines, the gold marks, hover and drag
final class PetTreeCanvas {

    interface Owner {
        void tile(CustomPanelAPI world, Node n, float x, float y);

        float height(Node n);

        boolean dashed(Brood brood);

        boolean hovers(String id);

        float getX();

        void setX(float x);

        void emptyClick();

        // the No family label also when no family is in view
        default boolean labelAlone() {
            return false;
        }
    }

    final PetTreeLayout.Result result;
    final PetTreeLayout.Metrics m;
    final boolean small;
    final Set<String> gold;
    // the young whose brood lines stay gold, the clicked planned young; null for none
    final String lit;
    final Owner owner;
    final UiScroll.Offsets scroll;
    final String key;
    final float pitch;
    final float tileH;
    final float top;
    final Map<String, Brood> byKid = new HashMap<>();
    final Map<String, Integer> broodCount = new HashMap<>();
    CustomPanelAPI world;
    TooltipMakerAPI element;
    PositionAPI viewport;
    float viewW;
    float viewH;
    String hovered = null;

    PetTreeCanvas(PetTreeLayout.Result result, PetTreeLayout.Metrics m, boolean small, Set<String> gold, String lit, Owner owner,
                  UiScroll.Offsets scroll, String key) {
        this.result = result;
        this.m = m;
        this.small = small;
        this.gold = gold;
        this.lit = lit;
        this.owner = owner;
        this.scroll = scroll;
        this.key = key;
        pitch = small ? PetTree.SMALL_PITCH : PetTree.ROW_PITCH;
        tileH = small ? m.tileW() : PetTree.TILE_H;

        // the No family label sits above the singles, inside the block when they stand in the first row
        boolean label = labelled() && result.nodes().get(result.singles().get(0)).row == 0;
        top = PetTree.MARGIN + (label ? PetTree.LABEL_H : 0f);

        for (Brood brood : result.broods()) {
            for (String kid : brood.kids) byKid.put(kid, brood);
            broodCount.merge(brood.a, 1, Integer::sum);
            if (brood.b != null) broodCount.merge(brood.b, 1, Integer::sum);
        }
    }

    void draw(CustomPanelAPI p, float x, float y, float w, float h) {
        float treeH = result.rows() * pitch - (pitch - tileH);
        float blockH = top + treeH + PetTree.MARGIN;
        UiScroll.Column column = UiScroll.list(p, x, y, w, h, scroll, key);
        viewW = column.width();
        viewH = h;
        float worldW = Math.max(viewW, result.width() + 2f * PetTree.MARGIN);
        owner.setX(Math.max(0f, Math.min(worldW - viewW, owner.getX())));

        CustomPanelAPI block = column.block(blockH, 0f);
        world = Ui.painted(block, -owner.getX(), 0f, worldW, blockH, this::paintLines, this::paintMarks);
        for (Node n : result.nodes().values()) if (!n.lane) owner.tile(world, n, left(n), rowTop(n.row));

        if (labelled()) {
            float lo = Float.MAX_VALUE;
            float hi = 0f;
            for (String id : result.singles()) {
                lo = Math.min(lo, left(result.nodes().get(id)));
                hi = Math.max(hi, left(result.nodes().get(id)) + m.tileW());
            }
            float row = rowTop(result.nodes().get(result.singles().get(0)).row);
            float lw = Math.max(hi - lo, 100f);
            Ui.text(world, Math.round((lo + hi - lw) / 2f), row - PetTree.LABEL_H, lw, "No family", Ui.gray(), Ui.CENTER);
        }

        element = column.end();
        CustomPanelAPI input = p.createCustomPanel(w - UiScroll.GUTTER, h, new Input());
        p.addComponent(input).inTL(x, y);
    }

    private boolean labelled() {
        return !result.singles().isEmpty() && (!result.families().isEmpty() || owner.labelAlone());
    }

    float rowTop(int row) {
        return top + row * pitch;
    }

    float left(Node n) {
        return PetTree.MARGIN + n.x - m.tileW() / 2f;
    }

    // the node under the screen point, lanes left out
    String pick(float mx, float my) {
        if (world == null || viewport == null) return null;
        if (mx < viewport.getX() || mx >= viewport.getX() + viewport.getWidth() || my < viewport.getY() || my >= viewport.getY() + viewport.getHeight()) return null;

        PositionAPI pos = world.getPosition();
        float wx = mx - pos.getX();
        float wy = pos.getY() + pos.getHeight() - my;
        for (Node n : result.nodes().values()) {
            if (n.lane) continue;
            float l = left(n);
            float t = rowTop(n.row);
            if (wx >= l && wx < l + m.tileW() && wy >= t && wy < t + owner.height(n)) return n.id;
        }
        return null;
    }

    private void paintLines(PositionAPI pos, float alpha) {
        for (Brood brood : result.broods()) brood(pos, brood, brood.dashed ? Ui.hl() : Ui.base(), alpha, null);

        Brood fixed = lit != null ? byKid.get(lit) : null;
        if (fixed != null) brood(pos, fixed, Ui.hl(), alpha, lit);
        Brood hover = hovered != null ? byKid.get(hovered) : null;
        if (hover != null) brood(pos, hover, Ui.hl(), alpha, hovered);
    }

    // the parents of the clicked young and of the one under the mouse, and the marked pets, get the gold border
    private void paintMarks(PositionAPI pos, float alpha) {
        Set<String> marks = new HashSet<>(gold);
        for (String kid : new String[]{lit, hovered}) {
            Brood brood = kid != null ? byKid.get(kid) : null;
            if (brood == null) continue;
            marks.add(brood.a);
            if (brood.b != null) marks.add(brood.b);
        }

        for (String id : marks) {
            Node n = result.nodes().get(id);
            if (n == null) continue;
            float h = owner.height(n);
            UiDraw.outline(pos.getX() + left(n), pos.getY() + pos.getHeight() - rowTop(n.row) - h, m.tileW(), h, Ui.hl(), alpha);
        }
    }

    // one line down from each parent to a bar, one from the bar to the bar of the young, one down to each young;
    // the older parent of a pair in two rows comes down its lane; only draws the line to that one young
    private void brood(PositionAPI pos, Brood brood, Color c, float alpha, String only) {
        Map<String, Node> nodes = result.nodes();
        boolean dashed = owner.dashed(brood);
        Node sa = PetTreeLayout.slot(brood, brood.a, nodes);
        Node sb = brood.b != null ? PetTreeLayout.slot(brood, brood.b, nodes) : null;
        int row = Math.max(sa.row, sb != null ? sb.row : sa.row);
        float pe = rowTop(row) + tileH;
        float gap = pitch - tileH;
        int k = brood.levels * 2 + 1;
        float l1 = pe + Math.round(gap * (brood.level + 1) / k);
        float l2 = pe + Math.round(gap * (brood.levels + brood.level + 1) / k);

        float xa = drop(pos, brood, brood.a, sa, sb, c, alpha, sb == null ? l2 : l1, dashed);
        float xb = sb != null ? drop(pos, brood, brood.b, sb, sa, c, alpha, l1, dashed) : xa;
        if (sb != null) seg(pos, xa, l1, xb, l1, c, alpha, dashed);
        if (brood.kids.isEmpty()) return;

        float xd = sb == null ? xa : Math.round((xa + xb) / 2f);
        if (sb != null) seg(pos, xd, l1, xd, l2, c, alpha, dashed);
        float lo = xd;
        float hi = xd;
        for (String kid : brood.kids) {
            if (only != null && !only.equals(kid)) continue;
            Node n = nodes.get(kid);
            float kx = PetTree.MARGIN + n.x;
            lo = Math.min(lo, kx);
            hi = Math.max(hi, kx);
            seg(pos, kx, l2, kx, rowTop(n.row), c, alpha, dashed);
        }
        seg(pos, lo, l2, hi, l2, c, alpha, dashed);
    }

    // the line from under one parent down to y; returns its x. a pet with several mates drops beside its centre, towards the mate
    private float drop(PositionAPI pos, Brood brood, String id, Node slot, Node other, Color c, float alpha, float y, boolean dashed) {
        Node parent = result.nodes().get(id);
        if (!id.equals(brood.older)) {
            float x = PetTree.MARGIN + parent.x;
            if (broodCount.getOrDefault(id, 0) > 1 && other != null && other.x != parent.x) x += Math.signum(other.x - parent.x) * m.drop();
            seg(pos, x, rowTop(parent.row) + owner.height(parent), x, y, c, alpha, dashed);
            return x;
        }

        Node first = brood.lane.get(0);
        float x = Math.abs(first.x - parent.x) <= m.tileW() / 2f - 4f ? PetTree.MARGIN + first.x : PetTree.MARGIN + parent.x;
        float from = rowTop(parent.row) + owner.height(parent);
        for (Node lane : brood.lane) {
            float lx = PetTree.MARGIN + lane.x;
            if (lx != x) {
                float jog = rowTop(lane.row) - PetTree.JOG;
                seg(pos, x, from, x, jog, c, alpha, dashed);
                seg(pos, x, jog, lx, jog, c, alpha, dashed);
                from = jog;
                x = lx;
            }
        }
        seg(pos, x, from, x, y, c, alpha, dashed);
        return x;
    }

    // 1 px line between two points of the world panel, y down
    private void seg(PositionAPI pos, float x1, float y1, float x2, float y2, Color c, float alpha, boolean dashed) {
        float left = pos.getX();
        float topY = pos.getY() + pos.getHeight();
        boolean flat = y1 == y2;
        float from = flat ? Math.min(x1, x2) : Math.min(y1, y2);
        float to = flat ? Math.max(x1, x2) + 1f : Math.max(y1, y2) + 1f;
        float step = dashed ? PetTree.DASH + PetTree.DASH_GAP : to - from;
        for (float s = from; s < to; s += step) {
            float len = Math.min(dashed ? PetTree.DASH : to - from, to - s);
            if (flat) UiDraw.quad(left + s, topY - y1 - 1f, len, 1f, c, alpha);
            else UiDraw.quad(left + x1, topY - s - len, 1f, len, c, alpha);
        }
    }

    // hover for the gold lines, drag on the empty tree to move it, a click on the empty tree goes to the owner
    private final class Input extends BaseCustomUIPanelPlugin {
        private boolean down = false;
        private boolean dragging = false;
        private float startMx;
        private float startMy;
        private float startX;
        private float startY;

        @Override
        public void positionChanged(PositionAPI position) {
            viewport = position;
        }

        @Override
        public void advance(float amount) {
            hovered = pick(Global.getSettings().getMouseX(), Global.getSettings().getMouseY());
            if (hovered != null && !owner.hovers(hovered)) hovered = null;
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (viewport == null) return;

            for (InputEventAPI event : events) {
                if (event.isLMBDownEvent() && viewport.containsEvent(event) && pick(event.getX(), event.getY()) == null) {
                    down = true;
                    startMx = event.getX();
                    startMy = event.getY();
                    startX = owner.getX();
                    ScrollPanelAPI scroller = element.getExternalScroller();
                    startY = scroller != null ? scroller.getYOffset() : 0f;
                } else if (event.isMouseMoveEvent() && down) {
                    if (!dragging && Math.abs(event.getX() - startMx) + Math.abs(event.getY() - startMy) > PetTree.DRAG) dragging = true;
                    if (!dragging) continue;
                    move(event.getX(), event.getY());
                    event.consume();
                } else if (event.isLMBUpEvent() && down) {
                    down = false;
                    if (dragging) {
                        dragging = false;
                        event.consume();
                    } else if (viewport.containsEvent(event) && pick(event.getX(), event.getY()) == null) owner.emptyClick();
                }
            }
        }

        private void move(float mx, float my) {
            float worldW = world.getPosition().getWidth();
            owner.setX(Math.max(0f, Math.min(worldW - viewW, startX - (mx - startMx))));
            world.getPosition().inTL(-owner.getX(), 0f);

            ScrollPanelAPI scroller = element.getExternalScroller();
            if (scroller == null) return;
            float max = Math.max(0f, element.getHeightSoFar() - viewH);
            scroller.setYOffset(Math.max(0f, Math.min(max, startY + (my - startMy))));
        }
    }
}
