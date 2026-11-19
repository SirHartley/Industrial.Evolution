package indevo.industries.petshop.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.memory.SessionTransientMemory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class PetDataRepo {

    public static final String CSV_PATH = "data/campaign/pets.csv";
    public static final String LINES_PATH = "data/strings/pet_lines.csv";
    public static final String TRAITS_PATH = "data/campaign/pet_traits.csv";
    public static final String LOOKS_PATH = "data/campaign/pet_morphs.csv";
    public static final String LOOK_DIR = "graphics/pets/morphs/";
    public static final String WILD_TYPE = "Wild type";
    // saves hold trait ids, never rename one after release
    public static final String TRAIT_PREFIX = "IndEvo_pt_";
    public static final String KIND_STAT = "stat";
    public static final String KIND_FLEET = "fleet";
    public static final String KIND_SIGNATURE = "signature";
    public static final String KIND_MUTATION = "mutation";
    public static final String KIND_FLAW = "flaw";
    public static final String MODE_PERCENT = "percent";
    public static final String MODE_REDUCE = "reduce";
    public static final String MODE_FLAT = "flat";
    public static final String MODE_COUNT = "count";
    // x1/2 at level 1, x1/3 at level 2
    public static final String MODE_DIVIDE = "divide";
    // no levels, the trait is there or not
    public static final String MODE_NONE = "none";
    public static final String FROM_INBREEDING = "inbreeding";
    public static final String ANY = "any";
    public static final String EVENT_AMBIENT = "ambient";
    public static final String EVENT_DOCKING = "docking";
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_PETTING = "petting";
    public static final String EVENT_PETTING_STARVING = "petting_starving";
    public static final String EVENT_NAME = "name";
    public static final String EVENT_PLAY = "play";
    public static final String EVENT_TREAT = "treat";
    public static final String EVENT_SCAVENGER = "scavenger";
    public static final String EVENT_JUMP = "jump";
    public static final String EVENT_FIGHT = "fight";
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
            // the other status lines tell of life aboard a ship
            if (event.equals(EVENT_STATUS) && context.contains(CONTEXT_STORAGE) && !contexts.contains(CONTEXT_STORAGE)) return 0f;
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

    // a look of a species; saves hold its id, never rename one after release
    public record Look(String id, String species, int number, String name, float weight, String icon) {
    }

    // value of grades: perLevel for every full step of per grades, times the pet's talent mult, then the cap
    // with: the flaw ids a mutation arrives with; from: inbreeding when a malformed young can get the flaw
    public record Trait(String id, String name, String kind, String stat, String mode, float perLevel, int per, float cap, float weight,
                        String effect, String desc, String flavour, List<String> with, String from) {

        public float value(int grade, float mult) {
            float v = perLevel * (grade / per) * mult;
            return cap > 0f ? Math.min(cap, v) : v;
        }

        public boolean isTalent() {
            return kind.equals(KIND_STAT) || kind.equals(KIND_FLEET) || kind.equals(KIND_SIGNATURE);
        }

        public boolean isSignature() {
            return kind.equals(KIND_SIGNATURE);
        }

        public boolean isFleet() {
            return kind.equals(KIND_FLEET);
        }

        public boolean isMutation() {
            return kind.equals(KIND_MUTATION);
        }

        public boolean isFlaw() {
            return kind.equals(KIND_FLAW);
        }

        public boolean hasLevels() {
            return !mode.equals(MODE_NONE);
        }

        // +10%, -10% for a reduction, +1 for a flat value, a count in whole steps
        public String format(float v) {
            if (mode.equals(MODE_NONE)) return "";
            if (mode.equals(MODE_COUNT)) return "+" + Math.round(v);
            if (mode.equals(MODE_DIVIDE)) return "x1/" + (1 + Math.round(v));
            String n = Math.abs(v - Math.round(v)) < 0.005f ? String.valueOf(Math.round(v)) : new BigDecimal(String.format(Locale.ROOT, "%.2f", v)).stripTrailingZeros().toPlainString();
            return (mode.equals(MODE_REDUCE) ? "-" : "+") + n + (mode.equals(MODE_FLAT) ? "" : "%");
        }
    }

    public static Collection<PetData> getAll() {
        return getPets().values();
    }

    public static PetData get(String id) {
        return getPets().get(id);
    }

    // null for null, Wild type, or an id no loaded mod has
    public static Look getLook(String id) {
        if (id == null) return null;
        for (PetData data : getAll()) {
            Look look = data.getLook(id);
            if (look != null) return look;
        }
        return null;
    }

    // the cryochamber, pet and look portraits; a sprite the game has not loaded draws nothing
    public static void loadTextures() {
        for (PetData data : getAll()) {
            List<String> icons = new ArrayList<>(List.of(data.icon));
            for (Look look : data.looks) icons.add(look.icon());
            for (String icon : icons) {
                try {
                    Global.getSettings().loadTexture(icon);
                } catch (IOException e) {
                    throw new IllegalStateException("IndEvo pet " + data.id + " portrait " + icon + " failed to load", e);
                }
            }
        }
    }

    public static Map<String, Trait> getTraits() {
        return load(TRAITS_PATH, PetDataRepo::parseTraits);
    }

    // null for an id no loaded mod has, a save can hold traits of a removed mod
    public static Trait getTrait(String id) {
        return getTraits().get(id);
    }

    public static List<Line> getLines() {
        return load(LINES_PATH, PetDataRepo::parseLines);
    }

    // a name for a pet out of a Cryochamber, name rows hold the name as text
    public static String suggestName(PetData data) {
        return suggestName(data, Set.of());
    }

    public static String suggestName(PetData data, Set<String> taken) {
        return pickName(getLines(), data, taken, Misc.random);
    }

    // a name not in taken while one is left, else any
    public static String pickName(List<Line> lines, PetData data, Set<String> taken, Random random) {
        List<Line> free = new ArrayList<>();
        for (Line line : lines) if (line.event().equals(EVENT_NAME) && !taken.contains(line.text())) free.add(line);
        Line line = pickLine(free, EVENT_NAME, data, "", "", Set.of(), random);
        if (line == null) line = pickLine(lines, EVENT_NAME, data, "", "", Set.of(), random);
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
            String pace = row.optString("pace", "").trim();
            Map<String, Float> talents = new HashMap<>();
            for (String token : tokens(row.optString("talents", ""))) {
                String[] lean = token.split(":");
                talents.put(TRAIT_PREFIX + lean[0], lean.length > 1 ? Float.parseFloat(lean[1]) : 1f);
            }

            dataMap.put(id, new PetData(id, name, value, maxLife, tags, desc, natDeath, icon, hullmod, foodCommodityIDs, foodPerMonth, rarity, body, pace, talents));
        }

        return dataMap;
    }

    // the portrait of a look is LOOK_DIR + the species icon file name + _<number>.png
    public static void addLooks(Map<String, PetData> pets, JSONArray rows) throws JSONException {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);

            String id = row.optString("id", "").trim();
            if (id.isEmpty() || id.startsWith("#")) continue;

            String species = row.getString("species").trim();
            PetData data = pets.get(species);
            if (data == null) continue;

            int number = row.getInt("number");
            String weight = row.optString("weight", "").trim();
            String file = data.icon.substring(data.icon.lastIndexOf('/') + 1).replace(".png", "");
            data.looks.add(new Look(id, species, number, row.getString("name").trim(), weight.isEmpty() ? 1f : Float.parseFloat(weight),
                    LOOK_DIR + file + "_" + number + ".png"));
        }
    }

    public static Map<String, Trait> parseTraits(JSONArray rows) throws JSONException {
        Map<String, Trait> traits = new LinkedHashMap<>();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);

            String id = row.optString("id", "").trim();
            if (id.isEmpty() || id.startsWith("#")) continue;

            String per = row.optString("per", "").trim();
            String cap = row.optString("cap", "").trim();
            List<String> with = new ArrayList<>();
            for (String flaw : tokens(row.optString("with", ""))) with.add(TRAIT_PREFIX + flaw);
            traits.put(id, new Trait(id,
                    row.getString("name").trim(),
                    row.getString("kind").trim(),
                    row.optString("stat", "").trim(),
                    row.getString("mode").trim(),
                    (float) row.getDouble("perLevel"),
                    per.isEmpty() ? 1 : Integer.parseInt(per),
                    cap.isEmpty() ? 0f : Float.parseFloat(cap),
                    (float) row.getDouble("weight"),
                    row.getString("effect").trim(),
                    row.getString("desc").trim(),
                    row.optString("flavour", "").trim(),
                    with,
                    row.optString("from", "").trim()));
        }

        return traits;
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
        return load(CSV_PATH, rows -> {
            Map<String, PetData> pets = parsePets(rows);
            addLooks(pets, read(LOOKS_PATH));
            return pets;
        });
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
            T data = parser.parse(read(path));
            transientMemory.set(key, data);
            return data;
        } catch (JSONException ex) {
            throw new IllegalStateException("IndEvo failed to load " + path, ex);
        }
    }

    private static JSONArray read(String path) {
        try {
            return Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "IndEvo");
        } catch (IOException | JSONException ex) {
            throw new IllegalStateException("IndEvo failed to load " + path, ex);
        }
    }
}
