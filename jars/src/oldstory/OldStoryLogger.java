package oldstory;

import com.fs.starfarer.api.Global;
import oldstory.plugins.Historian;

public class OldStoryLogger {

    private static String getPreface() {
        return "[" + Global.getSettings().getModManager().getModSpec(Historian.MOD_ID).getName() + "] - ";
    }

    public static void logDevInfo(String text) {
        if (Global.getSettings().isDevMode()) Global.getLogger(OldStoryLogger.class).info(getPreface() + text);
    }
}
