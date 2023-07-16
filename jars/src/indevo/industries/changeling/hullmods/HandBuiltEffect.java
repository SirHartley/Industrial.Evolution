package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandBuiltEffect {
    private String id;
    private Map<String, Float> effectIds = new HashMap<>();

    public HandBuiltEffect(String fleetMemberId) {
        this.id = fleetMemberId;
    }

    public String getId() {
        return id;
    }

    public void add(String id, float amt){
        effectIds.put(id, amt);
    }

    public List<Pair<SimpleHullmodEffectPlugin, Float>> getPluginsWithEffectAmounts(){
        List<Pair<SimpleHullmodEffectPlugin, Float>> pluginPairList = new ArrayList<>();

        for (Map.Entry<String, Float> e : effectIds.entrySet()){
            pluginPairList.add(new Pair<>(SimpleHullmodEffectPluginRepo.HULLMOD_EFFECTS.get(e.getKey()), e.getValue()));
        }

        return pluginPairList;
    }

    public void apply(MutableShipStatsAPI stats){
        for (Map.Entry<String, Float> e : effectIds.entrySet()){
            SimpleHullmodEffectPluginRepo.HULLMOD_EFFECTS.get(e.getKey()).apply(stats, e.getValue());
        }
    }
}
