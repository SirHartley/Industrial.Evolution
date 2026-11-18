package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.List;
import java.util.*;

// New young popup: a finished brood opens like a draw, one face down card per young, then each gets its place
public class PetYoungDialog extends BaseCustomDialogDelegate {

    static final float CARD_W = 250f;
    static final float CARD_H = 374f;
    static final float CARD_GAP = 12f;
    static final float CARDS_Y = 46f;
    static final float MIN_W = 330f;
    static final float ROW_H = 18f;
    static final float FROM_W = 80f;
    static final float PLACE_Y = CARD_H - 8f - 17f - 6f - 24f;
    // turn over, then one glow in the card's colour; real seconds
    static final float FLIP = 0.33f;
    static final float GLOW = 0.5f;
    static final float GLOW_W = 14f;
    static final float[] DIAMOND = {1f, 0f, 2f, 1f, 1f, 2f, 0f, 1f};
    // sparks: start offset from the card's top left, as a share of its size, and the push outwards in px
    static final float[][] SPARKS = {{-0.05f, 0.08f, -18f, 0f}, {-0.04f, 0.4f, -14f, 0f}, {-0.06f, 0.8f, -20f, 0f}, {1.03f, 0.16f, 16f, 0f},
            {1.04f, 0.56f, 20f, 0f}, {1.02f, 0.88f, 14f, 0f}, {0.24f, -0.04f, 0f, -16f}, {0.72f, -0.03f, 0f, -12f}, {0.48f, 1.02f, 0f, 14f}};

    private final PetShop shop;
    private final PetBrood brood;
    private final String pair;
    private final Map<String, FleetMemberAPI> ships = new HashMap<>();
    private final Map<String, TextFieldAPI> fields = new HashMap<>();
    private final Map<String, Float> openedAt = new HashMap<>();
    private final UiScroll.Offsets scroll = new UiScroll.Offsets();
    private InteractionDialogAPI dialog;
    private UiRoot root;
    private float clock = 0f;

    enum Tier {
        PLAIN, SUM, MUTATION, MALFORMED
    }

    public PetYoungDialog(PetShop shop, PetBrood brood) {
        this.shop = shop;
        this.brood = brood;
        PetStatusManager manager = PetStatusManager.getInstance();
        Pet a = manager.get(brood.parentA);
        Pet b = manager.get(brood.parentB);
        this.pair = (a != null ? a.name : "?") + " x " + (b != null ? b.name : "?");
    }

    public void show(Runnable onClose) {
        brood.shown = true;
        UiHost.over(dialog -> {
            this.dialog = dialog;
            dialog.showCustomDialog(width(), CARDS_Y + CARD_H, this);
        }, onClose);
    }

    private int count() {
        return brood.getYoung().size();
    }

    private float cardsWidth() {
        return count() * CARD_W + (count() - 1) * CARD_GAP;
    }

