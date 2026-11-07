package indevo.items.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

public class ItemPayloadMemory {
    public static final String PAYLOAD_MAP_KEY = "$IndEvo_itemPayloads";

    public static String add(Object payload) {
        String uid = Misc.genUID();
        getPayloadMap().put(uid, payload);
        return uid;
    }

    public static void put(String key, Object payload) {
        getPayloadMap().put(key, payload);
    }

    public static <T> T get(String key, Class<T> type) {
        if (key == null) return null;
        Object payload = getPayloadMap().get(key);
        return type.isInstance(payload) ? type.cast(payload) : null;
    }

    public static boolean contains(String key) {
        return key != null && getPayloadMap().containsKey(key);
    }

    public static void remove(String key) {
        if (key != null) getPayloadMap().remove(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPayloadMap() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains(PAYLOAD_MAP_KEY)) mem.set(PAYLOAD_MAP_KEY, new HashMap<String, Object>());
        return (Map<String, Object>) mem.get(PAYLOAD_MAP_KEY);
    }
}
