package indevo.industries.senate;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import indevo.utils.memory.SessionTransientMemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EdictRepo {
    public static final String EDICT_TAG = "IndEvo_edict";
    private static final String SPECS_KEY = "$IndEvo_EdictSpecs";

    @SuppressWarnings("unchecked")
    public static List<MarketConditionSpecAPI> getAll() {
        SessionTransientMemory mem = SessionTransientMemory.getInstance();
        if (mem.contains(SPECS_KEY)) return (List<MarketConditionSpecAPI>) mem.get(SPECS_KEY);

        List<MarketConditionSpecAPI> specs = new ArrayList<>();
        for (MarketConditionSpecAPI spec : Global.getSettings().getAllMarketConditionSpecs()) {
            if (spec.hasTag(EDICT_TAG)) specs.add(spec);
        }

        specs.sort(Comparator.comparingDouble(MarketConditionSpecAPI::getOrder).thenComparing(MarketConditionSpecAPI::getName));
        mem.set(SPECS_KEY, specs);
        return specs;
    }

    public static boolean isEdict(String conditionId) {
        for (MarketConditionSpecAPI spec : getAll()) {
            if (spec.getId().equals(conditionId)) return true;
        }

        return false;
    }

    public static String getShortName(String name) {
        return name.startsWith("Edict: ") ? name.substring("Edict: ".length()) : name;
    }
}
