package oldstory.memory;

import java.util.List;

public class StoryEntry {
    String id;
    List<String> optionalPlanetTypes;
    List<String> optionalRuinsSizes;
    float pickerWeight;
    String commsIntroText;
    String commsOutroText;
    List<String> entries;

    public StoryEntry(String id, List<String> optionalPlanetTypes, List<String> optionalRuinsSizes, float pickerWeight, String commsIntroText, List<String> entries, String commsOutroText) {
        this.id = id;
        this.optionalPlanetTypes = optionalPlanetTypes;
        this.optionalRuinsSizes = optionalRuinsSizes;
        this.pickerWeight = pickerWeight;
        this.commsIntroText = commsIntroText;
        this.entries = entries;
        this.commsOutroText = commsOutroText;
    }

    public String getId() {
        return id;
    }

    public List<String> getOptionalPlanetTypes() {
        return optionalPlanetTypes;
    }

    public List<String> getOptionalRuinsSizes() {
        return optionalRuinsSizes;
    }

    public float getPickerWeight() {
        return pickerWeight;
    }

    public String getCommsIntroText() {
        return commsIntroText;
    }

    public String getCommsOutroText() {
        return commsOutroText;
    }

    public List<String> getEntries() {
        return entries;
    }

    public String getEntry(int i){
        return getEntries().size() < i + 1 ? null : getEntries().get(i).isEmpty() ? null : getEntries().get(i);
    }
}
