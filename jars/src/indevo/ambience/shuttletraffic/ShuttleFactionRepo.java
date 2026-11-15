package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import indevo.utils.memory.SessionTransientMemory;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// faction profiles from shuttle_factions.csv, read once per session; a faction without a row flies the default row
public final class ShuttleFactionRepo {

    public static final String CSV_PATH = "data/config/indEvo/shuttle_factions.csv";
    private static final String MEMORY_KEY = "$IndEvo_shuttleFactions";
    private static final Logger log = Global.getLogger(ShuttleFactionRepo.class);

    private ShuttleFactionRepo() {
    }

    // null faction (the headless checks) gives the default without reading anything
    public static ShuttleProfile get(FactionAPI faction) {
        if (faction == null) return ShuttleProfile.DEFAULT;

        Map<String, ShuttleProfile> profiles = getAll();
        ShuttleProfile profile = profiles.get(faction.getId());
        return profile != null ? profile : profiles.getOrDefault("default", ShuttleProfile.DEFAULT);
    }

    public static boolean isPirate(FactionAPI faction) {
        return faction != null && get(faction).pirate();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ShuttleProfile> getAll() {
        SessionTransientMemory memory = SessionTransientMemory.getInstance();
        if (memory.contains(MEMORY_KEY)) return (Map<String, ShuttleProfile>) memory.get(MEMORY_KEY);

        Map<String, ShuttleProfile> profiles = load();
        memory.set(MEMORY_KEY, profiles);
        return profiles;
    }

    private static Map<String, ShuttleProfile> load() {
        Map<String, ShuttleProfile> profiles = new HashMap<>();
        JSONArray rows;
        try {
            rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", CSV_PATH, "IndEvo");
        } catch (IOException | JSONException e) {
            log.warn("Could not read " + CSV_PATH, e);
            return profiles;
        }

        JSONObject defaults = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && "default".equals(row.optString("id"))) defaults = row;
        }

        ShuttleProfile base = defaults != null ? read(defaults, ShuttleProfile.DEFAULT) : ShuttleProfile.DEFAULT;
        profiles.put("default", base);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            String id = row.optString("id", "");
            if (id.isEmpty() || id.startsWith("#") || id.equals("default")) continue;
            profiles.put(id, read(row, base));
        }

        return profiles;
    }

    private static ShuttleProfile read(JSONObject row, ShuttleProfile base) {
        return new ShuttleProfile(row.optString("id"), bool(row, "pirate", base.pirate()), bool(row, "dark", base.dark()),
                num(row, "laneDiscipline", base.laneDiscipline()), num(row, "jitter", base.jitter()), num(row, "speedSpread", base.speedSpread()),
                num(row, "weave", base.weave()), num(row, "formation", base.formation()), bool(row, "queue", base.queue()),
                num(row, "lanes", base.lanes()), num(row, "cruises", base.cruises()), num(row, "pickets", base.pickets()), num(row, "convoys", base.convoys()),
                num(row, "speed", base.speed()), num(row, "flicker", base.flicker()));
    }

    // empty cells take the default row
    private static float num(JSONObject row, String key, float fallback) {
        String value = row.optString(key, "").trim();
        if (value.isEmpty()) return fallback;

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            log.warn(CSV_PATH + ": bad " + key + " '" + value + "' for " + row.optString("id"));
            return fallback;
        }
    }

    private static boolean bool(JSONObject row, String key, boolean fallback) {
        String value = row.optString(key, "").trim();
        return value.isEmpty() ? fallback : Boolean.parseBoolean(value);
    }
}
