package livetest;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/**
 * Test-only. When Console Commands is enabled, makes F10 alone open the console.
 * The console's default key is Ctrl+Backspace, and background key messages cannot hold Ctrl.
 */
public class LiveTestPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() {
        setConsoleKey();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        setConsoleKey();
    }

    private static void setConsoleKey() {
        // Kept in its own class so this plugin still loads when Console Commands is not installed.
        if (Global.getSettings().getModManager().isModEnabled("lw_console")) {
            ConsoleKey.setF10();
        }
    }
}
