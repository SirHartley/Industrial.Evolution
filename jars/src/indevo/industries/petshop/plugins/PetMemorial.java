package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.petshop.PetConstants;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.memory.DeathData;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetMemorialFile;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiCanvas;
import indevo.utils.ui.UiData;
import indevo.utils.ui.UiDraw;
import indevo.utils.ui.UiImages;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// section Remembered of the Memorial tooltip: one pet at a time over a wall of faces, stepping every PERIOD real seconds
public final class PetMemorial {

    public static final float TOOLTIP_W = 420f;
    static final float CARD = 72f;
    static final float PORTRAIT = 64f;
    static final float TEXT_X = 84f;
    static final float NAME_W = 257f;
    static final float STAR = 14f;
    static final float STAR_GAP = 6f;
    static final float LINE_W = 336f;
    static final float HEART = 11f;
    static final float HEART_GAP = 3f;
    static final float HEARTS_X = 353f;
    static final float HEARTS_Y = 3f;
    static final float WALL_Y = 80f;
    static final float FACE = 44f;
    static final float FACE_PITCH = 47f;
    static final int PER_LINE = 9;
    static final int MAX_LINES = 5;
    static final float PERIOD = 3.5f;
    static final float FADE = 0.15f;
    // no frame for this long means the tooltip closed; a shorter gap is the F1 rebuild, which keeps the pet and phase
    static final float REOPEN = 0.25f;
    // estimate of vanilla's lines under the section: items, improvements, the footnote, the F1 strip
    static final float VANILLA_BELOW = 200f;
    static final float SCREEN_MARGIN = 20f;
    static final String ELSEWHERE = "Lost in another universe";
    static final String LOST = "Lost";

    // auto scroll state, real time and never saved; a new sector starts on the first pet
    private static WeakReference<SectorAPI> sector = new WeakReference<>(null);
    private static int shown = -1;
    private static int first;
    private static long start;
    private static long lastFrame;

    // pet null: a starred pet that died in another save, kept in PetMemorialFile; cycle -1 when not known
    public record Gone(String id, Pet pet, String name, String species, String icon, String age, float days, String cause, int cycle, String how,
                       List<String> titles, int hearts, boolean star) {
        public boolean grey() {
            return pet == null;
        }

        String about() {
            return species + ", " + age;
        }
    }

    private PetMemorial() {
    }

    // this save's dead newest first, then pets from other saves newest first; sold and rehomed pets are not dead
    public static List<Gone> remembered() {
        List<Pet> dead = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Pet pet : PetStatusManager.getInstance().getPets()) {
            ids.add(pet.id);
            if (pet.isRemembered()) dead.add(pet);
        }
        // clock timestamps are negative in early cycles, only 0 means a death saved before the field
        dead.sort(Comparator.comparing((Pet pet) -> pet.deathData.timestamp == 0L)
                .thenComparing(pet -> pet.deathData.timestamp, Comparator.reverseOrder()));

        List<Gone> gone = new ArrayList<>();
        for (Pet pet : dead) gone.add(gone(pet));

