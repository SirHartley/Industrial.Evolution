package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.utils.helper.StringHelper;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

// the Husbandry tab of a docked Animal Center: habitats and adults on the left, the selected habitat on the right
public class PetHusbandry {

    static final float HAB_W = 129f;
    static final float HAB_H = 88f;
    static final float HAB_PITCH = 139f;
    static final float HAB_FACE = 36f;
    static final float TILE_H = 134f;
    static final float DIAL_ROW = 18f;
    static final float DIALS_Y = 62f;
    static final float START_W = 104f;
    static final float TREE_W = 96f;
    static final float PLAN_X = 244f;
    static final float YOUNG_W = 40f;
    static final float YOUNG_H = 56f;
    static final float YOUNG_GAP = 8f;
    static final float CROSS_Y = 118f;
    static final float CHIP_H = 24f;
    static final float CHIP_PITCH = 28f;
    static final float CHIP_BAR_X = 124f;
    static final float CHIP_SEG_MIN = 8f;
    static final float NODE_W = 200f;
    static final float NODE_H = 52f;
    static final float OUT_W = 140f;
    static final float OUT_H = 96f;
    // the flow chart sits on the brood line: thead, parents, lines, results
    static final float DETAIL_H = 204f;
    static final float LINK_H = 30f;
    static final float DETAIL_BOTTOM = 68f;
    static final String LOOK = "look";
    static final String DISPOSITION = "disposition";
    static final String MALFORMED = PetGenetics.MALFORMED;

    static final String ALL = "All";
    static final String NO_PARTNER = "No partner";
    static final String SORT_GRADE = "Grade";
    static final String SORT_NAME = "Name";
    static final List<UiFilter.Item> KEYS = List.of(
            new UiFilter.Item(SORT_GRADE, 76f, "Best grade of the trait in the field first."),
            new UiFilter.Item(SORT_NAME, 70f),
            new UiFilter.Item("Hearts", 78f, "Most hearts first."),
            new UiFilter.Item("Age", 62f));

    private final PetsPanel panel;
    private final PetsView view;
    private final PetShop shop;
    private final PetStatusManager manager = PetStatusManager.getInstance();

    PetHusbandry(PetsPanel panel, PetsView view) {
        this.panel = panel;
        this.view = view;
        this.shop = panel.shop();
    }

    static UiFilter.State newFilter() {
        UiFilter.State filter = new UiFilter.State(SORT_GRADE, ALL);
        filter.fallback = SORT_NAME;
        return filter;
    }

    static String busy(PetShop shop) {
        return shop.getBroods().size() + " / " + shop.getHabitatCount();
    }

    void build(CustomPanelAPI p, float w, float h) {
        int selected = resolveHabitat();
        float y = addHabitats(p, PetsPanel.BODY_Y, selected);
        addAdults(p, y, h, selected);
        Ui.vrule(p, PetsPanel.LIST_W + 5f, PetsPanel.BODY_Y, h - PetsPanel.BODY_Y);
        addCard(Ui.panel(p, PetsPanel.CARD_X, PetsPanel.BODY_Y, w - PetsPanel.CARD_X, h - PetsPanel.BODY_Y), w - PetsPanel.CARD_X, h - PetsPanel.BODY_Y, selected);
    }

    // the picked one, else the first free habitat, else the first
    private int resolveHabitat() {
        int n = shop.getHabitatCount();
        if (view.habitat >= 0 && view.habitat < n) return view.habitat;

        for (int i = 0; i < n; i++) {
            if (shop.getBrood(i) == null) {
                view.habitat = i;
                return i;
            }
        }

        view.habitat = 0;
        return 0;
    }

    private float addHabitats(CustomPanelAPI p, float y, int selected) {
        Ui.heading(p, 0f, y, PetsPanel.LIST_W - 10f, "Habitats", busy(shop));
        y += Ui.HEADING_H + 4f;

        for (int i = 0; i < shop.getHabitatCount(); i++) habitat(p, 2f + i * HAB_PITCH, y, i, i == selected);
        return y + HAB_H + PetsPanel.HEAD_GAP;
    }

