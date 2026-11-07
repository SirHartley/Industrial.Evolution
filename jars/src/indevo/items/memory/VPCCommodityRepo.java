package indevo.items.memory;

import com.fs.starfarer.api.Global;
import indevo.ids.Ids;
import indevo.utils.memory.SessionTransientMemory;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class VPCCommodityRepo {

    public enum Pool {
        COMMON,
        UNCOMMON,
        RARE
    }

    public static final Logger log = Global.getLogger(VPCCommodityRepo.class);

    private static final String COMMODITY_CSV = "data/config/indEvo/vpc_commodities.csv";
    private static final String PACKAGE_CSV = "data/config/indEvo/vpc_packages.csv";
    private static final String COMMODITY_KEY = "$IndEvo_vpcCommodities";
    private static final String PACKAGE_KEY = "$IndEvo_vpcPackages";
    private static final String[] DEPOSIT_COLUMNS = {Ids.ADASSEM, Ids.ADMANUF, Ids.COMFORGE};

    public record Commodity(String id, Pool pool, int supply, Map<String, Integer> demand,
                            Map<String, Integer> deposits, String name, String icon, String desc) {

        public int getDeposit(String industryId) {
            return deposits.getOrDefault(industryId, 0);
        }
    }

    public record Package(String id, Set<String> commodities, String name, String icon, String desc) {
    }

    public static Collection<Commodity> getAll() {
        return getCommodities().values();
    }

    public static Commodity get(String commodityId) {
        return getCommodities().get(commodityId);
    }

    public static Package getPackage(Collection<String> commodityIds) {
        Set<String> set = new HashSet<>(commodityIds);
        for (Package p : getPackages()) {
            if (p.commodities().equals(set)) return p;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Commodity> getCommodities() {
        SessionTransientMemory memory = SessionTransientMemory.getInstance();
        if (memory.contains(COMMODITY_KEY)) return (Map<String, Commodity>) memory.get(COMMODITY_KEY);

        Map<String, Commodity> map = new LinkedHashMap<>();
        for (JSONObject row : readRows(COMMODITY_CSV)) {
            try {
                String id = row.getString("id");
                if (Global.getSettings().getCommoditySpec(id) == null) {
                    log.warn("VPC commodity " + id + " does not exist, skipping");
                    continue;
                }

                Map<String, Integer> deposits = new HashMap<>();
                for (String industryId : DEPOSIT_COLUMNS) deposits.put(industryId, row.optInt(industryId, 0));

                String icon = row.optString("icon", "");
                loadTexture(icon);

                map.put(id, new Commodity(id,
                        Pool.valueOf(row.getString("pool").trim().toUpperCase()),
                        row.getInt("supply"),
                        parseDemand(id, row.optString("demand", "")),
                        deposits,
                        row.optString("name", ""),
                        icon,
                        row.optString("desc", "")));
            } catch (JSONException | IllegalArgumentException e) {
                log.warn("Skipping invalid VPC commodity row " + row, e);
            }
        }

        memory.set(COMMODITY_KEY, map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Package> getPackages() {
        SessionTransientMemory memory = SessionTransientMemory.getInstance();
        if (memory.contains(PACKAGE_KEY)) return (List<Package>) memory.get(PACKAGE_KEY);

        List<Package> list = new ArrayList<>();
        for (JSONObject row : readRows(PACKAGE_CSV)) {
            try {
                Set<String> commodities = new HashSet<>();
                for (String s : row.getString("commodities").split(",")) commodities.add(s.trim());

                String icon = row.optString("icon", "");
                loadTexture(icon);

                list.add(new Package(row.getString("id"), commodities, row.getString("name"), icon, row.optString("desc", "")));
            } catch (JSONException e) {
                log.warn("Skipping invalid VPC package row " + row, e);
            }
        }

        memory.set(PACKAGE_KEY, list);
        return list;
    }

    private static List<JSONObject> readRows(String path) {
        List<JSONObject> rows = new ArrayList<>();
        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "IndEvo");
            for (int i = 0; i < config.length(); i++) {
                JSONObject row = config.getJSONObject(i);
                if (!row.optString("id", "").isEmpty()) rows.add(row);
            }
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }

        return rows;
    }

    private static Map<String, Integer> parseDemand(String id, String demand) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String entry : demand.split(",")) {
            if (entry.isBlank()) continue;

            String[] pair = entry.split(":");
            if (pair.length != 2) throw new IllegalArgumentException("Bad demand entry " + entry);

            String input = pair[0].trim();
            if (Global.getSettings().getCommoditySpec(input) == null) {
                log.warn("VPC commodity " + id + " demands unknown commodity " + input + ", skipping it");
                continue;
            }

            map.put(input, Integer.parseInt(pair[1].trim()));
        }

        return map;
    }

    private static void loadTexture(String path) {
        if (path.isEmpty()) return;
        try {
            Global.getSettings().loadTexture(path);
        } catch (IOException e) {
            log.warn("Could not load VPC icon " + path, e);
        }
    }
}
