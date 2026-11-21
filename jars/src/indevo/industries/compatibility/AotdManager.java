package indevo.industries.compatibility;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import data.kaysaar.aotd.vok.scripts.research.AoTDMainResearchManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// the only ie class that touches ashes of the domain; vok types stay in the nested class behind isVokEnabled
public class AotdManager {

    public static final String MOD_ID = "IndEvo";
    public static final String VOK_ID = "aotd_vok";
    public static final String TOT_ID = "aotd_theory_of_toolbox";
    public static final String OBJECTIVE_TAG = "aotd_objective";
    public static final String TENEBRIAN_NANOFORGE = "aotd_shrouded_nanoforge";
    public static final String TECH_OPTIONS = "data/campaign/aotd_tech_options.csv";
    public static final String INDUSTRY_REWARD = "industry";

    private static Map<String, List<String>> nodesByIndustry = null;
    private static final Map<String, String> nodeNames = new HashMap<>();

    public static boolean isVokEnabled() {
        return Global.getSettings().getModManager().isModEnabled(VOK_ID);
    }

    public static boolean isTotEnabled() {
        return Global.getSettings().getModManager().isModEnabled(TOT_ID);
    }

    public static boolean isResearched(Industry ind) {
        return ind == null || isResearched(ind.getId(), ind.getMarket());
    }

    // true without vok, outside the campaign, on markets the player does not own, or when no ie node rewards this id
    public static boolean isResearched(String industryId, MarketAPI market) {
        if (!isVokEnabled() || Global.getCurrentState() != GameState.CAMPAIGN || market == null || !market.isPlayerOwned()) return true;

        List<String> nodes = getNodes(industryId);
        return nodes.isEmpty() || Vok.isAnyAvailable(nodes, market);
    }

    public static String getMissingResearchReason(Industry ind) {
        return ind == null ? null : getMissingResearchReason(ind.getId(), ind.getMarket());
    }

    public static String getMissingResearchReason(String industryId, MarketAPI market) {
        if (isResearched(industryId, market)) return null;
        return "Requires research: " + nodeNames.get(getNodes(industryId).get(0));
    }

    public static boolean isObjective(SectorEntityToken entity) {
        return entity.hasTag(Tags.OBJECTIVE) || entity.hasTag(OBJECTIVE_TAG);
    }

    public static List<SectorEntityToken> getObjectives(LocationAPI location) {
        Set<SectorEntityToken> objectives = new LinkedHashSet<>(location.getEntitiesWithTag(Tags.OBJECTIVE));
        objectives.addAll(location.getEntitiesWithTag(OBJECTIVE_TAG));
        return new ArrayList<>(objectives);
    }

    private static List<String> getNodes(String industryId) {
        if (nodesByIndustry == null) loadNodes();
        List<String> nodes = nodesByIndustry.get(industryId);
        return nodes == null ? new ArrayList<>() : nodes;
    }

    // ie rows only, nodes researched from start gate nothing
    private static void loadNodes() {
        Map<String, List<String>> map = new HashMap<>();

        try {
            JSONArray rows = Global.getSettings().loadCSV(TECH_OPTIONS, MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("id", "");
                if (id.isEmpty() || id.startsWith("#") || row.optBoolean("isResearchedFromStart", false)) continue;

                nodeNames.put(id, row.optString("name", id));

                for (String reward : row.optString("rewards", "").split("\n")) {
                    String[] split = reward.split(":", 2);
                    if (split.length != 2 || !INDUSTRY_REWARD.equals(split[1].trim().toLowerCase())) continue;
                    map.computeIfAbsent(split[0].trim(), k -> new ArrayList<>()).add(id);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not load " + TECH_OPTIONS, e);
        }

        nodesByIndustry = map;
    }

    private static class Vok {
        static boolean isAnyAvailable(List<String> nodes, MarketAPI market) {
            AoTDMainResearchManager manager = AoTDMainResearchManager.loadData();
            if (manager == null || manager.getSpecificFactionManager(market.getFaction()) == null) return true;

            for (String node : nodes) {
                if (manager.isAvailableForThisMarket(node, market)) return true;
            }

            return false;
        }
    }
}
