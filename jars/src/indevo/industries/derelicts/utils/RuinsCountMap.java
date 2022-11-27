package indevo.industries.derelicts.utils;

import com.fs.starfarer.api.Global;

import java.util.HashMap;
import java.util.Map;

@Deprecated
public class RuinsCountMap {

    public static final String MAP_KEY = "$IndEvo_ruinsMapKey";

    public static Map<String, Integer> getCountMap(){
        Map<String, Integer> count;
        Map<String, Object> mem = Global.getSector().getPersistentData();

        if (mem.containsKey(MAP_KEY)) count = ( Map<String, Integer>) mem.get(MAP_KEY);
        else {
            count = new HashMap<>();
            mem.put(MAP_KEY, count);
        }

        return count;
    }

    public static void addOrIncrement(String id) {
        Map<String, Integer> map = getCountMap();

        if (!map.containsKey(id)) map.put(id, 1);
        else map.put(id, map.get(id) + 1);
    }

    public static int getCount(String id){
        Map<String, Integer> map = getCountMap();
        return map.containsKey(id) ? map.get(id) : 0;
    }
}
