package indevo.industries.petshop.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import indevo.config.Setting;
import indevo.industries.petshop.PetGenetics;
import indevo.industries.petshop.memory.Pet;
import indevo.industries.petshop.memory.PetDataRepo;
import indevo.industries.petshop.memory.PetSheet;
import indevo.utils.ui.Ui;
import indevo.utils.ui.UiDraw;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// the gene scan reveal: the scan reads at once, the rows it read show one by one in real seconds under a green border; never saved
final class PetReveal {

    // row i at (i + 1) x ROW_TIME, its bar fades in, one tick each
    static final float ROW_TIME = 0.72f;
    static final float FADE_TIME = 0.45f;
    static final float GLOW_TIME = 1.5f;
    static final String ROW_SOUND = "ui_cargo_supplies";
    static final float GLOW_W = 10f;
    static final float PAD = 5f;
    // the flaw part of a pet with no flaw left to read: "Flaws: Unknown" goes, "No flaws" shows when it has none
    static final String FLAWS = "flaws";
    static final Color GREEN = new Color(75, 195, 0);
    static final Color LIT = new Color(50, 130, 0);

    private String petId = null;
    private final List<String> rows = new ArrayList<>();
    private float start;
    private float now;
    private int step;

    // call before the scan reads: the rows it is about to read, unknown talents in card order, then the flaws
    void begin(Pet pet, float clock) {
        petId = pet.id;
        rows.clear();
        for (String id : pet.getTalents()) if (!pet.sheet.isKnown(id)) rows.add(id);
        if (!pet.sheet.flawsRead) {
            int talents = rows.size();
            for (String id : pet.sheet.traits.keySet()) {
                PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
                if (trait != null && trait.isFlaw() && !pet.sheet.isKnown(id)) rows.add(id);
            }
            if (rows.size() == talents) rows.add(FLAWS);
        }

        start = clock;
        now = clock;
        step = Setting.PET_MOTION.enabled() ? -1 : rows.size();
    }

    // every frame; true when the panel needs a rebuild: the next row is read, or the reveal is over
    boolean tick(float clock) {
        if (petId == null) return false;

        now = clock;
        if (now - doneAt() > GLOW_TIME) {
            petId = null;
            return true;
        }

        int s = Setting.PET_MOTION.enabled() ? Math.min(rows.size(), (int) ((now - start) / ROW_TIME) - 1) : rows.size();
        if (s == step) return false;
        step = s;
        if (step < rows.size()) Global.getSoundPlayer().playUISound(ROW_SOUND, 1f, 1f);
        return true;
    }

    // a click anywhere shows the rest at once; a panel plugin gets input after its children, so the scan button's own click comes right after begin
    void skip() {
        if (petId == null || step >= rows.size() || now <= start) return;
        start = now - (rows.size() + 1) * ROW_TIME;
    }

    boolean on(Pet pet) {
        return pet != null && pet.id.equals(petId);
    }

    boolean reading(Pet pet) {
        return on(pet) && step < rows.size();
    }

    boolean scanned(Pet pet, String id) {
        return on(pet) && rows.contains(id);
    }

    boolean known(Pet pet, String id) {
        return pet.sheet.isKnown(id) && !(on(pet) && rows.indexOf(id) > step);
    }

    boolean flawsRead(Pet pet) {
        if (!pet.sheet.flawsRead) return false;
        if (!on(pet)) return true;

        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i);
            PetDataRepo.Trait trait = PetDataRepo.getTrait(id);
            if (id.equals(FLAWS) || trait != null && trait.isFlaw()) return i <= step;
        }
        return true;
    }

    boolean lit(Pet pet, String id) {
        return reading(pet) && rows.indexOf(id) == step;
    }

    // "2 of 5" while reading
    int count() {
        return Math.max(1, Math.min(step + 1, rows.size()));
    }

    int total() {
        return rows.size();
    }

    // the sheet as the player knows it, rows the reveal has not reached left out
    PetSheet seen(Pet pet) {
        PetSheet seen = PetGenetics.seen(pet.sheet);
        if (!on(pet)) return seen;

        for (int i = step + 1; i < rows.size(); i++) seen.traits.remove(rows.get(i));
        seen.known.retainAll(seen.traits.keySet());
        return seen;
    }

    // 1 px green around the panel while the rows are read, then one glow that fades with it
    void border(CustomPanelAPI p, float x, float y, float w, float h) {
        border(p, x, y, w, h, PAD);
    }

    void border(CustomPanelAPI p, float x, float y, float w, float h, float pad) {
        if (petId == null) return;
        Ui.painted(p, x - pad, y - pad, w + 2f * pad, h + 2f * pad, null, this::paintBorder);
    }

    // the green bar behind the row being read
    void bar(CustomPanelAPI row, float w, float h) {
        float at = start + (step + 1) * ROW_TIME;
        Ui.painted(row, 0f, 0f, w, h, (pos, a) -> UiDraw.quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), LIT,
                a * 0.55f * Math.min(1f, Math.max(0f, now - at) / FADE_TIME)), null);
    }

    private float doneAt() {
        return Setting.PET_MOTION.enabled() ? start + (rows.size() + 1) * ROW_TIME : start;
    }

    private void paintBorder(PositionAPI pos, float alpha) {
        if (petId == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();
        float g = now < doneAt() ? 0f : Math.min(1f, (now - doneAt()) / GLOW_TIME);
        float fade = now < doneAt() ? 1f : 1f - g;
        UiDraw.outline(x, y, w, h, GREEN, alpha * fade);
        if (now < doneAt()) return;

        for (int i = 1; i <= 4; i++) {
            float d = i * GLOW_W / 4f * (0.4f + 0.6f * g);
            UiDraw.outline(x - d, y - d, w + 2f * d, h + 2f * d, GREEN, alpha * fade * (1f - i / 5f) * 0.6f);
        }
    }
}
