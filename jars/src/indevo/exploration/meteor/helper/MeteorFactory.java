package indevo.exploration.meteor.helper;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.MeteorSwarmManager;
import indevo.exploration.meteor.entities.MeteorEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * this is abysmal dogshit
 */

public class MeteorFactory {

    private static final Map<Float, String> ASTEROID_SIZE_PATH_MAP = new HashMap<>(){{
        put(0f, "IndEvo_meteor_1");
        put(30f, "IndEvo_meteor_2");
        put(60f, "IndEvo_meteor_3");
        put(80f, "IndEvo_meteor_4");
    }};

    private static final Map<Float, String> IRRADIOID_SIZE_PATH_MAP = new HashMap<>(){{
        put(0f, "IndEvo_spicy_rock_1");
        put(30f, "IndEvo_spicy_rock_2");
        put(60f, "IndEvo_spicy_rock_3");
        put(80f, "IndEvo_spicy_rock_4");
    }};

    private static final Map<Float, String> ICY_SIZE_PATH_MAP = new HashMap<>(){{
        put(0f, "IndEvo_icy_rock_1");
        put(35f, "IndEvo_icy_rock_2");
        put(60f, "IndEvo_icy_rock_3");
        put(90f, "IndEvo_icy_rock_4");
        put(120f, "IndEvo_icy_rock_5");
        put(150f, "IndEvo_icy_rock_6");
    }};

    public static SectorEntityToken spawn(LocationAPI loc, MeteorEntity.MeteorData data, MeteorSwarmManager.MeteroidShowerType type){
        return loc.addCustomEntity(Misc.genUID(), null, MeteorFactory.getMeteorForSize(data.size, type), Factions.NEUTRAL, data.size * 1.2f, data.size, data.size, data);
    }

    public static String getMeteorForSize(float size, MeteorSwarmManager.MeteroidShowerType type) {
        String selectedMeteor = null;
        float closestSize = -1f;

        Map<Float, String> map = switch (type) {
            case ASTEROID -> ASTEROID_SIZE_PATH_MAP;
            case IRRADIOID -> IRRADIOID_SIZE_PATH_MAP;
            case ICEROID -> ICY_SIZE_PATH_MAP;
            default -> ASTEROID_SIZE_PATH_MAP;
        };

        for (Map.Entry<Float, String> e : map.entrySet()) {
            float entrySize = e.getKey();
            if (size >= entrySize && entrySize > closestSize) {
                closestSize = entrySize;
                selectedMeteor = e.getValue();
            }
        }

        return selectedMeteor;
    }
}
