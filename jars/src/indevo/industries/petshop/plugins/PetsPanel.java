package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetPlan;
import indevo.utils.ui.*;

import java.awt.Color;
import java.util.*;

public class PetsPanel extends BaseCustomDialogDelegate {

    public static final float BOARD_W = 1060f;
    public static final float BOARD_H = 720f;
    static final float BODY_Y = 64f;
    static final float LIST_W = 560f;
    static final float CARD_X = LIST_W + 12f;
    static final float TAB_W = 120f;
    static final float TILE_W = 86f;
    static final float TILE_H = 144f;
    static final float TILE_PITCH = 92f;
    static final float TILE_GAP = 6f;
    static final float HEAD_GAP = 20f;
    static final int PER_ROW = 6;

    static final String SORT_SHIP = "Ship";
    static final String MARKED = "Marked";
    static final String FAVOURITES = "Favourites";
    static final List<UiFilter.Item> TOGGLES = List.of(new UiFilter.Item(MARKED, 78f, "Only pets with a marker."));
    static final List<UiFilter.Item> KEYS = List.of(
            new UiFilter.Item(SORT_SHIP, 60f, "The order of your fleet."),
            new UiFilter.Item("Name", 70f),
            new UiFilter.Item("Hearts", 78f, "Most hearts first."),
            new UiFilter.Item("Species", 86f),
            new UiFilter.Item("Age", 62f));

    private final PetShop shop;
    private final PetsView view;
    private final Runnable onLeave;
    private CustomDialogCallback callback;
    private UiRoot root;
    private boolean open = false;
    private boolean popupOpen = false;
    private boolean youngChecked = false;

    record Group(String name, List<Pet> pets, String empty) {
    }

    public PetsPanel(PetShop shop, PetsView view, Runnable onLeave) {
        this.shop = shop;
        this.view = view;
        this.onLeave = onLeave;
    }

    public static float getWidth() {
        return UiFrame.width(BOARD_W);
    }

    public static float getHeight() {
        return UiFrame.height(BOARD_H);
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.callback = callback;
        open = true;
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return new BaseCustomUIPanelPlugin() {
            @Override
            public void advance(float amount) {
                view.clock += amount;
                view.hearts.removeIf(h -> view.clock - h.start() > PetCard.HEART_LIFE);
                if (!open) return;
                if (polled().poll()) {
                    if (view.tab == PetsView.TAB_TREE) PetTree.find(view);
                    filtered();
                }
                if (!youngChecked) openReadyBrood();
                if (view.reveal.tick(view.clock) && !popupOpen) refresh();
            }

            @Override
            public void processInput(List<InputEventAPI> events) {
                for (InputEventAPI event : events) if (event.isLMBDownEvent()) view.reveal.skip();
            }
        };
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Return";
    }

    @Override
    public void customDialogConfirm() {
        close();
    }

    @Override
    public void customDialogCancel() {
        close();
    }