    private float width() {
        return Math.max(cardsWidth(), MIN_W);
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
                clock += amount;
            }
        };
    }

    private void refresh() {
        keepNames();
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        boolean closed = false;
        for (Pet young : brood.getYoung()) if (!brood.opened.contains(young.id)) closed = true;

        float tw = closed ? w - 110f : w;
        Ui.runs(p, 0f, 2f, tw, Ui.INS21, Ui.text(), Ui.LEFT, "New young: " + Ui.fit(pair, Ui.INS21, tw - 140f), new String[]{Ui.fit(pair, Ui.INS21, tw - 140f)}, new Color[]{Ui.hl()});
        if (closed) UiButtons.button(p, w - 96f, 2f, 96f, PetCard.BUTTON_H, "Open all", UiButtons.Kind.STD, CutStyle.TL_BR, this::openAll);

        fields.clear();
        float x0 = Math.round((w - cardsWidth()) / 2f);
        List<Pet> young = brood.getYoung();
        for (int i = 0; i < young.size(); i++) {
            Pet pet = young.get(i);
            float x = x0 + i * (CARD_W + CARD_GAP);
            if (brood.opened.contains(pet.id)) openCard(p, x, CARDS_Y, pet);
            else {
                CustomPanelAPI row = UiButtons.row(p, x, CARDS_Y, CARD_W, CARD_H, false, true, null, () -> open(pet));
                back(row, 0f, 0f, CARD_W, CARD_H, true);
            }
        }
    }

    private void open(Pet pet) {
        brood.opened.add(pet.id);
        if (Setting.PET_MOTION.enabled()) openedAt.put(pet.id, clock);
        refresh();
    }

    private void openAll() {
        for (Pet pet : brood.getYoung()) {
            if (brood.opened.add(pet.id) && Setting.PET_MOTION.enabled()) openedAt.put(pet.id, clock);
        }
        refresh();
    }

    // the back of a closed card: a diamond lattice, a diamond in the middle with the question mark, a hint under it
    static void back(CustomPanelAPI p, float x, float y, float w, float h, boolean hint) {
        float scale = h / CARD_H;
        float tile = Math.max(8f, 20f * scale);
        float slope = 28f / 20f;
        float mw = Math.round(80f * scale);
        float mh = Math.round(112f * scale);
        Color lattice = Misc.scaleAlpha(Ui.base(), 0.4f);
        Color edge = Ui.line();

        Ui.painted(p, x, y, w, h, (pos, a) -> {
            float left = pos.getX() + 5f;
            float top = pos.getY() + pos.getHeight() - 5f;
            float iw = pos.getWidth() - 10f;
            float ih = pos.getHeight() - 10f;
            UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Color.BLACK, a);

            float reach = ih / slope;
            for (float c = -reach - tile; c <= iw + reach + tile; c += tile) {
                float t0 = Math.max(0f, -c);
                float t1 = Math.min(iw - c, reach);
                if (t1 > t0) UiDraw.line(left + c + t0, top - t0 * slope, left + c + t1, top - t1 * slope, 1f, lattice, 1f, 1f, 1f, a, false);

                float u0 = Math.max(0f, c - iw);
                float u1 = Math.min(c, reach);
                if (u1 > u0) UiDraw.line(left + c - u0, top - u0 * slope, left + c - u1, top - u1 * slope, 1f, lattice, 1f, 1f, 1f, a, false);
            }

            UiDraw.outline(pos.getX() + 5f, pos.getY() + 5f, iw, ih, edge, a);
            float cx = Math.round(pos.getX() + pos.getWidth() / 2f);
            float cy = Math.round(pos.getY() + pos.getHeight() / 2f);
            UiDraw.fan(cx - mw / 2f, cy - mh / 2f, mh, 2f, new float[]{mw / mh, 0f, 2f * mw / mh, 1f, mw / mh, 2f, 0f, 1f}, mw / mh, 1f, Color.BLACK, a);
            diamond(cx, cy, mw, mh, Ui.base(), a);
            diamond(cx, cy, mw - 8f, mh - 10f, edge, a);
        }, null);

        float q = Math.max(12f, Math.round(24f * scale));
        UiImages.img(p, x + Math.round((w - q) / 2f), y + Math.round((h - q) / 2f), q, q, PetCard.unknownSprite());

        if (!hint) return;
        String text = "Click to open";
        float hw = Ui.width(text, Ui.INS15) + 20f;
        float hy = y + Math.round(h / 2f + mh / 2f + 24f);
        Ui.box(p, x + Math.round((w - hw) / 2f), hy, hw, 22f, edge, Color.BLACK);
        Ui.textIn(p, x + Math.round((w - hw) / 2f), hy, hw, 22f, text, Ui.INS15, Ui.gray(), Ui.CENTER, new String[0], new Color[0]);
    }

    private static void diamond(float cx, float cy, float w, float h, Color c, float a) {
        UiDraw.line(cx, cy + h / 2f, cx + w / 2f, cy, 1f, c, 1f, 1f, 1f, a, false);
        UiDraw.line(cx + w / 2f, cy, cx, cy - h / 2f, 1f, c, 1f, 1f, 1f, a, false);
        UiDraw.line(cx, cy - h / 2f, cx - w / 2f, cy, 1f, c, 1f, 1f, 1f, a, false);
        UiDraw.line(cx - w / 2f, cy, cx, cy + h / 2f, 1f, c, 1f, 1f, 1f, a, false);
    }

    // sum and the talent tables only once its talents are read; a mutation and Malformed always show
    static Tier tier(Pet pet) {
        PetSheet sheet = pet.sheet;
        if (sheet.malformed) return Tier.MALFORMED;
        if (sheet.from == null) return Tier.PLAIN;
        if (sheet.from.containsValue(PetSheet.FROM_MUTATION)) return Tier.MUTATION;
        for (Map.Entry<String, String> e : sheet.from.entrySet()) {
            if (PetSheet.FROM_SUM.equals(e.getValue()) && sheet.isKnown(e.getKey())) return Tier.SUM;
        }
        return Tier.PLAIN;
    }

    static Color color(Tier tier) {
        return switch (tier) {
            case SUM -> Ui.hl();
            case MUTATION -> Ui.bright();
            case MALFORMED -> Ui.neg();
            default -> Misc.scaleAlpha(Ui.base(), 0.3f);
        };
    }

    static String tag(Tier tier) {
        return switch (tier) {
            case SUM -> "Sum";
            case MUTATION -> "Mutation";
            case MALFORMED -> "Malformed";
            default -> null;
        };
    }

    private void openCard(CustomPanelAPI parent, float x, float y, Pet pet) {
        Tier tier = tier(pet);
        Color border = color(tier);
        CustomPanelAPI p = Ui.panel(parent, x, y, CARD_W, CARD_H);
        Ui.box(p, 0f, 0f, CARD_W, CARD_H, border, Color.BLACK);

        fields.put(pet.id, PetCard.nameField(p, 8f, 8f, CARD_W - 16f, pet.name));
        Ui.card(p, 8f, 42f, PetCard.IDENT, PetCard.IDENT);
        UiImages.img(p, 12f, 46f, PetCard.PORTRAIT, PetCard.PORTRAIT, pet.getData().icon);
        UiData.grid(p, 88f, 44f, CARD_W - 96f, ROW_H, List.of(
                GridRow.of("Look", PetHusbandry.WILD_TYPE, Ui.text()),
                GridRow.of("Disposition", Misc.ucFirst(pet.sheet.disposition), Academy.COLOURS_BY_PERSONALITY.get(pet.sheet.disposition))));
        if (pet.sheet.malformed) {
            Ui.text(p, 88f, 84f, CARD_W - 96f, "Malformed", Ui.neg());
            Ui.tooltip(p, Ui.panel(p, 88f, 84f, CARD_W - 96f, ROW_H), "Malformed", "Its parents were kin: it came with a new flaw, or one more level of a flaw.");
        }

        float ty = addTalents(p, 130f, pet);
        addBody(p, ty + PetsPanel.HEAD_GAP, pet);
        addPlace(p, pet);

        String tag = tag(tier);
        if (tag != null) {
            float tw = Ui.width(tag, Ui.ORB12) + 18f;
            Ui.box(parent, x + CARD_W - tw - 8f, y - 10f, tw, 20f, border, Color.BLACK);
            Ui.textIn(parent, x + CARD_W - tw - 8f, y - 10f, tw, 20f, tag, Ui.ORB12, border, Ui.CENTER, new String[0], new Color[0]);
        }

        Float at = openedAt.get(pet.id);
        if (at != null) Ui.painted(parent, x, y, CARD_W, CARD_H, null, (pos, a) -> paintOpening(pos, a, clock - at, tier == Tier.PLAIN ? null : border));
    }

    // first FLIP: a black cover with the card turning as a narrowing then widening frame; then a glow and sparks in its colour
    private static void paintOpening(PositionAPI pos, float alpha, float t, Color color) {
        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        if (t < FLIP) {
            float f = Math.abs(1f - 2f * t / FLIP);
            float fw = Math.max(2f, Math.round(w * f));
            UiDraw.quad(x - 1f, y - 1f, w + 2f, h + 2f, Color.BLACK, alpha);
            UiDraw.outline(x + Math.round((w - fw) / 2f), y, fw, h, Ui.bright(), alpha);
            return;
        }

        float g = (t - FLIP) / GLOW;
        if (color == null || g >= 1f) return;

        float fade = 1f - g;
        for (int i = 1; i <= 4; i++) {
            float d = i * GLOW_W / 4f * (0.4f + 0.6f * g);
            UiDraw.outline(x - d, y - d, w + 2f * d, h + 2f * d, color, alpha * fade * (1f - i / 5f) * 0.6f);
        }

        float ease = 1f - (1f - g) * (1f - g);
        for (float[] s : SPARKS) {
            float sx = x + s[0] * w + s[2] * ease;
            float sy = y + h - s[1] * h - s[3] * ease;
            UiDraw.fan(sx - 3f, sy - 3f, 6f, 2f, DIAMOND, 1f, 1f, color, alpha * fade);
        }
    }

    // talents read with the parents, else one Unknown row each
    private float addTalents(CustomPanelAPI p, float y, Pet pet) {
        tableHead(p, y, "Grade", "Talents");
        y += Ui.HEADING_H + 4f;

        List<String> talents = pet.getTalents();
        if (talents.isEmpty()) return row(p, y, null, "None", null, false);

        for (String id : talents) {
            if (!pet.sheet.isKnown(id)) {
                y = row(p, y, "?", "Unknown", null, false);
                continue;
            }
            y = row(p, y, String.valueOf(pet.sheet.getGrade(id)), PetDataRepo.getTrait(id).name(), pet.sheet.getFrom(id), true, pet);
        }

        return y;
    }

    // mutations always, flaws once read
    private float addBody(CustomPanelAPI p, float y, Pet pet) {
        tableHead(p, y, "Level", "Mutations, flaws");
        y += Ui.HEADING_H + 4f;

        int rows = 0;
        for (String id : pet.sheet.traits.keySet()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null || trait.isTalent() || !pet.sheet.isKnown(id)) continue;
            String level = trait.hasLevels() ? String.valueOf(pet.sheet.getGrade(id)) : null;
            y = row(p, y, level, trait.name(), pet.sheet.getFrom(id), true, pet);
            rows++;
        }

        if (!pet.sheet.flawsRead) return row(p, y, "?", "Flaws: Unknown", null, false);
        return rows == 0 ? row(p, y, null, "None", null, false) : y;
    }

    private static void tableHead(CustomPanelAPI p, float y, String first, String second) {
        float w = CARD_W - 16f;
        Ui.thead(p, 8f, y, w, new String[]{first, second, "From"}, new float[]{4f, 52f, w - FROM_W - 8f},
                new float[]{44f, 100f, FROM_W}, new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});
    }

    private static float row(CustomPanelAPI p, float y, String grade, String name, String from, boolean on) {
        return row(p, y, grade, name, from, on, null);
    }

    private static float row(CustomPanelAPI p, float y, String grade, String name, String from, boolean on, Pet pet) {
        Color c = on ? Ui.text() : Ui.gray();
        if (grade != null) Ui.textIn(p, 12f, y, 44f, ROW_H, grade, Ui.INS15, on ? Ui.hl() : c, Ui.CENTER, new String[0], new Color[0]);
        Ui.textIn(p, 60f, y, CARD_W - 60f - FROM_W - 16f, ROW_H, name, Ui.INS15, c, Ui.LEFT, new String[0], new Color[0]);
        if (from != null && pet != null) {
            Ui.textIn(p, CARD_W - 8f - FROM_W - 8f, y, FROM_W, ROW_H, fromLabel(pet, from), Ui.INS15, fromColor(from), Ui.RIGHT, new String[0], new Color[0]);
        }
        return y + ROW_H;
    }

    private static String fromLabel(Pet pet, String from) {
        PetStatusManager manager = PetStatusManager.getInstance();
        return switch (from) {
            case PetSheet.FROM_A -> parentName(manager.get(pet.parentA));
            case PetSheet.FROM_B -> parentName(manager.get(pet.parentB));
            case PetSheet.FROM_SUM -> "Sum";
            case PetSheet.FROM_MUTATION -> "Mutation";
            case PetSheet.FROM_MALFORMED -> "Malformed";
            default -> "";
        };
    }

    private static String parentName(Pet parent) {
        return parent != null ? Ui.fit(parent.name, Ui.INS15, FROM_W) : "";
    }

    private static Color fromColor(String from) {
        return switch (from) {
            case PetSheet.FROM_SUM -> Ui.hl();
            case PetSheet.FROM_MUTATION -> Ui.bright();
            case PetSheet.FROM_MALFORMED -> Ui.neg();
            default -> Ui.gray();
        };
    }

    // Storage is picked when it opens; Ship... asks for a ship without a pet that no other young here has taken
    private void addPlace(CustomPanelAPI p, Pet pet) {
        FleetMemberAPI ship = ships.get(pet.id);
        float bw = Math.round((CARD_W - 16f - 4f) / 2f);
        UiButtons.button(p, 8f, PLACE_Y, bw, PetCard.BUTTON_H, "Storage", ship == null ? UiButtons.Kind.HOT : UiButtons.Kind.STD, CutStyle.TL_BR, () -> {
            ships.remove(pet.id);
            refresh();
        });

        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        for (Map.Entry<String, FleetMemberAPI> e : ships.entrySet()) if (!e.getKey().equals(pet.id)) free.remove(e.getValue());
        String reason = pet.getLifetime() <= 0f ? "It lives only where it does not age: Pet storage with a Beta core."
                : free.isEmpty() ? "No ship without a pet." : null;

        ButtonAPI shipButton = UiButtons.button(p, CARD_W - 8f - bw, PLACE_Y, bw, PetCard.BUTTON_H, "Ship...",
                reason != null ? UiButtons.Kind.OFF : ship != null ? UiButtons.Kind.HOT : UiButtons.Kind.STD, CutStyle.TL_BR,
                reason != null ? null : () -> pickShip(pet, free));
        if (reason != null) Ui.tooltip(p, shipButton, "Ship...", reason);

        String where = ship != null ? ship.getShipName() + ", " + PetCard.shipClass(ship) : "Pet storage, " + shop.getMarket().getName();
        Ui.text(p, 8f, PLACE_Y + 30f, CARD_W - 16f, Ui.fit(where, Ui.INS15, CARD_W - 16f), Ui.gray());
    }

    private void pickShip(Pet pet, List<FleetMemberAPI> free) {
        keepNames();
        PetPlaceDialog.pick(pet, free, ship -> ships.put(pet.id, ship)).show(this::refresh);
    }

    // the fields are made again on every rebuild; an empty field keeps the suggested name
    private void keepNames() {
        for (Map.Entry<String, TextFieldAPI> e : fields.entrySet()) {
            String typed = PetCard.trimName(e.getValue().getText());
            if (typed.isEmpty()) continue;
            for (Pet young : brood.getYoung()) if (young.id.equals(e.getKey())) young.rename(typed);
        }
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

    // every opened young goes where it was put; a closed card stays in the habitat
    @Override
    public void customDialogConfirm() {
        keepNames();
        for (Pet young : new ArrayList<>(brood.getYoung())) {
            if (brood.opened.contains(young.id)) shop.placeYoung(brood, young, ships.get(young.id));
        }
        dialog.dismiss();
    }

    @Override
    public void customDialogCancel() {
        keepNames();
        dialog.dismiss();
    }
}
