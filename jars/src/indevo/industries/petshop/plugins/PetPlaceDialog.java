package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.RightClickActionHelper;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetData;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

// where to popup for Move..., Assign... and Adopt (a ship without a pet, or the Pet storage of the docked Animal Center)
// and for a Cryochamber opened from the cargo (a name, then a ship without a pet or the menagerie)
public class PetPlaceDialog extends BaseCustomDialogDelegate {

    static final float W = 560f;
    static final float HEAD_Y = 34f;
    static final float ROW_H = 50f;
    static final float ROW_PITCH = 54f;
    static final float ROW_GAP = 4f;
    static final int ROWS_SHOWN = 4;
    static final String SHIPS = "ships";

    static final String ALL = "All";
    static final String SORT_FLEET = "Fleet";
    static final List<UiFilter.Item> SIZES = List.of(new UiFilter.Item(ALL, 40f), new UiFilter.Item("Frigate", 72f),
            new UiFilter.Item("Destroyer", 90f), new UiFilter.Item("Cruiser", 74f), new UiFilter.Item("Capital", 74f));
    static final List<UiFilter.Item> KEYS = List.of(new UiFilter.Item(SORT_FLEET, 66f, "The order of your fleet."),
            new UiFilter.Item("Name", 70f), new UiFilter.Item("Size", 62f));

    private final Pet pet;
    private final PetData data;
    private final RightClickActionHelper chamber;
    private final String verb;
    private final List<FleetMemberAPI> ships;
    private final PetShop storage;
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private final UiFilter.State filter = new UiFilter.State(SORT_FLEET, ALL);
    private InteractionDialogAPI dialog;
    private UiRoot root;
    private TextFieldAPI nameField;
    private String name;
    // index into ships, -1 for the storage or menagerie row
    private int pick;
    // pick mode: Confirm hands the ship back and moves nothing
    private Consumer<FleetMemberAPI> onPick;
    // adopt mode: Confirm pays the storage center and places the offer
    private boolean adopting = false;

    private PetPlaceDialog(Pet pet, PetData data, RightClickActionHelper chamber, String verb, List<FleetMemberAPI> ships, PetShop storage) {
        this.pet = pet;
        this.data = data;
        this.chamber = chamber;
        this.verb = verb;
        this.ships = ships;
        this.storage = storage;
        this.name = pet != null ? pet.name : PetDataRepo.suggestName(data);
        this.pick = ships.isEmpty() ? -1 : 0;
    }

    public PetPlaceDialog(Pet pet, String verb, List<FleetMemberAPI> ships, PetShop storage) {
        this(pet, pet.getData(), null, verb, ships, storage);
    }

    // a ship for a young of the New young popup, which places it on its own Confirm
    public static PetPlaceDialog pick(Pet young, List<FleetMemberAPI> ships, Consumer<FleetMemberAPI> onPick) {
        PetPlaceDialog dialog = new PetPlaceDialog(young, young.getData(), null, "Ship for", ships, null);
        dialog.onPick = onPick;
        return dialog;
    }

    // an offer of the Adoption tab; Cancel buys nothing
    public static PetPlaceDialog adopt(Pet offer, PetShop shop) {
        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        PetPlaceDialog dialog = new PetPlaceDialog(offer, offer.getData(), null, "Adopt", free, shop);
        dialog.adopting = true;
        return dialog;
    }

    // the pet is made on Confirm, so Cancel leaves the Cryochamber in the cargo
    public static PetPlaceDialog chamber(PetData data, RightClickActionHelper helper) {
        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        return new PetPlaceDialog(null, data, helper, "Open Cryochamber", free, null);
    }

    // a young put in a Cryochamber from the New young popup
    public static PetPlaceDialog chamber(Pet young, RightClickActionHelper helper) {
        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        return new PetPlaceDialog(young, young.getData(), helper, "Open Cryochamber", free, null);
    }

    public void show(Runnable onClose) {
        UiHost.over(dialog -> {
            this.dialog = dialog;
            dialog.showCustomDialog(W, height(), this);
        }, onClose);
    }

    private boolean isChamber() {
        return chamber != null;
    }

    private boolean hasLastRow() {
        return isChamber() || storage != null;
    }

    private float shipsHead() {
        return isChamber() ? HEAD_Y + PetCard.IDENT + PetsPanel.HEAD_GAP : HEAD_Y;
    }

    // the filter bar only shows while the ship list scrolls
    private boolean hasFilter() {
        return ships.size() > 2 * ROWS_SHOWN;
    }

    private float shipsHeight() {
        if (ships.isEmpty()) return Ui.LINE_H + 2f;
        if (hasFilter()) return Math.max(rowsHeight(ROWS_SHOWN), barHeight(true) + rowsHeight(ROWS_SHOWN - 1));
        return rowsHeight(Math.min(ROWS_SHOWN, (ships.size() + 1) / 2));
    }