    private void close() {
        commitName();
        open = false;
        onLeave.run();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isPopupOpen() {
        return popupOpen;
    }

    public void cancel() {
        callback.dismissCustomDialog(1);
    }

    PetShop shop() {
        return shop;
    }

    void refresh() {
        commitName();
        root.refresh();
    }

    // the popups open their own interaction dialog over this one, the panel is rebuilt when they close
    void popup(UiConfirm confirm) {
        commitName();
        popupOpen = true;
        confirm.show(this::popupClosed);
    }

    void popup(PetPlaceDialog place) {
        commitName();
        popupOpen = true;
        place.show(this::popupClosed);
    }

    void popup(PetYoungDialog young) {
        commitName();
        popupOpen = true;
        young.show(this::popupClosed);
    }

    void popup(PetPlanner.TraitDialog traits) {
        commitName();
        popupOpen = true;
        traits.show(this::popupClosed);
    }

    // a ready brood here opens its New young popup by itself, once, the first time the panel opens after it is ready
    private void openReadyBrood() {
        youngChecked = true;
        if (shop == null || popupOpen) return;

        for (PetBrood brood : shop.getBroods()) {
            if (!brood.isReady() || brood.shown) continue;

            tab(PetsView.TAB_HUSBANDRY);
            view.habitat = brood.habitat;
            refresh();
            popup(new PetYoungDialog(shop, brood));
            return;
        }
    }

    private void popupClosed() {
        popupOpen = false;
        refresh();
    }

    private UiFilter.State polled() {
        return switch (view.tab) {
            case PetsView.TAB_HUSBANDRY -> view.adults;
            case PetsView.TAB_TREE -> view.find;
            case PetsView.TAB_PLAN -> view.planCopy ? view.planCopies : view.planParents;
            case PetsView.TAB_ADOPT -> view.offers;
            case PetsView.TAB_BEAST -> view.beasts;
            default -> view.filter;
        };
    }

    void filtered() {
        view.scroll.reset(switch (view.tab) {
            case PetsView.TAB_HUSBANDRY -> PetsView.ADULTS;
            case PetsView.TAB_TREE -> PetsView.TREE;
            case PetsView.TAB_PLAN -> PetsView.PLAN_LIST;
            case PetsView.TAB_BEAST -> PetsView.BEASTS;
            default -> PetsView.LIST;
        });
        refresh();
    }

    // a new planned young of the species with the given parents, either null, shown on the Planner tab
    void newPlan(String typeId, String parentA, String parentB) {
        showPlan(PetStatusManager.getInstance().newPlan(typeId, parentA, parentB));
    }

    void showPlan(PetPlan plan) {
        tab(PetsView.TAB_PLAN);
        view.planSpecies = plan.typeID;
        view.planSelected = plan.id;
        view.planPlace = -1;
        view.planCopy = false;
        view.planRow = null;
        view.scroll.reset(PetsView.WANTED);
        refresh();
    }

    // the pair picked on the Husbandry tab, where Start brood is
    void openCross(Pet a, Pet b) {
        view.pair.clear();
        view.pair.addAll(List.of(a.id, b.id));
        view.habitat = -1;
        showHusbandry();
    }

    // the Family tree of one pet, from its card
    void showTree(Pet pet) {
        view.treeRoot = pet.id;
        view.treeCard = pet.id;
        view.treePair.clear();
        view.treeFull = false;
        openTree();
    }

    // the Family tree of a pair, from the cross
    void showPair(Pet a, Pet b) {
        view.treeRoot = a.id;
        view.treeCard = null;
        view.treePair.clear();
        view.treePair.addAll(List.of(a.id, b.id));
        view.treeFull = false;
        openTree();
    }

    // the Full view of the tree with one species, from the Bestiary
    void showSpecies(Pet root) {
        view.treeRoot = root.id;
        view.treeCard = null;
        view.treePair.clear();
        view.treeFull = true;
        view.treeSpecies = root.typeID;
        openTree();
    }

    private void openTree() {
        view.tab = PetsView.TAB_TREE;
        view.treeX = 0f;
        view.scroll.reset(PetsView.TREE);
        refresh();
    }

    void showHusbandry() {
        tab(PetsView.TAB_HUSBANDRY);
        refresh();
    }

    void showInPets(Pet pet) {
        tab(PetsView.TAB_PETS);
        view.select(pet.id);
        refresh();
    }

    // dead branches fold again once the tab is left
    private void tab(int tab) {
        if (view.tab == PetsView.TAB_TREE && tab != PetsView.TAB_TREE) view.unfolded.clear();
        view.tab = tab;
    }

    // typed names are kept on every rebuild, before a popup and on leave; an empty field keeps the old name
    private void commitName() {
        if (view.planField != null) {
            PetPlan plan = PetStatusManager.getInstance().getPlan(view.planNamed);
            String name = PetCard.trimName(view.planField.getText());
            if (plan != null && !name.isEmpty()) plan.name = name;
            view.planField = null;
            view.planNamed = null;
        }
        if (view.nameField == null) return;

        Pet pet = PetStatusManager.getInstance().get(view.namePet);
        String name = PetCard.trimName(view.nameField.getText());
        if (pet != null && !name.isEmpty()) pet.rename(name);

        view.nameField = null;
        view.namePet = null;
    }

    private void build(CustomPanelAPI p, float w, float h) {
        List<Group> groups = groups();
        if (shop == null && view.tab == PetsView.TAB_HUSBANDRY) view.tab = PetsView.TAB_PETS;
        if ((shop == null || !shop.hasAdoption()) && view.tab == PetsView.TAB_ADOPT) view.tab = PetsView.TAB_PETS;
        addHeader(p, w, groups);

        if (view.tab == PetsView.TAB_HUSBANDRY) {
            new PetHusbandry(this, view).build(p, w, h);
            return;
        }
        if (view.tab == PetsView.TAB_TREE) {
            new PetTree(this, view).build(p, w, h);
            return;
        }
        if (view.tab == PetsView.TAB_PLAN) {
            new PetPlanner(this, view).build(p, w, h);
            return;
        }
        if (view.tab == PetsView.TAB_ADOPT) {
            new PetAdoption(this, view).build(p, w, h);
            return;
        }
        if (view.tab == PetsView.TAB_BEAST) {
            new PetBestiary(this, view).build(p, w, h);
            return;
        }

        Pet selected = resolveSelection(groups);
        addList(p, h - BODY_Y, groups, selected);
        Ui.vrule(p, LIST_W + 5f, BODY_Y, h - BODY_Y);

        if (selected != null) new PetCard(this, view).build(p, CARD_X, BODY_Y, w - CARD_X, h - BODY_Y, selected);
        else Ui.text(p, CARD_X, BODY_Y, w - CARD_X, "No pets yet.", Ui.gray());
    }

    private List<Group> groups() {
        PetStatusManager manager = PetStatusManager.getInstance();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        List<Group> groups = new ArrayList<>();

        List<Pet> mascots = new ArrayList<>();
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            Pet pet = manager.getPet(m.getVariant());
            if (pet != null && !pet.isDead()) mascots.add(pet);
        }
        groups.add(new Group("Mascots", mascots, "No pets on ships."));

        // pets on ships of a detachment, by fleet; pets on ships in a storage submarket are not listed
        Map<CampaignFleetAPI, List<Pet>> away = new LinkedHashMap<>();
        for (Pet pet : manager.getPets()) {
            CampaignFleetAPI on = awayFleet(pet);
            if (!pet.isDead() && on != null) away.computeIfAbsent(on, f -> new ArrayList<>()).add(pet);
        }
        for (Map.Entry<CampaignFleetAPI, List<Pet>> e : away.entrySet()) groups.add(new Group(e.getKey().getName(), e.getValue(), null));

        groups.add(new Group("Menagerie", manager.getMenageriePets(), "No pets in the menagerie."));

        if (shop != null) {
            List<Pet> stored = new ArrayList<>();
            for (Pet pet : shop.getStoredPetsPetsCopy()) if (!pet.isDead()) stored.add(pet);
            stored.addAll(shop.getHabitatPets());
            groups.add(new Group("Pet storage", stored, "No pets in storage."));
        }

        // favourites move out of their place group into one group on top, in the order they were listed
        List<Pet> favourites = new ArrayList<>();
        for (Group group : groups) {
            for (Pet pet : group.pets()) if (pet.favourite) favourites.add(pet);
            group.pets().removeIf(pet -> pet.favourite);
        }
        groups.add(0, new Group(FAVOURITES, favourites, "Star a pet on its card to make it a favourite."));

        return groups;
    }

