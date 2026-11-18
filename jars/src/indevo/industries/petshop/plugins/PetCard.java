package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// the tiles of the Pets list and the card of the selected pet, column coordinates
public class PetCard {

    // pet art colour, not a kit colour
    static final Color HEART_RED = new Color(226, 78, 98);
    static final float[] HEART = {7, 13, 1, 7, 1, 3, 3, 1, 5, 1, 7, 3, 9, 1, 11, 1, 13, 3, 13, 7};
    static final float HEART_LIFE = 0.8f;
    static final float PET_TIME = 0.5f;
    static final float PET_GROW = 0.2f;
    static final float PORTRAIT = 64f;
    static final float MARKER = 28f;
    static final float NAME_H = 26f;
    static final float TAG_Y = 34f;
    static final float IDENT_Y = 82f;
    static final float IDENT = 72f;
    static final float GRID_X = 84f;
    static final float SHIP_Y = 170f;
    static final float STRIP_H = 50f;
    static final float BUTTON_W = 96f;
    static final float WIDE_W = 116f;
    static final float BUTTON_H = 24f;

    private final PetsPanel panel;
    private final PetsView view;
    private final PetShop shop;

    record Marker(String sprite, String title, String line) {
    }

    PetCard(PetsPanel panel, PetsView view) {
        this.panel = panel;
        this.view = view;
        this.shop = panel.shop();
    }

    static void tile(PetsPanel panel, CustomPanelAPI p, float x, float y, Pet pet, boolean selected) {
        CustomPanelAPI t = UiButtons.row(p, x, y, PetsPanel.TILE_W, PetsPanel.TILE_H, selected, true, null, () -> panel.select(pet));
        UiImages.img(t, 11f, 5f, PORTRAIT, PORTRAIT, pet.getData().icon);

        Marker marker = marker(pet);
        if (marker != null) {
            CustomPanelAPI icon = UiImages.img(t, 58f, -2f, MARKER, MARKER, marker.sprite());
            Ui.tooltip(t, icon, marker.title(), marker.line());
        }

        Ui.text(t, 4f, 71f, 78f, pet.name, Ui.text(), Ui.CENTER);
        Ui.rule(t, 13f, 107f, 60f);
        if (pet.assignedFleetMember != null) UiImages.ship(t, 30f, 114f, 26f, pet.assignedFleetMember, false, false);
    }

    // one marker per tile, the worst first
    private static Marker marker(Pet pet) {
        if (pet.isStarving()) return new Marker(sprite("pet_marker_starving"), "Starving", "Missed a meal. It does not survive a second one.");
        if (pet.storage == null && !PetStatusManager.getInstance().hasFood(pet))
            return new Marker(sprite("pet_marker_hungry"), "Hungry", "No food aboard for its next meal.");
        if (pet.shellShockedDays > 0f) return new Marker(sprite("pet_marker_shock"), "Shell shocked", "Its ship was in a hard fight.");
        return null;
    }

    private static String sprite(String id) {
        return Global.getSettings().getSpriteName("IndEvo", id);
    }

    void build(CustomPanelAPI parent, float x, float y, float w, float h, Pet pet) {
        CustomPanelAPI p = Ui.panel(parent, x, y, w, h);
        CampaignFleetAPI away = PetsPanel.awayFleet(pet);

        addName(p, w, pet);
        addTagline(p, w, pet);
        addIdent(p, w, pet, away);
        addPlace(p, w, pet, away);
        addBottom(p, w, h, pet, away);
        Ui.painted(p, 0f, 0f, w, h, null, (pos, a) -> paintHearts(pos, a));
    }

    private void addName(CustomPanelAPI p, float w, Pet pet) {
        TextFieldAPI field = UiButtons.field(p, 0f, 0f, w, NAME_H, pet.name, PetConstants.NAME_MAX_CHARS, Ui.ORB20);
        field.setMidAlignment();
        field.setColor(Ui.bright());
        field.setBorderColor(Misc.scaleAlpha(Ui.base(), 0.45f));
        view.nameField = field;
        view.namePet = pet.id;
    }

    private void addTagline(CustomPanelAPI p, float w, Pet pet) {
        boolean answer = view.answer != null;
        String text = answer ? view.answer : view.lines.computeIfAbsent(pet.id, id -> status(pet));
        if (text == null || text.isEmpty()) return;

        String shown = Ui.lines(text, Ui.INS15, w, 2);
        Ui.wrap(p, 0f, TAG_Y, w, shown, Ui.INS15, answer ? Ui.text() : Ui.gray(), Alignment.TMID, new String[0], new Color[0]);
    }

    private String status(Pet pet) {
        String line = PetStatusManager.getInstance().getLine(pet, PetDataRepo.EVENT_STATUS, shop != null);
        return line == null ? "" : line.replace("%s", pet.name) + ".";
    }

