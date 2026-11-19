package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetPlan;
import indevo.industries.petshop.memory.PetSheet;
import indevo.industries.petshop.plugins.PetHusbandry.CrossRow;
import indevo.industries.petshop.plugins.PetHusbandry.FlowNode;
import indevo.industries.petshop.plugins.PetHusbandry.Result;
import indevo.industries.petshop.plugins.PetTreeLayout.Brood;
import indevo.industries.petshop.plugins.PetTreeLayout.Item;
import indevo.industries.petshop.plugins.PetTreeLayout.Node;
import indevo.utils.ui.*;

import java.awt.Color;
import java.util.*;
import java.util.function.Consumer;

// the Planner tab: planned young and the pets in their places as a tree on the left; on the right the clicked plan's card,
// the parent list of one of its places or the Copy pet list
public class PetPlanner {

    static final float NEW_W = 96f;
    static final float STRIP_Y = 42f;
    static final float STRIP_H = 50f;
    static final float FIT_H = 40f;
    static final float WANT_ROW = 26f;
    static final float WANT_H = 24f;
    static final float STEP = 22f;
    static final float VALUE_X = 26f;
    static final float VALUE_W = 62f;
    static final float PLUS_X = 88f;
    static final float TRAIT_X = 120f;
    static final float EFFECT_X = 235f;
    static final float CHANCE_W = 60f;
    static final float ADD_W = 108f;
    static final float ADD_H = 22f;
    // from the bottom of the card: the frame under the Wanted table, Chance per young
    static final float FRAME_UP = 296f;
    static final float CHANCE_UP = 92f;
    static final float FLOW_LINK = 20f;
    static final float ROW_H = 50f;
    static final float ROW_GAP = 4f;
    static final float FACE = 44f;
    static final float HEARTS_X = 196f;
    static final float OPEN_W = 104f;
    static final float SIDE_W = 96f;
    static final int MAX_WANT = 20;
    static final int MAX_PAIRS = 30;
    static final String OPEN = "open:";
    static final String LOOK = PetHusbandry.LOOK;
    static final String MALFORMED = PetGenetics.MALFORMED;

    static final String NO_RELATIVES = "No relatives";
    static final String ABOVE_ZERO = "Above 0%";
    static final String LIVING = "Living";
    static final String SORT_CHANCE = "Chance";
    static final String SORT_TALENTS = "Talents";
    static final String SORT_NAME = "Name";
    static final List<UiFilter.Item> PARENT_TOGGLES = List.of(new UiFilter.Item(NO_RELATIVES, 104f, "Hides the kin of the other parent."),
            new UiFilter.Item(ABOVE_ZERO, 92f, "Hides the pets that cannot give the target."));
    static final List<UiFilter.Item> PARENT_KEYS = List.of(new UiFilter.Item(SORT_CHANCE, 78f, "Best chance first."), new UiFilter.Item(SORT_NAME, 70f),
            new UiFilter.Item("Hearts", 78f, "Most hearts first."), new UiFilter.Item("Age", 62f));
    static final List<UiFilter.Item> COPY_TOGGLES = List.of(new UiFilter.Item(LIVING, 70f, "Hides dead, sold and rehomed pets."));
    static final List<UiFilter.Item> COPY_KEYS = List.of(new UiFilter.Item(SORT_TALENTS, 82f, "Highest sum of known grades first."), new UiFilter.Item(SORT_NAME, 70f),
            new UiFilter.Item("Hearts", 78f, "Most hearts first."), new UiFilter.Item("Age", 62f));

    private final PetsPanel panel;
    private final PetsView view;
    private final PetShop shop;
    private final PetStatusManager manager = PetStatusManager.getInstance();
    private final Map<String, PetDataRepo.Trait> traits = PetDataRepo.getTraits();
    private final PetTree tree;
    private final PetShop center;
    private final float care;

    // a pet or a planned young in a parent place, as the math sees it: a pet with its known traits, a plan with what is wanted
    record Parent(String id, String name, PetSheet sheet, int hearts, Pet pet, PetPlan plan) {
    }

    record Calc(Map<String, Float> rows, float chance, float inbreeding) {
    }

    // one row of the best list: both parents, the one listed (null for a pair), the math and how they are related
    record Match(Parent a, Parent b, Pet listed, Calc calc, String kin) {
    }

    // broods until one young fits, -1 for never, and the days that takes, the steps of a chain included
    record Expect(int broods, float days) {
    }

    PetPlanner(PetsPanel panel, PetsView view) {
        this.panel = panel;
        this.view = view;
        this.shop = panel.shop();
        this.tree = new PetTree(panel, view);
        this.center = PetShop.planCenter(shop);
        this.care = center != null ? center.getCare() : PetConstants.NPC_CARE;
    }

    static UiFilter.State parentFilter() {
        UiFilter.State filter = new UiFilter.State(SORT_CHANCE);
        filter.fallback = SORT_NAME;
        return filter;
    }

    static UiFilter.State copyFilter() {
        return new UiFilter.State(SORT_TALENTS);
    }

    void build(CustomPanelAPI p, float w, float h) {
        manager.cleanPlans();
        List<String> kinds = species();
        if (kinds.isEmpty()) {
            Ui.text(p, 0f, PetsPanel.BODY_Y, w, "No pets yet.", Ui.gray());
            return;
        }

        if (!kinds.contains(view.planSpecies)) view.planSpecies = kinds.get(0);
        List<PetPlan> plans = plans(view.planSpecies);
        PetPlan plan = manager.getPlan(view.planSelected);
        if (plan == null || !plan.typeID.equals(view.planSpecies)) plan = plans.isEmpty() ? null : plans.get(0);
        view.planSelected = plan == null ? null : plan.id;
        if (plan == null) closeLists();

        left(p, h, kinds, plans, plan);
        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        if (plan == null) return;

        float cw = w - PetsPanel.CARD_X;
        float ch = h - PetsPanel.BODY_Y;
        CustomPanelAPI c = Ui.panel(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, cw, ch);
        if (view.planPlace >= 0) parentList(c, cw, ch, plan, view.planPlace);
        else if (view.planCopy) copyList(c, cw, ch, plan);
        else card(c, cw, ch, plan);
    }

    // species with a living pet or a plan, in the order they came
    private List<String> species() {
        List<String> kinds = new ArrayList<>();
        for (Pet pet : manager.getPets()) if (!pet.isDead() && !kinds.contains(pet.typeID) && PetDataRepo.get(pet.typeID) != null) kinds.add(pet.typeID);
        for (PetPlan plan : manager.getPlans()) if (!kinds.contains(plan.typeID) && plan.getData() != null) kinds.add(plan.typeID);
        return kinds;
    }

    private List<PetPlan> plans(String typeId) {
        List<PetPlan> plans = new ArrayList<>();
        for (PetPlan plan : manager.getPlans()) if (plan.typeID.equals(typeId)) plans.add(plan);
        return plans;
    }

    private void closeLists() {
        view.planPlace = -1;
        view.planCopy = false;
    }

    private void select(PetPlan plan) {
        view.planSelected = plan.id;
        view.planRow = null;
        closeLists();
        view.scroll.reset(PetsView.WANTED);
        panel.refresh();
    }

    private void openPlace(PetPlan plan, int place) {
        view.planSelected = plan.id;
        view.planPlace = place;
        view.planCopy = false;
        view.scroll.reset(PetsView.PLAN_LIST);
        panel.refresh();
    }

    private static int placeOf(PetPlan plan, String id) {
        return id.equals(plan.parentA) ? 0 : id.equals(plan.parentB) ? 1 : -1;
    }

    private static String open(PetPlan plan, int place) {
        return OPEN + place + ":" + plan.id;
    }

    // what stands in a place: a pet id, a plan id, or the open place
    private static String place(PetPlan plan, int place) {
        String id = plan.getParent(place);
        return id != null ? id : open(plan, place);
    }

    private Parent parent(String id) {
        if (id == null) return null;

        PetPlan plan = manager.getPlan(id);
        if (plan != null) return new Parent(id, plan.name, plan.sheet(), 0, null, plan);
        Pet pet = manager.get(id);
        return pet != null ? parent(pet) : null;
    }

    private Parent parent(Pet pet) {
        return new Parent(pet.id, pet.name, view.reveal.seen(pet), pet.getHearts(), pet, null);
    }

    private Calc calc(PetPlan plan) {
        Parent a = parent(plan.parentA);
        Parent b = parent(plan.parentB);
        return a != null && b != null ? calc(plan, a, b) : null;
    }

    private Calc calc(PetPlan plan, Parent a, Parent b) {
        float inbreeding = PetShop.getInbreeding(a.id(), b.id());
        Map<String, Float> rows = PetGenetics.wantedChances(plan.getData(), a.sheet(), b.sheet(), a.hearts() + b.hearts(), care, inbreeding, plan.wanted, traits);
        return new Calc(rows, PetGenetics.product(rows), inbreeding);
    }

