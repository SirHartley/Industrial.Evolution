package indevo.utils.helper;

import indevo.config.Setting;

// 4.x api kept for other mods compiled against it, their key constants are inlined
public class Settings {

    public static boolean getBoolean(String id) {
        Setting setting = Setting.find(id);
        return setting != null && setting.enabled();
    }

    public static float getFloat(String id) {
        Setting setting = Setting.find(id);
        return setting == null ? 0f : setting.value();
    }

    public static int getInt(String id) {
        Setting setting = Setting.find(id);
        return setting == null ? 0 : setting.intValue();
    }
}