    private static float rowsHeight(int rows) {
        return rows * ROW_PITCH - ROW_GAP;
    }

    private static float barHeight(boolean open) {
        return UiFilter.height(open, true, true) + UiFilter.BELOW;
    }

    private float height() {
        float h = shipsHead() + Ui.HEADING_H + 4f + shipsHeight();
        if (hasLastRow()) h += PetsPanel.HEAD_GAP + Ui.HEADING_H + 4f + ROW_H;
        return h;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        root = new UiRoot(panel, scroll, this::build);
        root.refresh();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return new BaseCustomUIPanelPlugin() {
            @Override
            public void advance(float amount) {
                if (root != null && filter.poll()) filtered();
            }
        };
    }

    private void filtered() {
        keepName();
        scroll.reset(SHIPS);
        root.refresh();
    }

    private void refresh() {
        keepName();
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        if (isChamber()) addChamber(p, w);
        else if (adopting) addAdoptTitle(p, w);
        else Ui.runs(p, 0f, 0f, w, Ui.INS21, Ui.text(), Ui.LEFT, verb + " " + pet.name, new String[]{pet.name}, new Color[]{Ui.hl()});

        float y = shipsHead();
        Ui.heading(p, 0f, y, w, "To a ship without a pet", Ui.LEFT);
        y += Ui.HEADING_H + 4f;

        if (ships.isEmpty()) Ui.text(p, 0f, y + 2f, w, "No ship without a pet.", Ui.gray());
        else if (hasFilter()) addFiltered(p, y, w, shown());
        else {
            float rw = Math.round((w - ROW_GAP) / 2f);
            for (int i = 0; i < ships.size(); i++) shipRow(p, (i % 2) * (rw + ROW_GAP), y + (i / 2) * ROW_PITCH, rw, i);
        }

        if (!hasLastRow()) return;

        y += shipsHeight() + PetsPanel.HEAD_GAP;
        Ui.heading(p, 0f, y, w, isChamber() ? "To the menagerie" : "To storage", Ui.LEFT);
        y += Ui.HEADING_H + 4f;

        CustomPanelAPI row = UiButtons.row(p, 0f, y, w, ROW_H, pick < 0, true, null, () -> pick(-1));
        Ui.dashbox(row, 3f, 3f, 44f, 44f);

        if (isChamber()) {
            Ui.text(row, 56f, 7f, w - 62f, "Menagerie");
            Ui.text(row, 56f, 26f, w - 62f, "In the fleet, on no ship", Ui.gray());
            return;
        }

        Ui.text(row, 56f, 7f, w - 62f, "Pet storage");
        String cost = Ui.credits(storage.getMonthlyStorageCost(pet));
        Ui.runs(row, 56f, 26f, w - 62f, Ui.INS15, Ui.gray(), Ui.LEFT, storage.getMarket().getName() + ", " + cost + " per month", new String[]{cost}, new Color[]{Ui.hl()});
    }

    // a long name is cut so the price stays on the line
    private void addAdoptTitle(CustomPanelAPI p, float w) {
        String price = Ui.credits(storage.getOfferPrice(pet));
        String tail = " for " + price;
        String shown = Ui.fit(pet.name, Ui.INS21, w - Ui.width(verb + "  " + tail, Ui.INS21) - 8f);
        Ui.runs(p, 0f, 0f, w, Ui.INS21, Ui.text(), Ui.LEFT, verb + " " + shown + tail, new String[]{shown, price}, new Color[]{Ui.hl(), Ui.hl()});
    }

    private void addChamber(CustomPanelAPI p, float w) {
        Ui.text(p, 0f, 0f, w, verb, Ui.INS21, Ui.text(), Ui.LEFT);

        CustomPanelAPI card = Ui.card(p, 0f, HEAD_Y, PetCard.IDENT, PetCard.IDENT);
        UiImages.img(card, 4f, 4f, PetCard.PORTRAIT, PetCard.PORTRAIT, pet != null ? pet.getIcon() : data.icon);

        float x = PetCard.GRID_X;
        nameField = PetCard.nameField(p, x, HEAD_Y, w - x, name);
        UiData.grid(p, x, HEAD_Y + PetCard.NAME_H + 8f, w - x, UiData.GRID_ROW_H, pet != null ? List.of(
                GridRow.of("Species", PetCard.species(pet), Ui.text()),
                GridRow.of("Age", PetCard.age(pet), Ui.text())) : List.of(
                GridRow.of("Species", data.species, Ui.text()),
                GridRow.of("Life span", lifeSpan(data), Ui.text())));
    }

    static String lifeSpan(PetData data) {
        return about(data.maxLife);
    }

