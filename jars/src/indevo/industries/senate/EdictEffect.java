package indevo.industries.senate;

// one effect line of an Edict: label left, value right in the tone's colour, optional grey note after the value
public record EdictEffect(String label, String value, Tone tone, String note, boolean otherColonies) {

    public enum Tone {
        GOOD, BAD, NEUTRAL
    }

    public static EdictEffect good(String label, String value) {
        return new EdictEffect(label, value, Tone.GOOD, null, false);
    }

    public static EdictEffect bad(String label, String value) {
        return new EdictEffect(label, value, Tone.BAD, null, false);
    }

    public static EdictEffect neutral(String label, String value) {
        return new EdictEffect(label, value, Tone.NEUTRAL, null, false);
    }

    public EdictEffect withNote(String note) {
        return new EdictEffect(label, value, tone, note, otherColonies);
    }

    public EdictEffect onOtherColonies() {
        return new EdictEffect(label, value, tone, note, true);
    }
}
