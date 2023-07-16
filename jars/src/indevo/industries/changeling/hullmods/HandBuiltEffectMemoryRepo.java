package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.HashMap;
import java.util.Map;

public class HandBuiltEffectMemoryRepo {
    public static final String MEM_REF = "$IndEvo_HandBuiltMemoryRepo";
    private Map<String, HandBuiltEffect> effectMap = new HashMap<>();

    public static HandBuiltEffectMemoryRepo getInstance(){
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        HandBuiltEffectMemoryRepo repo;
        if (memory.contains(MEM_REF)) repo = (HandBuiltEffectMemoryRepo) memory.get(MEM_REF);
        else {
            repo = new HandBuiltEffectMemoryRepo();
            memory.set(MEM_REF, repo);
        }

        return repo;
    }

    public void add(HandBuiltEffect effect){
        effectMap.put(effect.getId(), effect);
    }

    public boolean contains(String id){
        return effectMap.containsKey(id);
    }

    public HandBuiltEffect get(String id){
        return effectMap.get(id);
    }
}
