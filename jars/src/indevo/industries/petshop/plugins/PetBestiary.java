package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBestiaryFile;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// tab Bestiary: every species as a tile, the page of the picked one; the species and looks seen carry over between games
public class PetBestiary {

    enum Seen {
        GAME,
        OLD,
        NEVER
    }

    static final float ROW_W = PetsPanel.LIST_W - 10f;
    static final float TILE_W = 88f;
    static final float TILE_H = 110f;
    static final float TILE_PITCH = 92f;
    static final float TILE_GAP = 4f;
    static final float TILE_PORTRAIT = 64f;
    static final float TILE_NAME_Y = 71f;
    static final float QMARK = 24f;
    static final float IDENT_Y = 34f;
    static final float LOOKS_W = 240f;
    static final float LOOK_PITCH = 54f;
    static final float LOOK_PORTRAIT = 48f;

    static final String NAME = "Name";
    static final String CLASS = "Class";
    static final String LIFE_SPAN = "Life span";
    static final String THIS_GAME = "This game";
    static final List<UiFilter.Item> TOGGLES = List.of(new UiFilter.Item(THIS_GAME, 92f, "Only species seen in this game."));
    static final List<UiFilter.Item> KEYS = List.of(
            new UiFilter.Item(NAME, 70f),
            new UiFilter.Item(CLASS, 72f),
            new UiFilter.Item(LIFE_SPAN, 96f, "Longest first."));

    private final PetsPanel panel;
    private final PetsView view;
    private final PetStatusManager manager = PetStatusManager.getInstance();

    record Entry(PetData data, Seen seen) {
    }

    PetBestiary(PetsPanel panel, PetsView view) {
        this.panel = panel;
        this.view = view;
    }

    void build(CustomPanelAPI p, float w, float h) {
        manager.seeAll();
        List<Entry> all = entries();
        List<Entry> shown = shown(all);
        Entry selected = selected(all, shown);

        addList(p, h, all, shown, selected);
        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        if (selected != null) page(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, w - PetsPanel.CARD_X, h - PetsPanel.BODY_Y, selected);
    }

    private List<Entry> entries() {
        Set<String> game = manager.getSeenSpecies();
        Set<String> known = PetBestiaryFile.get().getSpecies();
        List<Entry> all = new ArrayList<>();
        for (PetData data : PetDataRepo.getAll()) {
            Seen seen = game.contains(data.id) ? Seen.GAME : known.contains(data.id) ? Seen.OLD : Seen.NEVER;
            all.add(new Entry(data, seen));
        }
        return all;
    }

    // this game, then the grey ones, then the ? tiles by id so they give nothing away; no typed text finds a ? tile
    private List<Entry> shown(List<Entry> all) {
        UiFilter.State filter = view.beasts;
        boolean thisGame = filter.on.contains(THIS_GAME);
        List<Entry> game = new ArrayList<>();
        List<Entry> old = new ArrayList<>();
        List<Entry> never = new ArrayList<>();

        for (Entry e : all) {
            if (e.seen() == Seen.NEVER) {
                if (!thisGame && filter.query().isEmpty()) never.add(e);
                continue;
            }
            if (thisGame && e.seen() == Seen.OLD) continue;
            if (!filter.matches(e.data().species, className(e.data()))) continue;
            (e.seen() == Seen.GAME ? game : old).add(e);
        }

        filter.sort(game, order(filter.sort));
        filter.sort(old, order(filter.sort));
        never.sort(Comparator.comparing(e -> e.data().id));

        List<Entry> shown = new ArrayList<>(game);
        shown.addAll(old);
        shown.addAll(never);
        return shown;
    }

    private static Comparator<Entry> order(String key) {
        Comparator<Entry> name = Comparator.comparing(e -> e.data().species, String.CASE_INSENSITIVE_ORDER);
        return switch (key) {
            case CLASS -> Comparator.comparing((Entry e) -> className(e.data())).thenComparing(name);
            case LIFE_SPAN -> Comparator.comparingDouble((Entry e) -> -e.data().maxLife).thenComparing(name);
            default -> name;
        };
    }

    // the picked species keeps its page while the filter hides its tile
    private Entry selected(List<Entry> all, List<Entry> shown) {
        for (Entry e : all) if (e.data().id.equals(view.beast)) return e;
        Entry first = !shown.isEmpty() ? shown.get(0) : null;
        view.beast = first != null ? first.data().id : null;
        return first;
    }

