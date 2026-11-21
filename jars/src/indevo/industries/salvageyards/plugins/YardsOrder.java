package indevo.industries.salvageyards.plugins;

import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// the contract being put together, lives while the dialog is open; registry amounts are requests, YardsContract caps them
public class YardsOrder {
    public final Map<String, Integer> hulls = new LinkedHashMap<>();
    public final Map<String, Integer> registryHulls = new LinkedHashMap<>();
    public final Map<String, Integer> weapons = new LinkedHashMap<>();
    public final Map<String, Integer> wings = new LinkedHashMap<>();
    public final List<FleetMemberAPI> scrapped = new ArrayList<>();
    public int parts = 0;
    public int relics = 0;

    public boolean isEmpty() {
        return hulls.isEmpty() && registryHulls.isEmpty() && weapons.isEmpty() && wings.isEmpty();
    }

    public int getLineCount() {
        return hulls.size() + registryHulls.size() + weapons.size() + wings.size();
    }

    public void clear() {
        hulls.clear();
        registryHulls.clear();
        weapons.clear();
        wings.clear();
        scrapped.clear();
        parts = 0;
        relics = 0;
    }

    public static void add(Map<String, Integer> map, String id, int amount) {
        int n = map.getOrDefault(id, 0) + amount;
        if (n <= 0) map.remove(id);
        else map.put(id, n);
    }
}
