package indevo.industries.petshop.plugins;

import indevo.config.PlayerSettings;
import indevo.config.Setting;
import indevo.utils.ui.UiTour;

// the Husbandry tour: lights the habitats, the adults, the dials and Pair the first time the tab opens while IndEvo_petTour is on;
// it ends on Done, Skip, Escape or a started pairing and switches the setting off; one per panel opening, so closing the panel mid tour starts it again
final class PetTour implements UiTour.State {

    static final int CARD = 0;
    static final int HABITATS = 1;
    static final int ADULTS = 2;
    static final int DIALS = 3;
    static final int PAIR = 4;
    static final int STEPS = 4;

    private final PetsView view;
    private int step = 0;
    private float start;
    private float end = -1f;
    // this build's targets from PetHusbandry, x y w h from the panel's top left; CARD stands in for a step whose part is not shown
    final float[][] targets = new float[STEPS + 1][];
    boolean pairPossible;
    String pairDays;

    PetTour(PetsView view) {
        this.view = view;
    }

    boolean running() {
        return step > 0 && end < 0f;
    }

    boolean visible() {
        return step > 0 && (end < 0f || view.clock - end < UiTour.FADE);
    }

    boolean at(int s) {
        return running() && step == s;
    }

    void begin() {
        if (step != 0) return;
        step = HABITATS;
        start = view.clock;
    }

    void next() {
        if (!running()) return;
        if (step >= STEPS) finish();
        else step++;
    }

    void finish() {
        if (!running()) return;
        end = view.clock;
        PlayerSettings.set(Setting.PET_TOUR, false);
    }

    // the step to draw; after the end it keeps the lit part with no box while it fades out
    UiTour.Step step(Runnable next, Runnable skip) {
        float[] t = targets[step] != null ? targets[step] : targets[CARD];
        if (t == null) return null;

        String text = running() ? text() : null;
        String number = step == PAIR && pairDays != null ? pairDays : null;
        return new UiTour.Step(t[0], t[1], t[2], t[3], text, number, step, STEPS, next, skip);
    }

    private String text() {
        return switch (step) {
            case HABITATS -> "Two pets in one habitat raise a spawn. Pick a free one.";
            case ADULTS -> pairPossible ? "Pick two adults of one species. No match shows pets without a partner here." : "Bring two adults of one species.";
            case DIALS -> "These set the odds for each young. Every trait rolls on its own. Hover one to see what it does.";
            default -> pairDays != null ? "Pair starts the incubation. It takes " + pairDays + " days." : "Pair starts the incubation. Pick two adults first.";
        };
    }

    @Override
    public float alpha() {
        float in = Math.min(1f, (view.clock - start) / UiTour.FADE);
        return end < 0f ? in : in * Math.max(0f, 1f - (view.clock - end) / UiTour.FADE);
    }

    @Override
    public float glow() {
        return Setting.PET_MOTION.enabled() ? UiTour.pulse(view.clock - start) : 1f;
    }

    @Override
    public boolean blocking() {
        return running();
    }
}
