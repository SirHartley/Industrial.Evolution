package indevo.exploration.distress.events;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.DistressCallIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.TimeoutTracker;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.utils.ModPlugin;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

//done

public class DistressCallManager implements EveryFrameScript {

    private boolean loaded = false;

    public static final String MEMORY_KEY = "indEvo_distressCallManager";
    public static Logger log = Global.getLogger(DistressCallManager.class);
    public static String DISTRESS_EVENT_LIST_PATH = "data/config/indEvo/distress_call_data.csv";

    public static final float DISTRESS_REPEAT_TIMEOUT = 90f;
    public static final float DISTRESS_ALREADY_WAS_NEARBY_TIMEOUT = 90f;
    public static final float DISTRESS_MIN_SINCE_PLAYER_IN_SYSTEM = 90f;
    public static final float DISTRESS_MIN_CHECK_INTERVAL = 14f;
    public static final float DISTRESS_MAX_CHECK_INTERVAL = 35f;
    public static final float DISTRESS_PROB_PER_SYSTEM = 0.1f;
    public static final float DISTRESS_MAX_PROB = 0.2f;

    public Map<String, String> distressCallEvents = new HashMap<>();
    protected IntervalUtil distressCallInterval = new IntervalUtil(DISTRESS_MIN_CHECK_INTERVAL, DISTRESS_MAX_CHECK_INTERVAL);
    protected TimeoutTracker<String> skipForDistressCalls = new TimeoutTracker<>();

    public static final Set<String> DISTRESS_CALL_ALLOWED_THEMES = new HashSet<String>();