    static String about(float days) {
        int cycles = Math.round(days / 365f);
        if (cycles < 1) return "About " + Math.max(1, Math.round(days)) + (Math.round(days) == 1 ? " day" : " days");
        return "About " + cycles + (cycles == 1 ? " cycle" : " cycles");
    }

    // the popup can not grow, so the open fold takes one row of the ship list
    private void addFiltered(CustomPanelAPI p, float y, float w, List<Integer> shown) {
        UiFilter.bar(p, 0f, y, filter, "Ship or class", "Filters and sort order of this list.", SIZES, this::size, KEYS, this::filtered, this::refresh);
        y += barHeight(filter.open);

        if (shown.isEmpty()) {
            Ui.text(p, 0f, y + 2f, w, "No ship matches.", Ui.gray());
            return;
        }

        if (pick >= 0 && !shown.contains(pick)) pick = shown.get(0);

        UiScroll.Column column = UiScroll.list(p, 0f, y, w, rowsHeight(filter.open ? ROWS_SHOWN - 1 : ROWS_SHOWN), scroll, SHIPS);
        float rw = Math.round((column.width() - ROW_GAP) / 2f);

        for (int i = 0; i < shown.size(); i += 2) {
            CustomPanelAPI b = column.block(ROW_H, i == 0 ? 0f : ROW_GAP);
            shipRow(b, 0f, 0f, rw, shown.get(i));
            if (i + 1 < shown.size()) shipRow(b, rw + ROW_GAP, 0f, rw, shown.get(i + 1));
        }

        column.end();
    }

    private void size(String label) {
        filter.on.clear();
        filter.on.add(label);
    }

    // indexes into ships that pass the filter, in the picked order
    private List<Integer> shown() {
        List<Integer> shown = new ArrayList<>();
        HullSize size = switch (filter.on.isEmpty() ? ALL : filter.on.iterator().next()) {
            case "Frigate" -> HullSize.FRIGATE;
            case "Destroyer" -> HullSize.DESTROYER;
            case "Cruiser" -> HullSize.CRUISER;
            case "Capital" -> HullSize.CAPITAL_SHIP;
            default -> null;
        };

        for (int i = 0; i < ships.size(); i++) {
            FleetMemberAPI m = ships.get(i);
            if ((size == null || m.getHullSpec().getHullSize() == size) && filter.matches(m.getShipName(), PetCard.shipClass(m))) shown.add(i);
        }

        Comparator<Integer> name = Comparator.comparing(i -> ships.get(i).getShipName(), String.CASE_INSENSITIVE_ORDER);
        filter.sort(shown, switch (filter.sort) {
            case "Name" -> name;
            case "Size" -> Comparator.comparingInt((Integer i) -> -ships.get(i).getHullSpec().getHullSize().ordinal()).thenComparing(Comparator.naturalOrder());
            default -> null;
        });
        return shown;
    }

    private void shipRow(CustomPanelAPI p, float x, float y, float rw, int index) {
        FleetMemberAPI member = ships.get(index);
        CustomPanelAPI row = UiButtons.row(p, x, y, rw, ROW_H, pick == index, true, null, () -> pick(index));
        UiImages.ship(row, 3f, 3f, 44f, member, true, false);
        Ui.text(row, 56f, 7f, rw - 62f, member.getShipName());
        Ui.text(row, 56f, 26f, rw - 62f, PetCard.shipClass(member), Ui.gray());
    }

    private void pick(int index) {
        pick = index;
        refresh();
    }

    // the field is made again on every rebuild; an empty field keeps the suggested name
    private void keepName() {
        if (nameField == null) return;
        String typed = PetCard.trimName(nameField.getText());
        if (!typed.isEmpty()) name = typed;
    }

    @Override
    public boolean hasCancelButton() {
        return true;
    }

    @Override
    public String getConfirmText() {
        return "Confirm";
    }

    @Override
    public String getCancelText() {
        return "Cancel";
    }

    @Override
    public void customDialogConfirm() {
        FleetMemberAPI ship = pick >= 0 ? ships.get(pick) : null;

        if (onPick != null) {
            if (ship != null) onPick.accept(ship);
        } else if (adopting) storage.adopt(pet, ship);
        else if (isChamber() && pet != null) {
            keepName();
            pet.name = name;
            pet.place(ship, null);
            chamber.removeFromClickedStackFirst(1);
        } else if (isChamber()) {
            keepName();
            Pet born = new Pet(data.id, name);
            PetStatusManager.getInstance().register(born);
            born.place(ship, null);
            chamber.removeFromClickedStackFirst(1);
        } else if (ship != null) pet.place(ship, null);
        else if (storage != null) pet.place(null, storage);

        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        dialog.dismiss();
    }
}
