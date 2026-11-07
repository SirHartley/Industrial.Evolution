package indevo.skillshot;

import com.fs.starfarer.api.Global;
import indevo.skillshot.input.OnKeyPressSkillshotListener;
import indevo.skillshot.input.SkillshotActivationManager;

public class SkillshotFramework {

    public static void register() {
        SkillshotLock.clear();
        OnKeyPressSkillshotListener.getInstanceOrRegister();
        SkillshotActivationManager.getInstanceOrRegister();
    }

    public static void reset() {
        OnKeyPressSkillshotListener.getInstanceOrRegister().reset();
        SkillshotActivationManager.getInstanceOrRegister().reset();
    }

    public static void log(String message) {
        if (SkillshotSettings.LOG_DEBUG) Global.getLogger(SkillshotFramework.class).info(message);
    }
}
