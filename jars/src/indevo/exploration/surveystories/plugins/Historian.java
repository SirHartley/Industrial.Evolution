package indevo.exploration.surveystories.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.exploration.surveystories.OldStoryLogger;
import indevo.exploration.surveystories.memory.FileLoader;
import indevo.exploration.surveystories.memory.StoryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Historian {
    private final static String HISTORIAN_MEM_ID = "$indEvo_Historian";
    private final static String FIXED_STORY_MEM_ID = "$indEvo_fixedStoryId";

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

        StarSystemAPI s = planet.getStarSystem();

        if (s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                || s.hasTag(Tags.THEME_HIDDEN)
                || s.hasTag(Tags.THEME_SPECIAL)
                || s.hasTag(Tags.SYSTEM_ABYSSAL)
                || planet.hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) return null;

        WeightedRandomPicker<StoryEntry> picker = new WeightedRandomPicker<StoryEntry>(random);

        for (StoryEntry entry : FileLoader.fetchStories()){
            if (toldStories.contains(entry.getId())) continue;

            boolean ruinsConditionMet = entry.getOptionalRuinsSizes().isEmpty();
            if (!ruinsConditionMet) for (String condID : entry.getOptionalRuinsSizes()) if(planet.getMarket().hasCondition(condID)){
                ruinsConditionMet = true;
                break;
            }

            boolean localConditionsMet = entry.getOptionalRequiredConditions().isEmpty();
            if (!localConditionsMet) for (String condID : entry.getOptionalRequiredConditions()) if(planet.getMarket().hasCondition(condID)){
                localConditionsMet = true;
                break;
            }

            boolean planetConditionMet = entry.getOptionalPlanetTypes().isEmpty() || entry.getOptionalPlanetTypes().contains(planet.getTypeId());

            OldStoryLogger.logDevInfo("Checking Story " + entry.getId() + " ruinsConditionMet: " + ruinsConditionMet + " planetConditionMet: " + planetConditionMet);
            if ((ruinsConditionMet && planetConditionMet && localConditionsMet) || Global.getSettings().isDevMode()) picker.add(entry, entry.getPickerWeight());
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
