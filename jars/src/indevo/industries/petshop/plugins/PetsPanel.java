package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
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
                if (open && view.filter.poll()) filtered();
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

    private void popupClosed() {
        popupOpen = false;
        refresh();
    }

    private void filtered() {
        view.scroll.reset(PetsView.LIST);
        refresh();
    }

    // typed names are kept on every rebuild, before a popup and on leave; an empty field keeps the old name
    private void commitName() {
        if (view.nameField == null) return;

        Pet pet = PetStatusManager.getInstance().get(view.namePet);
        String name = PetCard.trimName(view.nameField.getText());
        if (pet != null && !name.isEmpty()) pet.name = name;

        view.nameField = null;
        view.namePet = null;
    }

    private void build(CustomPanelAPI p, float w, float h) {
        List<Group> groups = groups();
        Pet selected = resolveSelection(groups);

        addHeader(p, w, groups);
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
    static CampaignFleetAPI awayFleet(Pet pet) {
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

    private void addHeader(CustomPanelAPI p, float w, List<Group> groups) {
        UiFrame.title(p, w, "Pets", shop != null ? shop.getMarket().getName() : null, this::addHelp);

        int count = 0;
        for (Group group : groups) count += group.pets().size();
        UiButtons.tabs(p, 0f, UiFrame.HEADER_Y, w, TAB_W, UiFrame.HEADER_H, new String[]{"Pets " + count}, 0, null, i -> refresh());

        if (shop != null) UiFrame.credits(p, w, UiFrame.HEADER_Y);
    }

    private void addHelp(TooltipMakerAPI tooltip) {
        Color hl = Misc.getHighlightColor();
        tooltip.addPara("Every pet on a ship reaches %s hearts with time. Its mascot effect starts at %s heart.", 0f, hl,
                String.valueOf(PetConstants.TIME_HEARTS), String.valueOf(PetConstants.MASCOT_HEARTS));
        tooltip.addPara("Hearts %s and %s are for favourites: answer their wants and take them along. A favourite asks for something about every %s months. An unanswered want costs nothing.", 10f, hl,
                String.valueOf(PetConstants.NEST_HEARTS), String.valueOf(PetConstants.MAX_HEARTS), String.valueOf(Math.round(PetConstants.WANT_COOLDOWN_DAYS / 30f)));
        tooltip.addPara("A pet that moves to another ship is Unsettled for %s days, %s if it had a nest. Its effects are off. Hearts stay.", 10f, hl,
                Misc.getRoundedValueMaxOneAfterDecimal(PetConstants.UNSETTLED_DAYS), Misc.getRoundedValueMaxOneAfterDecimal(PetConstants.NEST_UNSETTLED_DAYS));
    }

    // the selected pet keeps its card while the filter hides its tile
    private void addList(CustomPanelAPI p, float h, List<Group> groups, Pet selected) {
        UiFilter.State filter = view.filter;
        float bar = UiFilter.bar(p, 2f, BODY_Y, filter, "Name or species", "Filters and sort order of this list.", TOGGLES, null, KEYS, this::filtered, this::refresh);
        float top = bar + UiFilter.BELOW;

        UiScroll.Column list = UiScroll.list(p, 0f, BODY_Y + top, LIST_W, h - top, view.scroll, PetsView.LIST);
        float w = list.width();
        boolean first = true;
        boolean filtering = filter.isFiltering();

        for (Group group : groups) {
            List<Pet> pets = new ArrayList<>();
            for (Pet pet : group.pets()) if (filter.matches(pet.name, pet.getData().species) && (!filter.on.contains(MARKED) || PetCard.marked(pet))) pets.add(pet);
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