    private void habitat(CustomPanelAPI p, float x, float y, int index, boolean selected) {
        PetBrood brood = shop.getBrood(index);
        CustomPanelAPI t = UiButtons.row(p, x, y, HAB_W, HAB_H, selected, true, null, () -> {
            view.habitat = index;
            panel.refresh();
        });

        Pet a;
        Pet b;
        String state;
        Color color = Ui.gray();
        String[] parts = new String[0];
        if (brood != null) {
            a = manager.get(brood.parentA);
            b = manager.get(brood.parentB);
            if (brood.isReady()) {
                int n = brood.getYoung().size();
                state = n + " young";
                parts = new String[]{String.valueOf(n)};
                color = Ui.text();
                UiImages.img(t, HAB_W - 33f, 1f, PetCard.MARKER, PetCard.MARKER, PetCard.sprite("pet_marker_want"));
            } else {
                String days = String.valueOf(Math.max(1, (int) Math.ceil(shop.getDaysLeft(brood))));
                state = days + (days.equals("1") ? " day left" : " days left");
                parts = new String[]{days};
                UiData.thinbar(t, 9f, 82f, 111f, 1f - shop.getDaysLeft(brood) / brood.days, Ui.barBlue());
            }
        } else if (selected) {
            a = view.pair.isEmpty() ? null : manager.get(view.pair.get(0));
            b = view.pair.size() < 2 ? null : manager.get(view.pair.get(1));
            state = a == null ? "" : b == null ? "Pick a second" : "Not started";
        } else {
            a = null;
            b = null;
            state = "";
        }

        face(t, 24f, a);
        face(t, 70f, b);
        String name = a != null && b != null ? a.name + " x " + b.name : a != null ? a.name : "Empty";
        Ui.textIn(t, 5f, 45f, 119f, Ui.LINE_H, Ui.fit(name, Ui.INS15, 119f), Ui.INS15, a != null ? Ui.text() : Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
        if (!state.isEmpty()) Ui.textIn(t, 5f, 62f, 119f, Ui.LINE_H, state, Ui.INS15, color, Ui.CENTER, parts, new Color[]{Ui.hl()});
    }

    private static void face(CustomPanelAPI t, float x, Pet pet) {
        if (pet != null) UiImages.img(t, x, 7f, HAB_FACE, HAB_FACE, pet.getIcon());
        else Ui.dashbox(t, x, 7f, HAB_FACE, HAB_FACE);
    }

    // adults with the player or in storage here
    private List<Pet> adults() {
        List<Pet> list = new ArrayList<>();
        List<Pet> stored = shop.getStoredPetsPetsCopy();
        for (Pet pet : manager.getPets()) {
            if (pet.isDead() || pet.isYoung() || pet.getData().isNoSell()) continue;
            if (pet.isWithPlayer() || stored.contains(pet)) list.add(pet);
        }

        return list;
    }

    private void addAdults(CustomPanelAPI p, float y, float h, int habitat) {
        UiFilter.State filter = view.adults;
        List<Pet> all = adults();
        Map<String, Integer> perSpecies = new LinkedHashMap<>();
        for (Pet pet : all) perSpecies.merge(pet.typeID, 1, Integer::sum);

        PetDataRepo.Trait trait = typedTrait(filter.query());
        filter.off.clear();
        if (trait == null) filter.off.add(SORT_GRADE);

        String species = filter.on.stream().filter(s -> !s.equals(NO_PARTNER)).findFirst().orElse(ALL);
        boolean singles = filter.on.contains(NO_PARTNER);
        List<Pet> shown = new ArrayList<>();
        for (Pet pet : all) {
            boolean alone = perSpecies.get(pet.typeID) < 2;
            if (alone && !singles) continue;
            if (!species.equals(ALL) && !species.equals(pet.typeID)) continue;
            if (trait != null ? grade(pet, trait) <= 0 : !filter.matches(searchText(pet))) continue;
            shown.add(pet);
        }
        filter.sort(shown, order(filter.sortKey(), trait));

        int pairs = 0;
        for (int n : perSpecies.values()) if (n >= 2) pairs += n;
        String count = filter.isFiltering() ? shown.size() + " of " + all.size() : String.valueOf(pairs);
        Ui.heading(p, 0f, y, PetsPanel.LIST_W - 10f, "Adults", count);
        y += Ui.HEADING_H + 4f;

        List<UiFilter.Item> toggles = new ArrayList<>();
        toggles.add(new UiFilter.Item(ALL, 40f));
        for (Map.Entry<String, Integer> e : perSpecies.entrySet()) {
            if (e.getValue() < 2) continue;
            PetData data = PetDataRepo.get(e.getKey());
            toggles.add(new UiFilter.Item(e.getKey(), 52f, data.species, data.icon, String.valueOf(e.getValue())));
        }
        toggles.add(new UiFilter.Item(NO_PARTNER, 96f, "Also the adults with no second adult of their species here."));

        float bar = UiFilter.bar(p, 2f, y, filter, "Name or trait", "Filters and sort order of this list.", toggles, this::toggle, KEYS, panel::filtered, panel::refresh);
        float top = y + bar + UiFilter.BELOW;

        UiScroll.Column list = UiScroll.list(p, 0f, top, PetsPanel.LIST_W, h - top, view.scroll, PetsView.ADULTS);
        if (shown.isEmpty()) Ui.text(list.block(Ui.LINE_H, 0f), 0f, 0f, list.width(), all.isEmpty() ? "No adults here." : filter.isFiltering() ? "No adult matches." : "No two adults of one species here.", Ui.gray());

        boolean free = shop.getBrood(habitat) == null;
        for (int row = 0; row * PetsPanel.PER_ROW < shown.size(); row++) {
            CustomPanelAPI b = list.block(TILE_H, row == 0 ? 0f : PetsPanel.TILE_GAP);
            for (int i = row * PetsPanel.PER_ROW; i < Math.min(shown.size(), (row + 1) * PetsPanel.PER_ROW); i++) {
                Pet pet = shown.get(i);
                adultTile(b, 2f + (i % PetsPanel.PER_ROW) * PetsPanel.TILE_PITCH, pet, trait, perSpecies.get(pet.typeID) < 2, free);
            }
        }

        list.end();
    }

    // species toggles and All pick one, No partner flips
    private void toggle(String label) {
        UiFilter.State filter = view.adults;
        if (label.equals(NO_PARTNER)) {
            if (!filter.on.remove(NO_PARTNER)) filter.on.add(NO_PARTNER);
            return;
        }

        boolean singles = filter.on.contains(NO_PARTNER);
        filter.on.clear();
        filter.on.add(label);
        if (singles) filter.on.add(NO_PARTNER);
    }

    // a known trait whose name starts with the typed text
    private static PetDataRepo.Trait typedTrait(String query) {
        if (query.isEmpty()) return null;

        for (PetDataRepo.Trait trait : PetDataRepo.getTraits().values()) {
            if (trait.name().toLowerCase(Locale.ROOT).startsWith(query)) return trait;
        }

        return null;
    }

    private static int grade(Pet pet, PetDataRepo.Trait trait) {
        return pet.sheet.isKnown(trait.id()) ? pet.sheet.getGrade(trait.id()) : 0;
    }

    private static String[] searchText(Pet pet) {
        List<String> text = new ArrayList<>(List.of(pet.name, pet.getData().species, pet.getLookName()));
        text.addAll(pet.getKnownTraitNames());
        return text.toArray(new String[0]);
    }

    private static Comparator<Pet> order(String key, PetDataRepo.Trait trait) {
        Comparator<Pet> name = Comparator.comparing(pet -> pet.name, String.CASE_INSENSITIVE_ORDER);
        return switch (key) {
            case SORT_GRADE -> trait == null ? name : Comparator.comparingInt((Pet pet) -> -grade(pet, trait)).thenComparing(name);
            case "Hearts" -> Comparator.comparingDouble((Pet pet) -> -pet.bondDays).thenComparing(name);
            case "Age" -> Comparator.comparingDouble((Pet pet) -> -pet.age).thenComparing(name);
            default -> name;
        };
    }

    private void adultTile(CustomPanelAPI p, float x, Pet pet, PetDataRepo.Trait trait, boolean alone, boolean free) {
        String block = alone ? "No second adult of this species here." : shop.parentBlock(pet);
        if (block == null && !free) block = "This habitat is busy. Pick a free one.";
        boolean picked = free && view.pair.contains(pet.id);
        boolean enabled = block == null;

        CustomPanelAPI t = UiButtons.row(p, x, 0f, PetsPanel.TILE_W, TILE_H, picked, enabled, null, enabled ? () -> pick(pet) : null);
        UiImages.img(t, 11f, 5f, PetCard.PORTRAIT, PetCard.PORTRAIT, pet.getIcon(), null, enabled ? 1f : PetCard.DIM);

        int unknown = unknownTalents(pet);
        if (unknown > 0) {
            CustomPanelAPI icon = UiImages.img(t, 58f, -2f, PetCard.MARKER, PetCard.MARKER, PetCard.unknownSprite());
            Ui.tooltip(t, icon, "Unknown traits", unknown + (unknown == 1 ? " talent of " : " talents of ") + pet.name + " not shown yet.");
        }

        Color c = enabled ? Ui.text() : Ui.gray();
        Ui.textIn(t, 4f, 71f, 78f, Ui.LINE_H, Ui.fit(pet.name, Ui.INS15, 78f), Ui.INS15, c, Ui.CENTER, new String[0], new Color[0]);
        PetCard.hearts(t, 17f, 91f, pet, 9f, 2f);
        Ui.rule(t, 13f, 107f, 60f);

        if (trait != null) {
            String g = String.valueOf(grade(pet, trait));
            String word = trait.isTalent() ? "Grade " : "Level ";
            Ui.textIn(t, 4f, 112f, 78f, Ui.LINE_H, word + g, Ui.INS15, Ui.gray(), Ui.CENTER, new String[]{g}, new Color[]{trait.isFlaw() ? Ui.neg() : Ui.hl()});
        } else Ui.textIn(t, 4f, 112f, 78f, Ui.LINE_H, Ui.fit(pet.getLookName(), Ui.INS15, 78f), Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);

        if (block != null) Ui.tooltip(t, t, pet.getData().species, block);
    }

    static int unknownTalents(Pet pet) {
        int n = 0;
        for (String id : pet.getTalents()) if (!pet.sheet.isKnown(id)) n++;
        return n;
    }

    // unknown talents as the card shows them, a running reveal holds back the rows it has not reached
    private int unknown(Pet pet) {
        int n = 0;
        for (String id : pet.getTalents()) if (!view.reveal.known(pet, id)) n++;
        return n;
    }

    // the heading's "unknown traits": unread talents or unread flaws
    private boolean unread(Pet pet) {
        return unknown(pet) > 0 || !view.reveal.flawsRead(pet);
    }

    private boolean lit(CrossRow row, Pet a, Pet b) {
        return view.reveal.lit(a, row.key()) || view.reveal.lit(b, row.key());
    }

    // one Gene scan for the pair: the parent it reads now, else the first with unread talents, else the first with anything unread; the green border while it runs
    private void scanControls(CustomPanelAPI p, float w, float h, Pet a, Pet b) {
        PetReveal reveal = view.reveal;
        Pet target = reveal.reading(a) ? a : reveal.reading(b) ? b : unknownTalents(a) > 0 ? a : unknownTalents(b) > 0 ? b : !a.isRead() ? a : !b.isRead() ? b : null;
        if (target != null) PetCard.scanButton(p, TREE_W + 6f, h - PetCard.BUTTON_H, shop, target, reveal, PetCard.scanHeld(target, shop, PetsPanel.awayFleet(target)), () -> {
            reveal.begin(target, view.clock);
            shop.geneScan(target);
            panel.refresh();
        });
        if (reveal.on(a) || reveal.on(b)) reveal.border(p, 0f, 0f, w, h);
    }

    private void treeButton(CustomPanelAPI p, float h, Pet a, Pet b) {
        UiButtons.button(p, 0f, h - PetCard.BUTTON_H, TREE_W, PetCard.BUTTON_H, "Family tree", UiButtons.Kind.STD, CutStyle.TL_BR, () -> panel.showPair(a, b));
    }

    // a second adult of another species starts the pair again
    private void pick(Pet pet) {
        List<String> pair = view.pair;
        if (!pair.remove(pet.id)) {
            Pet first = pair.isEmpty() ? null : manager.get(pair.get(0));
            if (first != null && !first.typeID.equals(pet.typeID)) pair.clear();
            if (pair.size() >= 2) pair.remove(1);
            pair.add(pet.id);
        }

        panel.refresh();
    }

    private void addCard(CustomPanelAPI p, float w, float h, int habitat) {
        PetBrood brood = shop.getBrood(habitat);
        if (brood != null && brood.isReady()) readyCard(p, w, h, brood);
        else if (brood != null) runningCard(p, w, h, brood);
        else pairCard(p, w, h, habitat);
    }

    static void title(CustomPanelAPI p, float w, String title) {
        Ui.box(p, 0f, 0f, w, PetCard.NAME_H, Misc.scaleAlpha(Ui.base(), 0.45f), null);
        Ui.textIn(p, 4f, 0f, w - 8f, PetCard.NAME_H, Ui.fit(title, Ui.ORB20, w - 8f), Ui.ORB20, Ui.bright(), Ui.CENTER, new String[0], new Color[0]);
    }

    // one line, so the traits get a fourth row at 1280x768
    static void tagline(CustomPanelAPI p, float w, String line) {
        if (line == null || line.isEmpty()) return;
        String shown = Ui.fit(line, Ui.INS15, w);
        LabelAPI label = Ui.wrap(p, 0f, PetCard.TAG_Y, w, shown, Ui.INS15, Ui.gray(), Alignment.TMID, new String[0], new Color[0]);
        if (!shown.equals(line)) Ui.tooltip(p, label, null, line);
    }

    private void pairCard(CustomPanelAPI p, float w, float h, int habitat) {
        Pet a = view.pair.isEmpty() ? null : manager.get(view.pair.get(0));
        Pet b = view.pair.size() < 2 ? null : manager.get(view.pair.get(1));
        boolean pair = a != null && b != null;

        title(p, w, pair ? a.name + " x " + b.name : "Habitat " + (habitat + 1));
        tagline(p, w, pair ? kinLine(a, b) : a != null ? "Pick a second " + a.getData().species + "." : "Pick two adults of one species.");
        dials(p, w, a, b);
        if (pair && a.typeID.equals(b.typeID)) {
            cross(p, w, h, a, b);
            treeButton(p, h, a, b);
            scanControls(p, w, h, a, b);
            ButtonAPI plan = UiButtons.button(p, PLAN_X, h - PetCard.BUTTON_H, TREE_W, PetCard.BUTTON_H, "Add to plan", UiButtons.Kind.STD, CutStyle.TL_BR,
                    () -> panel.newPlan(a.typeID, a.id, b.id));
            Ui.tooltip(p, plan, "Add to plan", "A planned young of " + a.name + " and " + b.name + " on the Planner tab.");
        }

        float y = h - 60f;
        float fee = shop.getFee();
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        boolean broke = fee > 0f && credits < fee;
        if (pair) {
            PetData data = a.getData();
            PetSheet sa = view.reveal.seen(a);
            PetSheet sb = view.reveal.seen(b);
            String size = broodSize(PetGenetics.broodRange(data, sa, sb, PetDataRepo.getTraits()));
            String days = String.valueOf(Math.round(PetGenetics.broodDays(data, sa, sb, PetDataRepo.getTraits())));
            List<String> parts = new ArrayList<>(List.of(sizeParts(size)));
            parts.add(days);
            Ui.runs(p, 0f, y, 300f, Ui.INS15, Ui.gray(), Ui.LEFT, size + " in " + days + " days", parts.toArray(new String[0]), hls(parts.size()));
        }

        if (fee > 0f) {
            String amount = Misc.getDGSCredits(fee);
            Ui.textIn(p, w - 220f, y, 220f, Ui.LINE_H, "Fee per month " + amount, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[]{amount}, new Color[]{broke ? Ui.neg() : Ui.hl()});
        }

        String reason = startBlock(a, b, broke, fee, credits);
        Ui.rule(p, 0f, h - 36f, w);
        ButtonAPI start = UiButtons.button(p, w - START_W, h - PetCard.BUTTON_H, START_W, PetCard.BUTTON_H, "Start brood",
                reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.GREEN, CutStyle.TL_BR, reason != null ? null : () -> start(a, b, habitat));
        if (reason != null) Ui.tooltip(p, start, "Start brood", reason);
        else Ui.tooltip(p, start, "Start brood", "Both go into the habitat until the young arrive. They do not age there with a Beta core.");
    }

    private String startBlock(Pet a, Pet b, boolean broke, float fee, float credits) {
        if (a == null || b == null) return "Pick two adults of one species.";

        String block = shop.pairBlock(a, b);
        if (block != null) return block;
        block = shop.parentBlock(a);
        if (block != null) return a.name + ": " + block;
        block = shop.parentBlock(b);
        if (block != null) return b.name + ": " + block;
        if (broke) return "The fee is " + Misc.getDGSCredits(fee) + " a month. You have " + Misc.getDGSCredits(credits) + ".";
        return null;
    }

    private void start(Pet a, Pet b, int habitat) {
        shop.startBrood(a, b, habitat);
        view.pair.clear();
        view.habitat = -1;
        panel.refresh();
    }

    // the kin card's relation line; nothing for unrelated adults
    private static String kinLine(Pet a, Pet b) {
        return PetTree.relation(a, b);
    }

    private void dials(CustomPanelAPI p, float w, Pet a, Pet b) {
        boolean pair = a != null && b != null && a.typeID.equals(b.typeID);
        float half = Math.round((w - 24f) / 2f);
        int hearts = pair ? a.getHearts() + b.getHearts() : 0;
        float inbreeding = pair ? PetShop.getInbreeding(a, b) : 0f;

        dial(p, 0f, DIALS_Y, half, "Care", Misc.getRoundedValueMaxOneAfterDecimal(shop.getCare()), Ui.hl(),
                "Care", "How well the young turn out: a better grade of a parent's talent is more likely, losing one less likely. Player Animal Centers give " + Math.round(PetConstants.PLAYER_CARE)
                        + ", others " + Math.round(PetConstants.NPC_CARE) + ". A Gamma core and an improvement add " + Math.round(PetConstants.GAMMA_CARE) + " each.");
        dial(p, 0f, DIALS_Y + DIAL_ROW, half, "Sum chance", pair ? percent(PetGenetics.sumChance(a.getData(), hearts)) : "-", Ui.hl(),
                "Sum chance", "Chance that a talent both parents have comes out as the sum of both.",
                pair ? percent(PetGenetics.sumChance(a.getData(), 0)) + ", and " + percent(PetConstants.SUM_CHANCE_PER_HEART) + " for every heart of the parents: " + hearts + " hearts together." : "Depends on the species and the hearts of both.");
        dial(p, w - half, DIALS_Y, half, "Mutation rate", pair ? percent(shop.getMutationRate(a, b)) : "-", Ui.hl(),
                "Mutation rate", "Chance per young of a new mutation or talent. Higher on an irradiated world and with an Alpha core.");
        dial(p, w - half, DIALS_Y + DIAL_ROW, half, "Inbreeding", pair ? percent(inbreeding) : "-", inbreeding > 0f ? Ui.neg() : Ui.hl(),
                "Inbreeding", "How closely the two are related, counted over " + PetConstants.INBREEDING_GENERATIONS + " generations.",
                "The young turn out Malformed at " + Misc.getRoundedValueMaxOneAfterDecimal(PetConstants.MALFORMED_PER_INBREEDING) + " times this value: one new flaw, or one more level of a flaw.");
    }

    private static void dial(CustomPanelAPI p, float x, float y, float w, String label, String value, Color color, String title, String... lines) {
        CustomPanelAPI row = Ui.panel(p, x, y, w, DIAL_ROW);
        UiData.value(row, 0f, 0f, w, DIAL_ROW, label, value, color, new String[0], new Color[0]);
        Ui.tooltip(p, row, title, lines);
    }

    static String percent(float f) {
        return Math.round(f * 100f) + "%";
    }

    static String broodSize(int[] range) {
        return range[0] == range[1] ? range[0] + " young" : range[0] + " to " + range[1] + " young";
    }

    private static String[] sizeParts(String size) {
        return size.replace(" young", "").split(" to ");
    }

    private static Color[] hls(int n) {
        Color[] colors = new Color[n];
        Arrays.fill(colors, Ui.hl());
        return colors;
    }

    // one result of a cross row as drawn: the outcome, its value text and colour, where it comes from, what it does
    record Result(String lane, String value, Color color, float chance, String origin, String effect, String effectValue) {
    }

    record CrossRow(String key, String name, String unit, boolean flaw, String lineA, String lineB, List<Result> results) {
    }

    // every trait either parent is known to have, as a toggle with a small bar of its chances; the selected one drawn large
    private void cross(CustomPanelAPI p, float w, float h, Pet a, Pet b) {
        List<CrossRow> rows = crossRows(a, b);
        CrossRow selected = rows.stream().filter(r -> r.key().equals(view.crossTrait)).findFirst()
                .orElse(rows.stream().filter(PetHusbandry::isTrait).findFirst().orElse(rows.get(0)));

        PetReveal reveal = view.reveal;
        boolean ua = unread(a);
        boolean ub = unread(b);
        Pet reading = reveal.reading(a) ? a : reveal.reading(b) ? b : null;
        String note = reading != null ? "Reading " + reading.name + ", " + reveal.count() + " of " + reveal.total()
                : ua && ub ? "Both have unknown traits" : ua ? a.name + " has unknown traits" : ub ? b.name + " has unknown traits" : null;
        Ui.thead(p, 0f, CROSS_Y, w, new String[]{"Traits", note != null ? note : ""}, new float[]{8f, 118f}, new float[]{100f, w - 126f},
                new Alignment[]{Ui.LEFT, Ui.RIGHT});

        float dy = h - DETAIL_BOTTOM - DETAIL_H;
        float top = CROSS_Y + Ui.HEADING_H + 4f;
        int lines = (int) Math.min(Math.ceil(rows.size() / 2f), Math.floor((dy - PetsPanel.HEAD_GAP - top + 4f) / CHIP_PITCH));
        UiScroll.Column list = UiScroll.list(p, 0f, top, w, lines * CHIP_PITCH - 4f, view.scroll, PetsView.CROSS);
        float cw = Math.round((list.width() - 8f) / 2f);
        for (int i = 0; i < rows.size(); i += 2) {
            CustomPanelAPI line = list.block(CHIP_H, i == 0 ? 0f : CHIP_PITCH - CHIP_H);
            chip(line, 0f, cw, rows.get(i), rows.get(i) == selected, lit(rows.get(i), a, b));
            if (i + 1 < rows.size()) chip(line, cw + 8f, cw, rows.get(i + 1), rows.get(i + 1) == selected, lit(rows.get(i + 1), a, b));
        }
        list.end();

        if (rows.stream().noneMatch(PetHusbandry::isTrait)) Ui.text(p, 0f, top + lines * CHIP_PITCH + 4f, w, "Talents, mutations and flaws are unknown.", Ui.gray());

        detail(p, w, dy, selected, a, b);
    }

    private static boolean isTrait(CrossRow row) {
        return !row.key().equals(LOOK) && !row.key().equals(DISPOSITION) && !row.key().equals(MALFORMED);
    }

    private void chip(CustomPanelAPI p, float x, float cw, CrossRow row, boolean selected, boolean lit) {
        CustomPanelAPI chip = UiButtons.row(p, x, 0f, cw, CHIP_H, selected, true, null, () -> {
            view.crossTrait = row.key();
            panel.refresh();
        });
        if (lit) view.reveal.bar(chip, cw, CHIP_H);
        Ui.textIn(chip, 9f, 0f, CHIP_BAR_X - 14f, CHIP_H, Ui.fit(row.name(), Ui.INS15, CHIP_BAR_X - 14f), Ui.INS15, row.flaw() ? Ui.neg() : Ui.text(), Ui.LEFT, new String[0], new Color[0]);

        List<Result> results = row.results();
        float bw = cw - CHIP_BAR_X - 8f;
        float[] widths = new float[results.size()];
        float over = 0f;
        float big = 0f;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(CHIP_SEG_MIN, results.get(i).chance() * bw);
            over += widths[i];
            if (widths[i] > CHIP_SEG_MIN) big += widths[i];
        }
        over -= bw;
        float cx = CHIP_BAR_X;
        for (int i = 0; i < widths.length; i++) {
            float sw = widths[i] > CHIP_SEG_MIN && big > 0f ? widths[i] - over * widths[i] / big : widths[i];
            float sx = Math.round(cx);
            float ex = Math.round(cx + sw);
            Color c = laneColor(results.get(i).lane(), row.flaw());
            Ui.painted(chip, sx, 9f, Math.max(1f, ex - sx - 1f), 6f, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), c, a), null);
            cx += sw;
        }
    }

    // from the first parent, the sum, from the second, nothing; a kept flaw is red
    private static Color laneColor(String lane, boolean flaw) {
        Color blue = Ui.barBlue();
        if (lane.equals(PetGenetics.NONE)) return Misc.scaleAlpha(Ui.line(), 0.8f);
        if (flaw || lane.equals(MALFORMED)) return Ui.RED;
        return switch (lane) {
            case PetSheet.FROM_SUM -> Misc.interpolateColor(blue, Color.WHITE, 0.1f);
            case PetSheet.FROM_A -> Misc.scaleColor(blue, 0.65f);
            default -> Misc.scaleColor(blue, 0.42f);
        };
    }

    private List<CrossRow> crossRows(Pet a, Pet b) {
        List<CrossRow> rows = new ArrayList<>();
        PetData data = a.getData();
        PetSheet sa = view.reveal.seen(a);
        PetSheet sb = view.reveal.seen(b);
        float care = shop.getCare();
        float sum = PetGenetics.sumChance(data, a.getHearts() + b.getHearts());

        float malformed = PetGenetics.malformedChance(data, PetShop.getInbreeding(a, b));
        if (malformed > 0f) rows.add(malformedRow(malformed, kin(a, b), kin(b, a)));
        rows.add(lookRow(a.name, a.getLook(), b.name, b.getLook()));

        String da = Misc.ucFirst(a.sheet.disposition);
        String db = Misc.ucFirst(b.sheet.disposition);
        rows.add(new CrossRow(DISPOSITION, "Disposition", "", false, da, db, List.of(
                new Result(PetSheet.FROM_A, da, Academy.COLOURS_BY_PERSONALITY.get(a.sheet.disposition), 0.5f, "from " + a.name, "", null),
                new Result(PetSheet.FROM_B, db, Academy.COLOURS_BY_PERSONALITY.get(b.sheet.disposition), 0.5f, "from " + b.name, "", null))));

        Set<String> ids = new LinkedHashSet<>(sa.traits.keySet());
        ids.addAll(sb.traits.keySet());
        List<PetDataRepo.Trait> talents = new ArrayList<>();
        List<PetDataRepo.Trait> bodies = new ArrayList<>();
        for (String id : ids) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null) continue;
            if (trait.isTalent()) talents.add(trait);
            else bodies.add(trait);
        }
        talents.sort(Comparator.comparingInt((PetDataRepo.Trait t) -> -Math.max(sa.getGrade(t.id()), sb.getGrade(t.id()))));
        bodies.sort(Comparator.comparing(PetDataRepo.Trait::isFlaw));
        talents.addAll(bodies);

        for (PetDataRepo.Trait trait : talents) {
            int la = sa.getGrade(trait.id());
            int lb = sb.getGrade(trait.id());
            rows.add(traitRow(trait, la, lb, care, sum, a.name, b.name, parentLine(trait, a, la), parentLine(trait, b, lb)));
        }

        return rows;
    }

    static CrossRow malformedRow(float malformed, String lineA, String lineB) {
        return new CrossRow(MALFORMED, "Malformed", Misc.getRoundedValueMaxOneAfterDecimal(PetConstants.MALFORMED_PER_INBREEDING) + " times the inbreeding value", true,
                lineA, lineB, List.of(
                new Result(MALFORMED, "One new flaw", Ui.neg(), malformed, "at level 1, any flaw", "or one more level", null),
                new Result(PetGenetics.NONE, "none", Ui.gray(), 1f - malformed, "born sound", "No effect", null)));
    }

    // the look of one parent at random, one result when both have it; a new look comes from the mutation rate and is left out
    static CrossRow lookRow(String nameA, PetDataRepo.Look a, String nameB, PetDataRepo.Look b) {
        String la = lookName(a);
        String lb = lookName(b);
        if (a == b) return new CrossRow(LOOK, "Look", "", false, la, lb, List.of(lookResult(PetSheet.FROM_SUM, a, 1f, "from both")));
        return new CrossRow(LOOK, "Look", "", false, la, lb, List.of(lookResult(PetSheet.FROM_A, a, 0.5f, "from " + nameA), lookResult(PetSheet.FROM_B, b, 0.5f, "from " + nameB)));
    }

    static String lookName(PetDataRepo.Look look) {
        return look != null ? look.name() : PetDataRepo.WILD_TYPE;
    }

    private static Result lookResult(String lane, PetDataRepo.Look look, float chance, String origin) {
        return new Result(lane, lookName(look), Ui.text(), chance, origin, look != null ? "" : "Plain look", null);
    }

    static CrossRow traitRow(PetDataRepo.Trait trait, int la, int lb, float care, float sum, String nameA, String nameB, String lineA, String lineB) {
        List<Result> results = new ArrayList<>();
        for (PetGenetics.Outcome o : PetGenetics.outcomes(trait.isFlaw(), trait.hasLevels(), la, lb, care, sum)) results.add(result(trait, o, "from " + nameA, "from " + nameB));
        return new CrossRow(trait.id(), trait.name(), unit(trait), trait.isFlaw(), lineA, lineB, results);
    }

    private static Result result(PetDataRepo.Trait trait, PetGenetics.Outcome o, String fromA, String fromB) {
        if (o.lane().equals(PetGenetics.NONE)) {
            return new Result(o.lane(), "none", Ui.gray(), o.chance(), trait.isFlaw() ? "dropped" : "not passed on", "No effect", null);
        }

        String value = trait.hasLevels() ? String.valueOf(o.level()) : "Yes";
        Color color = trait.isFlaw() ? Ui.neg() : trait.hasLevels() ? Ui.hl() : Ui.text();
        String origin = switch (o.lane()) {
            case PetSheet.FROM_SUM -> trait.hasLevels() ? "sum of both" : "from both";
            case PetSheet.FROM_A -> fromA;
            default -> fromB;
        };
        String v = trait.format(trait.value(o.level(), 1f));
        return new Result(o.lane(), value, color, o.chance(), origin, trait.effect(), v.isEmpty() ? null : v);
    }

    private static String unit(PetDataRepo.Trait trait) {
        String v = trait.format(trait.value(trait.per(), 1f));
        if (v.isEmpty()) return trait.isFlaw() ? "Flaw" : trait.isMutation() ? "Mutation" : "";
        String per = trait.per() > 1 ? " per " + trait.per() + (trait.isTalent() ? " grades" : " levels") : trait.isTalent() ? " per grade" : " per level";
        String text = trait.effect() + " " + v + per;
        return trait.isFlaw() ? "Flaw, " + StringHelper.lcFirst(text) : trait.isMutation() ? "Mutation, " + StringHelper.lcFirst(text) : text;
    }

    // what the node of a parent says about the trait: its grade or level, Unknown while it may hide one, else None
    private String parentLine(PetDataRepo.Trait trait, Pet pet, int level) {
        if (level > 0) return trait.hasLevels() ? (trait.isTalent() ? "Grade " : "Level ") + level : "Has it";
        boolean hidden = trait.isFlaw() ? !view.reveal.flawsRead(pet) : trait.isTalent() && unknown(pet) > 0;
        return hidden ? "Unknown" : "None";
    }

    private static String kin(Pet pet, Pet other) {
        if (pet.parentA != null && Objects.equals(pet.parentA, other.parentA) && Objects.equals(pet.parentB, other.parentB)) return "Sibling of " + other.name;
        if (other.id.equals(pet.parentA) || other.id.equals(pet.parentB)) return "Young of " + other.name;
        if (pet.id.equals(other.parentA) || pet.id.equals(other.parentB)) return "Parent of " + other.name;
        return "Kin of " + other.name;
    }

    private void detail(CustomPanelAPI p, float w, float y, CrossRow row, Pet a, Pet b) {
        flow(p, w, y, LINK_H, row, flowNode(a, row.lineA()), flowNode(b, row.lineB()), r -> false, null);
    }

    private FlowNode flowNode(Pet pet, String line) {
        return new FlowNode(pet.name, pet.getIcon(), unknown(pet) > 0, pet, line);
    }

    // a parent in the flow chart: a pet, or a planned young with no hearts
    record FlowNode(String name, String icon, boolean unknown, Pet hearts, String line) {
    }

    // both parents, lines into every result, per result the value, chance, where it comes from and what it does;
    // a result that meets gets the gold border, click makes the results buttons
    static void flow(CustomPanelAPI p, float w, float y, float link, CrossRow row, FlowNode a, FlowNode b, Predicate<Result> meets, Consumer<Result> click) {
        Ui.thead(p, 0f, y, w, new String[]{row.name(), row.unit()}, new float[]{8f, 168f}, new float[]{150f, w - 176f}, new Alignment[]{Ui.LEFT, Ui.RIGHT});
        y += 26f;
        node(p, 0f, y, a);
        node(p, w - NODE_W, y, b);

        List<Result> results = row.results();
        int n = results.size();
        float[] xs = n == 1 ? new float[]{Math.round((w - OUT_W) / 2f)} : n == 2 ? new float[]{30f, w - OUT_W - 30f}
                : new float[]{0f, Math.round((w - OUT_W) / 2f), w - OUT_W};
        float by = y + NODE_H;
        float cy = by + link;

        links(p, NODE_W / 2f, by, Math.round(link * 0.4f), cy, results, xs, PetSheet.FROM_A);
        links(p, w - NODE_W / 2f, by, Math.round(link * 0.67f), cy, results, xs, PetSheet.FROM_B);
        for (int i = 0; i < n; i++) {
            Result r = results.get(i);
            outcome(p, xs[i], cy, r, row.flaw(), meets.test(r), click != null ? () -> click.accept(r) : null);
        }
    }

    // the node down by drop, across to every result it feeds, then down into them; the sum and Malformed take both parents
    private static void links(CustomPanelAPI p, float nx, float by, float drop, float cy, List<Result> results, float[] xs, String side) {
        List<Float> targets = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            String lane = results.get(i).lane();
            if (lane.equals(side) || lane.equals(PetSheet.FROM_SUM) || lane.equals(MALFORMED)) targets.add(xs[i] + OUT_W / 2f);
        }
        if (targets.isEmpty()) return;

        float lo = nx;
        float hi = nx;
        for (float t : targets) {
            lo = Math.min(lo, t);
            hi = Math.max(hi, t);
        }

        if (targets.size() == 1 && Math.abs(targets.get(0) - nx) < 1f) {
            vline(p, nx, by, cy - by);
            return;
        }

        vline(p, nx, by, drop);
        hline(p, lo, by + drop, hi - lo + 1f);
        for (float t : targets) vline(p, t, by + drop, cy - by - drop);
    }

    private static void vline(CustomPanelAPI p, float x, float y, float h) {
        Ui.painted(p, Math.round(x), y, 1f, h, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), 1f, pos.getHeight(), Ui.base(), a), null);
    }

    private static void hline(CustomPanelAPI p, float x, float y, float w) {
        Ui.painted(p, Math.round(x), y, Math.round(w), 1f, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), 1f, Ui.base(), a), null);
    }

    private static void node(CustomPanelAPI p, float x, float y, FlowNode node) {
        Ui.card(p, x, y, NODE_W, NODE_H);
        if (node.icon() != null) UiImages.img(p, x + 4f, y + 4f, 44f, 44f, node.icon());
        else planFace(p, x + 4f, y + 4f, 44f);
        if (node.unknown()) UiImages.img(p, x + 29f, y + 1f, 20f, 20f, PetCard.unknownSprite());
        float hw = node.hearts() != null ? PetCard.heartsWidth(9f, 2f) + 6f : 0f;
        Ui.textIn(p, x + 58f, y + 6f, NODE_W - 66f - hw, Ui.LINE_H, Ui.fit(node.name(), Ui.INS15, NODE_W - 66f - hw), Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        if (node.hearts() != null) PetCard.hearts(p, x + NODE_W - 8f - PetCard.heartsWidth(9f, 2f), y + 12f, node.hearts(), 9f, 2f);

        String line = node.line();
        String number = line.replaceAll("[^0-9]", "");
        Ui.textIn(p, x + 58f, y + 26f, NODE_W - 66f, Ui.LINE_H, Ui.fit(line, Ui.INS15, NODE_W - 66f), Ui.INS15, Ui.gray(), Ui.LEFT,
                number.isEmpty() ? new String[0] : new String[]{number}, new Color[]{Ui.hl()});
    }

    // a planned young in place of a portrait: the dashed square with the question mark
    static void planFace(CustomPanelAPI p, float x, float y, float s) {
        Ui.dashbox(p, x, y, s, s);
        float q = Math.round(s * 0.55f);
        UiImages.img(p, x + Math.round((s - q) / 2f), y + Math.round((s - q) / 2f), q, q, PetCard.unknownSprite());
    }

    private static void outcome(CustomPanelAPI p, float x, float y, Result r, boolean flaw, boolean met, Runnable click) {
        if (click != null) UiButtons.row(p, x, y, OUT_W, OUT_H, false, true, null, click);
        Ui.box(p, x, y, OUT_W, OUT_H, met ? Ui.hl() : r.lane().equals(PetSheet.FROM_SUM) ? Ui.base() : Ui.line(), null);
        if (met) Ui.painted(p, x + OUT_W - 18f, y + 6f, 12f, 9f, (pos, a) -> {
            UiDraw.line(pos.getX(), pos.getY() + 4f, pos.getX() + 4f, pos.getY(), 2f, Ui.GREEN, 1f, 1f, 1f, a, false);
            UiDraw.line(pos.getX() + 4f, pos.getY(), pos.getX() + 12f, pos.getY() + 9f, 2f, Ui.GREEN, 1f, 1f, 1f, a, false);
        }, null);
        Ui.textIn(p, x, y + 6f, OUT_W, 24f, Ui.fit(r.value(), Ui.ORB20, OUT_W - 8f), Ui.ORB20, r.color(), Ui.CENTER, new String[0], new Color[0]);
        Ui.textIn(p, x, y + 31f, OUT_W, 22f, percent(r.chance()), Ui.INS21, Ui.text(), Ui.CENTER, new String[0], new Color[0]);
        Ui.textIn(p, x, y + 54f, OUT_W, Ui.LINE_H, Ui.fit(r.origin(), Ui.INS15, OUT_W - 8f), Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);

        boolean none = r.lane().equals(PetGenetics.NONE);
        String effect = r.effectValue() != null ? r.effect() + " " + r.effectValue() : r.effect();
        Ui.textIn(p, x, y + 72f, OUT_W, Ui.LINE_H, Ui.fit(effect, Ui.INS15, OUT_W - 8f), Ui.INS15, none ? Ui.gray() : Ui.text(), Ui.CENTER,
                r.effectValue() != null ? new String[]{r.effectValue()} : new String[0], new Color[]{flaw ? Ui.neg() : Ui.hl()});
    }

    private void runningCard(CustomPanelAPI p, float w, float h, PetBrood brood) {
        Pet a = manager.get(brood.parentA);
        Pet b = manager.get(brood.parentB);
        title(p, w, a.name + " x " + b.name);
        tagline(p, w, kinLine(a, b));
        dials(p, w, a, b);
        cross(p, w, h, a, b);
        treeButton(p, h, a, b);
        scanControls(p, w, h, a, b);

        float left = shop.getDaysLeft(brood);
        String days = String.valueOf(Math.max(1, (int) Math.ceil(left)));
        String size = broodSize(PetGenetics.broodRange(a.getData(), view.reveal.seen(a), view.reveal.seen(b), PetDataRepo.getTraits()));
        float y = h - 62f;
        Ui.textIn(p, 0f, y, 200f, Ui.LINE_H, days + (days.equals("1") ? " day left" : " days left"), Ui.INS15, Ui.text(), Ui.LEFT, new String[]{days}, new Color[]{Ui.hl()});
        Ui.textIn(p, w - 200f, y, 200f, Ui.LINE_H, size, Ui.INS15, Ui.text(), Ui.RIGHT, sizeParts(size), hls(sizeParts(size).length));
        UiData.thinbar(p, 0f, y + 22f, w, 1f - left / brood.days, Ui.barBlue());

        Ui.rule(p, 0f, h - 36f, w);
        Color hl = Misc.getHighlightColor();
        ButtonAPI cancel = UiButtons.button(p, w - START_W, h - PetCard.BUTTON_H, START_W, PetCard.BUTTON_H, "Cancel brood", UiButtons.Kind.AMBER, CutStyle.TL_BR,
                () -> panel.popup(new UiConfirm("Confirm", info -> info.addPara("Cancel the brood of %s and %s?\n\nBoth go back to Pet storage. There will be no young.", 0f,
                        new Color[]{hl, hl}, a.name, b.name), () -> shop.cancelBrood(brood))));
        Ui.tooltip(p, cancel, "Cancel brood", "Both parents go back to Pet storage, Unsettled for " + Math.round(PetConstants.UNSETTLED_DAYS) + " days.");
    }

    private void readyCard(CustomPanelAPI p, float w, float h, PetBrood brood) {
        Pet a = manager.get(brood.parentA);
        Pet b = manager.get(brood.parentB);
        List<Pet> young = brood.getYoung();
        title(p, w, a.name + " x " + b.name);
        tagline(p, w, young.size() == 1 ? "One young is waiting." : young.size() + " young are waiting.");

        float y = DIALS_Y;
        Ui.heading(p, 0f, y, w, "Young", String.valueOf(young.size()));
        y += Ui.HEADING_H + 6f;
        for (int i = 0; i < young.size(); i++) {
            Pet pet = young.get(i);
            float x = 2f + i * (YOUNG_W + YOUNG_GAP + 16f);
            if (brood.opened.contains(pet.id)) {
                Ui.card(p, x, y, YOUNG_W + 16f, YOUNG_H);
                UiImages.img(p, x + 8f, y + 8f, YOUNG_W, YOUNG_W, pet.getIcon());
            } else PetYoungDialog.back(p, x, y, YOUNG_W + 16f, YOUNG_H);
        }

        Ui.rule(p, 0f, h - 36f, w);
        treeButton(p, h, a, b);
        UiButtons.button(p, w - START_W, h - PetCard.BUTTON_H, START_W, PetCard.BUTTON_H, "Open brood", UiButtons.Kind.GREEN, CutStyle.TL_BR,
                () -> panel.popup(new PetYoungDialog(shop, brood)));
    }
}