    // the fleet a pet's ship is out with when that is not the player fleet, else null
    public static CampaignFleetAPI awayFleet(Pet pet) {
        FleetMemberAPI member = pet.assignedFleetMember;
        if (member == null || member.getFleetData() == null) return null;

        CampaignFleetAPI fleet = member.getFleetData().getFleet();
        return fleet == null || fleet.isPlayerFleet() || !fleet.isAlive() ? null : fleet;
    }

    private Pet resolveSelection(List<Group> groups) {
        Pet first = null;
        for (Group group : groups) {
            for (Pet pet : group.pets()) {
                if (first == null) first = pet;
                if (pet.id.equals(view.selected)) return pet;
            }
        }

        if (first != null) view.select(first.id);
        return first;
    }

    void select(Pet pet) {
        view.select(pet.id);
        refresh();
    }

    // one width for all tabs, wide enough for the longest label (UiButtons.tab cuts at w - 12)
    private static float tabWidth(String[] tabs) {
        float w = TAB_W;
        for (String tab : tabs) w = Math.max(w, (float) Math.ceil(Ui.width(tab, Ui.ORB12)) + 16f);
        return w;
    }

    private void addHeader(CustomPanelAPI p, float w, List<Group> groups) {
        UiFrame.title(p, w, "Pets", shop != null ? shop.getMarket().getName() : null, this::addHelp);

        int count = 0;
        for (Group group : groups) count += group.pets().size();
        List<Integer> ids = new ArrayList<>(List.of(PetsView.TAB_PETS));
        List<String> labels = new ArrayList<>(List.of("Pets " + count));
        if (shop != null) {
            ids.add(PetsView.TAB_HUSBANDRY);
            labels.add("Husbandry " + PetHusbandry.busy(shop));
        }
        if (shop != null && shop.hasAdoption()) {
            ids.add(PetsView.TAB_ADOPT);
            labels.add("Adoption");
        }
        ids.add(PetsView.TAB_BEAST);
        labels.add("Bestiary");
        ids.addAll(List.of(PetsView.TAB_TREE, PetsView.TAB_PLAN));
        labels.addAll(List.of("Family tree", "Planner"));
        String[] tabs = labels.toArray(new String[0]);
        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, tabWidth(tabs), UiFrame.HEADER_H, tabs, ids.indexOf(view.tab), null, i -> {
            int tab = ids.get(i);
            if (tab == PetsView.TAB_TREE && view.tab != PetsView.TAB_TREE) {
                Pet pet = PetStatusManager.getInstance().get(view.selected);
                if (pet != null) view.treeRoot = view.treeCard = pet.id;
                view.treePair.clear();
            }
            if (tab == PetsView.TAB_PLAN && view.tab != PetsView.TAB_PLAN && view.planSpecies == null) {
                Pet pet = PetStatusManager.getInstance().get(view.selected);
                if (pet != null) view.planSpecies = pet.typeID;
            }
            tab(tab);
            refresh();
        });