    private void addIdent(CustomPanelAPI p, float w, Pet pet, CampaignFleetAPI away) {
        boolean canPet = away == null;
        CustomPanelAPI card = UiButtons.row(p, 0f, IDENT_Y, IDENT, IDENT, false, canPet, null, canPet ? () -> pet(pet) : null);
        SpriteAPI sprite = Global.getSettings().getSprite(pet.getData().icon);
        Ui.painted(card, 4f, 4f, PORTRAIT, PORTRAIT, null, (pos, a) -> paintPortrait(sprite, pos, a));
        if (!canPet) Ui.tooltip(p, card, null, pet.name + " is out with " + away.getName() + ".");

        if (view.clicks > 0) UiData.thinbar(p, 0f, IDENT_Y + IDENT + 3f, IDENT, view.clicks / (float) PetConstants.PETTING_CLICKS, HEART_RED);

        PetMood mood = pet.getMood();
        List<GridRow> rows = new ArrayList<>();
        rows.add(GridRow.of("Species", pet.getData().species, Ui.text()));
        rows.add(GridRow.of("Disposition", Misc.ucFirst(pet.personality), Academy.COLOURS_BY_PERSONALITY.get(pet.personality)));
        rows.add(GridRow.of("Age", age(pet), Ui.text()));
        rows.add(GridRow.of("Mood", mood.label, mood.getColor()));
        UiData.grid(p, GRID_X, IDENT_Y, w - GRID_X, UiData.GRID_ROW_H, rows);
    }

    static String age(Pet pet) {
        int cycles = (int) (pet.age / 365f);
        String span = cycles >= 1 ? cycles + (cycles == 1 ? " cycle" : " cycles") : pet.getAgeString();
        return (pet.isOld() ? "Old, " : "Adult, ") + span;
    }

    private void pet(Pet pet) {
        boolean motion = Setting.PET_MOTION.enabled();
        view.clicks++;
        if (motion) view.hearts.add(new PetsView.Heart(view.clock, Misc.random.nextFloat() * 24f - 12f, 40f, 10f));

        if (view.clicks >= PetConstants.PETTING_CLICKS) {
            view.clicks = 0;
            pet.pet();
            String event = pet.isStarving() ? PetDataRepo.EVENT_PETTING_STARVING : PetDataRepo.EVENT_PETTING;
            String line = PetStatusManager.getInstance().getLine(pet, event, shop != null);
            view.answer = line == null ? null : line.replace("%s", pet.name);

            if (motion) {
                view.pettedAt = view.clock;
                for (int i = 0; i < 6; i++) {
                    float angle = (float) Math.toRadians(30f + i * 24f);
                    view.hearts.add(new PetsView.Heart(view.clock, (float) Math.cos(angle) * 52f, (float) Math.sin(angle) * 52f, 8f + (i % 3) * 2f));
                }
            }
        }

        panel.refresh();
    }

    // grow to 1.2 and back with one eased full turn over PET_TIME after the last petting click
    private void paintPortrait(SpriteAPI sprite, PositionAPI pos, float alpha) {
        float t = view.pettedAt < 0f ? 1f : (view.clock - view.pettedAt) / PET_TIME;
        float scale = 1f;
        float angle = 0f;
        if (t >= 0f && t < 1f) {
            scale = 1f + PET_GROW * (float) Math.sin(Math.PI * t);
            angle = -360f * t * t * (3f - 2f * t);
        }

        float size = Math.round(PORTRAIT * scale);
        sprite.setSize(size, size);
        sprite.setAngle(angle);
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(alpha);
        sprite.setNormalBlend();
        sprite.renderAtCenter(Math.round(pos.getX() + pos.getWidth() / 2f), Math.round(pos.getY() + pos.getHeight() / 2f));
        sprite.setAngle(0f);
    }

    // hearts rise from the top of the portrait and fade, the column is the painter's frame
    private void paintHearts(PositionAPI pos, float alpha) {
        float ox = pos.getX() + IDENT / 2f;
        float oy = pos.getY() + pos.getHeight() - IDENT_Y - 8f;

        for (PetsView.Heart heart : view.hearts) {
            float t = (view.clock - heart.start()) / HEART_LIFE;
            if (t < 0f || t >= 1f) continue;

            float ease = 1f - (1f - t) * (1f - t);
            float hx = ox + heart.dx() * ease - heart.size() / 2f;
            float hy = oy + heart.dy() * ease;
            UiDraw.fan(Math.round(hx), Math.round(hy), heart.size(), 14f, HEART, 7f, 7f, HEART_RED, alpha * (1f - t));
        }
    }

