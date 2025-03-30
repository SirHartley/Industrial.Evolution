package indevo.exploration.surveystories;

import com.fs.starfarer.api.Global;

public class OldStoryLogger {

    private static String getPreface() {
        return "[" + Global.getSettings().getModManager().getModSpec("IndEvo").getName() + "] - ";
    }

    public static void logDevInfo(String text) {
        if (Global.getSettings().isDevMode()) Global.getLogger(OldStoryLogger.class).info(getPreface() + text);
    }
}