    static String chance(float p) {
        if (p <= 0f) return "0%";
        if (p < 0.001f) return "<0.1%";
        return PetTree.percent(p);
    }

    // living pets of the species that can be a parent some day
    private List<Pet> candidates(String typeId) {
        List<Pet> list = new ArrayList<>();
        for (Pet pet : manager.getPets()) {
            if (pet.isDead() || !pet.typeID.equals(typeId) || pet.getData().isNoSell()) continue;
            if (pet.sheet.isKnown(PetGenetics.STERILE) && pet.sheet.getGrade(PetGenetics.STERILE) > 0) continue;
            list.add(pet);
        }
        return list;
    }

    private void left(CustomPanelAPI p, float h, List<String> kinds, List<PetPlan> plans, PetPlan selected) {
        float w = PetTree.LEFT_W;
        int n = plans.size();
        Ui.heading(p, 0f, PetsPanel.BODY_Y, w, PetDataRepo.get(view.planSpecies).species, n + (n == 1 ? " plan" : " plans"));
        control(p, w, kinds);
        if (plans.isEmpty()) {
            Ui.text(p, 2f, PetTree.TREE_Y, w, "No plans for this species.", Ui.gray());
            return;
        }

        List<Item> items = new ArrayList<>();
        Set<String> pets = new LinkedHashSet<>();
        for (PetPlan plan : plans) {
            String a = place(plan, 0);
            String b = place(plan, 1);
            items.add(new Item(plan.id, a, b));
            for (String id : new String[]{a, b}) if (!PetPlan.isPlan(id)) pets.add(id);
        }
        for (String id : pets) items.add(new Item(id, null, null));
        PetTreeLayout.Result result = PetTreeLayout.layout(items, List.of(), PetTreeLayout.FULL, false);

        String focus = selected == null ? null : view.planPlace >= 0 ? place(selected, view.planPlace) : selected.id;
        Set<String> gold = selected != null && view.planPlace >= 0 ? Set.of(selected.id) : Set.of();
        canvas(result, focus, gold, selected).draw(p, 0f, PetTree.TREE_Y, PetsPanel.LIST_W, h - PetTree.TREE_Y);
    }

    // species toggles with their plan count, New plan at the right end
    private void control(CustomPanelAPI p, float w, List<String> kinds) {
        float y = PetTree.CONTROL_Y;
        float nx = w - 2f - NEW_W;
        ButtonAPI add = UiButtons.button(p, nx, y, NEW_W, UiFilter.H, "New plan", UiButtons.Kind.STD, CutStyle.TL_BR, () -> panel.newPlan(view.planSpecies, null, null));
        Ui.tooltip(p, add, "New plan", "A planned young with two open parent places.");

        Map<String, Integer> counts = new HashMap<>();
        for (String kind : kinds) counts.put(kind, plans(kind).size());
        float sw = PetTree.speciesW(counts.values());
        float pitch = sw + PetTree.SPECIES_GAP;
        int fit = Math.max(1, (int) Math.floor((nx - PetTree.TOGGLE_GAP - 2f + PetTree.SPECIES_GAP) / pitch));
        if (kinds.size() > fit) {
            kinds = new ArrayList<>(kinds.subList(0, fit));
            if (!kinds.contains(view.planSpecies)) kinds.set(fit - 1, view.planSpecies);
        }

        float x = 2f;
        for (String kind : kinds) {
            PetData data = PetDataRepo.get(kind);
            int count = counts.get(kind);
            PetTree.speciesToggle(p, x, y, sw, data, count, count > 0 ? Ui.hl() : Ui.gray(), kind.equals(view.planSpecies), data.species + ": " + count + (count == 1 ? " plan." : " plans."), () -> {
                view.planSpecies = kind;
                view.planSelected = null;
                view.planX = 0f;
                closeLists();
                view.scroll.reset(PetsView.PLAN);
                panel.refresh();
            });
            x += pitch;
        }
    }

    // every brood of the planner leads to a planned young, so every line is dashed
    private PetTreeCanvas canvas(PetTreeLayout.Result result, String focus, Set<String> gold, PetPlan selected) {
        return new PetTreeCanvas(result, PetTreeLayout.FULL, false, gold, selected != null ? selected.id : null, new PetTreeCanvas.Owner() {
            @Override
            public void tile(CustomPanelAPI world, Node n, float x, float y) {
                boolean on = n.id.equals(focus);
                if (n.id.startsWith(OPEN)) empty(world, x, y, n.id, on);
                else if (PetPlan.isPlan(n.id)) {
                    PetPlan plan = manager.getPlan(n.id);
                    if (plan != null) planTile(world, x, y, plan, on);
                } else {
                    Pet pet = manager.get(n.id);
                    if (pet != null) tree.tile(world, x, y, pet, on, false, () -> clickPet(pet));
                }
            }

            @Override
            public float height(Node n) {
                return PetTree.TILE_H;
            }

            @Override
            public boolean dashed(Brood brood) {
                return true;
            }

            @Override
            public boolean hovers(String id) {
                return !id.startsWith(OPEN);
            }

            @Override
            public float getX() {
                return view.planX;
            }

            @Override
            public void setX(float x) {
                view.planX = x;
            }

            @Override
            public void emptyClick() {
                if (view.planPlace < 0 && !view.planCopy) return;
                closeLists();
                panel.refresh();
            }
        }, view.scroll, PetsView.PLAN);
    }

    // a planned young in a place of the clicked plan opens that place first, a second click its own card
    private void clickPlan(PetPlan plan) {
        PetPlan selected = manager.getPlan(view.planSelected);
        int place = selected != null && selected != plan ? placeOf(selected, plan.id) : -1;
        if (place >= 0 && view.planPlace != place) openPlace(selected, place);
        else select(plan);
    }

    // a pet is on the planner only in a place: the place in the clicked plan, else its first one; a second click closes the list
    private void clickPet(Pet pet) {
        PetPlan selected = manager.getPlan(view.planSelected);
        int place = selected != null ? placeOf(selected, pet.id) : -1;
        if (place >= 0) {
            if (view.planPlace == place) {
                closeLists();
                panel.refresh();
            } else openPlace(selected, place);
            return;
        }

        for (PetPlan plan : plans(view.planSpecies)) {
            place = placeOf(plan, pet.id);
            if (place >= 0) {
                openPlace(plan, place);
                return;
            }
        }
    }

    // a planned young: the dashed face, its name, and its chance in place of the hearts
    private void planTile(CustomPanelAPI world, float x, float y, PetPlan plan, boolean focus) {
        float s = PetTreeLayout.FULL.tileW();
        CustomPanelAPI t = UiButtons.row(world, x, y, s, PetTree.TILE_H, focus, true, null, () -> clickPlan(plan));
        PetHusbandry.planFace(t, 8f, 5f, PetTree.PORTRAIT);
        Ui.textIn(t, 2f, 56f, s - 4f, Ui.LINE_H - 2f, Ui.fit(plan.name, Ui.INS15, s - 4f), Ui.INS15, Ui.text(), Ui.CENTER, new String[0], new Color[0]);

        Calc calc = calc(plan);
        String line = calc == null ? "?" : plan.wanted.isEmpty() ? "Any" : chance(calc.chance());
        Color c = calc == null || plan.wanted.isEmpty() ? Ui.gray() : calc.chance() <= 0f ? Ui.neg() : Ui.hl();
        Ui.textIn(t, 2f, 73f, s - 4f, Ui.LINE_H - 2f, line, Ui.INS15, c, Ui.CENTER, new String[0], new Color[0]);
        if (fit(plan) != null) UiImages.img(t, s - 21f, 1f, 20f, 20f, PetCard.quest());
        Ui.tooltip(world, t, PetTree.SHEET_W, TooltipMakerAPI.TooltipLocation.BELOW, tt -> planSheet(tt, plan, calc));
    }

