package indevo.config;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import indevo.abilities.pets.PetManagementAbilityPlugin;
import lunalib.backend.ui.settings.LunaSettingsLoader;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static indevo.config.Setting.*;

public final class PlayerSettings implements LunaSettingsListener {

    public static final String MOD_ID = "IndEvo";
    private static final Logger log = Global.getLogger(PlayerSettings.class);
    private static final List<Pair> PAIRS = List.of(
            new Pair(ARTILLERY_MIN_DELAY_BETWEEN_SHOTS, ARTILLERY_MAX_DELAY_BETWEEN_SHOTS),
            new Pair(ARTILLERY_MIN_COOLDOWN, ARTILLERY_MAX_COOLDOWN),
            new Pair(ARTILLERY_MIN_RANGE, ARTILLERY_MAX_RANGE),
            new Pair(METEOR_MIN_INTENSITY, METEOR_MAX_INTENSITY),
            new Pair(METEOR_MIN_DISTANCE_FROM_SUN, METEOR_MAX_DISTANCE_FROM_SUN),
            new Pair(METEOR_MIN_ANGLE, METEOR_MAX_ANGLE),
            new Pair(MUSEUM_MIN_ADDITIONAL_CREDITS, MUSEUM_MAX_ADDITIONAL_CREDITS));

    private static Map<Setting, Object> current = defaults();
    private static boolean loaded = false;

    private record Pair(Setting min, Setting max) {
    }

    private PlayerSettings() {
    }

    public static void install() {
        if (!LunaSettings.hasSettingsListenerOfClass(PlayerSettings.class)) {
            LunaSettings.addSettingsListener(new PlayerSettings());
        }
        reload();
    }

    public static Object get(Setting setting) {
        if (!loaded) reload();
        return current.get(setting);
    }

    public static Map<Setting, Object> defaults() {
        Map<Setting, Object> result = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) result.put(setting, setting.fallback);
        return result;
    }

    @Override
    public void settingsChanged(String modId) {
        if (!MOD_ID.equals(modId)) return;
        // lunalib logs listener exceptions at debug level only
        try {
            reload();
            // the title screen still holds the previous game's sector
            if (Global.getCurrentState() == GameState.CAMPAIGN) applyCampaignSettings();
        } catch (RuntimeException e) {
            log.error("Could not apply IndEvo settings", e);
        }
    }

    public static void applyCampaignSettings() {
        PetManagementAbilityPlugin.applySetting();
    }

    public static void reload() {
        loaded = true;
        // lunalib loads lazily, any getter forces it
        LunaSettings.getBoolean(MOD_ID, COMMERCE_BALANCE_CHANGES.id);
        CommonDataJSONObject data = LunaSettingsLoader.getSettings().get(MOD_ID);
        if (data == null) {
            log.error("LunaLib has no IndEvo settings, using defaults");
            current = defaults();
            return;
        }

        Map<Setting, Object> resolved = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) resolved.put(setting, setting.sanitize(data.opt(setting.id)));
        applyPairRule(resolved);
        writeBack(data, resolved);
        current = resolved;
        if (Global.getSettings().isDevMode()) log.info("Reloaded IndEvo settings");
    }

    public static void applyPairRule(Map<Setting, Object> values) {
        for (Pair pair : PAIRS) {
            double min = ((Number) values.get(pair.min)).doubleValue();
            if (((Number) values.get(pair.max)).doubleValue() < min) values.put(pair.max, pair.max.sanitize(min));
        }
    }

    private static void writeBack(CommonDataJSONObject data, Map<Setting, Object> resolved) {
        try {
            boolean changed = false;
            for (Setting setting : Setting.values()) {
                Object value = resolved.get(setting);
                if (Setting.same(data.opt(setting.id), value)) continue;
                data.put(setting.id, value);
                changed = true;
            }
            if (changed) data.save();
        } catch (IOException | JSONException e) {
            log.error("Could not save IndEvo settings", e);
        }
    }
}