    static {
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_MISC);
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_MISC_SKIP);
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_RUINS);
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_REMNANT_SUPPRESSED);
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_REMNANT_DESTROYED);
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_REMNANT_NO_FLEETS);
        DISTRESS_CALL_ALLOWED_THEMES.add(Tags.THEME_DERELICT);
    }

    public static DistressCallManager getInstanceOrRegister() {

        MemoryAPI memory = Global.getSector().getMemory();
        DistressCallManager manager;

        if (memory.contains(MEMORY_KEY)) manager = (DistressCallManager) memory.get(MEMORY_KEY);
        else {
            manager = new DistressCallManager();
            memory.set(MEMORY_KEY, manager);
            Global.getSector().addScript(manager);
        }

        return manager;
    }

    public void loadEvents() {
        try {
            JSONArray spreadsheet = Global.getSettings().getMergedSpreadsheetDataForMod("distress_call_id", DISTRESS_EVENT_LIST_PATH, "IndEvo");

            for (int i = 0; i < spreadsheet.length(); i++) {
                JSONObject row = spreadsheet.getJSONObject(i);

                // get call ID
                String distressCallEventId = row.getString("distress_call_id");

                // get call script
                String distressCallEventScript = row.getString("distress_call_script");

                // create distress call event object
                distressCallEvents.put(distressCallEventId, distressCallEventScript);
                ModPlugin.log("loaded distress call id " + distressCallEventId);

            }
        } catch (IOException | JSONException ex) {
            log.error("distress_call_data.csv loading ended");
        }
        loaded = true;
    }

    // serve starsystems to events as they're spawned
    // call this from your distress call script with the id as an parameter
    public StarSystemAPI getSystem(String eventId) {
        List<StarSystemAPI> systems = Global.getSector().getStarSystems();
        for (StarSystemAPI system : systems) {
            if (system.getMemoryWithoutUpdate().get(eventId) != null && (boolean) system.getMemoryWithoutUpdate().get(eventId)) {
                ModPlugin.log("serving system " + system + " to distress call event " + eventId);
                return system;
            }
        }
        return null;
    }

    @Override
    public void advance(float amount) {
        if (!loaded) loadEvents();
        if (isDone() || Global.getSector().isInFastAdvance() || Global.getSector().getPlayerFleet() == null) return;
        
        float days = Global.getSector().getClock().convertToDays(amount);

        skipForDistressCalls.advance(days);
        distressCallInterval.advance(days);
        
        if (distressCallInterval.intervalElapsed()) spawnDistressCallIfPossible();
    }

    protected void spawnDistressCallIfPossible() {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (!playerFleet.isInHyperspace() || playerFleet.isInHyperspaceTransition()) return;

        WeightedRandomPicker<StarSystemAPI> systems = new WeightedRandomPicker<>();

        ModPlugin.log("scanning systems within " + Global.getSettings().getFloat("distressCallEventRangeLY") + " LY for distress calls");
        OUTER:
        for (StarSystemAPI system : Misc.getNearbyStarSystems(playerFleet, Global.getSettings().getFloat("distressCallEventRangeLY"))) {

            if (skipForDistressCalls.contains(system.getId())) {
                ModPlugin.log("skipping " + system.getId() + " due to it being in this.skipForDistressCalls");
                continue;
            }

            if (system.hasPulsar()) {
                ModPlugin.log("skipping " + system.getId() + " due to it being a pulsar");
                continue;
            }

            float sincePlayerVisit = system.getDaysSinceLastPlayerVisit();
            if (sincePlayerVisit < DISTRESS_MIN_SINCE_PLAYER_IN_SYSTEM) {
                ModPlugin.log("skipping " + system.getId() + " due to the player having been there within the last " + DISTRESS_MIN_SINCE_PLAYER_IN_SYSTEM + " days");
                continue;
            }

            boolean validTheme = false;
            for (String tag : system.getTags()) {
                if (DISTRESS_CALL_ALLOWED_THEMES.contains(tag)) {
                    validTheme = true;
                    break;
                }
            }
            if (!validTheme) {
                ModPlugin.log("skipping " + system.getId() + " due to it having an invalid theme for distress calls");
                continue;
            }

            for (CampaignFleetAPI fleet : system.getFleets()) {
                if (!fleet.getFaction().isHostileTo(Factions.INDEPENDENT)) {
                    ModPlugin.log("skipping " + system.getId() + " due to it having fleets in it that are nonhostile to independent... and skipping back to OUTER:?");
                    continue OUTER;
                }
            }

            if (!Misc.getMarketsInLocation(system).isEmpty()) {
                ModPlugin.log("skipping " + system.getId() + " due to it having markets");
                continue;
            }

            skipForDistressCalls.add(system.getId(), DISTRESS_ALREADY_WAS_NEARBY_TIMEOUT);
            systems.add(system);
        }

        float p = systems.getItems().size() * DISTRESS_PROB_PER_SYSTEM;
        if (p > DISTRESS_MAX_PROB) {
            p = DISTRESS_MAX_PROB;
        }

        float roll = (float) Math.random();
        ModPlugin.log("rolled " + roll + " for distress call, roll-under threshold is " + p);

        if (roll >= p) return;

        StarSystemAPI system = systems.pick();
        ModPlugin.log("picked system " + system + " for distress call");
        if (system == null) return;

        skipForDistressCalls.set(system.getId(), DISTRESS_REPEAT_TIMEOUT);

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String event : distressCallEvents.keySet()) {
            picker.add(event);
        }

        String event = picker.pick();
        system.getMemoryWithoutUpdate().set(event, true, 3f);

        Class<?> scriptClass;
        try {
            scriptClass = (Class<?>) Global.getSettings().getScriptClassLoader().loadClass(distressCallEvents.get(event));
            EveryFrameScript script = (EveryFrameScript) scriptClass.newInstance();
            ModPlugin.log("adding new distress call script " + script);
            Global.getSector().addScript(script);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
            log.error(event + " distress call script failed to load");
        }

        ModPlugin.log("adding new distress call intel in " + system);
        DistressCallIntel intel = new DistressCallIntel(system);
        Global.getSector().getIntelManager().addIntel(intel);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