    private void addList(CustomPanelAPI p, float h, List<Entry> all, List<Entry> shown, Entry selected) {
        float y = PetsPanel.BODY_Y;
        int game = 0;
        for (Entry e : all) if (e.seen() == Seen.GAME) game++;
        Ui.heading(p, 0f, y, ROW_W, "Species", game + " / " + all.size(), String.valueOf(game));
        y += Ui.HEADING_H + 4f;
        y += UiFilter.bar(p, 2f, y, view.beasts, "Name or class", "Filters and sort order of this list.", TOGGLES, null, KEYS, panel::filtered, panel::refresh)
                + UiFilter.BELOW;

        UiScroll.Column list = UiScroll.list(p, 0f, y, PetsPanel.LIST_W, h - y, view.scroll, PetsView.BEASTS);
        if (shown.isEmpty()) Ui.text(list.block(Ui.LINE_H, 0f), 0f, 0f, list.width(), "No species matches.", Ui.gray());
        for (int row = 0; row * PetsPanel.PER_ROW < shown.size(); row++) {
            CustomPanelAPI b = list.block(TILE_H, row == 0 ? 0f : TILE_GAP);
            for (int i = row * PetsPanel.PER_ROW; i < Math.min(shown.size(), (row + 1) * PetsPanel.PER_ROW); i++) {
                tile(b, 1f + (i % PetsPanel.PER_ROW) * TILE_PITCH, shown.get(i), shown.get(i) == selected);
            }
        }
        list.end();
    }

    private void tile(CustomPanelAPI b, float x, Entry e, boolean selected) {
        CustomPanelAPI t = UiButtons.row(b, x, 0f, TILE_W, TILE_H, selected, true, null, () -> {
            view.beast = e.data().id;
            view.scroll.reset(PetsView.BEAST);
            panel.refresh();
        });
        float px = (TILE_W - TILE_PORTRAIT) / 2f;
        float nw = TILE_W - 8f;

        if (e.seen() == Seen.NEVER) {
            unknown(t, px, 5f, TILE_PORTRAIT);
            Ui.text(t, 4f, TILE_NAME_Y, nw, "Unknown", Ui.gray(), Ui.CENTER);
            return;
        }

        boolean old = e.seen() == Seen.OLD;
        String name = e.data().species;
        portrait(t, px, 5f, TILE_PORTRAIT, e.data().icon, old);
        String shownName = Ui.lines(name, Ui.INS15, nw, 2);
        Ui.wrap(t, 4f, TILE_NAME_Y, nw, shownName, Ui.INS15, old ? Ui.gray() : Ui.text(), Alignment.TMID, new String[0], new Color[0]);
        if (old) Ui.tooltip(b, t, name, "Not seen in this game yet.");
        else if (!shownName.equals(name)) Ui.tooltip(b, t, null, name);
    }

    // dashed box with the vanilla question mark, a species or look never seen
    private static void unknown(CustomPanelAPI p, float x, float y, float size) {
        Ui.dashbox(p, x, y, size, size);
        float d = (size - QMARK) / 2f;
        UiImages.img(p, x + d, y + d, QMARK, QMARK, PetCard.unknownSprite());
    }

    private static CustomPanelAPI portrait(CustomPanelAPI p, float x, float y, float size, String sprite, boolean grey) {
        return grey ? UiImages.grey(p, x, y, size, size, sprite) : UiImages.img(p, x, y, size, size, sprite);
    }

