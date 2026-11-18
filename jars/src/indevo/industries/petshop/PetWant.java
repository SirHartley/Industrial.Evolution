package indevo.industries.petshop;

import java.util.Locale;

// what a favourite asks for; a want with no button is answered by playing the game, the hint says how
public enum PetWant {
    ATTENTION("Wants attention", null, "Pet its portrait"),
    PLAY("Wants to play", "Play", null),
    TREAT("Wants a treat", "Treat", null),
    COMFORT("Wants comfort", null, "Pet its portrait"),
    SHORE_LEAVE("Wants shore leave", null, "Dock at any market"),
    COME_ALONG("Wants to come along", null, "Salvage, survey or discover");

    public final String label;
    public final String button;
    public final String hint;

    PetWant(String label, String button, String hint) {
        this.label = label;
        this.button = button;
        this.hint = hint;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    // the line event a pet says when the want opens
    public String event() {
        return "want_" + id();
    }
}
