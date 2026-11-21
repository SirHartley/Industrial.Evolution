package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.industries.petshop.plugins.PetTreeLayout.Brood;
import indevo.industries.petshop.plugins.PetTreeLayout.Item;
import indevo.industries.petshop.plugins.PetTreeLayout.Node;
import indevo.utils.ui.*;

import java.awt.Color;
import java.util.*;

// the Family tree tab: one family, a pair opened from the cross, or every family of a species; the clicked pet's card on the right
public class PetTree {

    static final float CONTROL_Y = PetsPanel.BODY_Y + Ui.HEADING_H + 8f;
    static final float TREE_Y = CONTROL_Y + UiFilter.H + 10f;
    static final float LEFT_W = PetsPanel.LIST_W - 10f;
    static final float FIND_W = 170f;
    static final float FULL_W = 84f;
    static final float SMALL_W = 96f;
    static final float WIDE_W = 88f;
    static final float WIDE_GAP = 6f;
    // sector persistent data, a Boolean: no class in the save
    static final String WIDE_KEY = "IndEvo_petTreeWide";
    static final float SPECIES_COUNT_X = 30f;
    static final float SPECIES_GAP = 4f;
    static final float TOGGLE_GAP = 12f;
    static final float TILE_H = 94f;
    static final float PORTRAIT = 48f;
    static final float SMALL_PORTRAIT = 30f;
    static final float ROW_PITCH = 128f;
    static final float SMALL_PITCH = 64f;
    static final float STUB_H = 34f;
    // inside the scroll area: a tile at x 0 would lose its left border to the clip
    static final float MARGIN = 4f;
    static final float LABEL_H = 22f;
    static final float HEART = 9f;
    static final float HEART_GAP = 2f;
    static final float SHEET_W = 330f;
    static final float SHEET_ROW = 18f;
    static final float KIN_ROW = 30f;
    static final float KIN_ICON = 26f;
    static final float BACK_W = 140f;
    static final float DRAG = 4f;
    static final float DASH = 4f;
    static final float DASH_GAP = 3f;
    static final float JOG = 3f;
    static final Color STUB_EDGE = new Color(140, 150, 160, 140);

    private final PetsPanel panel;
    private final PetsView view;
    private final PetStatusManager manager = PetStatusManager.getInstance();

    record Row(String grade, String name, String from, Color color) {
    }

    PetTree(PetsPanel panel, PetsView view) {
        this.panel = panel;
        this.view = view;
    }

    // the first pet whose name starts with the typed text, else the first that has it anywhere; gone pets too
    static void find(PetsView view) {
        Pet match = match(view.find.query());
        if (match == null) return;

        view.treeRoot = match.id;
        view.treeCard = match.id;
        view.treePair.clear();
        if (view.treeFull) view.treeSpecies = match.typeID;
        view.treeX = 0f;
    }

    private static Pet match(String query) {
        if (query.isEmpty()) return null;

        List<Pet> pets = PetStatusManager.getInstance().getPets();
        for (Pet pet : pets) if (pet.name.toLowerCase(Locale.ROOT).startsWith(query)) return pet;
        for (Pet pet : pets) if (pet.name.toLowerCase(Locale.ROOT).contains(query)) return pet;
        return null;
    }

    void build(CustomPanelAPI p, float w, float h) {
        Pet root = root();
        if (root == null) {
            Ui.text(p, 0f, PetsPanel.BODY_Y, w, "No pets yet.", Ui.gray());
            return;
        }

        if (view.treeFull) full(p, w, h, root);
        else family(p, w, h, root);
    }

    private Pet root() {
        Pet root = manager.get(view.treeRoot);
        if (root == null) root = manager.get(view.selected);
        if (root == null && !manager.getPets().isEmpty()) root = manager.getPets().get(0);
        view.treeRoot = root == null ? null : root.id;

        if (view.treePair.size() != 2 || pair().size() != 2) view.treePair.clear();
        return root;
    }

