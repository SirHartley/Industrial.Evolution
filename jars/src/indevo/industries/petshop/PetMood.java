package indevo.industries.petshop;

import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.Locale;

public enum PetMood {
    HUNGRY("Hungry", true),
    UNSETTLED("Unsettled", true),
    SHELL_SHOCKED("Shell shocked", true),
    UNHAPPY_WITH_FOOD("Unhappy with its food", true),
    HAPPY("Happy", false),
    SLEEPY("Sleepy", false),
    CONTENT("Content", false);

    public final String label;
    public final boolean bad;

    PetMood(String label, boolean bad) {
        this.label = label;
        this.bad = bad;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Color getColor() {
        if (bad) return Misc.getNegativeHighlightColor();
        return switch (this) {
            case HAPPY -> Misc.getPositiveHighlightColor();
            case SLEEPY -> Misc.getGrayColor();
            default -> Misc.getBasePlayerColor();
        };
    }
}
