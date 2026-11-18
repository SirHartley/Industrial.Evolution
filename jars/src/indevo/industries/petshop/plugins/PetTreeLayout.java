package indevo.industries.petshop.plugins;

import java.util.*;

// headless family tree layout, so PetChecks can run it: generations as rows, order by barycenter sweeps,
// x by least squares packing (pool adjacent violators), a pair in two rows joins through lane nodes in the rows between
public final class PetTreeLayout {

    public static final Metrics FULL = new Metrics(64f, 6f, 12f, 40f, 10f);
    public static final Metrics SMALL = new Metrics(34f, 4f, 8f, 24f, 6f);
    static final int SWEEPS = 4;
    static final float BAR_SLACK = 8f;
    static final String STUB = "stub:";
    static final String LANE = "lane:";
    // the stub of every gone pet with no family, in the full view
    public static final String SINGLES = STUB + "singles";

    public record Metrics(float tileW, float gap, float laneW, float familyGap, float drop) {
    }

    // one pet or stub; a and b are its parents, either null, and ignored when they are not in the set
    public record Item(String id, String a, String b) {
    }

    // two pets joined without young: the dashed pair of the pair view, or a stub folded with its mate
    public record Pair(String a, String b, boolean dashed) {
    }

    public static final class Node {
        public final String id;
        public final boolean lane;
        public final float w;
        public int row;
        public float x;
        public int family;
        final int index;
        float key;
        final List<Node> up = new ArrayList<>();
        final List<Node> down = new ArrayList<>();

        Node(String id, boolean lane, float w, int index) {
            this.id = id;
            this.lane = lane;
            this.w = w;
            this.index = index;
        }

        public boolean isStub() {
            return id.startsWith(STUB);
        }
    }

    // a pair and its young; b is null for young with one known parent, kids is empty for a Pair
    public static final class Brood {
        public final String a;
        public final String b;
        public final List<String> kids = new ArrayList<>();
        public final boolean dashed;
        // the older parent's line runs through these, one per row from the row under it to the row of the younger
        public final List<Node> lane = new ArrayList<>();
        public String older = null;
        public int level = 0;
        public int levels = 1;

        Brood(String a, String b, boolean dashed) {
            this.a = a;
            this.b = b;
            this.dashed = dashed;
        }

        public boolean has(String id) {
            return id.equals(a) || id.equals(b);
        }

        public String other(String id) {
            return id.equals(a) ? b : a;
        }
    }

    // rows: rows of the tallest family; families: their ids in drawing order, singles last
    public record Result(Map<String, Node> nodes, List<Brood> broods, int rows, float width, List<List<String>> families, List<String> singles) {
    }

    // the folded input: stubs in place of their members, by stub id
    public record Folded(List<Item> items, List<Pair> pairs, Map<String, List<String>> stubs) {
    }

    private PetTreeLayout() {
    }

    // a gone pet with no living descendant folds away with its branch into one stub per connected group; dead ancestors
    // of living pets stay. keep: never folded, with their ancestors; open: groups the player unfolded, by stub id
    public static Folded fold(List<Item> items, Set<String> gone, Set<String> keep, Set<String> open, boolean singles) {
        Map<String, Item> byId = index(items);
        Map<String, List<String>> kids = kids(items, byId);
        Map<String, Boolean> living = new HashMap<>();
        Set<String> kept = new HashSet<>();
        for (String id : keep) ancestors(id, byId, kept);

        Set<String> folded = new LinkedHashSet<>();
        for (Item item : items) {
            if (gone.contains(item.id()) && !kept.contains(item.id()) && !hasLiving(item.id(), kids, gone, living, new HashSet<>())) folded.add(item.id());
        }

        // groups over parent, young and mate links inside the folded set, in input order
        Map<String, String> root = new HashMap<>();
        for (String id : folded) root.put(id, id);
        for (Item item : items) {
            if (!folded.contains(item.id())) continue;
            String a = folded.contains(item.a()) ? item.a() : null;
            String b = folded.contains(item.b()) ? item.b() : null;
            if (a != null) union(root, item.id(), a);
            if (b != null) union(root, item.id(), b);
            if (item.a() != null && item.b() != null && folded.contains(item.a()) && folded.contains(item.b())) union(root, item.a(), item.b());
        }

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String id : folded) groups.computeIfAbsent(find(root, id), k -> new ArrayList<>()).add(id);

