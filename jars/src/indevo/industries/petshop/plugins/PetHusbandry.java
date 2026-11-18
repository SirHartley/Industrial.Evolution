package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
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
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;
import java.util.*;

// the Husbandry tab of a docked Animal Center: habitats and adults on the left, the selected habitat on the right
public class PetHusbandry {

    static final float HAB_W = 129f;
    static final float HAB_H = 88f;
    static final float HAB_PITCH = 139f;
    static final float HAB_FACE = 36f;
    static final float TILE_H = 134f;
    static final float DIAL_ROW = 18f;
    static final float DIALS_Y = 82f;
    static final float START_W = 104f;
    static final float YOUNG_W = 40f;
    static final float YOUNG_H = 56f;
    static final float YOUNG_GAP = 8f;
    static final String WILD_TYPE = "Wild type";

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
        if (pet != null) UiImages.img(t, x, 7f, HAB_FACE, HAB_FACE, pet.getData().icon);
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
        List<String> text = new ArrayList<>(List.of(pet.name, pet.getData().species, WILD_TYPE));
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
        UiImages.img(t, 11f, 5f, PetCard.PORTRAIT, PetCard.PORTRAIT, pet.getData().icon, null, enabled ? 1f : PetCard.DIM);

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
        } else Ui.textIn(t, 4f, 112f, 78f, Ui.LINE_H, WILD_TYPE, Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);

        if (block != null) Ui.tooltip(t, t, pet.getData().species, block);
    }

    static int unknownTalents(Pet pet) {
        int n = 0;
        for (String id : pet.getTalents()) if (!pet.sheet.isKnown(id)) n++;
        return n;
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

    private static void title(CustomPanelAPI p, float w, String title) {
        Ui.box(p, 0f, 0f, w, PetCard.NAME_H, Misc.scaleAlpha(Ui.base(), 0.45f), null);
        Ui.textIn(p, 4f, 0f, w - 8f, PetCard.NAME_H, Ui.fit(title, Ui.ORB20, w - 8f), Ui.ORB20, Ui.bright(), Ui.CENTER, new String[0], new Color[0]);
    }

    private static void tagline(CustomPanelAPI p, float w, String line) {
        if (line == null || line.isEmpty()) return;
        Ui.wrap(p, 0f, PetCard.TAG_Y, w, Ui.lines(line, Ui.INS15, w, 2), Ui.INS15, Ui.gray(), Alignment.TMID, new String[0], new Color[0]);
    }

    private void pairCard(CustomPanelAPI p, float w, float h, int habitat) {
        Pet a = view.pair.isEmpty() ? null : manager.get(view.pair.get(0));
        Pet b = view.pair.size() < 2 ? null : manager.get(view.pair.get(1));
        boolean pair = a != null && b != null;

        title(p, w, pair ? a.name + " x " + b.name : "Habitat " + (habitat + 1));
        tagline(p, w, pair ? kinLine(a, b) : a != null ? "Pick a second " + a.getData().species + "." : "Pick two adults of one species.");
        dials(p, w, a, b);

        float y = h - 60f;
        float fee = shop.getFee();
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        boolean broke = fee > 0f && credits < fee;
        if (pair) {
            PetData data = a.getData();
            PetSheet sa = PetGenetics.seen(a.sheet);
            PetSheet sb = PetGenetics.seen(b.sheet);
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

    // siblings, a parent and its young, or kin further back; nothing for unrelated adults
    private static String kinLine(Pet a, Pet b) {
        if (a.parentA != null && Objects.equals(a.parentA, b.parentA) && Objects.equals(a.parentB, b.parentB)) return a.name + " and " + b.name + " come from one brood.";
        if (b.id.equals(a.parentA) || b.id.equals(a.parentB)) return b.name + " is a parent of " + a.name + ".";
        if (a.id.equals(b.parentA) || a.id.equals(b.parentB)) return a.name + " is a parent of " + b.name + ".";
        return PetShop.getInbreeding(a, b) > 0f ? a.name + " and " + b.name + " are kin." : null;
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

    private void runningCard(CustomPanelAPI p, float w, float h, PetBrood brood) {
        Pet a = manager.get(brood.parentA);
        Pet b = manager.get(brood.parentB);
        title(p, w, a.name + " x " + b.name);
        tagline(p, w, kinLine(a, b));
        dials(p, w, a, b);

        float left = shop.getDaysLeft(brood);
        String days = String.valueOf(Math.max(1, (int) Math.ceil(left)));
        String size = broodSize(PetGenetics.broodRange(a.getData(), PetGenetics.seen(a.sheet), PetGenetics.seen(b.sheet), PetDataRepo.getTraits()));
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
                UiImages.img(p, x + 8f, y + 8f, YOUNG_W, YOUNG_W, pet.getData().icon);
            } else PetYoungDialog.back(p, x, y, YOUNG_W + 16f, YOUNG_H, false);
        }

        Ui.rule(p, 0f, h - 36f, w);
        UiButtons.button(p, w - START_W, h - PetCard.BUTTON_H, START_W, PetCard.BUTTON_H, "Open brood", UiButtons.Kind.GREEN, CutStyle.TL_BR,
                () -> panel.popup(new PetYoungDialog(shop, brood)));
    }
}