    private List<Pet> pair() {
        List<Pet> pair = new ArrayList<>();
        for (String id : view.treePair) {
            Pet pet = manager.get(id);
            if (pet != null) pair.add(pet);
        }
        return pair;
    }

    private void family(CustomPanelAPI p, float w, float h, Pet root) {
        List<Pet> pair = pair();
        List<Item> species = items(root.typeID);
        Set<String> members = new HashSet<>();
        for (Item item : PetTreeLayout.family(species, root.id)) members.add(item.id());
        if (!pair.isEmpty()) for (Item item : PetTreeLayout.family(species, pair.get(1).id)) members.add(item.id());

        List<Item> items = new ArrayList<>();
        for (Item item : species) if (members.contains(item.id())) items.add(item);

        Set<String> keep = new HashSet<>(List.of(root.id));
        if (view.treeCard != null) keep.add(view.treeCard);
        keep.addAll(view.treePair);
        PetTreeLayout.Folded folded = PetTreeLayout.fold(items, gone(items), keep, view.unfolded, false);
        List<PetTreeLayout.Pair> pairs = new ArrayList<>(folded.pairs());
        if (!pair.isEmpty()) pairs.add(new PetTreeLayout.Pair(pair.get(0).id, pair.get(1).id, true));
        PetTreeLayout.Result result = PetTreeLayout.layout(folded.items(), pairs, metrics(), false);

        Set<String> shared = new HashSet<>();
        if (pair.isEmpty()) Ui.heading(p, 0f, PetsPanel.BODY_Y, LEFT_W, "Family of " + root.name, root.getData().species);
        else {
            Pet a = pair.get(0);
            Pet b = pair.get(1);
            String inbreeding = percent(PetShop.getInbreeding(a, b));
            Ui.heading(p, 0f, PetsPanel.BODY_Y, LEFT_W, "Family of " + a.name + " and " + b.name, "Inbreeding " + inbreeding, inbreeding);
            shared.addAll(shared(a, b).keySet());
            shared.remove(a.id);
            shared.remove(b.id);
        }
        control(p, LEFT_W, root);

        Set<String> focus = new HashSet<>(view.treePair);
        Pet card = manager.get(view.treeCard);
        if (card != null && !result.nodes().containsKey(card.id)) card = null;
        if (card != null) focus.add(card.id);
        else if (pair.isEmpty()) focus.add(root.id);

        if (result.families().isEmpty() && folded.stubs().isEmpty()) alone(p, root, focus.contains(root.id));
        else canvas(result, metrics(), false, folded.stubs(), focus, shared).draw(p, 0f, TREE_Y, PetsPanel.LIST_W, h - TREE_Y);

        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        float cw = w - PetsPanel.CARD_X;
        float ch = h - PetsPanel.BODY_Y;
        if (!pair.isEmpty() && card == null) kinCard(Ui.panel(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, cw, ch), cw, ch, pair.get(0), pair.get(1));
        else new PetCard(panel, view).build(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, cw, ch, card != null ? card : root);
    }

    // a pet with no parents, mates or young in the registry
    private void alone(CustomPanelAPI p, Pet pet, boolean focus) {
        float x = Math.round((LEFT_W - metrics().tileW()) / 2f);
        tile(p, x, TREE_Y, pet, focus, false);
        Ui.text(p, 0f, TREE_Y + TILE_H + 12f, LEFT_W, "No known family.", Ui.gray(), Ui.CENTER);
    }

