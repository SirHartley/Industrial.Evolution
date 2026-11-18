package indevo.industries.petshop;

import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public enum PetMood {
    HUNGRY("Hungry", true),
    UNSETTLED("Unsettled", true),
    SHELL_SHOCKED("Shell shocked", true),
    UNHAPPY_WITH_FOOD("Unhappy with its food", true),
    SLEEPY("Sleepy", false),
    CONTENT("Content", false);

    public final String label;
    public final boolean bad;

    PetMood(String label, boolean bad) {
        this.label = label;
        this.bad = bad;
    }

    public Color getColor() {
        return bad ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
    }
}