        if (shop == null) return;
        float right = UiFrame.credits(p, w, UiFrame.HEADER_Y);
        UiFrame.counter(p, right, UiFrame.HEADER_Y, "", Global.getSettings().getCommoditySpec(ItemIds.RARE_PARTS).getIconName(), String.valueOf(shop.getRelics()));
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        if (view.tab == PetsView.TAB_BEAST) {
            tooltip.addPara("Bestiary. Every species you owned or saw on offer. It carries over between games.", 0f);
            tooltip.addPara("A grey species is known from an earlier game only. A species you never saw is a question mark.", 10f);
            tooltip.addPara("Mutations found lists the highest level of each mutation among your pets of the species in this game.", 10f);
            return;
        }
        if (view.tab == PetsView.TAB_ADOPT) {
            tooltip.addPara("Adoption. This Animal Center finds new homes for the pets handed in to it. New pets come at the start of each month.", 0f);
            tooltip.addPara("Their talents stay Unknown until a heart switches them on. A gene scan reads them before you pay.", 10f);
            tooltip.addPara("Pets on offer have no mutations or flaws.", 10f);
            return;
        }
        if (view.tab == PetsView.TAB_PLAN) {
            tooltip.addPara("Planner. A planned young stands in for a pet you do not have yet. Set what you want from it, then pick its parents.", 0f);
            tooltip.addPara("Chances use known traits only. A planned young counts as a parent with what is wanted from it.", 10f);
            tooltip.addPara("A plan starts nothing. Open the pairing at an Animal Center to pair them.", 10f);
            return;
        }