    private void full(CustomPanelAPI p, float w, float h, Pet root) {
        List<String> kinds = species();
        if (!kinds.contains(view.treeSpecies)) view.treeSpecies = root.typeID;
        List<Item> items = items(view.treeSpecies);

        Pet card = manager.get(view.treeCard);
        if (card != null && !card.typeID.equals(view.treeSpecies)) card = null;
        Set<String> keep = card != null ? Set.of(card.id) : Set.of();
        PetTreeLayout.Folded folded = PetTreeLayout.fold(items, gone(items), keep, view.unfolded, true);
        PetTreeLayout.Metrics m = view.treeSmall ? PetTreeLayout.SMALL : metrics();
        PetTreeLayout.Result result = PetTreeLayout.layout(folded.items(), folded.pairs(), m, true);
        if (card != null && !result.nodes().containsKey(card.id)) card = null;

        float tw = card != null ? LEFT_W : w;
        int families = result.families().size();
        String species = PetDataRepo.get(view.treeSpecies).species;
        Ui.heading(p, 0f, PetsPanel.BODY_Y, tw, species, families + (families == 1 ? " family, " : " families, ") + items.size() + (items.size() == 1 ? " pet" : " pets"));
        control(p, tw, root);

        Set<String> focus = card != null ? Set.of(card.id) : Set.of();
        canvas(result, m, view.treeSmall, folded.stubs(), focus, Set.of()).draw(p, 0f, TREE_Y, card != null ? PetsPanel.LIST_W : w, h - TREE_Y);
        if (card == null) return;

        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        new PetCard(panel, view).build(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, w - PetsPanel.CARD_X, h - PetsPanel.BODY_Y, card);
    }

    // Find a pet; in the full view also the small tiles and the species, then the Full view toggle at the right end
    private void control(CustomPanelAPI p, float w, Pet root) {
        float y = CONTROL_Y;
        UiFilter.field(p, 2f, y, FIND_W, view.find, "Find a pet");
        String query = view.find.query();
        float fx = 2f + FIND_W + TOGGLE_GAP;
        float x = w - 2f - FULL_W;
        boolean missing = !query.isEmpty() && match(query) == null;

        toggle(p, x, y, FULL_W, "Full view", view.treeFull, () -> {
            view.treeFull = !view.treeFull;
            if (!view.treeFull) {
                Pet card = manager.get(view.treeCard);
                if (card != null) view.treeRoot = card.id;
                view.treePair.clear();
            } else {
                view.treeSpecies = root.typeID;
                view.treeCard = null;
            }
            moved();
        });
        if (!view.treeFull) {
            wideToggle(p, x - TOGGLE_GAP - WIDE_W, y);
            if (missing) noMatch(p, fx, y, x - TOGGLE_GAP - WIDE_W);
            return;
        }

        List<String> kinds = species();
        Map<String, Integer> counts = new HashMap<>();
        for (String kind : kinds) {
            int count = 0;
            for (Pet pet : manager.getPets()) if (pet.typeID.equals(kind) && !pet.isDead()) count++;
            counts.put(kind, count);
        }
        float sw = speciesW(counts.values());
        float pitch = sw + SPECIES_GAP;
        float room = x - TOGGLE_GAP - (fx + 200f) - SMALL_W - WIDE_GAP - WIDE_W - TOGGLE_GAP;
        int fit = Math.max(1, (int) Math.floor((room + SPECIES_GAP) / pitch));
        if (kinds.size() > fit) {
            kinds = new ArrayList<>(kinds.subList(0, fit));
            if (!kinds.contains(view.treeSpecies)) kinds.set(fit - 1, view.treeSpecies);
        }

        float sx = x - TOGGLE_GAP - kinds.size() * pitch + SPECIES_GAP;
        float smallX = sx - 2f - SMALL_W;
        for (String kind : kinds) {
            PetData data = PetDataRepo.get(kind);
            int count = counts.get(kind);
            speciesToggle(p, sx, y, sw, data, count, Ui.hl(), kind.equals(view.treeSpecies), data.species + ": " + count + (count == 1 ? " living pet." : " living pets."), () -> {
                view.treeSpecies = kind;
                view.treeCard = null;
                moved();
            });
            sx += pitch;
        }

        toggle(p, smallX, y, SMALL_W, "Small tiles", view.treeSmall, () -> {
            view.treeSmall = !view.treeSmall;
            if (view.treeSmall) setWide(false);
            moved();
        });
        wideToggle(p, smallX - WIDE_GAP - WIDE_W, y);
        if (missing) noMatch(p, fx, y, smallX - WIDE_GAP - WIDE_W);
    }