    private void addPlace(CustomPanelAPI p, float w, Pet pet, CampaignFleetAPI away) {
        Ui.card(p, 0f, SHIP_Y, w, STRIP_H);
        float by = SHIP_Y + 13f;
        List<FleetMemberAPI> free = freeShips(pet);
        String noShip = free.isEmpty() ? "No ship without a pet." : null;
        FleetMemberAPI member = pet.assignedFleetMember;

        if (member != null) {
            float bx = w - 6f - BUTTON_W - 6f - BUTTON_W;
            String reason = away != null ? "Out with " + away.getName() : shop == null ? noShip : null;
            UiImages.ship(p, 3f, SHIP_Y + 3f, 44f, member, true, false);
            Ui.text(p, 56f, SHIP_Y + 7f, bx - 64f, member.getShipName());
            Ui.text(p, 56f, SHIP_Y + 26f, bx - 64f, reason != null ? reason : shipClass(member), reason != null ? Ui.neg() : Ui.gray());

            UiButtons.button(p, bx, by, BUTTON_W, BUTTON_H, "Move...", reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                    reason != null ? null : () -> panel.popup(new PetPlaceDialog(pet, "Move", free, shop)));
            UiButtons.button(p, w - 6f - BUTTON_W, by, BUTTON_W, BUTTON_H, "Unassign", away != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                    away != null ? null : () -> {
                        pet.place(null, null);
                        panel.refresh();
                    });
            return;
        }

        Ui.dashbox(p, 3f, SHIP_Y + 3f, 44f, 44f);

        if (pet.storage != null) {
            float bx = w - 6f - WIDE_W - 6f - BUTTON_W;
            Ui.text(p, 56f, SHIP_Y + 7f, bx - 64f, "Pet storage");
            Ui.text(p, 56f, SHIP_Y + 26f, bx - 64f, noShip != null ? noShip : pet.storage.getMarket().getName(), noShip != null ? Ui.neg() : Ui.gray());

            UiButtons.button(p, bx, by, BUTTON_W, BUTTON_H, "Assign...", noShip != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                    noShip != null ? null : () -> panel.popup(new PetPlaceDialog(pet, "Assign", free, null)));
            UiButtons.button(p, w - 6f - WIDE_W, by, WIDE_W, BUTTON_H, "To menagerie", UiButtons.Kind.STD, CutStyle.TL_BR, () -> {
                pet.place(null, null);
                panel.refresh();
            });
            return;
        }

        float bx = w - 6f - BUTTON_W;
        String reason = shop == null ? noShip : null;
        Ui.text(p, 56f, SHIP_Y + 7f, bx - 64f, "Menagerie");
        Ui.text(p, 56f, SHIP_Y + 26f, bx - 64f, reason != null ? reason : "In the fleet, on no ship", reason != null ? Ui.neg() : Ui.gray());
        UiButtons.button(p, bx, by, BUTTON_W, BUTTON_H, "Assign...", reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                reason != null ? null : () -> panel.popup(new PetPlaceDialog(pet, "Assign", free, shop)));
    }

    static String shipClass(FleetMemberAPI member) {
        return member.getHullSpec().getHullName() + " class " + member.getHullSpec().getDesignation().toLowerCase();
    }

    // ships of the player fleet without a pet, in fleet order
    static List<FleetMemberAPI> freeShips(Pet pet) {
        PetStatusManager manager = PetStatusManager.getInstance();
        List<FleetMemberAPI> free = new ArrayList<>();

        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (m.isFighterWing() || m == pet.assignedFleetMember || manager.getPet(m.getVariant()) != null) continue;
            free.add(m);
        }

        return free;
    }

    private void addBottom(CustomPanelAPI p, float w, float h, Pet pet, CampaignFleetAPI away) {
        Ui.rule(p, 0f, h - 36f, w);
        float y = h - BUTTON_H;
        Color hl = Misc.getHighlightColor();

        if (shop == null) {
            UiButtons.button(p, w - WIDE_W, y, WIDE_W, BUTTON_H, "Euthanize", UiButtons.Kind.RED, CutStyle.TL_BR, () -> euthanize(pet));
            return;
        }

        String fee = Misc.getDGSCredits(PetConstants.REHOME_FEE);
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        boolean broke = credits < PetConstants.REHOME_FEE;
        boolean blocked = away != null || broke;

        ButtonAPI rehome = UiButtons.button(p, w - 2f * BUTTON_W - 6f, y, BUTTON_W, BUTTON_H, "Rehome", blocked ? UiButtons.Kind.OFF : UiButtons.Kind.RED, CutStyle.TL_BR,
                blocked ? null : () -> panel.popup(new UiConfirm("Confirm", info -> info.addPara("Rehome %s for a fee of %s?\n\n%s goes to a new home and does not come back.", 0f,
                        new Color[]{hl, hl, Ui.text()}, pet.name, fee, pet.name), () -> shop.rehome(pet))));
        if (away != null) Ui.tooltip(p, rehome, "Rehome", pet.name + " is out with " + away.getName() + ".");
        else if (broke) Ui.tooltip(p, rehome, "Rehome", "Rehoming costs " + fee + ". You have " + Misc.getDGSCredits(credits) + ".");

        UiButtons.button(p, w - BUTTON_W, y, BUTTON_W, BUTTON_H, "Euthanize", UiButtons.Kind.RED, CutStyle.TL_BR, () -> euthanize(pet));
    }

    private void euthanize(Pet pet) {
        panel.popup(new UiConfirm("Confirm", info -> info.addPara("Euthanize %s?\n\nThis cannot be undone.", 0f, Misc.getHighlightColor(), pet.name),
                () -> PetStatusManager.getInstance().reportPetDied(pet, PetStatusManager.PetDeathCause.EUTHANIZED)));
    }
}
