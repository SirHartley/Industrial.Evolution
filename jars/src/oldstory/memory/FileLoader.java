package oldstory.memory;

import com.fs.starfarer.api.Global;
import oldstory.OldStoryLogger;
import oldstory.plugins.Historian;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sound.H;

import java.io.IOException;
import java.util.*;

public class FileLoader {

    /**
     * Stores generated Set as in transient memory and will attempt to retrieve it before generating a new one
     */

    //csv stuff
    public static Set<StoryEntry> fetchStories() {
        String path = Global.getSettings().getString("oldstory_csv_path");
        String idString = "$" + path;

        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();

        if (transientMemory.contains(idString)) {
            return (Set<StoryEntry>) transientMemory.getSet(idString);
        } else {
            Set<StoryEntry> stories = new LinkedHashSet<>();

            try {
                JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, Historian.MOD_ID);
                for (int i = 0; i < config.length(); i++) {

                    //optional_planet_type; optional_ruins_size; picker_weight; comms_intro_text; story_part_1; story_part_2; story_part_3
                    JSONObject row = config.getJSONObject(i);
                    String id = row.getString("id");
                    float weight = row.getString("picker_weight").isEmpty() ? 0f : (float) row.getDouble("picker_weight");
                    String commsIntro = row.getString("comms_intro_text");

                    List<String> planetTypes = row.getString("optional_planet_types").isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(row.getString("optional_planet_types").replaceAll("\\s", "").split(";")));
                    List<String> ruinsSizes = row.getString("optional_ruins_sizes").isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(row.getString("optional_ruins_sizes").replaceAll("\\s", "").split(";")));
                    List<String> storyParts = new LinkedList<>();

                    for (int j = 1; j < 4; j++) {
                        String s = row.getString("story_part_" + j);
                        if (!s.isEmpty()) storyParts.add(s);
                    }

                    String commsOutro = row.getString("comms_outro_text");

                    StoryEntry entry = new StoryEntry(
                            id,
                            planetTypes,
                            ruinsSizes,
                            weight,
                            commsIntro,
                            storyParts,
                            commsOutro
                    );

                    OldStoryLogger.logDevInfo("loading story <" + id + "> "
                            +"\npickerWeight <" + entry.pickerWeight + "> "
                            +"\ncommsIntroText <" + entry.commsIntroText + "> "
                            +"\ncommsOutroText <" + entry.commsOutroText + "> "
                            +"\noptionalPlanetTypes <" + entry.optionalPlanetTypes.toString() + "> "
                            +"\noptionalRuinsSizes <" + entry.optionalRuinsSizes.toString() + "> "
                            +"\nentries <" + entry.entries.toString() + "> "
                            );

                    stories.add(entry);
                }

            } catch (IOException | JSONException ex) {
                Global.getLogger(FileLoader.class).error(ex);
            }

            transientMemory.set(idString, stories);
            return stories;
        }
    }
}