    // an open parent place; a click lists the pets for it, a second click closes the list
    private void empty(CustomPanelAPI world, float x, float y, String id, boolean focus) {
        float s = PetTreeLayout.FULL.tileW();
        float th = PetTree.TILE_H;
        int place = id.charAt(OPEN.length()) - '0';
        PetPlan plan = manager.getPlan(id.substring(OPEN.length() + 2));
        if (plan == null) return;

        PetCard.Click click = new PetCard.Click(() -> {
            if (focus) {
                closeLists();
                panel.refresh();
            } else openPlace(plan, place);
        }, false);
        CustomPanelAPI t = world.createCustomPanel(s, th, click);
        world.addComponent(t).inTL(x, y);
        if (focus) Ui.box(t, 0f, 0f, s, th, Ui.bright(), Misc.scaleAlpha(Ui.dark(), 0.5f));
        else Ui.painted(t, 0f, 0f, s, th, null, (pos, a) -> UiDraw.dashed(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                Misc.scaleAlpha(Ui.base(), click.hover ? 0.6f : 0.35f), a));
        Ui.textIn(t, 0f, 0f, s, th, "Empty", Ui.INS15, focus ? Ui.text() : Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
        Ui.tooltip(world, t, "Empty", "An open parent place of " + plan.name + ". Click to pick a parent.");
    }

    // the hover of a planned young: what is wanted from it and the chance of each
    private void planSheet(TooltipMakerAPI t, PetPlan plan, Calc calc) {
        Parent a = parent(plan.parentA);
        Parent b = parent(plan.parentB);
        String of = a != null && b != null ? "Planned young of " + a.name() + " and " + b.name() + "." : a != null || b != null ? "Planned young of " + (a != null ? a : b).name() + "." : "Planned young, no parents yet.";

        List<String[]> talents = new ArrayList<>();
        List<String[]> bodies = new ArrayList<>();
        for (Map.Entry<String, Integer> e : plan.wanted.entrySet()) {
            String odds = calc != null ? chance(calc.rows().getOrDefault(e.getKey(), 0f)) : "";
            if (e.getKey().equals(MALFORMED)) {
                bodies.add(new String[]{"0", "Malformed", odds});
                continue;
            }
            PetDataRepo.Trait trait = traits.get(e.getKey());
            if (trait == null) continue;
            String grade = trait.hasLevels() || trait.isFlaw() ? String.valueOf(e.getValue()) : "";
            (trait.isTalent() ? talents : bodies).add(new String[]{grade, trait.name(), odds});
        }

        float w = PetTree.SHEET_W;
        float row = PetTree.SHEET_ROW;
        float h = 30f + row + 6f + row;
        if (!talents.isEmpty()) h += 10f + Ui.HEADING_H + 4f + talents.size() * row;
        if (!bodies.isEmpty()) h += 10f + Ui.HEADING_H + 4f + bodies.size() * row;

        CustomPanelAPI s = Global.getSettings().createCustom(w, h, new UiCanvas());
        Ui.text(s, 0f, 4f, w, plan.name, Ui.hl());
        float y = 30f;
        Ui.text(s, 0f, y, w, Ui.fit(plan.getData().species + ". " + of, Ui.INS15, w), Ui.text());
        y += row;
        y = sheetTable(s, y, w, new String[]{"Grade", "Wanted", "Chance"}, talents, false);
        y = sheetTable(s, y, w, new String[]{"Level", "Mutations, flaws", "Chance"}, bodies, true);
        y += 6f;
        if (calc == null) Ui.text(s, 0f, y, w, "Pick both parents.", Ui.gray());
        else {
            String odds = chance(calc.chance());
            Ui.runs(s, 0f, y, w, Ui.INS15, Ui.text(), Ui.LEFT, "Chance per young " + odds + ".", new String[]{odds}, new Color[]{Ui.hl()});
        }
        t.addCustom(s, 0f);
    }

    private static float sheetTable(CustomPanelAPI s, float y, float w, String[] labels, List<String[]> rows, boolean body) {
        if (rows.isEmpty()) return y;

        float row = PetTree.SHEET_ROW;
        y += 10f;
        Ui.thead(s, 0f, y, w, labels, new float[]{0f, 54f, w - 84f}, new float[]{44f, 180f, 80f}, new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});
        y += Ui.HEADING_H + 4f;
        for (String[] r : rows) {
            boolean flaw = body && (r[1].equals("Malformed") || isFlawName(r[1]));
            Ui.textIn(s, 0f, y, 44f, row, r[0], Ui.INS15, flaw ? Ui.neg() : Ui.hl(), Ui.CENTER, new String[0], new Color[0]);
            Ui.textIn(s, 58f, y, 180f, row, r[1], Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            Ui.textIn(s, w - 84f, y, 80f, row, r[2], Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
            y += row;
        }
        return y;
    }

    private static boolean isFlawName(String name) {
        for (PetDataRepo.Trait trait : PetDataRepo.getTraits().values()) if (trait.name().equals(name)) return trait.isFlaw();
        return false;
    }

    // a living young of exactly these two parents with every wanted trait read; an unknown trait does not count as met
    private Pet fit(PetPlan plan) {
        if (plan.parentA == null || plan.parentB == null || PetPlan.isPlan(plan.parentA) || PetPlan.isPlan(plan.parentB) || plan.wanted.isEmpty()) return null;

        Set<String> parents = new HashSet<>(List.of(plan.parentA, plan.parentB));
        for (Pet pet : manager.getPets()) {
            if (pet.isDead() || pet.parentA == null || pet.parentB == null) continue;
            if (parents.equals(new HashSet<>(List.of(pet.parentA, pet.parentB))) && meets(pet, plan)) return pet;
        }
        return null;
    }

    private boolean meets(Pet pet, PetPlan plan) {
        PetSheet seen = view.reveal.seen(pet);
        boolean flaws = view.reveal.flawsRead(pet);
        for (Map.Entry<String, Integer> e : plan.wanted.entrySet()) {
            if (e.getKey().equals(MALFORMED)) {
                if (!flaws || pet.sheet.malformed) return false;
                continue;
            }
            PetDataRepo.Trait trait = traits.get(e.getKey());
            if (trait == null) continue;
            int level = seen.getGrade(trait.id());
            if (trait.isFlaw() ? !flaws || level > e.getValue() : level < e.getValue()) return false;
        }
        return true;
    }

    private void card(CustomPanelAPI p, float w, float h, PetPlan plan) {
        view.planField = PetCard.nameField(p, 0f, 0f, w, plan.name);
        view.planNamed = plan.id;

        Parent a = parent(plan.parentA);
        Parent b = parent(plan.parentB);
        boolean both = a != null && b != null;
        Calc calc = both ? calc(plan, a, b) : null;
        strip(p, w, plan, a, b, calc);

        float y = STRIP_Y + STRIP_H;
        Pet fit = fit(plan);
        if (fit != null) {
            fits(p, w, y + 8f, plan, fit, a, b);
            y += 8f + FIT_H;
        }

        float frame = h - FRAME_UP;
        float end = wanted(p, w, y + PetsPanel.HEAD_GAP, frame - PetsPanel.HEAD_GAP - ADD_H - PetsPanel.HEAD_GAP, plan, calc, both);
        float by = end + PetsPanel.HEAD_GAP;
        ButtonAPI add = UiButtons.button(p, 4f, by, ADD_W, ADD_H, "Add trait...", UiButtons.Kind.STD, CutStyle.TL_BR, () -> panel.popup(new TraitDialog(plan, candidates(plan.typeID), id -> {
            view.planRow = id;
            view.scroll.reset(PetsView.WANTED);
        })));
        Ui.tooltip(p, add, "Add trait", "A trait you want from " + plan.name + ".");
        ButtonAPI copy = UiButtons.button(p, 10f + ADD_W, by, ADD_W, ADD_H, "Copy pet...", UiButtons.Kind.STD, CutStyle.TL_BR, () -> {
            view.planCopy = true;
            view.planPlace = -1;
            view.scroll.reset(PetsView.PLAN_LIST);
            panel.refresh();
        });
        Ui.tooltip(p, copy, "Copy pet", "Takes the known traits of a pet as what is wanted, in place of the rows here.");

        if (both) flow(p, w, frame, plan, a, b);
        else best(p, w, frame, h - CHANCE_UP - 10f, plan, a != null ? a : b);
        result(p, w, h, plan, calc);
        bottom(p, w, h, plan, a, b);
    }

    private void strip(CustomPanelAPI p, float w, PetPlan plan, Parent a, Parent b, Calc calc) {
        Ui.card(p, 0f, STRIP_Y, w, STRIP_H);
        PetHusbandry.planFace(p, 3f, STRIP_Y + 3f, FACE);
        Ui.text(p, 56f, STRIP_Y + 7f, w - 56f - 158f, plan.getData().species);
        String parents = a != null && b != null ? a.name() + " x " + b.name() : a != null ? a.name() : b != null ? b.name() : "No parents";
        Ui.text(p, 56f, STRIP_Y + 26f, w - 56f - 158f, parents, Ui.gray());
        if (calc == null) return;

        String inbreeding = PetTree.percent(calc.inbreeding());
        Ui.textIn(p, w - 158f, STRIP_Y + 6f, 150f, Ui.LINE_H, "Inbreeding " + inbreeding, Ui.INS15, Ui.gray(), Ui.RIGHT,
                new String[]{inbreeding}, new Color[]{calc.inbreeding() > 0f ? Ui.neg() : Ui.hl()});
        String sum = PetHusbandry.percent(PetGenetics.sumChance(plan.getData(), a.hearts() + b.hearts()));
        Ui.textIn(p, w - 158f, STRIP_Y + 25f, 150f, Ui.LINE_H, "Sum chance " + sum, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[]{sum}, new Color[]{Ui.hl()});
    }

    // a young of the planned cross with every wanted trait; Use puts it in place of the planned young
    private void fits(CustomPanelAPI p, float w, float y, PetPlan plan, Pet pet, Parent a, Parent b) {
        Ui.card(p, 0f, y, w, FIT_H);
        CustomPanelAPI icon = UiImages.img(p, 8f, y + 10f, 20f, 20f, PetCard.quest());
        Ui.tooltip(p, icon, null, "A young fits. " + pet.name + " has every wanted trait.");
        Ui.textIn(p, 38f, y, 110f, FIT_H, Ui.fit(pet.name + " fits", Ui.INS15, 110f), Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        float lw = w - 150f - SIDE_W - 16f;
        Ui.textIn(p, 150f, y, lw, FIT_H, Ui.fit("Young of " + a.name() + " and " + b.name(), Ui.INS15, lw), Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        ButtonAPI use = UiButtons.button(p, w - SIDE_W - 8f, y + 8f, SIDE_W, PetCard.BUTTON_H, "Use", UiButtons.Kind.STD, CutStyle.TL_BR, () -> use(plan, pet));
        Ui.tooltip(p, use, "Use", pet.name + " takes the place of " + plan.name + ".");
    }

    private void use(PetPlan plan, Pet pet) {
        manager.usePlan(plan, pet);
        view.planSelected = null;
        for (PetPlan other : plans(plan.typeID)) {
            if (placeOf(other, pet.id) >= 0) {
                view.planSelected = other.id;
                break;
            }
        }
        view.planRow = null;
        closeLists();
        panel.refresh();
    }

    // the row drawn as the flow chart: the one clicked, else the first wanted trait, else the look
    private String selectedRow(PetPlan plan) {
        if (LOOK.equals(view.planRow) || plan.wanted.containsKey(view.planRow)) return view.planRow;
        return plan.wanted.isEmpty() ? LOOK : plan.wanted.keySet().iterator().next();
    }

    // the Look row, then a row per wanted trait with its stepper; the rows scroll once they reach limit; returns the y under them
    private float wanted(CustomPanelAPI p, float w, float y, float limit, PetPlan plan, Calc calc, boolean flow) {
        float rw = w - UiScroll.GUTTER;
        float cx = rw - CHANCE_W - 8f;
        Ui.thead(p, 0f, y, w, new String[]{"Wanted", "Trait", "Effect", "Chance"}, new float[]{8f, TRAIT_X, EFFECT_X, cx},
                new float[]{100f, EFFECT_X - TRAIT_X - 8f, cx - EFFECT_X - 8f, CHANCE_W}, new Alignment[]{Ui.LEFT, Ui.LEFT, Ui.LEFT, Ui.RIGHT});
        CustomPanelAPI head = Ui.panel(p, 0f, y, 100f, Ui.HEADING_H);
        Ui.tooltip(p, head, "Wanted", "This grade or better. For a flaw: this level or less.");
        y += Ui.HEADING_H + 6f;

        List<String> keys = new ArrayList<>();
        keys.add(LOOK);
        keys.addAll(plan.wanted.keySet());
        int room = Math.max(1, (int) Math.floor((limit - y + WANT_ROW - WANT_H) / WANT_ROW));
        float listH = Math.min(keys.size(), room) * WANT_ROW - (WANT_ROW - WANT_H);
        String selected = flow ? selectedRow(plan) : null;

        UiScroll.Column rows = UiScroll.list(p, 0f, y, w, listH, view.scroll, PetsView.WANTED);
        for (int i = 0; i < keys.size(); i++) {
            CustomPanelAPI b = rows.block(WANT_H, i == 0 ? 0f : WANT_ROW - WANT_H);
            wantRow(b, rows.width(), plan, keys.get(i), keys.get(i).equals(selected), calc);
        }
        rows.end();
        return y + listH;
    }

    private void wantRow(CustomPanelAPI b, float rw, PetPlan plan, String key, boolean selected, Calc calc) {
        CustomPanelAPI row = UiButtons.row(b, 0f, 0f, rw, WANT_H, selected, true, null, () -> {
            view.planRow = key;
            panel.refresh();
        });
        float cx = rw - CHANCE_W - 8f;
        float ew = cx - EFFECT_X - 8f;

        if (key.equals(LOOK)) {
            stepper(row, "Any", Ui.gray(), null, null);
            Ui.textIn(row, TRAIT_X, 0f, EFFECT_X - TRAIT_X - 8f, WANT_H, "Look", Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            Ui.tooltip(row, row, "Look", "Any look counts.");
            return;
        }

        int v = plan.wanted.get(key);
        String odds = calc != null ? chance(calc.rows().getOrDefault(key, 0f)) : "";
        Color oddsColor = calc != null && calc.rows().getOrDefault(key, 0f) <= 0f ? Ui.neg() : Ui.hl();
        Ui.textIn(row, cx, 0f, CHANCE_W, WANT_H, odds, Ui.INS15, oddsColor, Ui.RIGHT, new String[0], new Color[0]);

        if (key.equals(MALFORMED)) {
            stepper(row, "None", Ui.hl(), () -> change(plan, key, -1, 0), null);
            Ui.textIn(row, TRAIT_X, 0f, EFFECT_X - TRAIT_X - 8f, WANT_H, "Malformed", Ui.INS15, Ui.neg(), Ui.LEFT, new String[0], new Color[0]);
            Ui.textIn(row, EFFECT_X, 0f, ew, WANT_H, "None", Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
            Ui.tooltip(row, row, "Malformed", "A young of related parents can come out with one new flaw, or one more level of a flaw.", "Wanted: none.");
            return;
        }

        PetDataRepo.Trait trait = traits.get(key);
        if (trait == null) return;
        int min = trait.isFlaw() ? 0 : 1;
        String value = trait.isFlaw() && v == 0 ? "None" : trait.hasLevels() ? String.valueOf(v) : trait.isFlaw() ? "Any" : "Yes";
        boolean up = trait.hasLevels() && v < MAX_WANT;
        stepper(row, value, Ui.hl(), () -> change(plan, key, v - 1, min), up ? () -> change(plan, key, v + 1, min) : null);
        Ui.textIn(row, TRAIT_X, 0f, EFFECT_X - TRAIT_X - 8f, WANT_H, Ui.fit(trait.name(), Ui.INS15, EFFECT_X - TRAIT_X - 8f), Ui.INS15,
                trait.isFlaw() ? Ui.neg() : Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        Ui.textIn(row, EFFECT_X, 0f, ew, WANT_H, Ui.fit(effect(trait, v), Ui.INS15, ew), Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
        Ui.tooltip(row, row, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> wantTip(t, trait, v));
    }

    private static String effect(PetDataRepo.Trait trait, int v) {
        if (trait.isFlaw() && v == 0) return "None";
        String value = trait.format(trait.value(v, 1f));
        return value.isEmpty() ? trait.effect() : trait.effect() + " " + value;
    }

    // minus, the wanted value, plus; a null action greys its button
    private static void stepper(CustomPanelAPI p, String value, Color color, Runnable minus, Runnable plus) {
        UiButtons.button(p, 4f, 1f, STEP, STEP, "-", minus != null ? UiButtons.Kind.STD : UiButtons.Kind.OFF, minus);
        Ui.textIn(p, VALUE_X, 0f, VALUE_W, WANT_H, value, Ui.INS15, color, Ui.CENTER, new String[0], new Color[0]);
        UiButtons.button(p, PLUS_X, 1f, STEP, STEP, "+", plus != null ? UiButtons.Kind.STD : UiButtons.Kind.OFF, plus);
    }

    // minus under the lowest value takes the row out
    private void change(PetPlan plan, String key, int v, int min) {
        if (v < min) plan.wanted.remove(key);
        else plan.wanted.put(key, v);
        panel.refresh();
    }

    private static void wantTip(TooltipMakerAPI t, PetDataRepo.Trait trait, int v) {
        Color c = trait.isFlaw() ? Ui.neg() : Ui.hl();
        t.addPara(trait.name(), c, 0f);
        if (trait.hasLevels()) t.addPara(trait.desc(), 10f, c, trait.format(trait.value(trait.per(), 1f)));
        else t.addPara(trait.desc(), 10f);
        for (String id : trait.with()) {
            PetDataRepo.Trait flaw = PetDataRepo.getTrait(id);
            if (flaw == null) continue;
            String per = flaw.format(flaw.value(flaw.per(), 1f));
            String what = per.isEmpty() ? Misc.lcFirst(flaw.effect()) + "." : Misc.lcFirst(flaw.effect()) + " " + per + " per level.";
            t.addPara("Arrives with the flaw %s: " + esc(what), 10f, new Color[]{Ui.neg(), Ui.neg()}, flaw.name(), per);
        }

        String value = trait.format(trait.value(v, 1f));
        String what = value.isEmpty() ? "" : ", " + Misc.lcFirst(trait.effect()) + " " + value;
        if (trait.isFlaw() && v == 0) t.addPara("Wanted: none.", 10f);
        else if (trait.isFlaw()) t.addPara("Wanted: level %s or less" + esc(what) + ".", 10f, new Color[]{Ui.hl(), Ui.neg()}, String.valueOf(v), value);
        else if (!trait.hasLevels()) t.addPara("Wanted: has it.", 10f);
        else t.addPara("Wanted: " + (trait.isTalent() ? "grade" : "level") + " %s or better" + esc(what) + ".", 10f, Ui.hl(), String.valueOf(v), value);
    }

    // addPara formats its text: effect values like "+15%" are text, not a conversion
    private static String esc(String s) {
        return s.replace("%", "%%");
    }

    // both parents stand: the selected row as the cross card draws it, the results that meet the target in gold, a click sets the grade
    private void flow(CustomPanelAPI p, float w, float y, PetPlan plan, Parent a, Parent b) {
        String key = selectedRow(plan);
        PetData data = plan.getData();
        if (key.equals(LOOK)) {
            CrossRow row = PetHusbandry.lookRow(a.name(), b.name());
            PetHusbandry.flow(p, w, y, FLOW_LINK, row, node(a, row.lineA()), node(b, row.lineB()), r -> true, null);
            return;
        }
        if (key.equals(MALFORMED)) {
            float inbreeding = PetShop.getInbreeding(a.id(), b.id());
            String kin = inbreeding > 0f ? kinOf(a.id(), b.id(), b.name()) : "Not related";
            String kinB = inbreeding > 0f ? kinOf(b.id(), a.id(), a.name()) : "Not related";
            CrossRow row = PetHusbandry.malformedRow(PetGenetics.malformedChance(data, inbreeding), kin, kinB);
            PetHusbandry.flow(p, w, y, FLOW_LINK, row, node(a, row.lineA()), node(b, row.lineB()), r -> r.lane().equals(PetGenetics.NONE), null);
            return;
        }

        PetDataRepo.Trait trait = traits.get(key);
        if (trait == null) return;
        int la = a.sheet().getGrade(key);
        int lb = b.sheet().getGrade(key);
        float sum = PetGenetics.sumChance(data, a.hearts() + b.hearts());
        CrossRow row = PetHusbandry.traitRow(trait, la, lb, care, sum, a.name(), b.name(), line(trait, a, la), line(trait, b, lb));
        int want = plan.wanted.get(key);
        PetHusbandry.flow(p, w, y, FLOW_LINK, row, node(a, row.lineA()), node(b, row.lineB()), r -> {
            int level = level(r, trait);
            return trait.isFlaw() ? level <= want : level >= want;
        }, r -> {
            int level = level(r, trait);
            if (!trait.isFlaw() && level <= 0) return;
            plan.wanted.put(key, level);
            panel.refresh();
        });
    }

    private static int level(Result r, PetDataRepo.Trait trait) {
        if (r.lane().equals(PetGenetics.NONE)) return 0;
        return trait.hasLevels() ? Integer.parseInt(r.value()) : 1;
    }

    private FlowNode node(Parent parent, String line) {
        if (parent.plan() != null) return new FlowNode(parent.name(), null, false, null, line);
        Pet pet = parent.pet();
        return new FlowNode(pet.name, pet.getData().icon, unknown(pet) > 0, pet, line);
    }

    private int unknown(Pet pet) {
        int n = 0;
        for (String id : pet.getTalents()) if (!view.reveal.known(pet, id)) n++;
        return n;
    }

    // a parent in the flow chart: a pet's grade or Unknown, a planned young's wanted value
    private String line(PetDataRepo.Trait trait, Parent parent, int level) {
        if (parent.plan() != null) {
            Integer v = parent.plan().wanted.get(trait.id());
            if (v == null || trait.isFlaw() && v == 0) return "None";
            if (!trait.hasLevels()) return trait.isFlaw() ? "Any" : "Has it";
            return trait.isFlaw() ? "Level " + v + " or less" : (trait.isTalent() ? "Grade " : "Level ") + v + " or more";
        }

        if (level > 0) return trait.hasLevels() ? (trait.isTalent() ? "Grade " : "Level ") + level : "Has it";
        Pet pet = parent.pet();
        boolean hidden = trait.isFlaw() ? !view.reveal.flawsRead(pet) : trait.isTalent() && unknown(pet) > 0;
        return hidden ? "Unknown" : "None";
    }

    // how a pet or planned young stands to the other parent; only asked for kin
    private String kinOf(String id, String other, String otherName) {
        String[] mine = manager.parentsOf(id);
        String[] theirs = manager.parentsOf(other);
        if (theirs != null && (id.equals(theirs[0]) || id.equals(theirs[1]))) return "Parent of " + otherName;
        if (mine != null && (other.equals(mine[0]) || other.equals(mine[1]))) return "Young of " + otherName;
        int shared = shared(mine, theirs);
        return shared == 2 ? "Sibling of " + otherName : shared == 1 ? "Half sibling of " + otherName : "Kin of " + otherName;
    }

    private String kinPair(String a, String b) {
        String[] pa = manager.parentsOf(a);
        String[] pb = manager.parentsOf(b);
        if (pb != null && (a.equals(pb[0]) || a.equals(pb[1])) || pa != null && (b.equals(pa[0]) || b.equals(pa[1]))) return "Parent and young";
        int shared = shared(pa, pb);
        return shared == 2 ? "Siblings" : shared == 1 ? "Half siblings" : "Kin";
    }

    private static int shared(String[] a, String[] b) {
        if (a == null || b == null) return 0;
        int n = 0;
        for (String x : a) if (x != null && (x.equals(b[0]) || x.equals(b[1]))) n++;
        return n;
    }

    // a place is open: the pairs of your pets that give the target best, or the best partners for the one that stands
    private void best(CustomPanelAPI p, float w, float y, float bottom, PetPlan plan, Parent fixed) {
        List<Pet> pets = candidates(plan.typeID);
        String note = "of your " + pets.size() + (pets.size() == 1 ? " pet" : " pets");
        Ui.heading(p, 0f, y, w, fixed == null ? "Best pairs" : "Best partners for " + fixed.name(), note);
        y += Ui.HEADING_H + 4f;

        if (fixed != null && fixed.pet() != null) pets.remove(fixed.pet());
        String empty = fixed == null && pets.isEmpty() ? "You have no pets of this species."
                : fixed == null && pets.size() < 2 ? "A pair needs two pets of this species. You have 1."
                : pets.isEmpty() ? "You have no other pets of this species."
                : plan.wanted.isEmpty() ? "Nothing wanted yet." : null;
        if (empty != null) {
            Ui.text(p, 0f, y + 2f, w, empty, Ui.gray());
            return;
        }

        List<Match> matches = new ArrayList<>();
        if (fixed == null) {
            for (int i = 0; i < pets.size(); i++) {
                for (int j = i + 1; j < pets.size(); j++) matches.add(match(plan, parent(pets.get(i)), parent(pets.get(j)), null));
            }
        } else for (Pet pet : pets) matches.add(match(plan, fixed, parent(pet), pet));

        boolean none = matches.stream().noneMatch(m -> m.calc().chance() > 0f);
        Comparator<Match> order = none ? Comparator.comparingLong((Match m) -> -m.calc().rows().values().stream().filter(c -> c > 0f).count())
                .thenComparingDouble(m -> -lowest(m.calc()))
                : Comparator.comparingDouble((Match m) -> -m.calc().chance());
        matches.sort(order.thenComparing(m -> m.a().name() + m.b().name(), String.CASE_INSENSITIVE_ORDER));
        if (matches.size() > MAX_PAIRS) matches = new ArrayList<>(matches.subList(0, MAX_PAIRS));

        if (none) {
            Ui.text(p, 0f, y + 2f, w, "No pair gives all of this. The closest:", Ui.gray());
            y += Ui.LINE_H + 6f;
        }

        int rows = Math.max(1, (int) Math.floor((bottom - y + ROW_GAP) / (ROW_H + ROW_GAP)));
        float listH = Math.min(rows, matches.size()) * (ROW_H + ROW_GAP) - ROW_GAP;
        UiScroll.Column list = UiScroll.list(p, 0f, y, w, listH, view.scroll, PetsView.BEST);
        for (int i = 0; i < matches.size(); i++) matchRow(list.block(ROW_H, i == 0 ? 0f : ROW_GAP), list.width(), plan, matches.get(i), none);
        list.end();
    }

    private Match match(PetPlan plan, Parent a, Parent b, Pet listed) {
        Calc calc = calc(plan, a, b);
        String kin = null;
        if (calc.inbreeding() > 0f) {
            String malformed = ", malformed " + PetHusbandry.percent(PetGenetics.malformedChance(plan.getData(), calc.inbreeding()));
            kin = (listed != null ? kinOf(b.id(), a.id(), a.name()) : kinPair(a.id(), b.id())) + malformed;
        }
        return new Match(a, b, listed, calc, kin);
    }

    private static float lowest(Calc calc) {
        float low = 1f;
        for (float c : calc.rows().values()) low = Math.min(low, c);
        return low;
    }

    private void matchRow(CustomPanelAPI b, float w, PetPlan plan, Match m, boolean none) {
        CustomPanelAPI row = UiButtons.row(b, 0f, 0f, w, ROW_H, false, true, null, () -> {
            if (m.listed() == null) {
                plan.parentA = m.a().id();
                plan.parentB = m.b().id();
            } else plan.setParent(plan.parentA == null ? 0 : 1, m.listed().id);
            panel.refresh();
        });

        float x = 56f;
        if (m.listed() != null) UiImages.img(row, 3f, 3f, FACE, FACE, m.listed().getData().icon);
        else {
            UiImages.img(row, 3f, 3f, FACE, FACE, m.a().pet().getData().icon);
            UiImages.img(row, 51f, 3f, FACE, FACE, m.b().pet().getData().icon);
            x = 104f;
        }

        String name = m.listed() != null ? m.listed().name : m.a().name() + " x " + m.b().name();
        float chance = m.calc().chance();
        Ui.text(row, x, 6f, w - x - 100f, name);
        Ui.textIn(row, w - 98f, 4f, 90f, Ui.LINE_H, chance(chance), Ui.INS15, chance > 0f ? Ui.hl() : Ui.neg(), Ui.RIGHT, new String[0], new Color[0]);

        String line;
        Color lineColor;
        if (none) {
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, Float> e : m.calc().rows().entrySet()) if (e.getValue() <= 0f) missing.add(wantName(plan, e.getKey()));
            line = "Cannot give: " + String.join(", ", missing);
            lineColor = Ui.neg();
        } else {
            String key = null;
            float low = 2f;
            for (Map.Entry<String, Float> e : m.calc().rows().entrySet()) {
                if (e.getValue() < low) {
                    low = e.getValue();
                    key = e.getKey();
                }
            }
            line = key != null ? "Lowest: " + wantName(plan, key) + " " + chance(low) : "";
            lineColor = Ui.gray();
        }

        float kw = m.kin() != null ? Math.min(Ui.width(m.kin(), Ui.INS15) + 2f, w - x - 120f) : 0f;
        if (m.kin() != null) Ui.textIn(row, w - 8f - kw, 24f, kw, Ui.LINE_H, Ui.fit(m.kin(), Ui.INS15, kw), Ui.INS15, Ui.neg(), Ui.RIGHT, new String[0], new Color[0]);
        float lw = w - x - 8f - (kw > 0f ? kw + 12f : 0f);
        Ui.textIn(row, x, 24f, lw, Ui.LINE_H, Ui.fit(line, Ui.INS15, lw), Ui.INS15, lineColor, Ui.LEFT, new String[0], new Color[0]);
    }

    // a wanted row by name: Scavenger 9 for a grade, Malformed, a flaw or a mutation by name
    private String wantName(PetPlan plan, String key) {
        if (key.equals(MALFORMED)) return "Malformed";
        PetDataRepo.Trait trait = traits.get(key);
        if (trait == null) return key;
        return trait.hasLevels() && !trait.isFlaw() ? trait.name() + " " + plan.wanted.get(key) : trait.name();
    }

    private void result(CustomPanelAPI p, float w, float h, PetPlan plan, Calc calc) {
        float y = h - CHANCE_UP;
        CustomPanelAPI label = Ui.panel(p, 0f, y, w, 24f);
        Ui.textIn(p, 0f, y, 200f, 24f, "Chance per young", Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        Ui.textIn(p, w - 140f, y, 140f, 24f, calc == null ? "?" : chance(calc.chance()), Ui.INS21, Ui.hl(), Ui.RIGHT, new String[0], new Color[0]);
        UiData.thinbar(p, 0f, y + 25f, w, calc == null ? 0f : calc.chance(), Ui.hl());
        String careLine = "Care " + Math.round(care) + (center != null ? ", from the Animal Center on " + center.getMarket().getName() + "." : ". You have no Animal Center.");
        Ui.tooltip(p, label, "Chance per young", "Every wanted trait has to come up in the same young.", careLine);

        y += 32f;
        List<String> before = new ArrayList<>();
        for (String id : new String[]{plan.parentA, plan.parentB}) {
            PetPlan parent = manager.getPlan(id);
            if (parent != null) before.add(parent.name);
        }
        String name = before.isEmpty() ? "Expected" : "Expected, with " + String.join(" and ", before);
        Ui.textIn(p, 0f, y, 220f, Ui.LINE_H, Ui.fit(name, Ui.INS15, 220f), Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);

        Expect e = calc == null ? null : expected(plan, new HashSet<>());
        if (e == null || e.broods() < 0) {
            Ui.textIn(p, w - 200f, y, 200f, Ui.LINE_H, "-", Ui.INS15, Ui.text(), Ui.RIGHT, new String[0], new Color[0]);
            return;
        }
        String broods = String.valueOf(e.broods());
        String days = String.valueOf(Math.round(e.days()));
        Ui.textIn(p, w - 240f, y, 240f, Ui.LINE_H, "about " + broods + (e.broods() == 1 ? " brood, " : " broods, ") + days + " days", Ui.INS15, Ui.text(), Ui.RIGHT,
                new String[]{broods, days}, new Color[]{Ui.hl(), Ui.hl()});
    }

    // broods on the average brood size until one young fits; a planned parent adds its own broods and the days it takes to grow up
    private Expect expected(PetPlan plan, Set<String> path) {
        if (!path.add(plan.id)) return null;

        Parent a = parent(plan.parentA);
        Parent b = parent(plan.parentB);
        if (a == null || b == null) return null;

        PetData data = plan.getData();
        int broods = PetGenetics.broods(calc(plan, a, b).chance(), PetGenetics.broodRange(data, a.sheet(), b.sheet(), traits));
        if (broods < 0) return new Expect(-1, 0f);
        float days = broods * PetGenetics.broodDays(data, a.sheet(), b.sheet(), traits);

        for (Parent parent : List.of(a, b)) {
            if (parent.plan() == null) continue;
            Expect step = expected(parent.plan(), path);
            if (step == null || step.broods() < 0) return step;
            broods += step.broods();
            days += step.days() + PetGenetics.youngDays(data, parent.sheet(), traits);
        }
        return new Expect(broods, days);
    }

    private void bottom(CustomPanelAPI p, float w, float h, PetPlan plan, Parent a, Parent b) {
        Ui.rule(p, 0f, h - 36f, w);
        float y = h - PetCard.BUTTON_H;
        String reason = a == null || b == null ? "Pick both parents." : a.plan() != null ? a.name() + " is not raised yet."
                : b.plan() != null ? b.name() + " is not raised yet." : shop == null ? "Only at an Animal Center." : null;
        ButtonAPI open = UiButtons.button(p, 0f, y, OPEN_W, PetCard.BUTTON_H, "Open cross", reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                reason != null ? null : () -> panel.openCross(a.pet(), b.pet()));
        if (reason != null) Ui.textIn(p, OPEN_W + 8f, y, w - OPEN_W - SIDE_W - 24f, PetCard.BUTTON_H, reason, Ui.INS15, Ui.neg(), Ui.LEFT, new String[0], new Color[0]);
        else Ui.tooltip(p, open, "Open cross", "The pair on the Husbandry tab, where Start brood is.");

        ButtonAPI remove = UiButtons.button(p, w - SIDE_W, y, SIDE_W, PetCard.BUTTON_H, "Remove", UiButtons.Kind.RED, CutStyle.TL_BR, () -> {
            manager.removePlan(plan);
            view.planSelected = null;
            closeLists();
            panel.refresh();
        });
        Ui.tooltip(p, remove, "Remove", "Deletes " + plan.name + ". The places it stood in are open again.");
    }

    // a click on a place: every living pet of the species for it, the first row a new planned young
    private void parentList(CustomPanelAPI p, float w, float h, PetPlan plan, int place) {
        String current = plan.getParent(place);
        Parent other = parent(plan.getParent(1 - place));
        Ui.heading(p, 0f, 0f, w, "Parent of " + plan.name, other != null ? "with " + other.name() : "");

        UiFilter.State filter = view.planParents;
        filter.off.clear();
        if (other == null) filter.off.add(SORT_CHANCE);
        float top = Ui.HEADING_H + 4f;
        top += UiFilter.bar(p, 0f, top, filter, "Name or trait", "Filters and sort order of this list.", PARENT_TOGGLES, null, PARENT_KEYS, panel::filtered, panel::refresh)
                + UiFilter.BELOW;

        List<Pet> all = candidates(plan.typeID);
        if (other != null && other.pet() != null) all.remove(other.pet());
        Map<Pet, Calc> calcs = new HashMap<>();
        List<Pet> shown = new ArrayList<>();
        for (Pet pet : all) {
            Calc calc = null;
            if (other != null) {
                Parent self = parent(pet);
                calc = place == 0 ? calc(plan, self, other) : calc(plan, other, self);
                calcs.put(pet, calc);
            }
            if (!filter.matches(searchText(pet))) continue;
            if (calc != null && filter.on.contains(NO_RELATIVES) && calc.inbreeding() > 0f) continue;
            if (calc != null && filter.on.contains(ABOVE_ZERO) && calc.chance() <= 0f) continue;
            shown.add(pet);
        }
        Comparator<Pet> name = Comparator.comparing(pet -> pet.name, String.CASE_INSENSITIVE_ORDER);
        filter.sort(shown, switch (filter.sortKey()) {
            case SORT_CHANCE -> Comparator.comparingDouble((Pet pet) -> -calcs.get(pet).chance()).thenComparing(name);
            case "Hearts" -> Comparator.comparingDouble((Pet pet) -> -pet.bondDays).thenComparing(name);
            case "Age" -> Comparator.comparingDouble((Pet pet) -> -pet.age).thenComparing(name);
            default -> name;
        });

        float listH = UiScroll.wholeRows(h - 36f - 8f - top, 0f, ROW_H, ROW_GAP);
        UiScroll.Column list = UiScroll.list(p, 0f, top, w, listH, view.scroll, PetsView.PLAN_LIST);
        float lw = list.width();
        CustomPanelAPI first = UiButtons.row(list.block(ROW_H, 0f), 0f, 0f, lw, ROW_H, PetPlan.isPlan(current), true, null, () -> {
            PetPlan young = manager.newPlan(plan.typeID, null, null);
            plan.setParent(place, young.id);
            closeLists();
            panel.refresh();
        });
        PetHusbandry.planFace(first, 3f, 3f, FACE);
        Ui.text(first, 56f, 6f, lw - 64f, "Planned young");
        Ui.text(first, 56f, 24f, lw - 64f, "A pet you do not have yet", Ui.gray());

        if (shown.isEmpty()) Ui.text(list.block(Ui.LINE_H, 8f), 0f, 0f, lw, all.isEmpty() ? "You have no other pets of this species." : "No pet matches.", Ui.gray());
        for (Pet pet : shown) {
            Calc calc = calcs.get(pet);
            String odds = calc != null ? chance(calc.chance()) : null;
            String kin = calc != null && calc.inbreeding() > 0f
                    ? kinOf(pet.id, other.id(), other.name()) + ", malformed " + PetHusbandry.percent(PetGenetics.malformedChance(plan.getData(), calc.inbreeding())) : null;
            petRow(list.block(ROW_H, ROW_GAP), lw, pet, pet.id.equals(current), odds, calc != null && calc.chance() <= 0f ? Ui.neg() : Ui.hl(), kin, Ui.neg(), () -> {
                plan.setParent(place, pet.id);
                closeLists();
                panel.refresh();
            });
        }
        list.end();

        Ui.rule(p, 0f, h - 36f, w);
        ButtonAPI clear = UiButtons.button(p, w - SIDE_W, h - PetCard.BUTTON_H, SIDE_W, PetCard.BUTTON_H, "Clear", current != null ? UiButtons.Kind.RED : UiButtons.Kind.OFF,
                CutStyle.TL_BR, current != null ? () -> {
                    plan.setParent(place, null);
                    panel.refresh();
                } : null);
        Ui.tooltip(p, clear, "Clear", "Empties the place.");
    }

    // Copy pet: any pet of the species in the family tree, gone ones too; a click makes its known traits the target
    private void copyList(CustomPanelAPI p, float w, float h, PetPlan plan) {
        Ui.heading(p, 0f, 0f, w, "Copy a pet", "to " + plan.name);
        UiFilter.State filter = view.planCopies;
        float top = Ui.HEADING_H + 4f;
        top += UiFilter.bar(p, 0f, top, filter, "Name or trait", "Filters and sort order of this list.", COPY_TOGGLES, null, COPY_KEYS, panel::filtered, panel::refresh)
                + UiFilter.BELOW;

        List<Pet> all = new ArrayList<>();
        for (Pet pet : manager.getPets()) if (pet.typeID.equals(plan.typeID)) all.add(pet);
        List<Pet> shown = new ArrayList<>();
        for (Pet pet : all) if (filter.matches(searchText(pet)) && (!filter.on.contains(LIVING) || !pet.isDead())) shown.add(pet);
        Comparator<Pet> name = Comparator.comparing(pet -> pet.name, String.CASE_INSENSITIVE_ORDER);
        filter.sort(shown, switch (filter.sortKey()) {
            case SORT_TALENTS -> Comparator.comparingInt((Pet pet) -> -knownSum(pet)).thenComparing(name);
            case "Hearts" -> Comparator.comparingDouble((Pet pet) -> -pet.bondDays).thenComparing(name);
            case "Age" -> Comparator.comparingDouble((Pet pet) -> -pet.age).thenComparing(name);
            default -> name;
        });

        float listH = UiScroll.wholeRows(h - 36f - 8f - top, 0f, ROW_H, ROW_GAP);
        UiScroll.Column list = UiScroll.list(p, 0f, top, w, listH, view.scroll, PetsView.PLAN_LIST);
        float lw = list.width();
        if (shown.isEmpty()) Ui.text(list.block(Ui.LINE_H, 0f), 0f, 0f, lw, all.isEmpty() ? "No pets of this species." : "No pet matches.", Ui.gray());
        boolean firstRow = true;
        for (Pet pet : shown) {
            petRow(list.block(ROW_H, firstRow ? 0f : ROW_GAP), lw, pet, false, PetHusbandry.WILD_TYPE, Ui.text(), pet.isDead() ? PetTree.goneTag(pet) : null, Ui.gray(), () -> copy(plan, pet));
            firstRow = false;
        }
        list.end();

        Ui.rule(p, 0f, h - 36f, w);
        UiButtons.button(p, w - SIDE_W, h - PetCard.BUTTON_H, SIDE_W, PetCard.BUTTON_H, "Cancel", UiButtons.Kind.STD, CutStyle.TL_BR, () -> {
            closeLists();
            panel.refresh();
        });
    }

    // talents and mutations at their known grade, read flaws at their level; unknown traits are left out
    private void copy(PetPlan plan, Pet pet) {
        PetSheet seen = view.reveal.seen(pet);
        List<PetDataRepo.Trait> list = new ArrayList<>();
        for (String id : seen.traits.keySet()) {
            PetDataRepo.Trait trait = traits.get(id);
            if (trait != null) list.add(trait);
        }
        list.sort(Comparator.comparingInt((PetDataRepo.Trait t) -> t.isTalent() ? 0 : t.isMutation() ? 1 : 2).thenComparingInt(t -> -seen.getGrade(t.id())));

        plan.wanted.clear();
        for (PetDataRepo.Trait trait : list) plan.wanted.put(trait.id(), seen.getGrade(trait.id()));
        view.planRow = null;
        view.scroll.reset(PetsView.WANTED);
        closeLists();
        panel.refresh();
    }

    private int knownSum(Pet pet) {
        int sum = 0;
        PetSheet seen = view.reveal.seen(pet);
        for (String id : seen.traits.keySet()) {
            PetDataRepo.Trait trait = traits.get(id);
            if (trait != null && trait.isTalent()) sum += seen.getGrade(id);
        }
        return sum;
    }

    private String[] searchText(Pet pet) {
        List<String> text = new ArrayList<>(List.of(pet.name));
        text.addAll(pet.getKnownTraitNames());
        return text.toArray(new String[0]);
    }

    // known talents best first, then mutations
    private String traitsLine(Pet pet) {
        PetSheet seen = view.reveal.seen(pet);
        List<PetDataRepo.Trait> list = new ArrayList<>();
        for (String id : seen.traits.keySet()) {
            PetDataRepo.Trait trait = traits.get(id);
            if (trait != null && !trait.isFlaw()) list.add(trait);
        }
        if (list.isEmpty()) return unknown(pet) > 0 ? "Unknown" : "No talents";

        list.sort(Comparator.comparingInt((PetDataRepo.Trait t) -> t.isTalent() ? 0 : 1).thenComparingInt(t -> -seen.getGrade(t.id())));
        List<String> parts = new ArrayList<>();
        for (PetDataRepo.Trait trait : list) parts.add(trait.hasLevels() ? trait.name() + " " + seen.getGrade(trait.id()) : trait.name());
        return String.join(", ", parts);
    }

    // portrait, name and hearts; known traits below; at the right a value and a note under it
    private void petRow(CustomPanelAPI b, float w, Pet pet, boolean selected, String value, Color valueColor, String note, Color noteColor, Runnable click) {
        CustomPanelAPI row = UiButtons.row(b, 0f, 0f, w, ROW_H, selected, true, null, click);
        boolean gone = pet.isDead();
        UiImages.img(row, 3f, 3f, FACE, FACE, pet.getData().icon, null, gone ? PetCard.DIM : 1f);
        Ui.text(row, 56f, 6f, HEARTS_X - 56f - 8f, pet.name, gone ? Ui.gray() : Ui.text());
        if (!gone) PetCard.hearts(row, HEARTS_X, 11f, pet, 9f, 2f);
        if (value != null) Ui.textIn(row, w - 108f, 4f, 100f, Ui.LINE_H, value, Ui.INS15, valueColor, Ui.RIGHT, new String[0], new Color[0]);

        float nw = note != null ? Math.min(Ui.width(note, Ui.INS15) + 2f, w - 56f - 120f) : 0f;
        if (note != null) Ui.textIn(row, w - 8f - nw, 24f, nw, Ui.LINE_H, Ui.fit(note, Ui.INS15, nw), Ui.INS15, noteColor, Ui.RIGHT, new String[0], new Color[0]);
        float tw = w - 56f - 8f - (note != null ? nw + 12f : 0f);
        Ui.textIn(row, 56f, 24f, tw, Ui.LINE_H, Ui.fit(traitsLine(pet), Ui.INS15, tw), Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
    }

    // Add trait: talents, then mutations and flaws, the ones you have seen with a name; the rest are ??? rows with no click
    static final class TraitDialog extends BaseCustomDialogDelegate {

        static final float W = 560f;
        static final float H = 400f;
        static final float HEAD_Y = 34f;
        static final float ROW = 24f;
        static final float PITCH = 28f;
        static final float PER_X = 180f;
        static final float BEST_W = 140f;

        private final PetPlan plan;
        private final List<Pet> pets;
        private final Consumer<String> onAdd;
        private final Set<String> seen = PetStatusManager.getInstance().getSeenTraits();
        private final UiScroll.Offsets scroll = new UiScroll.Offsets();
        private InteractionDialogAPI dialog;
        private CustomDialogCallback callback;

        TraitDialog(PetPlan plan, List<Pet> pets, Consumer<String> onAdd) {
            this.plan = plan;
            this.pets = pets;
            this.onAdd = onAdd;
        }

        void show(Runnable onClose) {
            UiHost.over(d -> {
                dialog = d;
                d.showCustomDialog(W, H, this);
            }, onClose);
        }

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
            this.callback = callback;
            new UiRoot(panel, scroll, this::build).refresh();
        }

        private void build(CustomPanelAPI p, float w, float h) {
            Ui.runs(p, 0f, 0f, w, Ui.INS21, Ui.text(), Ui.LEFT, "Add a trait to " + plan.name, new String[]{plan.name}, new Color[]{Ui.hl()});

            List<PetDataRepo.Trait> talents = new ArrayList<>();
            List<PetDataRepo.Trait> bodies = new ArrayList<>();
            for (PetDataRepo.Trait trait : PetDataRepo.getTraits().values()) {
                if (plan.wanted.containsKey(trait.id()) || !trait.isFlaw() && trait.weight() <= 0f) continue;
                (trait.isTalent() ? talents : bodies).add(trait);
            }
            Comparator<PetDataRepo.Trait> order = Comparator.comparing((PetDataRepo.Trait t) -> !seen.contains(t.id()))
                    .thenComparing(PetDataRepo.Trait::isFlaw).thenComparing(PetDataRepo.Trait::name, String.CASE_INSENSITIVE_ORDER);
            talents.sort(order);
            bodies.sort(order);

            UiScroll.Column list = UiScroll.list(p, 0f, HEAD_Y, w, h - HEAD_Y, scroll, "traits");
            float lw = list.width();
            if (!talents.isEmpty()) section(list, lw, "Talents", "Per grade", talents, false, 0f);
            section(list, lw, "Mutations, flaws", "Per level", bodies, !plan.wanted.containsKey(MALFORMED), talents.isEmpty() ? 0f : PetsPanel.HEAD_GAP);
            list.end();
        }

        private void section(UiScroll.Column list, float w, String title, String per, List<PetDataRepo.Trait> rows, boolean malformed, float gap) {
            Ui.thead(list.heading(gap), 0f, 0f, w, new String[]{title, per, "Your best"}, new float[]{8f, PER_X, w - BEST_W - 8f},
                    new float[]{PER_X - 16f, w - PER_X - BEST_W - 16f, BEST_W}, new Alignment[]{Ui.LEFT, Ui.LEFT, Ui.RIGHT});
            float g = 4f;
            for (PetDataRepo.Trait trait : rows) {
                CustomPanelAPI b = list.block(ROW, g);
                g = PITCH - ROW;
                if (!seen.contains(trait.id())) {
                    CustomPanelAPI box = Ui.box(b, 0f, 0f, w, ROW, Ui.line(), null);
                    Ui.textIn(b, 8f, 0f, 100f, ROW, "???", Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
                    Ui.tooltip(b, box, "???", "Not seen yet.");
                    continue;
                }
                row(b, w, trait.name(), trait.isFlaw() ? Ui.neg() : Ui.text(), perText(trait), best(trait), () -> add(trait.id(), trait.isFlaw() ? 0 : 1));
                Ui.tooltip(b, b, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> tip(t, trait));
            }
            if (!malformed) return;

            CustomPanelAPI b = list.block(ROW, g);
            row(b, w, "Malformed", Ui.neg(), "One new flaw from inbreeding", "", () -> add(MALFORMED, 0));
            Ui.tooltip(b, b, "Malformed", "A young of related parents can come out with one new flaw, or one more level of a flaw.");
        }

        private static void row(CustomPanelAPI b, float w, String name, Color color, String per, String best, Runnable action) {
            UiButtons.row(b, 0f, 0f, w, ROW, false, true, null, action);
            Ui.textIn(b, 8f, 0f, PER_X - 16f, ROW, Ui.fit(name, Ui.INS15, PER_X - 16f), Ui.INS15, color, Ui.LEFT, new String[0], new Color[0]);
            float pw = w - PER_X - BEST_W - 16f;
            Ui.textIn(b, PER_X, 0f, pw, ROW, Ui.fit(per, Ui.INS15, pw), Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
            boolean none = best.equals("None");
            Ui.textIn(b, w - BEST_W - 8f, 0f, BEST_W, ROW, Ui.fit(best, Ui.INS15, BEST_W), Ui.INS15, none ? Ui.gray() : Ui.text(), Ui.RIGHT, new String[0], new Color[0]);
        }

        private static String perText(PetDataRepo.Trait trait) {
            String v = trait.format(trait.value(trait.per(), 1f));
            if (v.isEmpty()) return trait.effect();
            return trait.effect() + " " + v + (trait.per() > 1 ? " per " + trait.per() : "");
        }

        // the living pet of the species with the highest known grade or level of it
        private String best(PetDataRepo.Trait trait) {
            Pet top = null;
            int grade = 0;
            for (Pet pet : pets) {
                int g = pet.sheet.isKnown(trait.id()) ? pet.sheet.getGrade(trait.id()) : 0;
                if (g > grade) {
                    grade = g;
                    top = pet;
                }
            }
            if (top == null) return "None";
            return trait.hasLevels() ? top.name + " " + grade : top.name;
        }

        private static void tip(TooltipMakerAPI t, PetDataRepo.Trait trait) {
            Color c = trait.isFlaw() ? Ui.neg() : Ui.hl();
            t.addPara(trait.name(), c, 0f);
            if (trait.hasLevels()) t.addPara(trait.desc(), 10f, c, trait.format(trait.value(trait.per(), 1f)));
            else t.addPara(trait.desc(), 10f);
            if (trait.cap() > 0f) t.addPara("At most %s.", 0f, Ui.hl(), trait.format(trait.cap()));
        }

        private void add(String id, int value) {
            plan.wanted.put(id, value);
            onAdd.accept(id);
            callback.dismissCustomDialog(1);
        }

        @Override
        public boolean hasCancelButton() {
            return false;
        }

        @Override
        public String getConfirmText() {
            return "Cancel";
        }

        @Override
        public void customDialogConfirm() {
            dialog.dismiss();
        }

        @Override
        public void customDialogCancel() {
            dialog.dismiss();
        }
    }
}
