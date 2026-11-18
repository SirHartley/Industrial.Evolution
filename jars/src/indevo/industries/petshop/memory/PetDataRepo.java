package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.memory.SessionTransientMemory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class PetDataRepo {

    public static final String CSV_PATH = "data/campaign/pets.csv";
    public static final String LINES_PATH = "data/strings/pet_lines.csv";
    public static final String ANY = "any";
    public static final String EVENT_AMBIENT = "ambient";
    public static final String EVENT_DOCKING = "docking";
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_PETTING = "petting";
    public static final String EVENT_PETTING_STARVING = "petting_starving";
    public static final String EVENT_NAME = "name";
    public static final String CONTEXT_DOCKED = "docked";
    public static final String CONTEXT_STORAGE = "storage";
    public static final String CONTEXT_OLD = "old";
    public static final String CONTEXT_HYPERSPACE = "hyperspace";
    public static final float WEIGHT_PET = 4f;
    public static final float WEIGHT_BODY = 2f;
    public static final float WEIGHT_CLASS = 1.5f;
    public static final float WEIGHT_ANY = 1f;

    public record Line(String id, String event, Set<String> match, Set<String> moods, Set<String> dispositions, Set<String> contexts, String text) {

        public float weight(PetData data, String mood, String disposition, Set<String> context) {
            if (!fits(moods, mood) || !fits(dispositions, disposition)) return 0f;
            if (!contexts.isEmpty() && Collections.disjoint(contexts, context)) return 0f;
            if (match.isEmpty()) return WEIGHT_ANY;

            float weight = 0f;
            for (String token : match) {
                if (token.equals(data.id)) weight = Math.max(weight, WEIGHT_PET);
                else if (token.equals(data.body)) weight = Math.max(weight, WEIGHT_BODY);
                else if (token.equals(data.getClassTag())) weight = Math.max(weight, WEIGHT_CLASS);
            }

            return weight;
        }

        private static boolean fits(Set<String> allowed, String value) {
            return allowed.isEmpty() || allowed.contains(value);
        }
    }

    public static Collection<PetData> getAll() {
        return getPets().values();
    }

    public static PetData get(String id) {
        return getPets().get(id);
    }

    public static List<Line> getLines() {
        return load(LINES_PATH, PetDataRepo::parseLines);
    }

    // a name for a pet out of a Cryochamber, name rows hold the name as text
    public static String suggestName(PetData data) {
        Line line = pickLine(getLines(), EVENT_NAME, data, "", "", Set.of(), Misc.random);
        return line != null ? line.text() : data.species;
    }

    public static Line pickLine(List<Line> lines, String event, PetData data, String mood, String disposition, Set<String> context, Random random) {
        List<Line> matching = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0f;

        for (Line line : lines) {
            if (!line.event().equals(event)) continue;

            float weight = line.weight(data, mood, disposition, context);
            if (weight <= 0f) continue;

            matching.add(line);
            weights.add(weight);
            total += weight;
        }

        if (matching.isEmpty()) return null;

        float roll = random.nextFloat() * total;
        for (int i = 0; i < matching.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0f) return matching.get(i);
        }

        return matching.get(matching.size() - 1);
    }

    public static Map<String, PetData> parsePets(JSONArray rows) throws JSONException {
        Map<String, PetData> dataMap = new LinkedHashMap<>();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);

            String id = row.optString("id", "").trim();
            if (id.isEmpty() || id.startsWith("#")) continue;

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
            String body = row.optString("body", "").trim();

            dataMap.put(id, new PetData(id, name, value, maxLife, tags, desc, natDeath, icon, hullmod, foodCommodityIDs, foodPerMonth, rarity, body));
        }

        return dataMap;
    }

    public static List<Line> parseLines(JSONArray rows) throws JSONException {
        List<Line> lines = new ArrayList<>();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);

            String id = row.optString("id", "").trim();
            if (id.isEmpty() || id.startsWith("#")) continue;

            lines.add(new Line(id,
                    row.getString("event").trim(),
                    tokens(row.optString("match", "")),
                    tokens(row.optString("mood", "")),
                    tokens(row.optString("disposition", "")),
                    tokens(row.optString("context", "")),
                    row.getString("text").trim()));
        }

        return lines;
    }

    private static Set<String> tokens(String cell) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : cell.trim().split("[\\s,]+")) {
            if (!token.isEmpty() && !token.equals(ANY)) tokens.add(token);
        }

        return tokens;
    }

    private static Map<String, PetData> getPets() {
        return load(CSV_PATH, PetDataRepo::parsePets);
    }

    private interface Parser<T> {
        T parse(JSONArray rows) throws JSONException;
    }

    @SuppressWarnings("unchecked")
    private static <T> T load(String path, Parser<T> parser) {
        String key = "$" + path;
        SessionTransientMemory transientMemory = SessionTransientMemory.getInstance();
        if (transientMemory.contains(key)) return (T) transientMemory.get(key);

        try {
            T data = parser.parse(Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "IndEvo"));
            transientMemory.set(key, data);
            return data;
        } catch (IOException | JSONException ex) {
            throw new IllegalStateException("IndEvo failed to load " + path, ex);
        }
    }
}
