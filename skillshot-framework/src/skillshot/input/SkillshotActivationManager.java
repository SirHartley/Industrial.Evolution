package skillshot.input;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import skillshot.SkillshotFramework;

/**
 * Keeps track of the one targeting session that is allowed to be live, and clears it out once it
 * goes inactive. Runs while paused, because aiming usually happens paused.
 */
public class SkillshotActivationManager implements EveryFrameScript {

    protected SkillshotInputListener currentListener = null;
    protected SkillshotInputListener forceDeregisterNextTick = null;

    public static SkillshotActivationManager getInstanceOrRegister() {
        SkillshotActivationManager manager = null;

        for (EveryFrameScript s : Global.getSector().getScripts()) {
            if (s instanceof SkillshotActivationManager) {
                manager = (SkillshotActivationManager) s;
                break;
            }
        }

        if (manager == null) {
            manager = new SkillshotActivationManager();
            Global.getSector().addScript(manager);
        }

        return manager;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (currentListener == null) return;

        if (forceDeregisterNextTick != null) {
            Global.getSector().getListenerManager().removeListener(forceDeregisterNextTick);
            forceDeregisterNextTick = null;
        }

        if (!currentListener.isActive()) currentListener = null;
    }

    public boolean hasActiveListener() {
        return currentListener != null && currentListener.isActive();
    }

    public SkillshotInputListener getCurrentListener() {
        return currentListener;
    }

    public void setCurrentListener(SkillshotInputListener listener) {
        SkillshotFramework.log("Setting skillshot listener: " + listener.getClass().getName()
                + ", current: " + (currentListener != null ? currentListener.getClass().getName() : "null"));

        currentListener = listener;
    }

    /**
     * Queues a per-session listener for removal from the listener manager. Deferring by a tick keeps
     * us from mutating the listener list while the game is iterating it.
     */
    public void deregisterListenerOnNextTick(SkillshotInputListener listener) {
        forceDeregisterNextTick = listener;
    }
}