    // only when the whole line fits left of the first toggle on its right
    private static void noMatch(CustomPanelAPI p, float x, float y, float right) {
        String text = "No pet of that name.";
        float w = Math.min(200f, right - TOGGLE_GAP - x);
        if (Ui.width(text, Ui.INS15) > w - 2f) return;
        Ui.textIn(p, x, y, w, UiFilter.H, text, Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
    }

    // off by default; small tiles have no name, so the two exclude each other
    private void wideToggle(CustomPanelAPI p, float x, float y) {
        toggle(p, x, y, WIDE_W, "Wide tiles", wide(), () -> {
            setWide(!wide());
            if (wide()) view.treeSmall = false;
            moved();
        });
    }

    // the tile size of the Family tree and the Planner, kept per game
    static PetTreeLayout.Metrics metrics() {
        return wide() ? PetTreeLayout.WIDE : PetTreeLayout.FULL;
    }

    static boolean wide() {
        return Boolean.TRUE.equals(Global.getSector().getPersistentData().get(WIDE_KEY));
    }

    private static void setWide(boolean on) {
        if (on) Global.getSector().getPersistentData().put(WIDE_KEY, true);
        else Global.getSector().getPersistentData().remove(WIDE_KEY);
    }

    // width of a species toggle: the icon, then room for the widest count
    static float speciesW(Collection<Integer> counts) {
        float countW = 0f;
        for (int count : counts) countW = Math.max(countW, (float) Math.ceil(Ui.width(String.valueOf(count), Ui.ORB12)) + 2f);
        return SPECIES_COUNT_X + countW + 8f;
    }

    // icon and count on an area toggle; the tooltip only as wide as its line, a 360 px one runs past the panel edge and is clipped there
    static void speciesToggle(CustomPanelAPI p, float x, float y, float w, PetData data, int count, Color countColor, boolean on, String tip, Runnable action) {
        ButtonAPI button = UiButtons.area(p, x, y, w, UiFilter.H, null, on, true, null, Ui.CENTER, action);
        Ui.tooltip(p, button, (float) Math.ceil(Ui.width(tip, Ui.INS15)) + 10f, TooltipMakerAPI.TooltipLocation.BELOW, t -> t.addPara(tip, 0f));
        UiImages.img(p, x + 5f, y + 3f, 20f, 20f, data.icon);
        Ui.textIn(p, x + SPECIES_COUNT_X, y, w - SPECIES_COUNT_X - 8f, UiFilter.H, String.valueOf(count), Ui.ORB12, countColor, Ui.LEFT, new String[0], new Color[0]);
    }

    private static void toggle(CustomPanelAPI p, float x, float y, float w, String label, boolean on, Runnable action) {
        UiButtons.area(p, x, y, w, UiFilter.H, label, on, true, null, Ui.CENTER, action);
    }

    private void moved() {
        view.treeX = 0f;
        view.scroll.reset(PetsView.TREE);
        panel.refresh();
    }

    private List<Item> items(String typeId) {
        List<Item> items = new ArrayList<>();
        for (Pet pet : manager.getPets()) if (pet.typeID.equals(typeId)) items.add(new Item(pet.id, pet.parentA, pet.parentB));
        return items;
    }

    private Set<String> gone(List<Item> items) {
        Set<String> gone = new HashSet<>();
        for (Item item : items) {
            Pet pet = manager.get(item.id());
            if (pet != null && pet.isDead()) gone.add(item.id());
        }
        return gone;
    }

    // species the player has or had pets of, in the order they came
    private List<String> species() {
        List<String> kinds = new ArrayList<>();
        for (Pet pet : manager.getPets()) if (!kinds.contains(pet.typeID) && PetDataRepo.get(pet.typeID) != null) kinds.add(pet.typeID);
        return kinds;
    }

    static Map<String, int[]> shared(Pet a, Pet b) {
        PetStatusManager manager = PetStatusManager.getInstance();
        return PetGenetics.shared(a.id, b.id, id -> {
            Pet pet = manager.get(id);
            return pet != null ? new String[]{pet.parentA, pet.parentB} : null;
        });
    }

    static String percent(float f) {
        return Misc.getRoundedValueMaxOneAfterDecimal(f * 100f) + "%";
    }

    // the pair's card: how they are related and who they share
    private void kinCard(CustomPanelAPI p, float w, float h, Pet a, Pet b) {
        Map<String, int[]> shared = shared(a, b);
        PetHusbandry.title(p, w, a.name + " x " + b.name);
        String line = relation(a, b, shared);
        PetHusbandry.tagline(p, w, line != null ? line : a.name + " and " + b.name + " are not related.");

        float inbreeding = PetShop.getInbreeding(a, b);
        List<UiData.GridRow> rows = new ArrayList<>();
        rows.add(UiData.GridRow.of("Inbreeding", percent(inbreeding), Ui.hl()));
        rows.add(UiData.GridRow.of("Malformed", percent(PetGenetics.malformedChance(a.getData(), inbreeding)), inbreeding > 0f ? Ui.neg() : Ui.hl()));
        float y = PetCard.IDENT_Y + UiData.grid(p, 0f, PetCard.IDENT_Y, w, UiData.GRID_ROW_H, rows) + PetsPanel.HEAD_GAP;

        float rx = 208f;
        Ui.thead(p, 0f, y, w, new String[]{"Shared ancestors", "Of both"}, new float[]{8f, rx}, new float[]{200f, w - rx - 8f}, new Alignment[]{Ui.LEFT, Ui.RIGHT});
        y += Ui.HEADING_H + 4f;

        boolean any = false;
        for (Map.Entry<String, int[]> e : shared.entrySet()) {
            Pet pet = manager.get(e.getKey());
            if (pet == null || pet == a || pet == b) continue;
            any = true;
            UiImages.img(p, 4f, y, KIN_ICON, KIN_ICON, pet.getIcon(), null, pet.isDead() ? PetCard.DIM : 1f);
            Ui.textIn(p, 38f, y, 160f, KIN_ICON, Ui.fit(pet.name, Ui.INS15, 160f), Ui.INS15, pet.isDead() ? Ui.gray() : Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            int[] d = e.getValue();
            String of = d[0] == d[1] ? generation(d[0]) : generation(d[0]) + ", " + generation(d[1]).toLowerCase(Locale.ROOT);
            Ui.textIn(p, rx, y, w - rx - 8f, KIN_ICON, of, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
            y += KIN_ROW;
            if (y > h - 36f - KIN_ROW) break;
        }
        if (!any) Ui.text(p, 8f, y, w - 16f, "No shared ancestors.", Ui.gray());

        Ui.rule(p, 0f, h - 36f, w);
        UiButtons.button(p, w - BACK_W, h - PetCard.BUTTON_H, BACK_W, PetCard.BUTTON_H, "Back to the pairing", UiButtons.Kind.STD, CutStyle.TL_BR, panel::showHusbandry);
    }

    static String generation(int n) {
        return n == 1 ? "Parent" : n == 2 ? "Grandparent" : n == 3 ? "Great grandparent" : n + " generations up";
    }

    static String relation(Pet a, Pet b) {
        return relation(a, b, shared(a, b));
    }

    // how two pets are related, from their nearest shared ancestors; null when they are not
    static String relation(Pet a, Pet b, Map<String, int[]> shared) {
        if (shared.containsKey(a.id)) return ancestor(a, b, shared.get(a.id)[1]);
        if (shared.containsKey(b.id)) return ancestor(b, a, shared.get(b.id)[0]);
        if (shared.isEmpty()) return null;

        String both = a.name + " and " + b.name;
        if (a.parentA != null && a.parentB != null && Set.of(a.parentA, a.parentB).equals(new HashSet<>(Arrays.asList(b.parentA, b.parentB)))) return both + " come from one spawn.";
        for (int[] d : shared.values()) if (d[0] == 1 && d[1] == 1) return both + " share a parent.";
        for (int[] d : shared.values()) if (d[0] == 2 && d[1] == 2) return both + " are cousins.";
        return both + " are kin.";
    }

    private static String ancestor(Pet older, Pet younger, int generations) {
        String what = generations == 1 ? "a parent" : generations == 2 ? "a grandparent" : "an ancestor";
        return older.name + " is " + what + " of " + younger.name + ".";
    }

    static int young(Pet pet) {
        int n = 0;
        for (Pet other : PetStatusManager.getInstance().getPets()) if (pet.id.equals(other.parentA) || pet.id.equals(other.parentB)) n++;
        return n;
    }

    static String goneTag(Pet pet) {
        PetStatusManager.PetDeathCause cause = pet.deathData != null ? pet.deathData.cause : null;
        return cause == PetStatusManager.PetDeathCause.SOLD ? "Sold" : cause == PetStatusManager.PetDeathCause.REHOMED ? "Rehomed" : "Dead";
    }

    // a full size tile: portrait, name, hearts; a gone pet dimmed with Dead, Sold or Rehomed in place of the hearts
    private CustomPanelAPI tile(CustomPanelAPI p, float x, float y, Pet pet, boolean focus, boolean small) {
        return tile(p, x, y, pet, focus, small, () -> click(pet));
    }

    CustomPanelAPI tile(CustomPanelAPI p, float x, float y, Pet pet, boolean focus, boolean small, Runnable click) {
        float s = small ? PetTreeLayout.SMALL.tileW() : metrics().tileW();
        float th = small ? s : TILE_H;
        CustomPanelAPI t = UiButtons.row(p, x, y, s, th, focus, true, null, click);
        float alpha = pet.isDead() ? PetCard.DIM : 1f;
        if (small) UiImages.img(t, 2f, 2f, SMALL_PORTRAIT, SMALL_PORTRAIT, pet.getIcon(), null, alpha);
        else {
            UiImages.img(t, Math.round((s - PORTRAIT) / 2f), 5f, PORTRAIT, PORTRAIT, pet.getIcon(), null, alpha);
            Ui.textIn(t, 2f, 56f, s - 4f, Ui.LINE_H - 2f, Ui.fit(pet.name, Ui.INS15, s - 4f), Ui.INS15, pet.isDead() ? Ui.gray() : Ui.text(), Ui.CENTER, new String[0], new Color[0]);
            if (pet.isDead()) Ui.textIn(t, 2f, 73f, s - 4f, Ui.LINE_H - 2f, goneTag(pet), Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
            else PetCard.hearts(t, Math.round((s - PetCard.heartsWidth(HEART, HEART_GAP)) / 2f), 78f, pet, HEART, HEART_GAP);
        }
        Ui.tooltip(p, t, SHEET_W, TooltipMakerAPI.TooltipLocation.BELOW, tt -> sheet(tt, pet));
        return t;
    }

    private void click(Pet pet) {
        view.select(pet.id);
        view.treeCard = pet.id;
        panel.refresh();
    }

    // the hover of a tile: the card's tables in a tooltip, every trait by name with its grade and the parent it came from
    private void sheet(TooltipMakerAPI t, Pet pet) {
        PetReveal reveal = view.reveal;
        Pet a = manager.get(pet.parentA);
        Pet b = manager.get(pet.parentB);
        boolean parents = pet.parentA != null || pet.parentB != null;

        List<Row> talents = new ArrayList<>();
        boolean known = false;
        for (String id : pet.getTalents()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null) continue;
            if (!reveal.known(pet, id)) talents.add(new Row(null, "Unknown", "", null));
            else {
                talents.add(new Row(String.valueOf(pet.sheet.getGrade(id)), trait.name(), from(pet, id, a, b), Ui.hl()));
                known = true;
            }
        }

        List<Row> body = new ArrayList<>();
        for (String id : pet.sheet.traits.keySet()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null || !trait.isMutation()) continue;
            body.add(new Row(trait.hasLevels() ? String.valueOf(pet.sheet.getGrade(id)) : "", trait.name(), from(pet, id, a, b), Ui.bright()));
            known = true;
        }
        for (String id : pet.sheet.traits.keySet()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null || !trait.isFlaw() || !reveal.known(pet, id)) continue;
            body.add(new Row(trait.hasLevels() ? String.valueOf(pet.sheet.getGrade(id)) : "", trait.name(), from(pet, id, a, b), Ui.neg()));
            known = true;
        }
        if (!reveal.flawsRead(pet)) body.add(new Row(null, "Unknown", "", null));

        String line = PetCard.species(pet) + ". " + (pet.isDead() ? goneAge(pet) : PetCard.age(pet)) + ".";
        String family = a != null && b != null ? "Young of " + a.name + " and " + b.name + "." : a != null || b != null ? "Young of " + (a != null ? a : b).name + "." : "Parents unknown.";
        boolean none = !known && !talents.isEmpty();

        float h = 30f + 2f * SHEET_ROW + (none ? SHEET_ROW : 0f);
        if (!none) {
            if (!talents.isEmpty()) h += 10f + Ui.HEADING_H + 4f + talents.size() * SHEET_ROW;
            if (!body.isEmpty()) h += 10f + Ui.HEADING_H + 4f + body.size() * SHEET_ROW;
        }
        h += 6f;

        CustomPanelAPI s = Global.getSettings().createCustom(SHEET_W, h, new UiCanvas());
        float w = SHEET_W;
        Ui.text(s, 0f, 4f, w - 80f, pet.name, Ui.hl());
        if (pet.isDead()) Ui.textIn(s, w - 96f, 4f, 96f, Ui.LINE_H, goneTag(pet), Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
        else PetCard.hearts(s, w - PetCard.heartsWidth(10f, 2f), 8f, pet, 10f, 2f);

        float y = 30f;
        Ui.text(s, 0f, y, w, Ui.fit(line, Ui.INS15, w), Ui.text());
        y += SHEET_ROW;
        Ui.text(s, 0f, y, w, Ui.fit(family, Ui.INS15, w), Ui.gray());
        y += SHEET_ROW;
        if (none) {
            int n = talents.size();
            Ui.text(s, 0f, y, w, n + (n == 1 ? " talent, unknown." : " talents, all unknown."), Ui.gray());
        } else {
            y = sheetTable(s, y, w, new String[]{"Grade", "Talents", parents ? "From" : ""}, talents);
            sheetTable(s, y, w, new String[]{"Level", "Mutations, flaws", parents ? "From" : ""}, body);
        }
        t.addCustom(s, 0f);
    }

    private static float sheetTable(CustomPanelAPI s, float y, float w, String[] labels, List<Row> rows) {
        if (rows.isEmpty()) return y;

        float fw = w - 150f - 4f;
        y += 10f;
        Ui.thead(s, 0f, y, w, labels, new float[]{0f, 54f, 150f}, new float[]{44f, 120f, fw},
                new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});
        y += Ui.HEADING_H + 4f;
        for (Row row : rows) {
            boolean unknown = row.grade() == null;
            Ui.textIn(s, 0f, y, 44f, SHEET_ROW, unknown ? "?" : row.grade(), Ui.INS15, unknown ? Ui.gray() : row.color(), Ui.CENTER, new String[0], new Color[0]);
            Ui.textIn(s, 58f, y, 120f, SHEET_ROW, row.name(), Ui.INS15, unknown ? Ui.gray() : Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            if (!row.from().isEmpty()) Ui.textIn(s, 150f, y, fw, SHEET_ROW, Ui.fit(row.from(), Ui.INS15, fw), Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
            y += SHEET_ROW;
        }
        return y;
    }

    // "Magpie 2 + Flint 1" is the sum, one name is that parent's grade passed on; a parent's grade shows once the player knows it
    private String from(Pet pet, String id, Pet a, Pet b) {
        String from = pet.sheet.getFrom(id);
        if (from == null) return "";
        return switch (from) {
            case PetSheet.FROM_SUM -> part(a, id) + " + " + part(b, id);
            case PetSheet.FROM_A -> part(a, id);
            case PetSheet.FROM_B -> part(b, id);
            case PetSheet.FROM_MUTATION -> "New with " + pet.name;
            case PetSheet.FROM_MALFORMED -> "Inbreeding";
            default -> "";
        };
    }

    private String part(Pet parent, String id) {
        if (parent == null) return "Parent";
        return view.reveal.known(parent, id) ? parent.name + " " + parent.sheet.getGrade(id) : parent.name;
    }

    static String goneAge(Pet pet) {
        String tag = goneTag(pet);
        return (tag.equals("Dead") ? "Died" : tag) + " at " + pet.getSpan();
    }

    // tiles on a world panel that scrolls up and down and drags sideways, lines painted below them
    private PetTreeCanvas canvas(PetTreeLayout.Result result, PetTreeLayout.Metrics m, boolean small, Map<String, List<String>> stubs, Set<String> focus, Set<String> gold) {
        return new PetTreeCanvas(result, m, small, gold, null, new PetTreeCanvas.Owner() {
            @Override
            public void tile(CustomPanelAPI world, Node n, float x, float y) {
                if (n.isStub()) stub(world, n, x, y, m, small, stubs);
                else {
                    Pet pet = manager.get(n.id);
                    if (pet != null) PetTree.this.tile(world, x, y, pet, focus.contains(n.id), small);
                }
            }

            @Override
            public float height(Node n) {
                return small ? m.tileW() : n.isStub() ? STUB_H : TILE_H;
            }

            @Override
            public boolean dashed(Brood brood) {
                return brood.dashed;
            }

            @Override
            public boolean hovers(String id) {
                return !id.startsWith(PetTreeLayout.STUB);
            }

            @Override
            public boolean labelAlone() {
                return true;
            }

            @Override
            public float getX() {
                return view.treeX;
            }

            @Override
            public void setX(float x) {
                view.treeX = x;
            }

            @Override
            public void emptyClick() {
                if (view.treeCard == null || !view.treeFull && view.treePair.isEmpty()) return;
                view.treeCard = null;
                panel.refresh();
            }
        }, view.scroll, PetsView.TREE);
    }

    // a folded dead branch: one grey tile with the count; a click shows the pets until the tab is left
    private void stub(CustomPanelAPI world, Node n, float x, float y, PetTreeLayout.Metrics m, boolean small, Map<String, List<String>> stubs) {
        int count = stubs.getOrDefault(n.id, List.of()).size();
        float h = small ? m.tileW() : STUB_H;
        CustomPanelAPI t = UiButtons.row(world, x, y, m.tileW(), h, false, true, null, () -> {
            view.unfolded.add(n.id);
            panel.refresh();
        });
        Ui.box(t, 0f, 0f, m.tileW(), h, STUB_EDGE, null);
        Ui.textIn(t, 0f, 0f, m.tileW(), h, "+" + count, Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
        String what = n.id.equals(PetTreeLayout.SINGLES) ? " pets, dead or sold, with no family." : " pets, dead or sold, with no living young.";
        Ui.tooltip(world, t, n.id.equals(PetTreeLayout.SINGLES) ? "No family" : "Dead branch", count + what + " Click to show them.");
    }
}
