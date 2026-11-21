package indevo.industries.changeling.industry;

// one rule of a variant for the picker and the population tooltip; text is a format, highlights fill its %s
public record EffectLine(boolean gain, String text, String[] highlights) {

    public String shown() {
        return highlights.length == 0 ? text : String.format(text, (Object[]) highlights);
    }
}
