package livetest;

import org.lazywizard.console.ConsoleSettings;
import org.lazywizard.console.overlay.v2.settings.ConsoleV2Settings;
import org.lwjgl.input.Keyboard;

/**
 * Console Commands 4.x has two consoles. The new one (the default) reads its key from LunaLib settings
 * and otherwise keeps a built-in Ctrl+Backspace; the legacy one reads "consoleKeystroke" from
 * saves/common/config/lw_console_settings.json.data. Setting the new console's key in memory covers the default.
 */
final class ConsoleKey {
    private ConsoleKey() {
    }

    static void setF10() {
        ConsoleV2Settings.INSTANCE.setConsoleKeybind(
                new ConsoleSettings.Keystroke(Keyboard.KEY_F10, false, false, false));
    }
}
