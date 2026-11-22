package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.ItemIds;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.listener.PetStatusManager.Unlock;
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
    static final String REMEMBERED = "Remembered";
    static final String FAVOURITES = "Favourites";
    static final List<UiFilter.Item> TOGGLES = List.of(new UiFilter.Item(MARKED, 78f),
            new UiFilter.Item(REMEMBERED, 112f, "The pets that died, also favourites from your other games."));
    static final List<UiFilter.Item> KEYS = List.of(
            new UiFilter.Item(SORT_SHIP, 60f),
            new UiFilter.Item("Name", 70f),
            new UiFilter.Item("Hearts", 78f),
            new UiFilter.Item("Species", 86f),
            new UiFilter.Item("Age", 62f));
    static final List<UiFilter.Item> GONE_KEYS = List.of(
            new UiFilter.Item("Latest", 76f),
            new UiFilter.Item("Name", 70f),
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
        if (lockReason(view.tab) != null) view.tab = PetsView.TAB_PETS;
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

        if (view.filter.on.contains(REMEMBERED)) {
            buildRemembered(p, w, h);
            return;
        }

        Pet selected = resolveSelection(groups);
        addList(p, h - BODY_Y, groups, selected);
        Ui.vrule(p, LIST_W + 5f, BODY_Y, h - BODY_Y);

        if (selected != null) new PetCard(this, view).build(p, CARD_X, BODY_Y, w - CARD_X, h - BODY_Y, selected);
        else Ui.wrap(p, CARD_X, BODY_Y, w - CARD_X, "No pets yet. Animal Centers sell Cryochambers and offer pets for adoption.", Ui.gray());
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
        select(pet.id);
    }

    void select(String id) {
        view.select(id);
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
        List<Integer> ids = List.of(PetsView.TAB_PETS, PetsView.TAB_HUSBANDRY, PetsView.TAB_ADOPT, PetsView.TAB_BEAST, PetsView.TAB_TREE, PetsView.TAB_PLAN);
        String[] tabs = {"Pets " + count, shop != null ? "Husbandry " + PetHusbandry.busy(shop) : "Husbandry", "Adoption", "Bestiary", "Family tree", "Planner"};
        boolean[] enabled = new boolean[tabs.length];
        String[] tips = new String[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            tips[i] = lockReason(ids.get(i));
            enabled[i] = tips[i] == null;
        }
        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, tabWidth(tabs), UiFrame.HEADER_H, tabs, ids.indexOf(view.tab), enabled, tips, i -> {
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

    // why a tab is grey, null when it opens; every tab is always shown
    private String lockReason(int tab) {
        PetStatusManager manager = PetStatusManager.getInstance();
        return switch (tab) {
            case PetsView.TAB_HUSBANDRY -> shop == null ? "Pair pets at an Animal Center." : null;
            case PetsView.TAB_ADOPT -> shop == null || !shop.hasAdoption() ? "Only at Animal Centers with pets for adoption." : null;
            case PetsView.TAB_TREE -> manager.isOpen(Unlock.TREE) ? null : Unlock.TREE.reason;
            case PetsView.TAB_PLAN -> manager.isOpen(Unlock.PLAN) ? null : Unlock.PLAN.reason;
            default -> null;
        };
    }

    // a button into the Family tree or the Planner: grey with the unlock reason until that tab opens
    static ButtonAPI tabButton(CustomPanelAPI p, float x, float y, float w, float h, String label, Unlock unlock, Runnable action) {
        boolean open = PetStatusManager.getInstance().isOpen(unlock);
        ButtonAPI button = UiButtons.button(p, x, y, w, h, label, open ? UiButtons.Kind.STD : UiButtons.Kind.OFF, CutStyle.TL_BR, open ? action : null);
        if (!open) Ui.tooltip(p, button, label, unlock.reason);
        return button;
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        if (view.tab == PetsView.TAB_BEAST) {
            tooltip.addPara("Every species you owned or saw on offer. It carries over between games.", 0f);
            return;
        }
        if (view.tab == PetsView.TAB_ADOPT) {
            tooltip.addPara("New pets come at the start of each month.", 0f);
            tooltip.addPara("A gene scan reads their talents before you pay.", 10f);
            tooltip.addPara("Pets on offer have no mutations or flaws.", 10f);
            return;
        }
        if (view.tab == PetsView.TAB_PLAN) {
            tooltip.addPara("A planned young stands in for a pet you do not have yet.", 0f);
            tooltip.addPara("Chances use known traits only. A planned young counts as a parent with what is wanted from it.", 10f);
            return;
        }
        if (view.tab == PetsView.TAB_HUSBANDRY) {
            tooltip.addPara("Pair two adults of one species in a free habitat. The young come when the brood time runs out.", 0f);
            tooltip.addPara("Each trait rolls on its own for every young. Hover a dial for what it does.", 10f);
            tooltip.addPara("Chances use known traits only. A gene scan reads the rest.", 10f);
            return;
        }
        if (view.tab == PetsView.TAB_TREE) {
            tooltip.addPara("Every pet of a species linked through parents and young, gone pets too.", 0f);
            tooltip.addPara("Hover a pet to light up the lines to its parents. A gold outline marks the shared ancestors of a pair.", 10f);
            tooltip.addPara("The closer the kin, the higher the Inbreeding and the chance of malformed young.", 10f);
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
        float top = bar(p, KEYS) + UiFilter.BELOW;

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

    private float bar(CustomPanelAPI p, List<UiFilter.Item> keys) {
        return UiFilter.bar(p, 2f, BODY_Y, view.filter, "Name, species or trait", null, TOGGLES, this::toggle, keys, this::filtered, this::refresh);
    }

    // Marked and Remembered exclude each other; each list has its own sort keys
    private void toggle(String label) {
        UiFilter.State f = view.filter;
        if (!f.on.remove(label)) {
            f.on.add(label);
            f.on.remove(label.equals(REMEMBERED) ? MARKED : REMEMBERED);
        }

        List<UiFilter.Item> keys = f.on.contains(REMEMBERED) ? GONE_KEYS : KEYS;
        if (keys.stream().noneMatch(k -> k.label().equals(f.sort))) {
            f.sort = keys.get(0).label();
            f.reversed = false;
        }
    }

    // the Pets tab with Remembered on: PetMemorial.remembered in one group in place of the living, the gone card at the right
    private void buildRemembered(CustomPanelAPI p, float w, float h) {
        List<PetMemorial.Gone> all = PetMemorial.remembered();
        PetMemorial.Gone selected = null;
        for (PetMemorial.Gone g : all) if (g.id().equals(view.selected)) selected = g;
        if (selected == null && !all.isEmpty()) {
            selected = all.get(0);
            view.select(selected.id());
        }

        addGoneList(p, h - BODY_Y, all, selected);
        Ui.vrule(p, LIST_W + 5f, BODY_Y, h - BODY_Y);

        PetCard card = new PetCard(this, view);
        if (selected == null) Ui.text(p, CARD_X, BODY_Y, w - CARD_X, "No pet has died.", Ui.gray());
        else if (selected.pet() != null) card.build(p, CARD_X, BODY_Y, w - CARD_X, h - BODY_Y, selected.pet());
        else card.buildLost(p, CARD_X, BODY_Y, w - CARD_X, h - BODY_Y, selected);
    }

    private void addGoneList(CustomPanelAPI p, float h, List<PetMemorial.Gone> all, PetMemorial.Gone selected) {
        UiFilter.State filter = view.filter;
        float top = bar(p, GONE_KEYS) + UiFilter.BELOW;
        UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y + top, LIST_W, h - top, view.scroll, PetsView.LIST);
        float w = list.width();

        List<PetMemorial.Gone> gone = new ArrayList<>();
        for (PetMemorial.Gone g : all) if (filter.matches(searchText(g))) gone.add(g);
        filter.sort(gone, goneOrder(filter.sort));

        String count = filter.query().isEmpty() ? String.valueOf(all.size()) : gone.size() + " of " + all.size();
        Ui.heading(list.heading(0f), 0f, 0f, w, REMEMBERED, count);
        if (gone.isEmpty()) Ui.text(list.block(Ui.LINE_H, 8f), 0f, 0f, w, all.isEmpty() ? "No pet has died." : "No pet matches.", Ui.gray());

        for (int row = 0; row * PER_ROW < gone.size(); row++) {
            CustomPanelAPI b = list.block(TILE_H, row == 0 ? 4f : TILE_GAP);
            for (int i = row * PER_ROW; i < Math.min(gone.size(), (row + 1) * PER_ROW); i++) {
                PetMemorial.Gone g = gone.get(i);
                PetCard.goneTile(this, b, 2f + (i % PER_ROW) * TILE_PITCH, 0f, g, g == selected);
            }
        }
        list.end();
    }

    private static String[] searchText(PetMemorial.Gone gone) {
        if (gone.pet() != null) return searchText(gone.pet());
        List<String> text = new ArrayList<>(List.of(gone.name(), gone.species()));
        text.addAll(gone.titles());
        return text.toArray(new String[0]);
    }

    // null keeps the Latest order of PetMemorial.remembered
    private static Comparator<PetMemorial.Gone> goneOrder(String key) {
        Comparator<PetMemorial.Gone> name = Comparator.comparing(PetMemorial.Gone::name, String.CASE_INSENSITIVE_ORDER);
        return switch (key) {
            case "Name" -> name;
            case "Species" -> Comparator.comparing(PetMemorial.Gone::species, String.CASE_INSENSITIVE_ORDER).thenComparing(name);
            case "Age" -> Comparator.comparingDouble((PetMemorial.Gone g) -> -g.days()).thenComparing(name);
            default -> null;
        };
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
