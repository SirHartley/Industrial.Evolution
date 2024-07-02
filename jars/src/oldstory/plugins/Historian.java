package oldstory.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import oldstory.OldStoryLogger;
import oldstory.memory.FileLoader;
import oldstory.memory.StoryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Historian {
    public static final String MOD_ID = "oldstory";
    private final static String HISTORIAN_MEM_ID = "$oldStory_Historian";
    private final static String FIXED_STORY_MEM_ID = "$oldStory_fixedStoryId";

    private List<String> toldStories = new ArrayList<>();
    private Random random = new Random();

    public static Historian getHistorian(){
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        Historian historian;

        if (!mem.contains(HISTORIAN_MEM_ID)) {
            historian = new Historian();
            mem.set(HISTORIAN_MEM_ID, historian);
        } else historian = (Historian) mem.get(HISTORIAN_MEM_ID);

        return historian;
    }

    public StoryEntry getStoryForPlanet(PlanetAPI planet){
        MemoryAPI mem = planet.getMemoryWithoutUpdate();
        if (mem.contains(FIXED_STORY_MEM_ID)) return getSpecificStory(mem.getString(FIXED_STORY_MEM_ID));

        WeightedRandomPicker<StoryEntry> picker = new WeightedRandomPicker<StoryEntry>(random);

        for (StoryEntry entry : FileLoader.fetchStories()){
            if (toldStories.contains(entry.getId())) continue;

            boolean ruinsConditionMet = entry.getOptionalRuinsSizes().isEmpty();
            if (!ruinsConditionMet) for (String condID : entry.getOptionalRuinsSizes()) if(planet.getMarket().hasCondition(condID)){
                ruinsConditionMet = true;
                break;
            }

            boolean planetConditionMet = entry.getOptionalPlanetTypes().isEmpty() || entry.getOptionalPlanetTypes().contains(planet.getTypeId());

            OldStoryLogger.logDevInfo("Checking Story " + entry.getId() + " ruinsConditionMet: " + ruinsConditionMet + " planetConditionMet: " + planetConditionMet);
            if (ruinsConditionMet && planetConditionMet) picker.add(entry, entry.getPickerWeight());
        }

        return picker.pick();
    }

    public StoryEntry getSpecificStory(String id){
        for (StoryEntry e : FileLoader.fetchStories()) if (e.getId().equals(id)) return e;
        return null;
    }

    public void setStoryRead(StoryEntry entry){
        toldStories.add(entry.getId());
    }
}