        Color hl = Misc.getHighlightColor();
        tooltip.addPara("Every pet on a ship reaches %s hearts with time. Each heart switches on one talent, the best first.", 0f, hl,
                String.valueOf(PetConstants.TIME_HEARTS));
        tooltip.addPara("Hearts %s and %s are for favourites: answer their wants and take them along. A favourite asks for something about every %s months. An unanswered want costs nothing.", 10f, hl,
                String.valueOf(PetConstants.NEST_HEARTS), String.valueOf(PetConstants.MAX_HEARTS), String.valueOf(Math.round(PetConstants.WANT_COOLDOWN_DAYS / 30f)));
        tooltip.addPara("A pet that moves to another ship is Unsettled for %s days, %s if it had a nest. Its effects are off. Hearts stay.", 10f, hl,
                Misc.getRoundedValueMaxOneAfterDecimal(PetConstants.UNSETTLED_DAYS), Misc.getRoundedValueMaxOneAfterDecimal(PetConstants.NEST_UNSETTLED_DAYS));
    }

    // the selected pet keeps its card while the filter hides its tile
    private void addList(CustomPanelAPI p, float h, List<Group> groups, Pet selected) {
        UiFilter.State filter = view.filter;
        float bar = UiFilter.bar(p, 2f, BODY_Y, filter, "Name, species or trait", "Filters and sort order of this list.", TOGGLES, null, KEYS, this::filtered, this::refresh);
        float top = bar + UiFilter.BELOW;

        UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y + top, LIST_W, h - top, view.scroll, PetsView.LIST);
        float w = list.width();
        boolean first = true;
        boolean filtering = filter.isFiltering();

        for (Group group : groups) {
            List<Pet> pets = new ArrayList<>();
            for (Pet pet : group.pets()) if (filter.matches(searchText(pet)) && (!filter.on.contains(MARKED) || PetCard.marked(pet))) pets.add(pet);
            if (filtering && pets.isEmpty()) continue;
            filter.sort(pets, order(filter.sort));

            CustomPanelAPI head = list.heading(first ? 0f : HEAD_GAP);
            String count = filtering ? pets.size() + " of " + group.pets().size()
                    : group.name().equals(FAVOURITES) ? pets.size() + " / " + PetConstants.MAX_FAVOURITES : String.valueOf(pets.size());
            Ui.heading(head, 0f, 0f, w, group.name(), count);
            first = false;

            if (pets.isEmpty()) {
                Ui.text(list.block(Ui.LINE_H, 8f), 0f, 0f, w, group.empty(), Ui.gray());
                continue;
            }

            for (int row = 0; row * PER_ROW < pets.size(); row++) {
                CustomPanelAPI b = list.block(TILE_H, row == 0 ? 4f : TILE_GAP);
                for (int i = row * PER_ROW; i < Math.min(pets.size(), (row + 1) * PER_ROW); i++) {
                    Pet pet = pets.get(i);
                    PetCard.tile(this, b, 2f + (i % PER_ROW) * TILE_PITCH, 0f, pet, pet == selected);
                }
            }
        }

        if (first) Ui.text(list.block(Ui.LINE_H, 0f), 0f, 0f, w, "No pet matches.", Ui.gray());
        list.end();
    }

    // null keeps the group order: the fleet order on ships, the order they came in elsewhere
    // traits the player has not seen stay out of the search
    private static String[] searchText(Pet pet) {
        List<String> text = new ArrayList<>(List.of(pet.name, pet.getData().species, pet.getLookName()));
        text.addAll(pet.getKnownTraitNames());
        return text.toArray(new String[0]);
    }

    private static Comparator<Pet> order(String key) {
        Comparator<Pet> name = Comparator.comparing(pet -> pet.name, String.CASE_INSENSITIVE_ORDER);
        return switch (key) {
            case "Name" -> name;
            case "Hearts" -> Comparator.comparingDouble((Pet pet) -> -pet.bondDays).thenComparing(name);
            case "Species" -> Comparator.comparing((Pet pet) -> pet.getData().species, String.CASE_INSENSITIVE_ORDER).thenComparing(name);
            case "Age" -> Comparator.comparingDouble((Pet pet) -> -pet.age).thenComparing(name);
            default -> null;
        };
    }
}
