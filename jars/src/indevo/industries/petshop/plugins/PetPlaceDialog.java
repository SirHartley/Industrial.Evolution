package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.ui.*;

import java.awt.*;
import java.util.List;

// where to popup for Move... and Assign...: a ship without a pet, or the Pet storage of the docked Animal Center
public class PetPlaceDialog extends BaseCustomDialogDelegate {

    static final float W = 560f;
    static final float HEAD_Y = 34f;
    static final float ROWS_Y = 56f;
    static final float ROW_H = 50f;
    static final float ROW_PITCH = 54f;
    static final float ROW_GAP = 4f;
    static final int ROWS_SHOWN = 4;
    static final String SHIPS = "ships";

    private final Pet pet;
    private final String verb;
    private final List<FleetMemberAPI> ships;
    private final PetShop storage;
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private InteractionDialogAPI dialog;
    private UiRoot root;
    // index into ships, -1 for the storage row
    private int pick;

    public PetPlaceDialog(Pet pet, String verb, List<FleetMemberAPI> ships, PetShop storage) {
        this.pet = pet;
        this.verb = verb;
        this.ships = ships;
        this.storage = storage;
        this.pick = ships.isEmpty() ? -1 : 0;
    }

    public void show(Runnable onClose) {
        UiHost.over(dialog -> {
            this.dialog = dialog;
            dialog.showCustomDialog(W, height(), this);
        }, onClose);
    }

    private float shipsHeight() {
        if (ships.isEmpty()) return Ui.LINE_H + 2f;
        int rows = Math.min(ROWS_SHOWN, (ships.size() + 1) / 2);
        return rows * ROW_PITCH - ROW_GAP;
    }

    private float height() {
        float h = ROWS_Y + shipsHeight();
        if (storage != null) h += PetsPanel.HEAD_GAP + Ui.HEADING_H + 4f + ROW_H;
        return h;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        root = new UiRoot(panel, scroll, this::build);
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        Ui.runs(p, 0f, 0f, w, Ui.INS21, Ui.text(), Ui.LEFT, verb + " " + pet.name, new String[]{pet.name}, new Color[]{Ui.hl()});
        Ui.heading(p, 0f, HEAD_Y, w, "To a ship without a pet", Ui.LEFT);

        if (ships.isEmpty()) Ui.text(p, 0f, ROWS_Y + 2f, w, "No ship without a pet.", Ui.gray());
        else if (ships.size() > 2 * ROWS_SHOWN) addScrolling(p, w);
        else {
            float rw = Math.round((w - ROW_GAP) / 2f);
            for (int i = 0; i < ships.size(); i++) shipRow(p, (i % 2) * (rw + ROW_GAP), ROWS_Y + (i / 2) * ROW_PITCH, rw, i);
        }

        if (storage == null) return;

        float y = ROWS_Y + shipsHeight() + PetsPanel.HEAD_GAP;
        Ui.heading(p, 0f, y, w, "To storage", Ui.LEFT);
        y += Ui.HEADING_H + 4f;

        CustomPanelAPI row = UiButtons.row(p, 0f, y, w, ROW_H, pick < 0, true, null, () -> pick(-1));
        Ui.dashbox(row, 3f, 3f, 44f, 44f);
        Ui.text(row, 56f, 7f, w - 62f, "Pet storage");
        String cost = Ui.credits(storage.getMonthlyStorageCost(pet));
        Ui.runs(row, 56f, 26f, w - 62f, Ui.INS15, Ui.gray(), Ui.LEFT, storage.getMarket().getName() + ", " + cost + " per month", new String[]{cost}, new Color[]{Ui.hl()});
    }

    private void addScrolling(CustomPanelAPI p, float w) {
        UiScroll.Column column = UiScroll.list(p, 0f, ROWS_Y, w, shipsHeight(), scroll, SHIPS);
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
        pick = index;
        root.refresh();
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
        if (pick >= 0) pet.place(ships.get(pick), null);
        else if (storage != null) pet.place(null, storage);
        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        dialog.dismiss();
    }
}
