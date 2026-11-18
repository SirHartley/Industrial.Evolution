package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
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
    static final float SPECIES_W = 52f;
    static final float SPECIES_PITCH = 56f;
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
        PetTreeLayout.Result result = PetTreeLayout.layout(folded.items(), pairs, PetTreeLayout.FULL, false);

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
        else new Drawn(result, PetTreeLayout.FULL, false, folded.stubs(), focus, shared).draw(p, 0f, TREE_Y, PetsPanel.LIST_W, h - TREE_Y);

        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        float cw = w - PetsPanel.CARD_X;
        float ch = h - PetsPanel.BODY_Y;
        if (!pair.isEmpty() && card == null) kinCard(Ui.panel(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, cw, ch), cw, ch, pair.get(0), pair.get(1));
        else new PetCard(panel, view).build(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, cw, ch, card != null ? card : root);
    }

    // a pet with no parents, mates or young in the registry
    private void alone(CustomPanelAPI p, Pet pet, boolean focus) {
        float x = Math.round((LEFT_W - PetTreeLayout.FULL.tileW()) / 2f);
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
        PetTreeLayout.Metrics m = view.treeSmall ? PetTreeLayout.SMALL : PetTreeLayout.FULL;
        PetTreeLayout.Result result = PetTreeLayout.layout(folded.items(), folded.pairs(), m, true);
        if (card != null && !result.nodes().containsKey(card.id)) card = null;

        float tw = card != null ? LEFT_W : w;
        int families = result.families().size();
        String species = PetDataRepo.get(view.treeSpecies).species;
        Ui.heading(p, 0f, PetsPanel.BODY_Y, tw, species, families + (families == 1 ? " family, " : " families, ") + items.size() + (items.size() == 1 ? " pet" : " pets"));
        control(p, tw, root);

        Set<String> focus = card != null ? Set.of(card.id) : Set.of();
        new Drawn(result, m, view.treeSmall, folded.stubs(), focus, Set.of()).draw(p, 0f, TREE_Y, card != null ? PetsPanel.LIST_W : w, h - TREE_Y);
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
        if (!query.isEmpty() && match(query) == null) Ui.textIn(p, fx, y, 200f, UiFilter.H, "No pet of that name.", Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);

        float x = w - 2f - FULL_W;
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
        }, "Every family of a species side by side, the pets with no family at the right.");
        if (!view.treeFull) return;

        List<String> kinds = species();
        float room = x - TOGGLE_GAP - (fx + 200f) - SMALL_W - TOGGLE_GAP;
        int fit = Math.max(1, (int) Math.floor((room + SPECIES_PITCH - SPECIES_W) / SPECIES_PITCH));
        if (kinds.size() > fit) {
            kinds = new ArrayList<>(kinds.subList(0, fit));
            if (!kinds.contains(view.treeSpecies)) kinds.set(fit - 1, view.treeSpecies);
        }

        float sx = x - TOGGLE_GAP - kinds.size() * SPECIES_PITCH + (SPECIES_PITCH - SPECIES_W);
        for (String kind : kinds) {
            PetData data = PetDataRepo.get(kind);
            int count = 0;
            for (Pet pet : manager.getPets()) if (pet.typeID.equals(kind) && !pet.isDead()) count++;
            toggle(p, sx, y, SPECIES_W, null, kind.equals(view.treeSpecies), () -> {
                view.treeSpecies = kind;
                view.treeCard = null;
                moved();
            }, data.species + ": " + count + (count == 1 ? " living pet." : " living pets."));
            UiImages.img(p, sx + 5f, y + 3f, 20f, 20f, data.icon);
            Ui.textIn(p, sx + 28f, y, 20f, UiFilter.H, String.valueOf(count), Ui.ORB12, Ui.hl(), Ui.CENTER, new String[0], new Color[0]);
            sx += SPECIES_PITCH;
        }

        float smallX = x - TOGGLE_GAP - kinds.size() * SPECIES_PITCH - 2f - SMALL_W + (SPECIES_PITCH - SPECIES_W);
        toggle(p, smallX, y, SMALL_W, "Small tiles", view.treeSmall, () -> {
            view.treeSmall = !view.treeSmall;
            moved();
        }, "Portraits only. Name, hearts and traits are on the hover.");
    }

    private static void toggle(CustomPanelAPI p, float x, float y, float w, String label, boolean on, Runnable action, String tip) {
        ButtonAPI button = UiButtons.area(p, x, y, w, UiFilter.H, label, on, true, null, Ui.CENTER, action);
        Ui.tooltip(p, button, null, tip);
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

    private Map<String, int[]> shared(Pet a, Pet b) {
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
            UiImages.img(p, 4f, y, KIN_ICON, KIN_ICON, pet.getData().icon, null, pet.isDead() ? PetCard.DIM : 1f);
            Ui.textIn(p, 38f, y, 160f, KIN_ICON, Ui.fit(pet.name, Ui.INS15, 160f), Ui.INS15, pet.isDead() ? Ui.gray() : Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            int[] d = e.getValue();
            String of = d[0] == d[1] ? generation(d[0]) : generation(d[0]) + ", " + generation(d[1]).toLowerCase(Locale.ROOT);
            Ui.textIn(p, rx, y, w - rx - 8f, KIN_ICON, of, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
            y += KIN_ROW;
            if (y > h - 36f - KIN_ROW) break;
        }
        if (!any) Ui.text(p, 8f, y, w - 16f, "No shared ancestors.", Ui.gray());

        Ui.rule(p, 0f, h - 36f, w);
        UiButtons.button(p, w - BACK_W, h - PetCard.BUTTON_H, BACK_W, PetCard.BUTTON_H, "Back to the cross", UiButtons.Kind.STD, CutStyle.TL_BR, panel::showHusbandry);
    }

    static String generation(int n) {
        return n == 1 ? "Parent" : n == 2 ? "Grandparent" : n == 3 ? "Great grandparent" : n + " generations up";
    }

    // how two pets are related, from their nearest shared ancestors; null when they are not
    static String relation(Pet a, Pet b, Map<String, int[]> shared) {
        if (shared.containsKey(a.id)) return ancestor(a, b, shared.get(a.id)[1]);
        if (shared.containsKey(b.id)) return ancestor(b, a, shared.get(b.id)[0]);
        if (shared.isEmpty()) return null;

        String both = a.name + " and " + b.name;
        if (a.parentA != null && a.parentB != null && Set.of(a.parentA, a.parentB).equals(new HashSet<>(Arrays.asList(b.parentA, b.parentB)))) return both + " come from one brood.";
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
        float s = small ? PetTreeLayout.SMALL.tileW() : PetTreeLayout.FULL.tileW();
        float th = small ? s : TILE_H;
        CustomPanelAPI t = UiButtons.row(p, x, y, s, th, focus, true, null, () -> click(pet));
        float alpha = pet.isDead() ? PetCard.DIM : 1f;
        if (small) UiImages.img(t, 2f, 2f, SMALL_PORTRAIT, SMALL_PORTRAIT, pet.getData().icon, null, alpha);
        else {
            UiImages.img(t, 8f, 5f, PORTRAIT, PORTRAIT, pet.getData().icon, null, alpha);
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

        String line = pet.getData().species + ". " + (pet.isDead() ? goneAge(pet) : PetCard.age(pet)) + ".";
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
        return (tag.equals("Dead") ? "Died" : tag) + " at " + PetCard.span(pet);
    }

    // one drawn tree: tiles on a world panel that scrolls up and down and drags sideways, lines painted below them
    private final class Drawn {
        final PetTreeLayout.Result result;
        final PetTreeLayout.Metrics m;
        final boolean small;
        final Map<String, List<String>> stubs;
        final Set<String> focus;
        final Set<String> shared;
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

        Drawn(PetTreeLayout.Result result, PetTreeLayout.Metrics m, boolean small, Map<String, List<String>> stubs, Set<String> focus, Set<String> shared) {
            this.result = result;
            this.m = m;
            this.small = small;
            this.stubs = stubs;
            this.focus = focus;
            this.shared = shared;
            pitch = small ? SMALL_PITCH : ROW_PITCH;
            tileH = small ? m.tileW() : TILE_H;

            // the No family label sits above the singles, inside the block when they stand in the first row
            boolean label = !result.singles().isEmpty() && !result.families().isEmpty() && result.nodes().get(result.singles().get(0)).row == 0;
            top = MARGIN + (label ? LABEL_H : 0f);

            for (Brood brood : result.broods()) {
                for (String kid : brood.kids) byKid.put(kid, brood);
                broodCount.merge(brood.a, 1, Integer::sum);
                if (brood.b != null) broodCount.merge(brood.b, 1, Integer::sum);
            }
        }

        void draw(CustomPanelAPI p, float x, float y, float w, float h) {
            float treeH = result.rows() * pitch - (pitch - tileH);
            float blockH = top + treeH + MARGIN;
            UiScroll.Column column = UiScroll.list(p, x, y, w, h, view.scroll, PetsView.TREE);
            viewW = column.width();
            viewH = h;
            float worldW = Math.max(viewW, result.width() + 2f * MARGIN);
            view.treeX = Math.max(0f, Math.min(worldW - viewW, view.treeX));

            CustomPanelAPI block = column.block(blockH, 0f);
            world = Ui.painted(block, -view.treeX, 0f, worldW, blockH, this::paintLines, this::paintMarks);
            for (Node n : result.nodes().values()) {
                if (n.lane) continue;
                if (n.isStub()) stub(n);
                else {
                    Pet pet = manager.get(n.id);
                    if (pet != null) tile(world, left(n), rowTop(n.row), pet, focus.contains(n.id), small);
                }
            }

            if (!result.singles().isEmpty() && !result.families().isEmpty()) {
                float lo = Float.MAX_VALUE;
                float hi = 0f;
                for (String id : result.singles()) {
                    lo = Math.min(lo, left(result.nodes().get(id)));
                    hi = Math.max(hi, left(result.nodes().get(id)) + m.tileW());
                }
                float row = rowTop(result.nodes().get(result.singles().get(0)).row);
                float lw = Math.max(hi - lo, 100f);
                Ui.text(world, Math.round((lo + hi - lw) / 2f), row - LABEL_H, lw, "No family", Ui.gray(), Ui.CENTER);
            }

            element = column.end();
            CustomPanelAPI input = p.createCustomPanel(w - UiScroll.GUTTER, h, new Input());
            p.addComponent(input).inTL(x, y);
        }

        // a folded dead branch: one grey tile with the count; a click shows the pets until the tab is left
        private void stub(Node n) {
            int count = stubs.getOrDefault(n.id, List.of()).size();
            float h = small ? m.tileW() : STUB_H;
            CustomPanelAPI t = UiButtons.row(world, left(n), rowTop(n.row), m.tileW(), h, false, true, null, () -> {
                view.unfolded.add(n.id);
                panel.refresh();
            });
            Ui.box(t, 0f, 0f, m.tileW(), h, STUB_EDGE, null);
            Ui.textIn(t, 0f, 0f, m.tileW(), h, "+" + count, Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
            String what = n.id.equals(PetTreeLayout.SINGLES) ? " pets, dead or sold, with no family." : " pets, dead or sold, with no living young.";
            Ui.tooltip(world, t, n.id.equals(PetTreeLayout.SINGLES) ? "No family" : "Dead branch", count + what + " Click to show them.");
        }

        float rowTop(int row) {
            return top + row * pitch;
        }

        float left(Node n) {
            return MARGIN + n.x - m.tileW() / 2f;
        }

        float height(Node n) {
            return n.isStub() ? (small ? m.tileW() : STUB_H) : tileH;
        }

        // the node under the screen point, pets and stubs only
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
                if (wx >= l && wx < l + m.tileW() && wy >= t && wy < t + height(n)) return n.id;
            }
            return null;
        }

        private void paintLines(PositionAPI pos, float alpha) {
            for (Brood brood : result.broods()) brood(pos, brood, brood.dashed ? Ui.hl() : Ui.base(), alpha, null);

            Brood lit = hovered != null ? byKid.get(hovered) : null;
            if (lit != null) brood(pos, lit, Ui.hl(), alpha, hovered);
        }

        // the parents of the pet under the mouse, and the ancestors a pair shares, get the gold border
        private void paintMarks(PositionAPI pos, float alpha) {
            Set<String> gold = new HashSet<>(shared);
            Brood lit = hovered != null ? byKid.get(hovered) : null;
            if (lit != null) {
                gold.add(lit.a);
                if (lit.b != null) gold.add(lit.b);
            }

            for (String id : gold) {
                Node n = result.nodes().get(id);
                if (n == null) continue;
                UiDraw.outline(pos.getX() + left(n), pos.getY() + pos.getHeight() - rowTop(n.row) - height(n), m.tileW(), height(n), Ui.hl(), alpha);
            }
        }

        // one line down from each parent to a bar, one from the bar to the bar of the young, one down to each young;
        // the older parent of a pair in two rows comes down its lane; only draws the line to that one young
        private void brood(PositionAPI pos, Brood brood, Color c, float alpha, String only) {
            Map<String, Node> nodes = result.nodes();
            Node sa = PetTreeLayout.slot(brood, brood.a, nodes);
            Node sb = brood.b != null ? PetTreeLayout.slot(brood, brood.b, nodes) : null;
            int row = Math.max(sa.row, sb != null ? sb.row : sa.row);
            float pe = rowTop(row) + tileH;
            float gap = pitch - tileH;
            int k = brood.levels * 2 + 1;
            float l1 = pe + Math.round(gap * (brood.level + 1) / k);
            float l2 = pe + Math.round(gap * (brood.levels + brood.level + 1) / k);

            float xa = drop(pos, brood, brood.a, sa, sb, c, alpha, sb == null ? l2 : l1);
            float xb = sb != null ? drop(pos, brood, brood.b, sb, sa, c, alpha, l1) : xa;
            if (sb != null) seg(pos, xa, l1, xb, l1, c, alpha, brood.dashed);
            if (brood.kids.isEmpty()) return;

            float xd = sb == null ? xa : Math.round((xa + xb) / 2f);
            if (sb != null) seg(pos, xd, l1, xd, l2, c, alpha, false);
            float lo = xd;
            float hi = xd;
            for (String kid : brood.kids) {
                if (only != null && !only.equals(kid)) continue;
                Node n = nodes.get(kid);
                float kx = MARGIN + n.x;
                lo = Math.min(lo, kx);
                hi = Math.max(hi, kx);
                seg(pos, kx, l2, kx, rowTop(n.row), c, alpha, false);
            }
            seg(pos, lo, l2, hi, l2, c, alpha, false);
        }

        // the line from under one parent down to y; returns its x. a pet with several mates drops beside its centre, towards the mate
        private float drop(PositionAPI pos, Brood brood, String id, Node slot, Node other, Color c, float alpha, float y) {
            Node parent = result.nodes().get(id);
            if (!id.equals(brood.older)) {
                float x = MARGIN + parent.x;
                if (broodCount.getOrDefault(id, 0) > 1 && other != null && other.x != parent.x) x += Math.signum(other.x - parent.x) * m.drop();
                seg(pos, x, rowTop(parent.row) + height(parent), x, y, c, alpha, brood.dashed);
                return x;
            }

            Node first = brood.lane.get(0);
            float x = Math.abs(first.x - parent.x) <= m.tileW() / 2f - 4f ? MARGIN + first.x : MARGIN + parent.x;
            float from = rowTop(parent.row) + height(parent);
            for (Node lane : brood.lane) {
                float lx = MARGIN + lane.x;
                if (lx != x) {
                    float jog = rowTop(lane.row) - JOG;
                    seg(pos, x, from, x, jog, c, alpha, brood.dashed);
                    seg(pos, x, jog, lx, jog, c, alpha, brood.dashed);
                    from = jog;
                    x = lx;
                }
            }
            seg(pos, x, from, x, y, c, alpha, brood.dashed);
            return x;
        }

        // 1 px line between two points of the world panel, y down; dashed for a pair joined in the pair view
        private void seg(PositionAPI pos, float x1, float y1, float x2, float y2, Color c, float alpha, boolean dashed) {
            float left = pos.getX();
            float topY = pos.getY() + pos.getHeight();
            boolean flat = y1 == y2;
            float from = flat ? Math.min(x1, x2) : Math.min(y1, y2);
            float to = flat ? Math.max(x1, x2) + 1f : Math.max(y1, y2) + 1f;
            float step = dashed ? DASH + DASH_GAP : to - from;
            for (float s = from; s < to; s += step) {
                float len = Math.min(dashed ? DASH : to - from, to - s);
                if (flat) UiDraw.quad(left + s, topY - y1 - 1f, len, 1f, c, alpha);
                else UiDraw.quad(left + x1, topY - s - len, 1f, len, c, alpha);
            }
        }

        // hover for the gold lines, drag on the empty tree to move it, a click on the empty tree closes the card
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
                if (hovered != null && hovered.startsWith("stub:")) hovered = null;
            }

            @Override
            public void processInput(List<InputEventAPI> events) {
                if (viewport == null) return;

                for (InputEventAPI event : events) {
                    if (event.isLMBDownEvent() && viewport.containsEvent(event) && pick(event.getX(), event.getY()) == null) {
                        down = true;
                        startMx = event.getX();
                        startMy = event.getY();
                        startX = view.treeX;
                        ScrollPanelAPI scroller = element.getExternalScroller();
                        startY = scroller != null ? scroller.getYOffset() : 0f;
                    } else if (event.isMouseMoveEvent() && down) {
                        if (!dragging && Math.abs(event.getX() - startMx) + Math.abs(event.getY() - startMy) > DRAG) dragging = true;
                        if (!dragging) continue;
                        move(event.getX(), event.getY());
                        event.consume();
                    } else if (event.isLMBUpEvent() && down) {
                        down = false;
                        if (dragging) {
                            dragging = false;
                            event.consume();
                        } else if (viewport.containsEvent(event) && pick(event.getX(), event.getY()) == null) emptyClick();
                    }
                }
            }

            private void move(float mx, float my) {
                float worldW = world.getPosition().getWidth();
                view.treeX = Math.max(0f, Math.min(worldW - viewW, startX - (mx - startMx)));
                world.getPosition().inTL(-view.treeX, 0f);

                ScrollPanelAPI scroller = element.getExternalScroller();
                if (scroller == null) return;
                float max = Math.max(0f, element.getHeightSoFar() - viewH);
                scroller.setYOffset(Math.max(0f, Math.min(max, startY + (my - startMy))));
            }

            private void emptyClick() {
                if (view.treeCard == null || !view.treeFull && view.treePair.isEmpty()) return;
                view.treeCard = null;
                panel.refresh();
            }
        }
    }
}