        List<String> lonely = new ArrayList<>();
        Map<String, List<String>> stubs = new LinkedHashMap<>();
        Map<String, String> stubOf = new HashMap<>();
        for (List<String> group : groups.values()) {
            String first = group.get(0);
            if (group.size() == 1 && kids.getOrDefault(first, List.of()).isEmpty() && parentsIn(byId.get(first), byId) == 0) {
                if (singles) lonely.add(first);
                continue;
            }

            String stub = STUB + first;
            if (open.contains(stub)) continue;
            stubs.put(stub, group);
            for (String id : group) stubOf.put(id, stub);
        }
        if (lonely.size() > 1 && !open.contains(SINGLES)) {
            stubs.put(SINGLES, lonely);
            for (String id : lonely) stubOf.put(id, SINGLES);
        }

        List<Item> out = new ArrayList<>();
        List<Pair> pairs = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (Item item : items) {
            String stub = stubOf.get(item.id());
            if (stub == null) {
                out.add(item);
                continue;
            }
            if (!placed.add(stub)) continue;

            Item top = stubTop(stubs.get(stub), byId, stubOf, stub);
            if (top != null) {
                out.add(new Item(stub, top.a(), top.b()));
                continue;
            }

            out.add(new Item(stub, null, null));
            String mate = stubMate(items, byId, stubOf, stub);
            if (mate != null) pairs.add(new Pair(mate, stub, false));
        }
        return new Folded(out, pairs, stubs);
    }

    // the stub hangs under the parents of its first member with a parent outside it
    private static Item stubTop(List<String> group, Map<String, Item> byId, Map<String, String> stubOf, String stub) {
        for (String id : group) {
            Item item = byId.get(id);
            String a = item.a() != null && byId.containsKey(item.a()) && !stub.equals(stubOf.get(item.a())) ? item.a() : null;
            String b = item.b() != null && byId.containsKey(item.b()) && !stub.equals(stubOf.get(item.b())) ? item.b() : null;
            if (a != null || b != null) return new Item(id, a, b);
        }
        return null;
    }

    // else it stands next to the first mate outside it
    private static String stubMate(List<Item> items, Map<String, Item> byId, Map<String, String> stubOf, String stub) {
        for (Item item : items) {
            boolean a = stub.equals(stubOf.get(item.a()));
            boolean b = stub.equals(stubOf.get(item.b()));
            if (a && !b && byId.containsKey(item.b())) return stubOf.getOrDefault(item.b(), item.b());
            if (b && !a && byId.containsKey(item.a())) return stubOf.getOrDefault(item.a(), item.a());
        }
        return null;
    }

    private static boolean hasLiving(String id, Map<String, List<String>> kids, Set<String> gone, Map<String, Boolean> memo, Set<String> path) {
        Boolean known = memo.get(id);
        if (known != null) return known;
        if (!path.add(id)) return false;

        boolean living = false;
        for (String kid : kids.getOrDefault(id, List.of())) {
            if (!gone.contains(kid) || hasLiving(kid, kids, gone, memo, path)) {
                living = true;
                break;
            }
        }
        path.remove(id);
        memo.put(id, living);
        return living;
    }

    private static void ancestors(String id, Map<String, Item> byId, Set<String> out) {
        if (id == null || !byId.containsKey(id) || !out.add(id)) return;
        ancestors(byId.get(id).a(), byId, out);
        ancestors(byId.get(id).b(), byId, out);
    }

    // the pets connected to id over parent links, in input order
    public static List<Item> family(List<Item> items, String id) {
        Map<String, Item> byId = index(items);
        Map<String, List<String>> kids = kids(items, byId);
        Set<String> seen = new HashSet<>();
        Deque<String> open = new ArrayDeque<>();
        if (byId.containsKey(id)) open.add(id);

        while (!open.isEmpty()) {
            String at = open.poll();
            if (!seen.add(at)) continue;
            Item item = byId.get(at);
            if (byId.containsKey(item.a())) open.add(item.a());
            if (byId.containsKey(item.b())) open.add(item.b());
            open.addAll(kids.getOrDefault(at, List.of()));
        }

        List<Item> out = new ArrayList<>();
        for (Item item : items) if (seen.contains(item.id())) out.add(item);
        return out;
    }

    // alignBottom puts the youngest row of every family on one line, as the full view does
    public static Result layout(List<Item> items, List<Pair> pairs, Metrics m, boolean alignBottom) {
        Map<String, Item> byId = index(items);
        Map<String, Node> nodes = new LinkedHashMap<>();
        Map<String, String[]> par = new HashMap<>();
        int index = 0;
        for (Item item : items) {
            nodes.put(item.id(), new Node(item.id(), false, m.tileW(), index++));
            String a = byId.containsKey(item.a()) && !item.a().equals(item.id()) ? item.a() : null;
            String b = byId.containsKey(item.b()) && !item.b().equals(item.id()) && !item.b().equals(a) ? item.b() : null;
            par.put(item.id(), a == null ? new String[]{b, null} : new String[]{a, b});
        }

        List<Brood> broods = broods(items, par, pairs, nodes);
        List<List<String>> families = new ArrayList<>();
        List<String> singles = new ArrayList<>();
        for (List<String> family : components(items, broods)) {
            if (family.size() == 1 && !hasBrood(broods, family.get(0))) singles.add(family.get(0));
            else families.add(family);
        }

        Map<String, Integer> rows = new HashMap<>();
        int tallest = 0;
        for (List<String> family : families) tallest = Math.max(tallest, rows(family, par, broods, rows));
        int last = tallest;

        float offset = 0f;
        List<Node> all = new ArrayList<>();
        for (int f = 0; f < families.size(); f++) {
            List<String> family = families.get(f);
            int max = 0;
            for (String id : family) max = Math.max(max, rows.get(id));
            int shift = alignBottom ? last - max : 0;
            for (String id : family) nodes.get(id).row = rows.get(id) + shift;

            List<Node> members = new ArrayList<>();
            for (String id : family) members.add(nodes.get(id));
            List<Node> lanes = lanes(broods, family, nodes, f);
            members.addAll(lanes);
            for (Node n : members) n.family = f;

            float width = place(members, broods, nodes, m);
            for (Node n : members) n.x += offset;
            offset += width + m.familyGap();
            all.addAll(members);
            for (Node n : lanes) nodes.put(n.id, n);
        }

        // pets with no family stand side by side in the youngest row, after the families
        float right = Math.max(0f, offset - m.familyGap());
        float sx = offset;
        for (String id : singles) {
            Node n = nodes.get(id);
            n.row = families.isEmpty() ? 0 : last;
            n.family = -1;
            n.x = sx + m.tileW() / 2f;
            right = sx + m.tileW();
            sx += m.tileW() + m.gap();
        }

        levels(broods, nodes);
        int rowCount = 0;
        for (Node n : nodes.values()) rowCount = Math.max(rowCount, n.row + 1);
        return new Result(nodes, broods, rowCount, right, families, singles);
    }

    private static boolean hasBrood(List<Brood> broods, String id) {
        for (Brood brood : broods) if (brood.has(id) || brood.kids.contains(id)) return true;
        return false;
    }

    private static List<Brood> broods(List<Item> items, Map<String, String[]> par, List<Pair> pairs, Map<String, Node> nodes) {
        Map<String, Brood> byPair = new LinkedHashMap<>();
        for (Item item : items) {
            String[] p = par.get(item.id());
            if (p[0] == null) continue;

            boolean swap = p[1] != null && nodes.get(p[1]).index < nodes.get(p[0]).index;
            String a = swap ? p[1] : p[0];
            String b = swap ? p[0] : p[1];
            byPair.computeIfAbsent(a + "|" + b, k -> new Brood(a, b, false)).kids.add(item.id());
        }

        List<Brood> out = new ArrayList<>(byPair.values());
        for (Pair pair : pairs) {
            if (!nodes.containsKey(pair.a()) || !nodes.containsKey(pair.b()) || pair.a().equals(pair.b())) continue;
            if (byPair.containsKey(pair.a() + "|" + pair.b()) || byPair.containsKey(pair.b() + "|" + pair.a())) continue;
            out.add(new Brood(pair.a(), pair.b(), pair.dashed()));
        }
        return out;
    }

    private static List<List<String>> components(List<Item> items, List<Brood> broods) {
        Map<String, String> root = new HashMap<>();
        for (Item item : items) root.put(item.id(), item.id());
        for (Brood brood : broods) {
            if (brood.b != null) union(root, brood.a, brood.b);
            for (String kid : brood.kids) union(root, brood.a, kid);
        }

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Item item : items) groups.computeIfAbsent(find(root, item.id()), k -> new ArrayList<>()).add(item.id());
        return new ArrayList<>(groups.values());
    }

    // generation rows from 0: one under the younger parent; a pet with no parents one above its first young, or beside its mate
    private static int rows(List<String> family, Map<String, String[]> par, List<Brood> broods, Map<String, Integer> rows) {
        Map<String, Integer> raw = new HashMap<>();
        for (String id : family) row(id, par, raw, new HashSet<>());

        Map<String, Integer> pulled = new HashMap<>();
        for (Brood brood : broods) {
            if (brood.kids.isEmpty() || !family.contains(brood.a)) continue;
            int kidRow = Integer.MAX_VALUE;
            for (String kid : brood.kids) kidRow = Math.min(kidRow, raw.get(kid));
            for (String parent : new String[]{brood.a, brood.b}) {
                if (parent != null && par.get(parent)[0] == null) pulled.merge(parent, kidRow - 1, Math::min);
            }
        }
        for (Map.Entry<String, Integer> e : pulled.entrySet()) raw.put(e.getKey(), e.getValue());

        for (Brood brood : broods) {
            if (!brood.kids.isEmpty() || brood.b == null || !family.contains(brood.a)) continue;
            String a = brood.a;
            String b = brood.b;
            if (par.get(b)[0] == null && !pulled.containsKey(b)) raw.put(b, raw.get(a));
            else if (par.get(a)[0] == null && !pulled.containsKey(a)) raw.put(a, raw.get(b));
        }

        int min = Integer.MAX_VALUE;
        for (String id : family) min = Math.min(min, raw.get(id));
        int max = 0;
        for (String id : family) {
            rows.put(id, raw.get(id) - min);
            max = Math.max(max, raw.get(id) - min);
        }
        return max;
    }

    // a pet is never its own ancestor, the path guard only keeps bad data from looping
    private static int row(String id, Map<String, String[]> par, Map<String, Integer> memo, Set<String> path) {
        Integer known = memo.get(id);
        if (known != null) return known;
        if (!path.add(id)) return 0;

        int row = 0;
        for (String parent : par.get(id)) if (parent != null) row = Math.max(row, row(parent, par, memo, path) + 1);
        path.remove(id);
        memo.put(id, row);
        return row;
    }

    private static List<Node> lanes(List<Brood> broods, List<String> family, Map<String, Node> nodes, int f) {
        List<Node> lanes = new ArrayList<>();
        int n = 0;
        for (Brood brood : broods) {
            if (brood.b == null || !family.contains(brood.a)) continue;

            Node a = nodes.get(brood.a);
            Node b = nodes.get(brood.b);
            if (a.row == b.row) continue;

            Node older = a.row < b.row ? a : b;
            Node younger = older == a ? b : a;
            brood.older = older.id;
            Node prev = older;
            for (int r = older.row + 1; r <= younger.row; r++) {
                Node lane = new Node(LANE + f + ":" + n++ + ":" + r, true, 0f, Integer.MAX_VALUE);
                lane.row = r;
                link(prev, lane);
                brood.lane.add(lane);
                lanes.add(lane);
                prev = lane;
            }
        }
        return lanes;
    }

    private static void link(Node up, Node down) {
        up.down.add(down);
        down.up.add(up);
    }

    // the node a young hangs from in the row of the younger parent: the parent, or the end of the older parent's lane
    static Node slot(Brood brood, String parent, Map<String, Node> nodes) {
        return parent.equals(brood.older) ? brood.lane.get(brood.lane.size() - 1) : nodes.get(parent);
    }

    // width of the family; x of every member from 0
    private static float place(List<Node> members, List<Brood> broods, Map<String, Node> nodes, Metrics m) {
        int rowCount = 0;
        int base = Integer.MAX_VALUE;
        for (Node n : members) {
            rowCount = Math.max(rowCount, n.row + 1);
            base = Math.min(base, n.row);
        }

        Set<Node> in = new HashSet<>(members);
        Map<Node, Node> partner = new HashMap<>();
        Map<Node, List<Node>> mates = new HashMap<>();
        for (Brood brood : broods) {
            if (!in.contains(nodes.get(brood.a))) continue;
            for (String kid : brood.kids) {
                Node k = nodes.get(kid);
                link(slot(brood, brood.a, nodes), k);
                if (brood.b != null) link(slot(brood, brood.b, nodes), k);
            }
            if (brood.b != null) {
                Node sa = slot(brood, brood.a, nodes);
                Node sb = slot(brood, brood.b, nodes);
                partner.putIfAbsent(sa, sb);
                partner.putIfAbsent(sb, sa);
                mates.computeIfAbsent(sa, k -> new ArrayList<>()).add(sb);
                mates.computeIfAbsent(sb, k -> new ArrayList<>()).add(sa);
            }
        }

        List<List<Node>> rows = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) rows.add(new ArrayList<>());
        for (Node n : members) rows.get(n.row).add(n);
        for (List<Node> row : rows) row.sort(Comparator.comparingInt(n -> n.index));

        for (int s = 0; s < SWEEPS; s++) {
            for (int r = base + 1; r < rowCount; r++) order(rows.get(r), true, mates);
            for (int r = rowCount - 2; r >= base; r--) order(rows.get(r), false, mates);
        }
        for (int r = base + 1; r < rowCount; r++) order(rows.get(r), true, mates);

        for (List<Node> row : rows) {
            float x = 0f;
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) x += sep(row.get(i - 1), row.get(i), m);
                row.get(i).x = x;
            }
        }

        for (int s = 0; s < SWEEPS; s++) {
            for (int r = base + 1; r < rowCount; r++) pack(rows.get(r), true, partner, m);
            for (int r = rowCount - 2; r >= base; r--) pack(rows.get(r), false, partner, m);
        }
        for (int r = base + 1; r < rowCount; r++) pack(rows.get(r), true, partner, m);

        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (Node n : members) {
            left = Math.min(left, n.x - half(n, m));
            right = Math.max(right, n.x + half(n, m));
        }
        for (Node n : members) n.x = Math.round(n.x - left);
        return Math.round(right - left);
    }

    // barycenter of the neighbours above (down sweep) or below (up sweep); a pet with none stands beside a mate in the row,
    // on the side away from the mate's siblings, a second mate on the other side
    private static void order(List<Node> row, boolean downSweep, Map<Node, List<Node>> mates) {
        for (int i = 0; i < row.size(); i++) row.get(i).key = i;
        Map<Node, Float> keys = new HashMap<>();
        for (Node n : row) {
            List<Node> near = downSweep ? n.up : n.down;
            if (near.isEmpty()) continue;
            float sum = 0f;
            for (Node o : near) sum += o.key;
            keys.put(n, sum / near.size());
        }

        Map<Node, List<Node>> attached = new HashMap<>();
        List<Node> byIndex = new ArrayList<>(row);
        byIndex.sort(Comparator.comparingInt(n -> n.index));
        for (Node n : byIndex) {
            if (keys.containsKey(n) || attached.values().stream().anyMatch(l -> l.contains(n))) continue;
            Node to = null;
            for (Node mate : mates.getOrDefault(n, List.of())) {
                if (mate.row == n.row && row.contains(mate) && (keys.containsKey(mate) || attachedTo(attached, mate) != null)) {
                    to = mate;
                    break;
                }
            }
            if (to == null) keys.put(n, n.key);
            else attached.computeIfAbsent(to, k -> new ArrayList<>()).add(n);
        }

        List<Node> base = new ArrayList<>(keys.keySet());
        base.sort(Comparator.comparingDouble((Node n) -> keys.get(n)).thenComparingInt(n -> n.index));
        row.clear();
        for (int i = 0; i < base.size(); i++) {
            Node n = base.get(i);
            boolean sameBefore = i > 0 && keys.get(base.get(i - 1)).equals(keys.get(n));
            boolean sameAfter = i < base.size() - 1 && keys.get(base.get(i + 1)).equals(keys.get(n));
            emit(n, attached, row, !sameBefore || sameAfter);
        }
        for (int i = 0; i < row.size(); i++) row.get(i).key = i;
    }

    private static Node attachedTo(Map<Node, List<Node>> attached, Node n) {
        for (Map.Entry<Node, List<Node>> e : attached.entrySet()) if (e.getValue().contains(n)) return e.getKey();
        return null;
    }

    private static void emit(Node n, Map<Node, List<Node>> attached, List<Node> out, boolean leftFirst) {
        List<Node> mates = attached.getOrDefault(n, List.of());
        List<Node> left = new ArrayList<>();
        List<Node> right = new ArrayList<>();
        for (int j = 0; j < mates.size(); j++) ((j % 2 == 0) == leftFirst ? left : right).add(mates.get(j));
        Collections.reverse(left);
        for (Node m : left) emit(m, attached, out, true);
        out.add(n);
        for (Node m : right) emit(m, attached, out, false);
    }

    // targets from the neighbours, then the closest x to them that keeps every gap
    private static void pack(List<Node> row, boolean downSweep, Map<Node, Node> partner, Metrics m) {
        int n = row.size();
        if (n == 0) return;

        float[] t = new float[n];
        boolean[] set = new boolean[n];
        for (int i = 0; i < n; i++) {
            Node node = row.get(i);
            List<Node> near = downSweep ? node.up : node.down;
            if (near.isEmpty()) continue;

            float sum = 0f;
            for (Node o : near) sum += o.x;
            t[i] = sum / near.size();
            Node p = partner.get(node);
            if (!downSweep && p != null && p.row == node.row && !node.lane && p.down.containsAll(node.down)) {
                t[i] += (row.indexOf(p) > i ? -0.5f : 0.5f) * sep(node, p, m);
            }
            set[i] = true;
        }
        for (int i = 0; i < n; i++) {
            if (set[i]) continue;
            Node p = partner.get(row.get(i));
            int j = p == null ? -1 : row.indexOf(p);
            t[i] = j >= 0 && set[j] ? t[j] + (j > i ? -1f : 1f) * sep(row.get(i), p, m) : row.get(i).x;
        }

        float[] s = new float[n];
        for (int i = 1; i < n; i++) s[i] = s[i - 1] + sep(row.get(i - 1), row.get(i), m);
        float[] y = isotonic(t, s);
        for (int i = 0; i < n; i++) row.get(i).x = y[i] + s[i];
    }

    // least squares non decreasing fit of t - s, pool adjacent violators
    private static float[] isotonic(float[] t, float[] s) {
        int n = t.length;
        float[] mean = new float[n];
        int[] size = new int[n];
        int blocks = 0;
        for (int i = 0; i < n; i++) {
            mean[blocks] = t[i] - s[i];
            size[blocks] = 1;
            blocks++;
            while (blocks > 1 && mean[blocks - 2] > mean[blocks - 1]) {
                int total = size[blocks - 2] + size[blocks - 1];
                mean[blocks - 2] = (mean[blocks - 2] * size[blocks - 2] + mean[blocks - 1] * size[blocks - 1]) / total;
                size[blocks - 2] = total;
                blocks--;
            }
        }

        float[] out = new float[n];
        int i = 0;
        for (int b = 0; b < blocks; b++) for (int k = 0; k < size[b]; k++) out[i++] = mean[b];
        return out;
    }

    static float sep(Node a, Node b, Metrics m) {
        return half(a, m) + half(b, m) + m.gap();
    }

    static float half(Node n, Metrics m) {
        return (n.lane ? m.laneW() : n.w) / 2f;
    }

    // bars that share a gap and overlap get their own level, so two pairs never share one bar
    private static void levels(List<Brood> broods, Map<String, Node> nodes) {
        Map<String, List<Brood>> gaps = new HashMap<>();
        Map<Brood, float[]> spans = new HashMap<>();
        for (Brood brood : broods) {
            Node sa = slot(brood, brood.a, nodes);
            Node sb = brood.b != null ? slot(brood, brood.b, nodes) : sa;
            float lo = Math.min(sa.x, sb.x);
            float hi = Math.max(sa.x, sb.x);
            for (String kid : brood.kids) {
                lo = Math.min(lo, nodes.get(kid).x);
                hi = Math.max(hi, nodes.get(kid).x);
            }
            spans.put(brood, new float[]{lo, hi});
            int row = Math.max(sa.row, sb.row);
            gaps.computeIfAbsent(sa.family + ":" + row, k -> new ArrayList<>()).add(brood);
        }

        for (List<Brood> gap : gaps.values()) {
            gap.sort(Comparator.comparingDouble(b -> spans.get(b)[0]));
            List<Float> ends = new ArrayList<>();
            for (Brood brood : gap) {
                float[] span = spans.get(brood);
                int level = 0;
                while (level < ends.size() && ends.get(level) + BAR_SLACK > span[0]) level++;
                if (level == ends.size()) ends.add(span[1]);
                else ends.set(level, span[1]);
                brood.level = level;
            }
            for (Brood brood : gap) brood.levels = ends.size();
        }
    }

    private static Map<String, Item> index(List<Item> items) {
        Map<String, Item> byId = new LinkedHashMap<>();
        for (Item item : items) byId.put(item.id(), item);
        return byId;
    }

    private static Map<String, List<String>> kids(List<Item> items, Map<String, Item> byId) {
        Map<String, List<String>> kids = new HashMap<>();
        for (Item item : items) {
            if (byId.containsKey(item.a())) kids.computeIfAbsent(item.a(), k -> new ArrayList<>()).add(item.id());
            if (byId.containsKey(item.b()) && !Objects.equals(item.a(), item.b())) kids.computeIfAbsent(item.b(), k -> new ArrayList<>()).add(item.id());
        }
        return kids;
    }

    private static int parentsIn(Item item, Map<String, Item> byId) {
        return (byId.containsKey(item.a()) ? 1 : 0) + (byId.containsKey(item.b()) ? 1 : 0);
    }

    private static String find(Map<String, String> root, String id) {
        String r = id;
        while (!root.get(r).equals(r)) r = root.get(r);
        root.put(id, r);
        return r;
    }

    private static void union(Map<String, String> root, String a, String b) {
        String ra = find(root, a);
        String rb = find(root, b);
        if (!ra.equals(rb)) root.put(rb, ra);
    }
}
