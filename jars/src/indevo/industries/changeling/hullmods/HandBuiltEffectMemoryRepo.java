package indevo.industries.changeling.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Every hand-built effect is fully determined by its fleet member id (HandBuiltHullmod.createHullmodEffect
 * seeds its Random with the id's hash), but every member that ever had its stats applied stayed in the save, dead or
 * not. On load (readResolve), entries that are exactly what the current code would create again are dropped; a member
 * still in play gets its entry back the next time its stats are applied, and get() creates a missing entry the same
 * way instead of returning null. Entries that differ from the current code's (made by an older IndEvo with other
 * effects) are kept.
 */
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
        HandBuiltEffect effect = effectMap.get(id);
        if (effect == null && id != null) {
            effect = create(id);
            effectMap.put(id, effect);
        }
        return effect;
    }

    /** What HandBuiltHullmod.createHullmodEffect makes for this member id (without adding it). */
    static HandBuiltEffect create(String id) {
        Random random = new Random(id.hashCode());
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        picker.addAll(SimpleHullmodEffectPluginRepo.HULLMOD_EFFECTS.keySet());

        HandBuiltEffect effect = new HandBuiltEffect(id);

        for (int i = 0; i <= HandBuiltHullmod.EFFECT_COUNT; i++) {
            effect.add(picker.pickAndRemove(), random.nextFloat() * (HandBuiltHullmod.MAX_EFFECT - HandBuiltHullmod.MIN_EFFECT) + HandBuiltHullmod.MIN_EFFECT);
        }

        return effect;
    }

    static boolean sameEffect(HandBuiltEffect a, HandBuiltEffect b) {
        List<Pair<SimpleHullmodEffectPlugin, Float>> la = a.getPluginsWithEffectAmounts(), lb = b.getPluginsWithEffectAmounts();
        if (la.size() != lb.size()) return false;
        Map<SimpleHullmodEffectPlugin, Float> mb = new IdentityHashMap<>();
        for (Pair<SimpleHullmodEffectPlugin, Float> p : lb) mb.put(p.one, p.two);
        if (mb.size() != lb.size()) return false;
        for (Pair<SimpleHullmodEffectPlugin, Float> p : la) {
            if (p.one == null || !mb.containsKey(p.one)) return false;
            Float f = mb.get(p.one);
            if (f == null || p.two == null || Float.floatToIntBits(f) != Float.floatToIntBits(p.two)) return false;
        }
        return true;
    }

    /** Called by XStream after the repo is read from a save. */
    private Object readResolve() {
        if (effectMap == null) {
            effectMap = new HashMap<>();
            return this;
        }
        int before = effectMap.size(), kept = 0;
        try {
            for (Iterator<Map.Entry<String, HandBuiltEffect>> it = effectMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, HandBuiltEffect> e = it.next();
                HandBuiltEffect stored = e.getValue();
                if (e.getKey() != null && stored != null && e.getKey().equals(stored.getId()) && sameEffect(stored, create(e.getKey()))) it.remove();
                else kept++;
            }
            Global.getLogger(HandBuiltEffectMemoryRepo.class).info("Hand-built effects: " + (before - effectMap.size())
                    + " of " + before + " dropped (recreated from the member id when needed), " + kept + " kept");
        } catch (RuntimeException ex) {
            Global.getLogger(HandBuiltEffectMemoryRepo.class).warn("Hand-built effects: pruning stopped", ex);
        }
        return this;
    }
}