        List<PetMemorialFile.Lost> lost = PetMemorialFile.get().getPets();
        for (int i = lost.size() - 1; i >= 0; i--) {
            PetMemorialFile.Lost l = lost.get(i);
            if (ids.contains(l.id())) continue;
            gone.add(new Gone(l.id(), null, l.name(), l.species(), l.icon(), l.age(), l.days(), LOST, -1, ELSEWHERE, l.titles(), l.hearts(), true));
        }
        return gone;
    }

    private static Gone gone(Pet pet) {
        List<String> titles = new ArrayList<>();
        for (PetTitle title : pet.getTitles()) titles.add(title.label);
        DeathData data = pet.deathData;
        int cycle = data.timestamp != 0L ? Global.getSector().getClock().createClock(data.timestamp).getCycle() : -1;
        return new Gone(pet.id, pet, pet.name, pet.getData().species, pet.getIcon(), pet.getSpan(), pet.age, cause(data), cycle, how(data, cycle), titles,
                pet.getHearts(), pet.favourite);
    }

    // the short form on a list tile
    static String cause(DeathData data) {
        return switch (data.cause) {
            case NATURAL -> "Old age";
            case COMBAT -> "Combat";
            case STARVED -> "Starved";
            case EUTHANIZED -> "Put to sleep";
            default -> LOST;
        };
    }

    static String how(DeathData data, int c) {
        String cycle = c >= 0 ? " in cycle " + c : "";
        return switch (data.cause) {
            case NATURAL -> "Died of old age" + cycle;
            case COMBAT -> (data.shipName != null ? "Lost with the " + data.shipName : "Lost with its ship") + cycle;
            case STARVED -> "Starved" + cycle;
            case EUTHANIZED -> "Put to sleep" + cycle;
            default -> LOST;
        };
    }

    public static void addSection(TooltipMakerAPI tooltip, FactionAPI faction, boolean expanded) {
        List<Gone> gone = remembered();
        if (gone.isEmpty()) return;

        int n = gone.size();
        open(n);
        tooltip.addSectionHeading("Remembered", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, 10f);

        int needed = (n + PER_LINE - 1) / PER_LINE;
        int lines = n == 1 ? 0 : expanded ? Math.max(1, Math.min(Math.min(MAX_LINES, needed), fitting(tooltip))) : 1;
        int cells = Math.min(n, lines * PER_LINE);
        int faces = n > cells ? cells - 1 : cells;
        float h = lines == 0 ? CARD : WALL_Y + lines * FACE_PITCH - 3f;

        Featured featured = new Featured(gone, faces, Setting.PET_MOTION.enabled());
        CustomPanelAPI panel = Global.getSettings().createCustom(TOOLTIP_W, h, new UiCanvas((pos, a) -> featured.frame(), featured::paint));
        featured.build(panel, cells);
        tooltip.addCustom(panel, 4f);
    }

    // the tooltip plus the F1 strip keep SCREEN_MARGIN to the screen edges
    private static int fitting(TooltipMakerAPI tooltip) {
        float room = Global.getSettings().getScreenHeight() - 2f * SCREEN_MARGIN - tooltip.getHeightSoFar() - 4f - VANILLA_BELOW - WALL_Y + 3f;
        return (int) Math.floor(room / FACE_PITCH);
    }

    // each opening starts on the pet after the one shown last
    private static void open(int n) {
        long now = System.nanoTime();
        SectorAPI current = Global.getSector();
        if (sector.get() != current) {
            sector = new WeakReference<>(current);
            shown = -1;
            lastFrame = 0L;
        }

        boolean rebuild = lastFrame != 0L && (now - lastFrame) / 1e9f < REOPEN;
        if (rebuild && first < n) return;
        first = (shown + 1) % n;
        start = now;
    }

    // as many titles as fit, then " +N"; at least one, cut when even that does not fit
    static String titles(List<String> titles, float w) {
        if (titles.isEmpty()) return "";
        String all = String.join(", ", titles);
        if (Ui.width(all, Ui.INS15) + 2f <= w) return all;

        for (int k = titles.size() - 1; k >= 1; k--) {
            String s = String.join(", ", titles.subList(0, k)) + " +" + (titles.size() - k);
            if (Ui.width(s, Ui.INS15) + 2f <= w) return s;
        }
        String rest = " +" + (titles.size() - 1);
        return Ui.fit(titles.get(0), Ui.INS15, w - Ui.width(rest, Ui.INS15)) + rest;
    }

    private static final class Featured {
        final List<Gone> gone;
        final int faces;
        final boolean motion;
        LabelAPI name;
        LabelAPI about;
        LabelAPI how;
        LabelAPI titles;
        int index = -1;
        float fade = 1f;
        float starX;

        Featured(List<Gone> gone, int faces, boolean motion) {
            this.gone = gone;
            this.faces = faces;
            this.motion = motion;
        }

        void build(CustomPanelAPI p, int cells) {
            Ui.card(p, 0f, 0f, CARD, CARD);
            name = line(p, 0f, Ui.hl());
            about = line(p, UiData.GRID_ROW_H, Ui.text());
            how = line(p, 2f * UiData.GRID_ROW_H, Ui.gray());
            titles = line(p, 3f * UiData.GRID_ROW_H, Ui.gray());

            for (int i = 0; i < cells; i++) {
                float x = (i % PER_LINE) * FACE_PITCH;
                float y = WALL_Y + (i / PER_LINE) * FACE_PITCH;
                if (i == faces) {
                    Ui.card(p, x, y, FACE, FACE);
                    Ui.textIn(p, x, y, FACE, FACE, "+" + (gone.size() - faces), Ui.INS15, Ui.base(), Ui.CENTER, new String[0], new Color[0]);
                    continue;
                }

                Gone g = gone.get(i);
                if (g.grey()) UiImages.grey(p, x, y, FACE, FACE, g.icon());
                else UiImages.img(p, x, y, FACE, FACE, g.icon());
                Ui.card(p, x, y, FACE, FACE);
            }
            frame();
        }

        private static LabelAPI line(CustomPanelAPI p, float y, Color c) {
            LabelAPI label = Global.getSettings().createLabel("", Ui.INS15);
            label.setColor(c);
            label.setAlignment(Ui.LEFT);
            p.addComponent((UIComponentAPI) label).setSize(LINE_W, label.computeTextHeight("Ag")).inTL(TEXT_X, y);
            return label;
        }

        // runs before the children draw, so the labels show this frame's pet
        void frame() {
            long now = System.nanoTime();
            lastFrame = now;
            int n = gone.size();
            int next = first;
            fade = 1f;
            if (n > 1 && motion) {
                float s = (now - start) / 1e9f + FADE;
                int k = (int) (s / PERIOD);
                float phase = s - k * PERIOD;
                next = (first + k) % n;
                fade = phase < FADE ? phase / FADE : phase > PERIOD - FADE ? (PERIOD - phase) / FADE : 1f;
            }
            shown = next;
            if (next != index) show(next);

            name.setOpacity(fade);
            about.setOpacity(fade);
            how.setOpacity(fade);
            titles.setOpacity(fade);
        }

        private void show(int i) {
            index = i;
            Gone g = gone.get(i);
            String shownName = Ui.fit(g.name(), Ui.INS15, g.star() ? NAME_W - STAR - STAR_GAP : NAME_W);
            name.setText(shownName);
            starX = TEXT_X + Ui.width(shownName, Ui.INS15) + STAR_GAP;
            about.setText(Ui.fit(g.about(), Ui.INS15, LINE_W));
            how.setText(Ui.fit(g.how(), Ui.INS15, LINE_W));
            titles.setText(PetMemorial.titles(g.titles(), LINE_W));
        }

        // portrait, star and hearts fade with the lines; the gold frame steps with the pet
        void paint(PositionAPI pos, float alpha) {
            if (index < 0) return;
            Gone g = gone.get(index);
            float left = pos.getX();
            float top = pos.getY() + pos.getHeight();
            float a = alpha * fade;

            float inset = (CARD - PORTRAIT) / 2f;
            UiImages.paint(g.icon(), g.grey(), left + inset, top - inset - PORTRAIT, PORTRAIT, PORTRAIT, a);
            if (g.star()) UiDraw.fan(Math.round(left + starX), top - 1f - STAR, STAR, 20f, PetCard.STAR, 10f, 10f, Ui.hl(), a);

            Color empty = Misc.scaleAlpha(Ui.base(), PetCard.HEART_EMPTY);
            for (int i = 0; i < PetConstants.MAX_HEARTS; i++) {
                UiDraw.fan(left + HEARTS_X + i * (HEART + HEART_GAP), top - HEARTS_Y - HEART, HEART, 14f, PetCard.HEART, 7f, 7f,
                        i < g.hearts() ? PetCard.HEART_RED : empty, a);
            }

            if (gone.size() == 1) return;
            int cell = index < faces ? index : faces < gone.size() ? faces : -1;
            if (cell < 0) return;
            float x = (cell % PER_LINE) * FACE_PITCH;
            float y = WALL_Y + (cell / PER_LINE) * FACE_PITCH;
            UiDraw.outline(left + x, top - y - FACE, FACE, FACE, Ui.hl(), alpha);
        }
    }
}
