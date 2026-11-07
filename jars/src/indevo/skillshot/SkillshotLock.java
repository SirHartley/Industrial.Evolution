package indevo.skillshot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

public class SkillshotLock {

    public static boolean isHeldByOtherMod() {
        Object owner = getMemory().get(SkillshotSettings.LOCK_KEY);
        return owner != null && !SkillshotSettings.LOCK_OWNER.equals(owner);
    }

    public static void acquire() {
        getMemory().set(SkillshotSettings.LOCK_KEY, SkillshotSettings.LOCK_OWNER);
    }

    public static void release() {
        if (SkillshotSettings.LOCK_OWNER.equals(getMemory().get(SkillshotSettings.LOCK_KEY))) {
            getMemory().unset(SkillshotSettings.LOCK_KEY);
        }
    }

    // nothing aims during a load, so a key found then is stale, whichever mod left it
    public static void clear() {
        getMemory().unset(SkillshotSettings.LOCK_KEY);
    }

    private static MemoryAPI getMemory() {
        return Global.getSector().getMemoryWithoutUpdate();
    }
}
