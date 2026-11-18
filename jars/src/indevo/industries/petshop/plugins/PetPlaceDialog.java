package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemPlugin.RightClickActionHelper;
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
import java.util.List;

// where to popup for Move... and Assign... (a ship without a pet, or the Pet storage of the docked Animal Center)
// and for a Cryochamber opened from the cargo (a name, then a ship without a pet or the menagerie)
public class PetPlaceDialog extends BaseCustomDialogDelegate {

    static final float W = 560f;
    static final float HEAD_Y = 34f;
    static final float ROW_H = 50f;
    static final float ROW_PITCH = 54f;
    static final float ROW_GAP = 4f;
    static final int ROWS_SHOWN = 4;
    static final String SHIPS = "ships";

    private final Pet pet;
    private final PetData data;
    private final RightClickActionHelper chamber;
    private final String verb;
    private final List<FleetMemberAPI> ships;
    private final PetShop storage;
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private InteractionDialogAPI dialog;
    private UiRoot root;
    private TextFieldAPI nameField;
    private String name;
    // index into ships, -1 for the storage or menagerie row
    private int pick;

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

    // the pet is made on Confirm, so Cancel leaves the Cryochamber in the cargo
    public static PetPlaceDialog chamber(PetData data, RightClickActionHelper helper) {
        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        return new PetPlaceDialog(null, data, helper, "Open Cryochamber", free, null);
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

    private float shipsHeight() {
        if (ships.isEmpty()) return Ui.LINE_H + 2f;
        int rows = Math.min(ROWS_SHOWN, (ships.size() + 1) / 2);
        return rows * ROW_PITCH - ROW_GAP;
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

    private void build(CustomPanelAPI p, float w, float h) {
        if (isChamber()) addChamber(p, w);
        else Ui.runs(p, 0f, 0f, w, Ui.INS21, Ui.text(), Ui.LEFT, verb + " " + pet.name, new String[]{pet.name}, new Color[]{Ui.hl()});

        float y = shipsHead();
        Ui.heading(p, 0f, y, w, "To a ship without a pet", Ui.LEFT);
        y += Ui.HEADING_H + 4f;

        if (ships.isEmpty()) Ui.text(p, 0f, y + 2f, w, "No ship without a pet.", Ui.gray());
        else if (ships.size() > 2 * ROWS_SHOWN) addScrolling(p, y, w);
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

    private void addChamber(CustomPanelAPI p, float w) {
        Ui.text(p, 0f, 0f, w, verb, Ui.INS21, Ui.text(), Ui.LEFT);

        CustomPanelAPI card = Ui.card(p, 0f, HEAD_Y, PetCard.IDENT, PetCard.IDENT);
        UiImages.img(card, 4f, 4f, PetCard.PORTRAIT, PetCard.PORTRAIT, data.icon);

        float x = PetCard.GRID_X;
        nameField = PetCard.nameField(p, x, HEAD_Y, w - x, name);
        UiData.grid(p, x, HEAD_Y + PetCard.NAME_H + 8f, w - x, UiData.GRID_ROW_H, List.of(
                GridRow.of("Species", data.species, Ui.text()),
                GridRow.of("Life span", lifeSpan(data), Ui.text())));
    }

    static String lifeSpan(PetData data) {
        int cycles = Math.round(data.maxLife / 365f);
        if (cycles < 1) return "About " + Math.round(data.maxLife) + " days";
        return "About " + cycles + (cycles == 1 ? " cycle" : " cycles");
    }

    private void addScrolling(CustomPanelAPI p, float y, float w) {
        UiScroll.Column column = UiScroll.list(p, 0f, y, w, shipsHeight(), scroll, SHIPS);
        float rw = Math.round((column.width() - ROW_GAP) / 2f);

        for (int i = 0; i < ships.size(); i += 2) {
            CustomPanelAPI b = column.block(ROW_H, i == 0 ? 0f : ROW_GAP);
            shipRow(b, 0f, 0f, rw, i);
            if (i + 1 < ships.size()) shipRow(b, rw + ROW_GAP, 0f, rw, i + 1);
        }

        column.end();
    }

    private void shipRow(CustomPanelAPI p, float x, float y, float rw, int index) {
        FleetMemberAPI member = ships.get(index);
        CustomPanelAPI row = UiButtons.row(p, x, y, rw, ROW_H, pick == index, true, null, () -> pick(index));
        UiImages.ship(row, 3f, 3f, 44f, member, true, false);
        Ui.text(row, 56f, 7f, rw - 62f, member.getShipName());
        Ui.text(row, 56f, 26f, rw - 62f, PetCard.shipClass(member), Ui.gray());
    }

    private void pick(int index) {
        keepName();
        pick = index;
        root.refresh();
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

        if (isChamber()) {
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