    // name, portrait (its tooltip lists the looks), facts; About and Mutations found scroll; the tables are of this game only
    private void page(CustomPanelAPI parent, float x, float y, float w, float h, Entry e) {
        CustomPanelAPI p = Ui.panel(parent, x, y, w, h);
        PetData data = e.data();
        if (e.seen() == Seen.NEVER) {
            name(p, w, "Unknown", Ui.bright());
            unknown(p, 0f, IDENT_Y, PetCard.IDENT);
            Ui.text(p, PetCard.GRID_X, IDENT_Y, w - PetCard.GRID_X, "Not seen yet.", Ui.gray());
            return;
        }

        boolean old = e.seen() == Seen.OLD;
        List<Pet> pets = new ArrayList<>();
        for (Pet pet : manager.getPets()) if (pet.typeID.equals(data.id)) pets.add(pet);
        Set<String> knownLooks = PetBestiaryFile.get().getLooks();

        name(p, w, data.species, old ? Ui.gray() : Ui.bright());
        CustomPanelAPI card = Ui.card(p, 0f, IDENT_Y, PetCard.IDENT, PetCard.IDENT);
        portrait(p, 4f, IDENT_Y + 4f, PetCard.PORTRAIT, data.icon, old);
        Ui.tooltip(p, card, LOOKS_W, TooltipMakerAPI.TooltipLocation.RIGHT, t -> looksTip(t, data, old, pets, knownLooks));

        int found = 1;
        for (PetDataRepo.Look look : data.looks) if (knownLooks.contains(look.id())) found++;
        boolean broods = !data.isNoSell();
        UiData.grid(p, PetCard.GRID_X, IDENT_Y, w - PetCard.GRID_X, UiData.GRID_ROW_H, List.of(
                GridRow.of("Class", className(data), Ui.text()),
                GridRow.of("Life span", PetPlaceDialog.lifeSpan(data), Ui.text()),
                GridRow.of("Brood time", broods ? Math.round(PetGenetics.baseBroodDays(data)) + " days" : "None", broods ? Ui.text() : Ui.gray()),
                GridRow.of("Looks", found + " / " + (1 + data.looks.size()), Ui.hl())));

        float ty = IDENT_Y + PetCard.IDENT + PetsPanel.HEAD_GAP;
        UiScroll.Column col = UiScroll.list(p, 0f, ty, w, h - 44f - ty, view.scroll, PetsView.BEAST);
        float cw = col.width();
        Ui.heading(col.heading(0f), 0f, 0f, cw, "About", Ui.LEFT);
        String about = data.desc == null ? "" : data.desc;
        Ui.wrap(col.block(Ui.wrapHeight(about, Ui.INS15, cw), 4f), 0f, 0f, cw, about, Ui.text());
        addMutations(col, cw, pets);
        col.end();

        Ui.rule(p, 0f, h - 36f, w);
        Pet root = treeRoot(pets);
        ButtonAPI tree = UiButtons.button(p, 0f, h - PetCard.BUTTON_H, PetCard.TREE_W, PetCard.BUTTON_H, "Family tree", root != null ? UiButtons.Kind.STD : UiButtons.Kind.OFF,
                CutStyle.TL_BR, root != null ? () -> panel.showSpecies(root) : null);
        Ui.tooltip(p, tree, "Family tree", root != null ? "The Full view of the tree with this species." : "No pet of this species in this game yet.");
    }

    private static void name(CustomPanelAPI p, float w, String name, Color c) {
        Ui.textIn(p, 4f, 0f, w - 8f, PetCard.NAME_H, Ui.fit(name, Ui.ORB20, w - 8f), Ui.ORB20, c, Ui.CENTER, new String[0], new Color[0]);
    }

    static String className(PetData data) {
        return Misc.ucFirst(data.getClassTag().substring(PetData.TAG_SPECIES_PREFIX.length()));
    }

    // a living pet first, so the tree opens on a family that is still there
    private static Pet treeRoot(List<Pet> pets) {
        for (Pet pet : pets) if (!pet.isDead()) return pet;
        return pets.isEmpty() ? null : pets.get(0);
    }

    // each mutation any pet of the species had in this game, the highest level and the pet with it, a living pet before a gone one
    private static void addMutations(UiScroll.Column col, float cw, List<Pet> pets) {
        float ew = cw - PetCard.EFFECT_X;
        Ui.thead(col.heading(PetsPanel.HEAD_GAP), 0f, 0f, cw, new String[]{"Level", "Mutations found", "Highest in"},
                new float[]{PetCard.GRADE_X, PetCard.TALENT_X, PetCard.EFFECT_X}, new float[]{PetCard.GRADE_W, PetCard.TALENT_W, ew},
                new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});

        List<PetDataRepo.Trait> found = new ArrayList<>();
        List<Pet> best = new ArrayList<>();
        for (PetDataRepo.Trait trait : PetDataRepo.getTraits().values()) {
            if (!trait.isMutation()) continue;
            Pet top = null;
            for (Pet pet : pets) {
                int level = pet.sheet == null ? 0 : pet.sheet.getGrade(trait.id());
                if (level <= 0) continue;
                int topLevel = top == null ? 0 : top.sheet.getGrade(trait.id());
                if (level > topLevel || level == topLevel && top.isDead() && !pet.isDead()) top = pet;
            }
            if (top == null) continue;
            found.add(trait);
            best.add(top);
        }

        if (found.isEmpty()) {
            Ui.text(col.block(Ui.LINE_H, 4f), PetCard.TALENT_X, 0f, cw - PetCard.TALENT_X, "None found yet.", Ui.gray());
            return;
        }

