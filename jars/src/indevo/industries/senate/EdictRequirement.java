package indevo.industries.senate;

public class EdictRequirement {
    public final String text;
    public final String shortText;
    public final boolean met;

    public EdictRequirement(String text, String shortText, boolean met) {
        this.text = text;
        this.shortText = shortText;
        this.met = met;
    }
}
