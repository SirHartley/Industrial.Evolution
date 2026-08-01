package skillshot;

import com.fs.starfarer.api.Global;
import skillshot.input.OnKeyPressSkillshotListener;
import skillshot.input.SkillshotActivationManager;

/**
 * Entry point. Two calls from your ModPlugin are all the framework needs:
 *
 * <pre>
 * public void onGameLoad(boolean newGame) { SkillshotFramework.register(); }
 * public void beforeGameSave()            { SkillshotFramework.reset(); }
 * </pre>
 *
 * Everything else hangs off abilities tagged {@link SkillshotSettings#TAG_SKILLSHOT}.
 */
public class SkillshotFramework {

    /**
     * Installs the hotkey listener and the activation manager. Idempotent - safe to call on every
     * game load.
     */
    public static void register() {
        OnKeyPressSkillshotListener.getInstanceOrRegister();
        SkillshotActivationManager.getInstanceOrRegister();
    }

    /**
     * Tears down any in-progress targeting session. Call this from beforeGameSave so a half-aimed
     * skillshot is never written into the save.
     */
    public static void reset() {
        OnKeyPressSkillshotListener.getInstanceOrRegister().reset();

        SkillshotActivationManager manager = SkillshotActivationManager.getInstanceOrRegister();
        if (manager.getCurrentListener() != null) manager.getCurrentListener().reset();
    }

    public static void log(String message) {
        if (SkillshotSettings.LOG_DEBUG) Global.getLogger(SkillshotFramework.class).info(message);
    }
}
