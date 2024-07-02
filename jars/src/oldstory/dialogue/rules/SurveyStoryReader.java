package oldstory.dialogue.rules;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import oldstory.OldStoryLogger;
import oldstory.memory.StoryEntry;
import oldstory.plugins.Historian;

import java.util.List;
import java.util.Map;

public class SurveyStoryReader extends BaseCommandPlugin {

    public static final String LOCAL_STORY_ID = "$oldstory_localStory";
    public static final String LOCAL_STORY_LAST_CHAPTER = "$oldstory_lastChapter";
    public static final String LOCAL_STORY_DONE = "$oldstory_done";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);
        SectorEntityToken planet = dialog.getInteractionTarget();

        switch (action){
            case "HAS_NEXT" : return getLastReadChapter(planet) < getPreppedStory(planet).getEntries().size();
            case "PREP_STORY": return prepStoryEntry(planet);
            case "READ_INTRO" : dialog.getTextPanel().addPara(getPreppedStory(planet).getCommsIntroText()); break;
            case "READ_NEXT" :
                dialog.getTextPanel().addPara(getPreppedStory(planet).getEntry(getLastReadChapter(planet)));
                iterateLastChapter(planet);
                break;
            case "READ_OUTRO" :
                dialog.getTextPanel().addPara(getPreppedStory(planet).getCommsOutroText());
                Historian.getHistorian().setStoryRead(getPreppedStory(planet));
                //todo add a record item to inventory
                break;
        }

        return true;
    }

    public int getLastReadChapter(SectorEntityToken interactionTarget){
        return interactionTarget.getMemoryWithoutUpdate().getInt(LOCAL_STORY_LAST_CHAPTER);
    }

    public void iterateLastChapter(SectorEntityToken interactionTarget){
        MemoryAPI mem = interactionTarget.getMemoryWithoutUpdate();
        mem.set(LOCAL_STORY_LAST_CHAPTER, mem.getInt(LOCAL_STORY_LAST_CHAPTER) + 1, 0);
    }

    public StoryEntry getPreppedStory(SectorEntityToken interactionTarget){
        return Historian.getHistorian().getSpecificStory(interactionTarget.getMemoryWithoutUpdate().getString(LOCAL_STORY_ID));
    }

    public boolean prepStoryEntry(SectorEntityToken interactionTarget){
        if (interactionTarget instanceof PlanetAPI && !interactionTarget.getMarket().getSurveyLevel().equals(MarketAPI.SurveyLevel.FULL)){
            MemoryAPI mem = interactionTarget.getMemoryWithoutUpdate();
            if (mem.contains(LOCAL_STORY_DONE)) return false;

            StoryEntry entry = Historian.getHistorian().getStoryForPlanet((PlanetAPI) interactionTarget);

            if (entry == null) {
                OldStoryLogger.logDevInfo("Could not fetch valid story for planet");
                return false;
            } else OldStoryLogger.logDevInfo("Picked Story: " + entry.getId());


            mem.set(LOCAL_STORY_ID, entry.getId(),0);
            mem.set(LOCAL_STORY_LAST_CHAPTER, 0, 0);
            mem.set(LOCAL_STORY_DONE, true);
            return true;
        }

        OldStoryLogger.logDevInfo("Docked entity is not a planet, aborting");
        return false;
    }
}
