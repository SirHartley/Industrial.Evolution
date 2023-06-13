package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import indevo.utils.ModPlugin;
import indevo.utils.memory.SessionTransientMemory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class PetDataRepo {

    private static final String CSV_PATH = "data/campaign/pets.csv";

    public static Collection<PetData> getAll() {
        return getCSVSetFromMemory().values();
    }

    public static PetData get(String id) {
        return getCSVSetFromMemory().get(id);
    }

    private static Map<String, PetData> getCSVSetFromMemory() {
        String idString = "$" + CSV_PATH;

        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();

        if (transientMemory.contains(idString)) {
            return (Map<String, PetData>) transientMemory.get(idString);
        } else {
            Map<String, PetData> dataMap = new LinkedHashMap<>();

            try {
                JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", CSV_PATH, "IndEvo");

                for (int i = 0; i < config.length(); i++) {
                    JSONObject row = config.getJSONObject(i);

                    String id = row.getString("id");
                    String name = row.getString("name");
                    float value = row.getInt("value");
                    float maxLife = row.getInt("maxlife");
                    List<String> tags = new ArrayList<>(Arrays.asList(row.getString("tags").replaceAll("\\s", "").split(",")));
                    String desc = row.getString("desc");
                    String natDeath = row.getString("natural_death");
                    String icon = row.getString("icon");
                    String hullmod = row.getString("hullmod");
                    List<String> foodCommodityIDs = new LinkedList<>(Arrays.asList(row.getString("foodCommodities").replaceAll("\\s", "").split(",")));
                    float foodPerMonth = row.getInt("foodPerMonth");
                    float rarity = (float) row.getDouble("rarity");

                    dataMap.put(id, new PetData(id, name, value, maxLife, tags, desc, natDeath, icon, hullmod, foodCommodityIDs, foodPerMonth, rarity));
                    ModPlugin.log("loading " + id);
                }
            } catch (IOException | JSONException ex) {
                Global.getLogger(PetDataRepo.class).error(ex);
            }

            transientMemory.set(idString, dataMap);
            return dataMap;
        }
    }
}