        for (int i = 0; i < found.size(); i++) {
            PetDataRepo.Trait trait = found.get(i);
            Pet pet = best.get(i);
            CustomPanelAPI row = col.block(UiData.GRID_ROW_H, i == 0 ? 4f : 0f);
            String level = trait.hasLevels() ? String.valueOf(pet.sheet.getGrade(trait.id())) : "";
            Ui.textIn(row, PetCard.GRADE_X, 0f, PetCard.GRADE_W, UiData.GRID_ROW_H, level, Ui.INS15, Ui.hl(), Ui.CENTER, new String[0], new Color[0]);
            Ui.textIn(row, PetCard.TALENT_X, 0f, PetCard.TALENT_W, UiData.GRID_ROW_H, trait.name(), Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            Ui.textIn(row, PetCard.EFFECT_X, 0f, ew, UiData.GRID_ROW_H, Ui.fit(pet.name, Ui.INS15, ew), Ui.INS15, pet.isDead() ? Ui.gray() : Ui.text(), Ui.RIGHT,
                    new String[0], new Color[0]);
            Ui.tooltip(row, row, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> mutationTip(t, trait));
        }
    }

    private static void mutationTip(TooltipMakerAPI t, PetDataRepo.Trait trait) {
        t.addPara(trait.name(), Ui.hl(), 0f);
        if (trait.hasLevels()) t.addPara(trait.desc(), 10f, Ui.hl(), trait.format(trait.value(trait.per(), 1f)));
        else t.addPara(trait.desc(), 10f);
        if (trait.cap() > 0f) t.addPara("At most %s.", 0f, Ui.hl(), trait.format(trait.cap()));
        PetPlanner.arrivesWith(t, trait);
    }

    // Wild type, then each look: raised in this game, seen on offer, known from an earlier game only (grey), never found (?)
    private static void looksTip(TooltipMakerAPI t, PetData data, boolean old, List<Pet> pets, Set<String> knownLooks) {
        float tw = t.getWidthSoFar();
        t.addPara("Looks", Ui.hl(), 0f);

        int rows = 1 + data.looks.size();
        CustomPanelAPI list = Global.getSettings().createCustom(tw, rows * LOOK_PITCH - (LOOK_PITCH - LOOK_PORTRAIT), new UiCanvas());
        int wild = 0;
        for (Pet pet : pets) if (pet.getLook() == null) wild++;
        lookRow(list, 0f, tw, data.icon, PetDataRepo.WILD_TYPE, old ? -1 : wild, true);

        for (int i = 0; i < data.looks.size(); i++) {
            PetDataRepo.Look look = data.looks.get(i);
            int raised = 0;
            for (Pet pet : pets) if (pet.sheet != null && look.id().equals(pet.sheet.look)) raised++;
            float y = (i + 1) * LOOK_PITCH;
            if (raised == 0 && !knownLooks.contains(look.id())) {
                unknown(list, 0f, y, LOOK_PORTRAIT);
                Ui.textIn(list, LOOK_PORTRAIT + 10f, y, tw - LOOK_PORTRAIT - 10f, LOOK_PORTRAIT, "Unknown", Ui.INS15, Ui.gray(), Ui.LEFT, new String[0], new Color[0]);
            } else lookRow(list, y, tw, look.icon(), look.name(), raised > 0 ? raised : -1, false);
        }
        t.addCustom(list, 10f);
    }

    // raised -1: known from an earlier game only; 0 with seen: seen on offer in this game
    private static void lookRow(CustomPanelAPI p, float y, float w, String sprite, String name, int raised, boolean seen) {
        float tx = LOOK_PORTRAIT + 10f;
        float tw = w - tx;
        boolean grey = raised < 0;
        portrait(p, 0f, y, LOOK_PORTRAIT, sprite, grey);
        Ui.text(p, tx, y + 6f, tw, Ui.fit(name, Ui.INS15, tw), grey ? Ui.gray() : Ui.text());
        if (grey) Ui.text(p, tx, y + 24f, tw, "Not in this game yet", Ui.gray());
        else if (raised == 0 && seen) Ui.text(p, tx, y + 24f, tw, "Seen", Ui.gray());
        else Ui.runs(p, tx, y + 24f, tw, Ui.INS15, Ui.text(), Ui.LEFT, raised + " raised", new String[]{String.valueOf(raised)}, new Color[]{Ui.hl()});
    }
}
