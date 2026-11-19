package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import indevo.industries.petshop.PetTitle;
import indevo.industries.petshop.listener.PetStatusManager;
import indevo.industries.petshop.listener.PetStatusManager.PetDeathCause;
import indevo.industries.petshop.memory.DeathData;
import indevo.industries.petshop.memory.Pet;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiCanvas;
import indevo.utils.ui.UiData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// section Remembered of the Memorial tooltip: the dead pets, newest first; F1 lists more, each with a grey line
public final class PetMemorial {

    public static final float TOOLTIP_W = 420f;
    static final int SHOWN = 5;
    // a tooltip can not scroll, so F1 shows what fits the screen up to this
    static final int SHOWN_EXPANDED = 12;
    // vanilla's lines under the section: upkeep, items, the F1 line
    static final float VANILLA_BELOW = 260f;
    static final float SECTION_H = 30f;
    static final float[] COLUMN_X = {8f, 112f, 252f, 332f};
    static final float LINE_GAP = 4f;
    static final float MORE_GAP = 6f;

    private PetMemorial() {
    }

    // sold and rehomed pets are not dead
    public static List<Pet> remembered() {
        List<Pet> dead = new ArrayList<>();
        for (Pet pet : PetStatusManager.getInstance().getPets()) {
            DeathData data = pet.deathData;
            if (!pet.isDead() || data == null || data.cause == PetDeathCause.SOLD || data.cause == PetDeathCause.REHOMED) continue;
            dead.add(pet);
        }
        // clock timestamps are negative in early cycles, only 0 means a death saved before the field
        dead.sort(Comparator.comparing((Pet pet) -> pet.deathData.timestamp == 0L)
                .thenComparing(pet -> pet.deathData.timestamp, Comparator.reverseOrder()));
        return dead;
    }

    public static void addSection(TooltipMakerAPI tooltip, FactionAPI faction, boolean expanded) {
        List<Pet> dead = remembered();
        if (dead.isEmpty()) return;

        tooltip.addSectionHeading("Remembered", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, 10f);
        float w = tooltip.getWidthSoFar();
        float rowH = UiData.GRID_ROW_H + (expanded ? UiData.GRID_ROW_H + LINE_GAP : 0f);
        int shown = Math.min(dead.size(), expanded ? fitting(tooltip, rowH) : SHOWN);
        int more = dead.size() - shown;
        float h = Ui.HEADING_H + 4f + shown * rowH + (more > 0 ? MORE_GAP + Ui.LINE_H : 0f);

        CustomPanelAPI table = Global.getSettings().createCustom(w, h, new UiCanvas());
        float cw = w - COLUMN_X[3] - 8f;
        Ui.thead(table, 0f, 0f, w, new String[]{"Name", "Species", "Age", "Cause"}, COLUMN_X, new float[]{100f, 136f, 76f, cw},
                new Alignment[]{Ui.LEFT, Ui.LEFT, Ui.LEFT, Ui.RIGHT});

        float y = Ui.HEADING_H + 4f;
        for (int i = 0; i < shown; i++) {
            Pet pet = dead.get(i);
            cell(table, COLUMN_X[0], y, 96f, pet.name, Ui.text(), Ui.LEFT);
            cell(table, COLUMN_X[1], y, 136f, pet.getData().species, Ui.text(), Ui.LEFT);
            cell(table, COLUMN_X[2], y, 76f, PetCard.span(pet), Ui.text(), Ui.LEFT);
            cell(table, COLUMN_X[3], y, cw, cause(pet.deathData.cause), Ui.text(), Ui.RIGHT);
            y += UiData.GRID_ROW_H;
            if (!expanded) continue;

            cell(table, COLUMN_X[0], y, w - COLUMN_X[0] - 8f, line(pet), Ui.gray(), Ui.LEFT);
            y += UiData.GRID_ROW_H + LINE_GAP;
        }
        if (more > 0) Ui.text(table, 0f, y + MORE_GAP, w, "and " + more + " more", Ui.gray());

        tooltip.addCustom(table, 4f);
    }

    private static int fitting(TooltipMakerAPI tooltip, float rowH) {
        float room = Global.getSettings().getScreenHeight() - tooltip.getHeightSoFar() - VANILLA_BELOW - SECTION_H - Ui.HEADING_H - 4f - MORE_GAP - Ui.LINE_H;
        return Math.max(SHOWN, Math.min(SHOWN_EXPANDED, (int) (room / rowH)));
    }

    private static void cell(CustomPanelAPI p, float x, float y, float w, String s, Color c, Alignment align) {
        Ui.textIn(p, x, y, w, UiData.GRID_ROW_H, Ui.fit(s, Ui.INS15, w), Ui.INS15, c, align, new String[0], new Color[0]);
    }

    static String cause(PetDeathCause cause) {
        return switch (cause) {
            case NATURAL -> "Old age";
            case COMBAT -> "Combat";
            case STARVED -> "Starved";
            case EUTHANIZED -> "Put to sleep";
            default -> "Unknown";
        };
    }

    // titles, the last ship or place, the cycle it died in; deaths from before the timestamp have no cycle
    private static String line(Pet pet) {
        DeathData data = pet.deathData;
        List<String> titles = new ArrayList<>();
        for (PetTitle title : pet.getTitles()) titles.add(title.label);

        String place = data.shipName != null ? data.shipName : data.inStorage && data.marketName != null ? "Pet storage, " + data.marketName : "Menagerie";
        String cycle = data.timestamp != 0L ? ", cycle " + Global.getSector().getClock().createClock(data.timestamp).getCycle() : "";
        return (titles.isEmpty() ? "" : String.join(", ", titles) + ". ") + place + cycle;
    }
}
