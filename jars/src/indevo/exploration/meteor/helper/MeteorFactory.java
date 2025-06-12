package indevo.exploration.meteor.helper;

import java.util.HashMap;
import java.util.Map;

public class MeteorFactory {

    private static final Map<Float, String> ASTEROID_SIZE_PATH_MAP = new HashMap<>(){{
        put(0f, "IndEvo_meteor_1");
        put(30f, "IndEvo_meteor_2");
        put(60f, "IndEvo_meteor_3");
        put(100f, "IndEvo_meteor_4");
    }};

    public static String getMeteorForSize(float size) {
        String selectedMeteor = null;
        float closestSize = -1f;

        for (Map.Entry<Float, String> e : ASTEROID_SIZE_PATH_MAP.entrySet()) {
            float entrySize = e.getKey();
            if (size >= entrySize && entrySize > closestSize) {
                closestSize = entrySize;
                selectedMeteor = e.getValue();
            }
        }

        return selectedMeteor;
    }
}
