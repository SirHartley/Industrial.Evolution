package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.academy.industry.Academy;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.PetMood;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.PetWant;
import indevo.industries.petshop.hullmods.PetHullmod;
import indevo.industries.petshop.industry.PetShop;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetBrood;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.utils.helper.MiscIE;
import indevo.utils.ui.*;
import indevo.utils.ui.UiData.GridRow;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

// the tiles of the Pets list and the card of the selected pet, column coordinates
public class PetCard {

    // pet art colour, not a kit colour
    static final Color HEART_RED = new Color(226, 78, 98);
    static final Color HEART_OFF = new Color(120, 120, 120);
    static final float HEART_EMPTY = 0.18f;
    static final Color STAR_OFF = new Color(175, 175, 175, 128);
    // the favourite star in a 20 unit box, y down; the outline lies on its edge
    static final float[] STAR = {10f, 1f, 12.2f, 7f, 18.6f, 7.2f, 13.5f, 11.1f, 15.3f, 17.3f, 10f, 13.7f, 4.7f, 17.3f, 6.5f, 11.1f, 1.4f, 7.2f, 7.8f, 7f};
    static final float STAR_SIZE = 18f;
    static final float WANT_H = 40f;
    static final float WANT_W = 120f;
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
    static final float HEARTS_Y = 170f;
    static final float STRIP_H = 50f;
    static final float BUTTON_W = 96f;
    static final float WIDE_W = 116f;
    static final float BUTTON_H = 24f;
    static final float HOVER_W = 340f;
    static final float LINE_H = 17f;
    static final float HOVER_HEART = 11f;
    static final float HOVER_HEART_GAP = 3f;
    static final float CARD_HEART = 14f;
    static final float CARD_HEART_GAP = 4f;
    static final float GRADE_X = 4f;
    static final float GRADE_W = 44f;
    static final float TALENT_X = 58f;
    static final float TALENT_W = 180f;
    static final float EFFECT_X = 254f;
    static final float DIM = 0.4f;
    static final String NO_SHIP = "Effects work only on a ship.";
    public static final float REFIT_ICON = 34f;
    static final float REFIT_CUT = 12f;
    static final float REFIT_GAP = 3f;
    // the refit button: both left corners cut by 6, in a 34 box with y down
    static final float[] CUT = {6, 0, 12, 0, 12, 34, 6, 34, 0, 28, 0, 6};

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
        hearts(t, 17f, 91f, pet, 9f, 2f);
        Ui.rule(t, 13f, 107f, 60f);
        if (pet.assignedFleetMember != null) UiImages.ship(t, 30f, 114f, 26f, pet.assignedFleetMember, false, false);
    }

    static boolean marked(Pet pet) {
        return marker(pet) != null;
    }

    // one marker per tile, the worst first
    private static Marker marker(Pet pet) {
        if (pet.isStarving()) return new Marker(sprite("pet_marker_starving"), "Starving", "Missed a meal. It does not survive a second one.");
        if (pet.storage == null && !PetStatusManager.getInstance().hasFood(pet))
            return new Marker(sprite("pet_marker_hungry"), "Hungry", "No food aboard for its next meal.");
        if (pet.shellShockedDays > 0f) return new Marker(sprite("pet_marker_shock"), "Shell shocked", "Its ship was in a hard fight.");
        if (pet.want != null) return new Marker(sprite("pet_marker_want"), pet.want.label, daysLeft(pet.wantDaysLeft) + ".");
        return null;
    }

    static String sprite(String id) {
        return Global.getSettings().getSpriteName("IndEvo", id);
    }

    // the vanilla question mark, the unknown traits marker
    static String unknownSprite() {
        return Global.getSettings().getSpriteName("misc", "question_mark_actual");
    }

    // the vanilla intel (!), the quest marker
    static String quest() {
        return Global.getSettings().getSpriteName("intel", "important");
    }

    static String daysLeft(float days) {
        int n = Math.max(1, (int) Math.ceil(days));
        return n + (n == 1 ? " day left" : " days left");
    }

    private static String daysLeftNumber(float days) {
        return String.valueOf(Math.max(1, (int) Math.ceil(days)));
    }

    // five hearts, the earned ones red, grey while Unsettled
    static CustomPanelAPI hearts(CustomPanelAPI p, float x, float y, Pet pet, float size, float gap) {
        int n = pet.getHearts();
        Color on = pet.isUnsettled() ? HEART_OFF : HEART_RED;
        Color empty = Misc.scaleAlpha(Ui.base(), HEART_EMPTY);

        return Ui.painted(p, x, y, heartsWidth(size, gap), size, (pos, a) -> {
            for (int i = 0; i < PetConstants.MAX_HEARTS; i++) {
                UiDraw.fan(pos.getX() + i * (size + gap), pos.getY(), size, 14f, HEART, 7f, 7f, i < n ? on : empty, a);
            }
        }, null);
    }

    static float heartsWidth(float size, float gap) {
        return PetConstants.MAX_HEARTS * size + (PetConstants.MAX_HEARTS - 1) * gap;
    }

    // the mascot effect value with its sign, as the hull mod applies it
    static String mascotValue(Pet pet) {
        return "-" + Misc.getRoundedValueMaxOneAfterDecimal(PetHullmod.BASE_DECREASE * pet.getMascotMult()) + "%";
    }

    // the pet picture on a vanilla screen, the marker hangs over its top right corner as on the tiles
    public static CustomPanelAPI shipIcon(Pet pet, float size) {
        CustomPanelAPI icon = Global.getSettings().createCustom(size, size, new UiCanvas());
        picture(icon, 0f, 0f, size, pet, true);
        return icon;
    }

    // same with the hover block; a click opens the Pets panel on this pet and runs onClose after it
    public static CustomPanelAPI shipButton(Pet pet, float size, Runnable onClose) {
        CustomPanelAPI icon = Global.getSettings().createCustom(size, size, new Click(() -> PetsDialogPlugin.over(pet, onClose), true));
        picture(icon, 0f, 0f, size, pet, true);
        Ui.tooltip(icon, icon, HOVER_W, TooltipMakerAPI.TooltipLocation.RIGHT, t -> hover(t, pet, false, true));
        return icon;
    }

    // refit row: the cut button and the framed picture over the hull mod icon, which keeps its tooltip; the caller opens the panel
    public static CustomPanelAPI refitButton(Pet pet, Runnable action) {
        Click click = new Click(action, false);
        CustomPanelAPI p = Global.getSettings().createCustom(REFIT_CUT + REFIT_GAP + REFIT_ICON, REFIT_ICON, click);
        Ui.painted(p, 0f, 0f, REFIT_CUT, REFIT_ICON, (pos, a) -> UiDraw.fan(pos.getX(), pos.getY(), REFIT_ICON, REFIT_ICON, CUT, 6f, 17f, click.hover ? Ui.base() : Ui.dark(), a), null);

        float x = REFIT_CUT + REFIT_GAP;
        Ui.painted(p, x, 0f, REFIT_ICON, REFIT_ICON, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Color.BLACK, a), null);
        picture(p, x + 1f, 1f, REFIT_ICON - 2f, pet, false);
        Ui.painted(p, x, 0f, REFIT_ICON, REFIT_ICON, null, (pos, a) -> UiDraw.outline(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), click.hover ? Ui.bright() : Ui.line(), a));
        return p;
    }

    // dim while Unsettled; hang puts the marker over the corner, else inside it
    private static void picture(CustomPanelAPI p, float x, float y, float size, Pet pet, boolean hang) {
        UiImages.img(p, x, y, size, size, pet.getData().icon, null, pet.isUnsettled() ? DIM : 1f);

        Marker marker = marker(pet);
        if (marker == null) return;

        float s = size <= 32f ? 16f : 22f;
        UiImages.img(p, hang ? x + size - s + 6f : x + size - s, hang ? y - 5f : y, s, s, marker.sprite());
    }

    // the hover of the pet picture on a ship card; with heading it is the pet part of the hull mod tooltip
    public static void hover(TooltipMakerAPI t, Pet pet, boolean heading, boolean click) {
        float gw = t.getWidthSoFar();
        if (heading) {
            t.addSectionHeading(pet.name, Alignment.MID, 10f);
            t.addCustom(heartsLine(gw, pet.getData().species, Ui.gray(), pet), 10f);
        } else {
            t.addCustom(heartsLine(gw, pet.name, Ui.hl(), pet), 0f);
            t.addPara(pet.getData().species, Ui.gray(), 2f);
        }

        String line = dailyLine(pet);
        if (!line.isEmpty()) t.addPara(line, Ui.gray(), 10f);

        PetMood mood = pet.getMood();
        List<String> titles = new ArrayList<>();
        for (PetTitle title : pet.getTitles()) titles.add(title.label);
        int rows = 2 + (pet.lastMealSupplies ? 1 : 0) + (titles.isEmpty() ? 0 : 1);
        CustomPanelAPI grid = Global.getSettings().createCustom(gw, rows * UiData.GRID_ROW_H, new UiCanvas());
        float y = 0f;
        y += hoverRow(grid, y, gw, "Mood", Ui.text(), mood.label, mood.getColor(), null);
        if (!titles.isEmpty()) y += hoverRow(grid, y, gw, "Titles", Ui.text(), String.join(", ", titles), Ui.text(), null);
        if (pet.lastMealSupplies) y += hoverRow(grid, y, gw, "Last meal", Ui.text(), "Supplies", Ui.neg(), null);
        if (!PetStatusManager.getInstance().hasFood(pet)) hoverRow(grid, y, gw, "Next meal", Ui.text(), "No food aboard", Ui.neg(), null);
        else {
            int days = Math.max(1, (int) Math.ceil(pet.getDaysToMeal()));
            hoverRow(grid, y, gw, "Next meal", Ui.text(), "in " + days + (days == 1 ? " day" : " days"), Ui.text(), String.valueOf(days));
        }
        t.addCustom(grid, 10f);

        if (pet.lastMealSupplies && pet.getDaysToHeart() >= 0f) {
            String share = Math.round(PetConstants.SUPPLIES_HEART_RATE * 100f) + "%";
            String food = realFood(pet);
            t.addPara("Hearts grow at %s." + (food != null ? " Wants " + food + "." : ""), 10f, Ui.neg(), Ui.hl(), share);
        }

        boolean off = pet.isUnsettled();
        if (off) {
            int days = (int) Math.ceil(pet.unsettledDays);
            t.addPara("Moved here. Effects are off for %s " + (days == 1 ? "day." : "days."), 10f, Ui.neg(), Ui.hl(), String.valueOf(days));
        }

        if (pet.want != null) {
            CustomPanelAPI want = Global.getSettings().createCustom(gw, LINE_H, new UiCanvas());
            UiImages.img(want, 0f, 0f, 16f, 16f, quest());
            Ui.textIn(want, 22f, 0f, gw - 22f - WANT_W, LINE_H, pet.want.label, Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
            Ui.textIn(want, gw - WANT_W, 0f, WANT_W, LINE_H, daysLeft(pet.wantDaysLeft), Ui.INS15, Ui.gray(), Ui.RIGHT,
                    new String[]{daysLeftNumber(pet.wantDaysLeft)}, new Color[]{Ui.hl()});
            t.addCustom(want, 10f);
        }

        List<String> active = pet.getActiveTalents();
        CustomPanelAPI effects = Global.getSettings().createCustom(gw, (1 + active.size()) * UiData.GRID_ROW_H, new UiCanvas());
        String value = mascotValue(pet);
        if (off) hoverRow(effects, 0f, gw, "Mascot effect", Ui.gray(), "off", Ui.gray(), null);
        else if (pet.getHearts() < PetConstants.MASCOT_HEARTS) hoverRow(effects, 0f, gw, "Mascot effect", Ui.gray(), "at 1 heart", Ui.gray(), null);
        else hoverRow(effects, 0f, gw, "Mascot effect", Ui.text(), "Combat CR decay " + value, Ui.text(), value);

        float ty = UiData.GRID_ROW_H;
        for (String id : active) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (offReason(pet, trait) != null) ty += hoverRow(effects, ty, gw, trait.name(), Ui.gray(), "off", Ui.gray(), null);
            else {
                String v = trait.format(pet.getTalentValue(id));
                ty += hoverRow(effects, ty, gw, trait.name(), Ui.text(), trait.effect() + " " + v, Ui.text(), v);
            }
        }
        t.addCustom(effects, 10f);

        String more = moreTalents(pet);
        if (more != null) t.addPara(more, Ui.gray(), 0f);

        if (click) t.addPara("Click to open the Pets panel.", Ui.gray(), 10f);
    }

    // one text line with the hearts at its right end
    private static CustomPanelAPI heartsLine(float w, String text, Color color, Pet pet) {
        CustomPanelAPI line = Global.getSettings().createCustom(w, LINE_H, new UiCanvas());
        float hw = heartsWidth(HOVER_HEART, HOVER_HEART_GAP);
        Ui.textIn(line, 0f, 0f, w - hw - 8f, LINE_H, text, Ui.INS15, color, Ui.LEFT, new String[0], new Color[0]);
        hearts(line, w - hw, 3f, pet, HOVER_HEART, HOVER_HEART_GAP);
        return line;
    }

    // the first food that is not Supplies, by its commodity name
    private static String realFood(Pet pet) {
        for (String food : pet.getData().foodCommodities) {
            if (!Commodities.SUPPLIES.equals(food)) return Global.getSettings().getCommoditySpec(food).getName();
        }

        return null;
    }

    private static float hoverRow(CustomPanelAPI p, float y, float w, String label, Color labelColor, String value, Color color, String hl) {
        float h = UiData.GRID_ROW_H;
        String shown = Ui.fit(value, Ui.INS15, w * UiData.VALUE_SHARE);
        float vw = Math.min(Ui.width(shown, Ui.INS15) + 2f, w * UiData.VALUE_SHARE);
        String[] parts = hl == null ? new String[0] : new String[]{hl};
        Color[] colors = hl == null ? new Color[0] : new Color[]{Ui.hl()};
        Ui.textIn(p, w - vw, y, vw, h, shown, Ui.INS15, color, Ui.RIGHT, parts, colors);
        Ui.textIn(p, 0f, y, w - vw - 8f, h, label, Ui.INS15, labelColor, Ui.LEFT, new String[0], new Color[0]);
        return h;
    }

    // one status line a pet and day, so the hover does not change on every show
    private static String dailyLine(Pet pet) {
        CampaignClockAPI clock = Global.getSector().getClock();
        Random random = new Random(Objects.hash(pet.id, clock.getCycle(), clock.getMonth(), clock.getDay()));
        String line = PetStatusManager.getInstance().getLine(pet, PetDataRepo.EVENT_STATUS, MiscIE.getCurrentInteractionTargetMarket() != null, random);
        return line == null ? "" : status(pet, line);
    }

    // takes the left click on its rect before the vanilla screen under it; takeMoves also hides the hover from what lies under it
    private static class Click extends BaseCustomUIPanelPlugin {
        private final Runnable action;
        private final boolean takeMoves;
        private PositionAPI pos;
        private boolean down = false;
        boolean hover = false;

        Click(Runnable action, boolean takeMoves) {
            this.action = action;
            this.takeMoves = takeMoves;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void advance(float amount) {
            if (pos == null) return;
            float mx = Global.getSettings().getMouseX();
            float my = Global.getSettings().getMouseY();
            hover = mx >= pos.getX() && mx < pos.getX() + pos.getWidth() && my >= pos.getY() && my < pos.getY() + pos.getHeight();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !pos.containsEvent(event)) continue;

                if (event.isMouseMoveEvent()) {
                    if (takeMoves) event.consume();
                } else if (event.isLMBDownEvent()) {
                    event.consume();
                    down = true;
                } else if (event.isLMBUpEvent()) {
                    event.consume();
                    if (!down || action == null) continue;

                    down = false;
                    Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
                    action.run();
                }
            }
        }
    }

    void build(CustomPanelAPI parent, float x, float y, float w, float h, Pet pet) {
        CustomPanelAPI p = Ui.panel(parent, x, y, w, h);
        CampaignFleetAPI away = PetsPanel.awayFleet(pet);

        addName(p, w, pet);
        addTagline(p, w, pet);
        addIdent(p, w, pet, away);
        float shipY = addWant(p, addHearts(p, w, pet), w, pet, away);
        addPlace(p, shipY, w, pet, away);
        addTalents(p, shipY + STRIP_H + PetsPanel.HEAD_GAP, w, h, pet);
        addBottom(p, w, h, pet, heldReason(pet, away));
        Ui.painted(p, 0f, 0f, w, h, null, (pos, a) -> paintHearts(pos, a));
    }

    private void addName(CustomPanelAPI p, float w, Pet pet) {
        view.nameField = nameField(p, 0f, 0f, w, pet.name);
        view.namePet = pet.id;
        addStar(p, w - 24f, 4f, pet);
    }

    // gold when set, an outline when not, grey when every favourite slot is taken
    private void addStar(CustomPanelAPI p, float x, float y, Pet pet) {
        boolean can = PetStatusManager.getInstance().canFavourite(pet);
        // a blocked star still takes the click, the name field under it would start editing
        Click click = new Click(can ? () -> {
            pet.setFavourite(!pet.favourite);
            panel.refresh();
        } : null, false);

        CustomPanelAPI star = p.createCustomPanel(STAR_SIZE, STAR_SIZE, click);
        p.addComponent(star).inTL(x, y);
        Ui.painted(star, 0f, 0f, STAR_SIZE, STAR_SIZE, null, (pos, a) -> {
            Color c = pet.favourite ? Ui.hl() : can ? (click.hover ? Ui.bright() : Ui.base()) : STAR_OFF;
            paintStar(pos.getX(), pos.getY(), pet.favourite, c, a);
        });

        String rule = "Up to " + PetConstants.MAX_FAVOURITES + " pets. Only favourites have wants, and only they reach hearts 4 and 5.";
        if (can) Ui.tooltip(p, star, pet.favourite ? "Favourite" : "Make a favourite", rule);
        else Ui.tooltip(p, star, "Make a favourite", rule, "Unstar another pet first.");
    }

    private static void paintStar(float x, float y, boolean filled, Color c, float alpha) {
        float s = STAR_SIZE / 20f;
        if (filled) {
            UiDraw.fan(x, y, STAR_SIZE, 20f, STAR, 10f, 10f, c, alpha);
            return;
        }

        int n = STAR.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            UiDraw.line(x + STAR[2 * i] * s, y + STAR_SIZE - STAR[2 * i + 1] * s, x + STAR[2 * j] * s, y + STAR_SIZE - STAR[2 * j + 1] * s,
                    1.2f, c, 1f, 1f, 1f, alpha, false);
        }
    }

    static TextFieldAPI nameField(CustomPanelAPI p, float x, float y, float w, String name) {
        TextFieldAPI field = UiButtons.field(p, x, y, w, NAME_H, name, PetConstants.NAME_MAX_CHARS, Ui.ORB20);
        field.setMidAlignment();
        field.setColor(Ui.bright());
        field.setBorderColor(Misc.scaleAlpha(Ui.base(), 0.45f));
        return field;
    }

    static String trimName(String typed) {
        String name = typed.trim();
        return name.length() > PetConstants.NAME_MAX_CHARS ? name.substring(0, PetConstants.NAME_MAX_CHARS).trim() : name;
    }

    private void addTagline(CustomPanelAPI p, float w, Pet pet) {
        boolean answer = view.answer != null;
        String text = answer ? view.answer.replace("%s", pet.name) : status(pet, view.lines.computeIfAbsent(pet.id, id -> line(pet)));
        if (text == null || text.isEmpty()) return;

        String shown = Ui.lines(text, Ui.INS15, w, 2);
        Ui.wrap(p, 0f, TAG_Y, w, shown, Ui.INS15, answer ? Ui.text() : Ui.gray(), Alignment.TMID, new String[0], new Color[0]);
    }

    // the line is kept with its %s, so a rename shows at once
    private String line(Pet pet) {
        String line = PetStatusManager.getInstance().getLine(pet, PetDataRepo.EVENT_STATUS, shop != null);
        return line == null ? "" : line;
    }

    private static String status(Pet pet, String line) {
        return line.isEmpty() ? "" : line.replace("%s", pet.name) + ".";
    }

    private void addIdent(CustomPanelAPI p, float w, Pet pet, CampaignFleetAPI away) {
        boolean canPet = away == null;
        CustomPanelAPI card = UiButtons.row(p, 0f, IDENT_Y, IDENT, IDENT, false, canPet, null, canPet ? () -> pet(pet) : null);
        SpriteAPI sprite = Global.getSettings().getSprite(pet.getData().icon);
        Ui.painted(card, 4f, 4f, PORTRAIT, PORTRAIT, null, (pos, a) -> paintPortrait(sprite, pos, a));
        if (!canPet) Ui.tooltip(p, card, null, pet.name + " is out with " + away.getName() + ".");
        if (pet.want != null) UiImages.img(p, IDENT - 18f, IDENT_Y, 16f, 16f, quest());

        if (view.clicks > 0) UiData.thinbar(p, 0f, IDENT_Y + IDENT + 3f, IDENT, view.clicks / (float) PetConstants.PETTING_CLICKS, HEART_RED);

        PetMood mood = pet.getMood();
        List<GridRow> rows = new ArrayList<>();
        rows.add(GridRow.of("Species", pet.getData().species, Ui.text()));
        rows.add(GridRow.of("Disposition", Misc.ucFirst(pet.sheet.disposition), Academy.COLOURS_BY_PERSONALITY.get(pet.sheet.disposition)));
        rows.add(GridRow.of("Age", age(pet), Ui.text()));
        rows.add(GridRow.of("Mood", mood.label, mood.getColor()));
        UiData.grid(p, GRID_X, IDENT_Y, w - GRID_X, UiData.GRID_ROW_H, rows);
    }

    static String age(Pet pet) {
        int cycles = (int) (pet.age / 365f);
        String span = cycles >= 1 ? cycles + (cycles == 1 ? " cycle" : " cycles") : pet.getAgeString();
        return (pet.isYoung() ? "Young, " : pet.isOld() ? "Old, " : "Adult, ") + span;
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
            view.answer = line;

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

    // returns the top of the ship strip
    private float addHearts(CustomPanelAPI p, float w, Pet pet) {
        float y = HEARTS_Y;
        boolean off = pet.isUnsettled();
        Ui.text(p, 0f, y, 120f, "Hearts");
        hearts(p, w - heartsWidth(CARD_HEART, CARD_HEART_GAP), y + 2f, pet, CARD_HEART, CARD_HEART_GAP);
        UiData.thinbar(p, 0f, y + 21f, w, pet.getHeartProgress(), off ? HEART_OFF : HEART_RED);
        Ui.tooltip(p, Ui.panel(p, 0f, y, w, 24f), "Hearts", heartsTip(pet));
        y += 28f;

        List<PetTitle> titles = pet.getTitles();
        List<String> names = new ArrayList<>();
        for (PetTitle title : titles) names.add(title.label);
        float th = hoverRow(p, y, w, "Titles", Ui.text(), names.isEmpty() ? "None" : String.join(", ", names), names.isEmpty() ? Ui.gray() : Ui.text(), null);
        Ui.tooltip(p, Ui.panel(p, 0f, y, w, th), Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> titlesTip(t, titles));
        y += th;

        if (off) {
            int days = (int) Math.ceil(pet.unsettledDays);
            y += hoverRow(p, y, w, "Effects off", Ui.gray(), days + (days == 1 ? " day left" : " days left"), Ui.gray(), String.valueOf(days));
        }

        return y + 16f;
    }

    private static void titlesTip(TooltipMakerAPI t, List<PetTitle> titles) {
        t.addPara("Titles", Ui.hl(), 0f);
        if (titles.isEmpty()) t.addPara("None yet. A pet earns titles from what it lives through: battles, jumps, a long life, an escape pod, all five hearts, answered wants, finds in salvage, time on the flagship, many markets and hyperspace.", 10f);
        for (int i = 0; i < titles.size(); i++) {
            PetTitle title = titles.get(i);
            t.addPara("%s: " + title.getReason(), i == 0 ? 10f : 3f, Ui.hl(), title.label);
        }
    }

    // the open want as a strip over the ship strip; a want with no button shows what answers it. returns the top of the ship strip
    private float addWant(CustomPanelAPI p, float y, float w, Pet pet, CampaignFleetAPI away) {
        PetWant want = pet.want;
        if (want == null) return y;

        Ui.card(p, 0f, y, w, WANT_H);
        UiImages.img(p, 10f, y + 10f, 20f, 20f, quest());
        // columns from the measured text: the longest row (come along, 30 days) needs 445 of the 468 px card
        float lw = Ui.width(want.label, Ui.INS15) + 4f;
        Ui.textIn(p, 40f, y, lw, WANT_H, want.label, Ui.INS15, Ui.text(), Ui.LEFT, new String[0], new Color[0]);
        String days = daysLeft(pet.wantDaysLeft);
        float dx = 40f + lw + 12f;
        float dw = Ui.width(days, Ui.INS15) + 4f;
        Ui.textIn(p, dx, y, dw, WANT_H, days, Ui.INS15, Ui.gray(), Ui.LEFT, new String[]{daysLeftNumber(pet.wantDaysLeft)}, new Color[]{Ui.hl()});

        if (want.button == null) {
            float hx = dx + dw + 12f;
            Ui.textIn(p, hx, y, w - 8f - hx, WANT_H, want.hint, Ui.INS15, Ui.gray(), Ui.RIGHT, new String[0], new Color[0]);
            return y + WANT_H + 8f;
        }

        PetStatusManager manager = PetStatusManager.getInstance();
        String food = pet.getData().getFavouriteFood();
        String reason = away != null ? pet.name + " is out with " + away.getName() + "."
                : pet.storage != null ? pet.name + " is in pet storage."
                : want == PetWant.TREAT && !manager.hasTreat(pet) ? "No " + Global.getSettings().getCommoditySpec(food).getName() + " aboard." : null;

        ButtonAPI button = UiButtons.button(p, w - 6f - WANT_W, y + 8f, WANT_W, BUTTON_H, want.button, reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD,
                reason != null ? null : () -> answer(pet, want));
        if (reason != null) Ui.tooltip(p, button, want.button, reason);
        else if (want == PetWant.TREAT) Ui.tooltip(p, button, want.button, "Takes 1 " + Global.getSettings().getCommoditySpec(food).getName() + ".");
        return y + WANT_H + 8f;
    }

    private void answer(Pet pet, PetWant want) {
        if (want == PetWant.TREAT) PetStatusManager.getInstance().treat(pet);
        else pet.play();

        view.answer = PetStatusManager.getInstance().getLine(pet, want == PetWant.TREAT ? PetDataRepo.EVENT_TREAT : PetDataRepo.EVENT_PLAY, shop != null);
        panel.refresh();
    }

    private static String heartsTip(Pet pet) {
        if (pet.getHearts() >= PetConstants.MAX_HEARTS) return "All five hearts.";
        if (pet.getHearts() >= PetConstants.TIME_HEARTS) return pet.favourite ? "Hearts 4 and 5 come from answered wants, jumps and fights." : "Hearts 4 and 5 are for favourites.";
        if (!pet.isAboard()) return "Hearts grow only on a ship.";
        if (pet.isUnsettled()) return "Hearts do not grow while it is Unsettled.";
        if (pet.isStarving()) return "Hearts do not grow while it is starving.";

        int days = Math.max(1, (int) Math.ceil(pet.getDaysToHeart()));
        String next = "Next heart in " + days + (days == 1 ? " day." : " days.");
        return pet.lastMealSupplies ? next + " It grows at " + Math.round(PetConstants.SUPPLIES_HEART_RATE * 100f) + "% on Supplies." : next;
    }

    // mascot effect, then the talents best first, then mutations and flaws; only the rows scroll, unknown talents last
    private void addTalents(CustomPanelAPI p, float y, float w, float h, Pet pet) {
        float ew = w - EFFECT_X - UiScroll.GUTTER;
        Ui.thead(p, 0f, y, w, new String[]{"Grade", "Talents", "Effect"}, new float[]{GRADE_X, TALENT_X, EFFECT_X},
                new float[]{GRADE_W, TALENT_W, ew}, new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});
        float ty = y + Ui.HEADING_H + 4f;
        UiScroll.Column rows = UiScroll.list(p, 0f, ty, w, h - 44f - ty, view.scroll, PetsView.TALENTS);

        String value = mascotValue(pet);
        boolean on = pet.getMascotMult() > 0f && pet.isAboard();
        String effect = pet.isUnsettled() ? "off" : pet.getHearts() < PetConstants.MASCOT_HEARTS ? "at 1 heart" : on ? "Combat CR decay " + value : "off";
        CustomPanelAPI mascot = rows.block(UiData.GRID_ROW_H, 0f);
        talentRow(mascot, ew, null, "Mascot effect", effect, on ? value : null, on);

        String rate = Misc.getRoundedValueMaxOneAfterDecimal(PetHullmod.BASE_DECREASE);
        String nest = Math.round((PetConstants.NEST_MULT - 1f) * 100f) + "%";
        List<String> tip = new ArrayList<>(List.of("Its ship loses combat readiness " + rate + "% slower in battle, from 1 heart. A nest adds " + nest + "."));
        if (pet.getData().isHamster()) tip.add("A hamster counts twice.");
        if (pet.getHearts() >= PetConstants.MASCOT_HEARTS && !pet.isUnsettled() && !pet.isAboard()) tip.add(NO_SHIP);
        Ui.tooltip(mascot, mascot, "Mascot effect", tip.toArray(new String[0]));

        List<String> talents = pet.getTalents();
        int active = PetGenetics.activeCount(talents.size(), pet.getHearts());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < talents.size(); i++) if (pet.sheet.isKnown(talents.get(i))) order.add(i);
        for (int i = 0; i < talents.size(); i++) if (!pet.sheet.isKnown(talents.get(i))) order.add(i);

        for (int i : order) {
            String id = talents.get(i);
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            CustomPanelAPI row = rows.block(UiData.GRID_ROW_H, 0f);

            if (!pet.sheet.isKnown(id)) {
                talentRow(row, ew, "?", "Unknown", "", null, false);
                Ui.tooltip(row, row, "Unknown", "A talent it has not shown yet. It shows itself once a heart switches it on.");
                continue;
            }

            String grade = String.valueOf(pet.sheet.getGrade(id));
            String off = i < active ? offReason(pet, trait) : null;
            if (i >= active) {
                int at = Math.min(PetConstants.MAX_HEARTS, i + 1);
                talentRow(row, ew, grade, trait.name(), "at " + at + (at == 1 ? " heart" : " hearts"), null, false);
            } else if (off != null) talentRow(row, ew, grade, trait.name(), "off", null, false);
            else {
                String v = trait.format(pet.getTalentValue(id));
                talentRow(row, ew, grade, trait.name(), trait.effect() + " " + v, v, true);
            }

            Ui.tooltip(row, row, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> talentTip(t, pet, trait, off));
        }

        addBody(rows, ew, pet);
        rows.end();
    }

    // mutations are always known, flaws once read; a flaw on a ship stat is off without a ship
    private static void addBody(UiScroll.Column rows, float ew, Pet pet) {
        Ui.thead(rows.heading(PetsPanel.HEAD_GAP), 0f, 0f, rows.width(), new String[]{"Level", "Mutations, flaws", "Effect"},
                new float[]{GRADE_X, TALENT_X, EFFECT_X}, new float[]{GRADE_W, TALENT_W, ew}, new Alignment[]{Ui.CENTER, Ui.LEFT, Ui.RIGHT});

        List<PetDataRepo.Trait> flaws = new ArrayList<>();
        float gap = 4f;
        for (String id : pet.sheet.traits.keySet()) {
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (trait == null) continue;
            if (trait.isFlaw() && pet.sheet.isKnown(id)) flaws.add(trait);
            if (!trait.isMutation()) continue;

            bodyRow(rows.block(UiData.GRID_ROW_H, gap), ew, pet, trait);
            gap = 0f;
        }
        for (PetDataRepo.Trait trait : flaws) {
            bodyRow(rows.block(UiData.GRID_ROW_H, gap), ew, pet, trait);
            gap = 0f;
        }

        if (pet.sheet.flawsRead && !flaws.isEmpty()) return;
        CustomPanelAPI row = rows.block(UiData.GRID_ROW_H, gap);
        if (pet.sheet.flawsRead) {
            talentRow(row, ew, null, "No flaws", "", null, false);
            Ui.tooltip(row, row, "No flaws", "A gene scan found no flaws.");
        } else {
            talentRow(row, ew, "?", "Flaws: Unknown", "", null, false);
            Ui.tooltip(row, row, "Flaws: Unknown", "Flaws are read only by a gene scan.");
        }
    }

    private static void bodyRow(CustomPanelAPI row, float ew, Pet pet, PetDataRepo.Trait trait) {
        boolean off = trait.isFlaw() && !trait.stat().isEmpty() && !pet.isAboard();
        String level = trait.hasLevels() ? String.valueOf(pet.sheet.getGrade(trait.id())) : null;
        String v = trait.format(trait.value(pet.sheet.getGrade(trait.id()), 1f));

        if (off) talentRow(row, ew, level, trait.name(), "off", null, false);
        else if (v.isEmpty()) talentRow(row, ew, level, trait.name(), trait.effect(), null, true);
        else talentRow(row, ew, level, trait.name(), trait.effect() + " " + v, v, true, trait.isFlaw() ? Ui.neg() : Ui.hl());
        Ui.tooltip(row, row, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> bodyTip(t, pet, trait, off));
    }

    private static void bodyTip(TooltipMakerAPI t, Pet pet, PetDataRepo.Trait trait, boolean off) {
        t.addPara(trait.name(), trait.isFlaw() ? Ui.neg() : Ui.hl(), 0f);
        if (trait.hasLevels()) t.addPara(trait.desc(), 10f, trait.isFlaw() ? Ui.neg() : Ui.hl(), trait.format(trait.value(trait.per(), 1f)));
        else t.addPara(trait.desc(), 10f);
        if (trait.cap() > 0f) t.addPara("At most %s.", 0f, Ui.hl(), trait.format(trait.cap()));
        t.addPara(trait.isMutation() ? "A mutation, always known. Young can inherit it." : "A flaw. Young can inherit it.", 0f);
        if (off) t.addPara(NO_SHIP, Ui.gray(), 10f);
        if (!trait.flavour().isEmpty()) t.addPara(trait.flavour().replace("%s", pet.name), 10f);
    }

    private static void talentRow(CustomPanelAPI b, float ew, String grade, String name, String effect, String hl, boolean on) {
        talentRow(b, ew, grade, name, effect, hl, on, Ui.hl());
    }

    private static void talentRow(CustomPanelAPI b, float ew, String grade, String name, String effect, String hl, boolean on, Color hlColor) {
        Color c = on ? Ui.text() : Ui.gray();
        if (grade != null) Ui.textIn(b, GRADE_X, 0f, GRADE_W, UiData.GRID_ROW_H, grade, Ui.INS15, on ? Ui.hl() : c, Ui.CENTER, new String[0], new Color[0]);
        Ui.textIn(b, TALENT_X, 0f, TALENT_W, UiData.GRID_ROW_H, name, Ui.INS15, c, Ui.LEFT, new String[0], new Color[0]);
        Ui.textIn(b, EFFECT_X, 0f, ew, UiData.GRID_ROW_H, effect, Ui.INS15, c, Ui.RIGHT,
                hl != null ? new String[]{hl} : new String[0], hl != null ? new Color[]{hlColor} : new Color[0]);
    }

    private static void talentTip(TooltipMakerAPI t, Pet pet, PetDataRepo.Trait trait, String off) {
        t.addPara(trait.name(), Ui.hl(), 0f);
        t.addPara(trait.desc(), 10f, Ui.hl(), trait.format(trait.value(trait.per(), 1f)));
        if (trait.cap() > 0f) t.addPara("At most %s.", 0f, Ui.hl(), trait.format(trait.cap()));
        if (trait.isFleet()) t.addPara("Counts once per fleet, from the best pet.", 0f);
        if (trait.isSignature()) t.addPara("A signature talent. Only rare species come with one.", 0f);
        if (off != null) t.addPara(off, Ui.neg(), 10f);
        if (!trait.flavour().isEmpty()) t.addPara(trait.flavour().replace("%s", pet.name), 10f);
    }

    // why an active talent gives nothing now, null when it works
    private static String offReason(Pet pet, PetDataRepo.Trait trait) {
        if (pet.isUnsettled()) return "Effects are off while it is Unsettled.";
        if (!pet.isAboard()) return NO_SHIP;
        if (trait.id().equals(PetHullmod.CAPTAIN) && pet.captain != null && pet.assignedFleetMember.getCaptain() != pet.captain) {
            return "Another captain commands the " + pet.assignedFleetMember.getShipName() + ".";
        }
        if (!trait.isFleet()) return null;

        Pet lead = PetStatusManager.getInstance().getLead(pet.assignedFleetMember.getFleetData().getFleet(), trait.id());
        return lead == null || lead == pet ? null : lead.name + " has a better " + trait.name() + " in this fleet.";
    }

    // "2 more talents at 4 and 5 hearts.", null when every talent is on
    private static String moreTalents(Pet pet) {
        List<String> talents = pet.getTalents();
        int active = PetGenetics.activeCount(talents.size(), pet.getHearts());
        int n = talents.size() - active;
        if (n <= 0) return null;

        List<String> at = new ArrayList<>();
        for (int i = active; i < talents.size(); i++) {
            String heart = String.valueOf(Math.min(PetConstants.MAX_HEARTS, i + 1));
            if (!at.contains(heart)) at.add(heart);
        }

        String hearts = at.size() == 1 ? at.get(0) : String.join(", ", at.subList(0, at.size() - 1)) + " and " + at.get(at.size() - 1);
        return n + (n == 1 ? " more talent at " : " more talents at ") + hearts + (at.size() == 1 && at.get(0).equals("1") ? " heart." : " hearts.");
    }

    private void addPlace(CustomPanelAPI p, float y, float w, Pet pet, CampaignFleetAPI away) {
        Ui.card(p, 0f, y, w, STRIP_H);
        float by = y + 13f;
        List<FleetMemberAPI> free = PetStatusManager.getInstance().getFreeShips(Global.getSector().getPlayerFleet());
        String noShip = free.isEmpty() ? "No ship without a pet." : null;
        FleetMemberAPI member = pet.assignedFleetMember;

        if (member != null) {
            float bx = w - 6f - BUTTON_W - 6f - BUTTON_W;
            String reason = away != null ? "Out with " + away.getName() : shop == null ? noShip : null;
            UiImages.ship(p, 3f, y + 3f, 44f, member, true, false);
            Ui.text(p, 56f, y + 7f, bx - 64f, member.getShipName());
            Ui.text(p, 56f, y + 26f, bx - 64f, reason != null ? reason : shipClass(member), reason != null ? Ui.neg() : Ui.gray());

            UiButtons.button(p, bx, by, BUTTON_W, BUTTON_H, "Move...", reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                    reason != null ? null : () -> panel.popup(new PetPlaceDialog(pet, "Move", free, shop)));
            UiButtons.button(p, w - 6f - BUTTON_W, by, BUTTON_W, BUTTON_H, "Unassign", away != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                    away != null ? null : () -> {
                        pet.place(null, null);
                        panel.refresh();
                    });
            return;
        }

        Ui.dashbox(p, 3f, y + 3f, 44f, 44f);

        PetBrood brood = habitat(pet);
        if (brood != null) {
            Pet other = PetStatusManager.getInstance().get(brood.parentA.equals(pet.id) ? brood.parentB : brood.parentA);
            Ui.text(p, 56f, y + 7f, w - 64f, "Habitat " + (brood.habitat + 1));
            Ui.text(p, 56f, y + 26f, w - 64f, pet.storage.getMarket().getName() + (other != null ? ", with " + other.name : ""), Ui.gray());
            return;
        }

        if (pet.storage != null) {
            float bx = w - 6f - WIDE_W - 6f - BUTTON_W;
            Ui.text(p, 56f, y + 7f, bx - 64f, "Pet storage");
            Ui.text(p, 56f, y + 26f, bx - 64f, noShip != null ? noShip : pet.storage.getMarket().getName(), noShip != null ? Ui.neg() : Ui.gray());

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
        Ui.text(p, 56f, y + 7f, bx - 64f, "Menagerie");
        Ui.text(p, 56f, y + 26f, bx - 64f, reason != null ? reason : "In the fleet, on no ship", reason != null ? Ui.neg() : Ui.gray());
        UiButtons.button(p, bx, by, BUTTON_W, BUTTON_H, "Assign...", reason != null ? UiButtons.Kind.OFF : UiButtons.Kind.STD, CutStyle.TL_BR,
                reason != null ? null : () -> panel.popup(new PetPlaceDialog(pet, "Assign", free, shop)));
    }

    static String shipClass(FleetMemberAPI member) {
        return member.getHullSpec().getHullName() + " class " + member.getHullSpec().getDesignation().toLowerCase();
    }

    // why the pet cannot be rehomed or euthanized from here, null when it can
    private static String heldReason(Pet pet, CampaignFleetAPI away) {
        if (away != null) return pet.name + " is out with " + away.getName() + ".";
        return habitat(pet) != null ? pet.name + " is in a habitat." : null;
    }

    static PetBrood habitat(Pet pet) {
        return pet.storage instanceof PetShop at ? at.getBrood(pet) : null;
    }

    private void addBottom(CustomPanelAPI p, float w, float h, Pet pet, String held) {
        Ui.rule(p, 0f, h - 36f, w);
        float y = h - BUTTON_H;
        Color hl = Misc.getHighlightColor();

        if (shop == null) {
            euthanizeButton(p, w - WIDE_W, y, WIDE_W, pet, held);
            return;
        }

        String fee = Misc.getDGSCredits(PetConstants.REHOME_FEE);
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        boolean broke = credits < PetConstants.REHOME_FEE;
        boolean blocked = held != null || broke;

        ButtonAPI rehome = UiButtons.button(p, w - 2f * BUTTON_W - 6f, y, BUTTON_W, BUTTON_H, "Rehome", blocked ? UiButtons.Kind.OFF : UiButtons.Kind.RED, CutStyle.TL_BR,
                blocked ? null : () -> panel.popup(new UiConfirm("Confirm", info -> info.addPara("Rehome %s for a fee of %s?\n\n%s goes to a new home and does not come back.", 0f,
                        new Color[]{hl, hl, Ui.text()}, pet.name, fee, pet.name), () -> shop.rehome(pet))));
        if (held != null) Ui.tooltip(p, rehome, "Rehome", held);
        else if (broke) Ui.tooltip(p, rehome, "Rehome", "Rehoming costs " + fee + ". You have " + Misc.getDGSCredits(credits) + ".");

        euthanizeButton(p, w - BUTTON_W, y, BUTTON_W, pet, held);
    }

    private void euthanizeButton(CustomPanelAPI p, float x, float y, float w, Pet pet, String held) {
        ButtonAPI button = UiButtons.button(p, x, y, w, BUTTON_H, "Euthanize", held != null ? UiButtons.Kind.OFF : UiButtons.Kind.RED, CutStyle.TL_BR,
                held != null ? null : () -> euthanize(pet));
        if (held != null) Ui.tooltip(p, button, "Euthanize", held);
    }

    private void euthanize(Pet pet) {
        panel.popup(new UiConfirm("Confirm", info -> info.addPara("Euthanize %s?\n\nThis cannot be undone.", 0f, Misc.getHighlightColor(), pet.name),
                () -> PetStatusManager.getInstance().reportPetDied(pet, PetStatusManager.PetDeathCause.EUTHANIZED)));
    }
}
