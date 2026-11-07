package indevo.skillshot.input;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import indevo.skillshot.SkillshotFramework;
import indevo.skillshot.SkillshotLock;
import indevo.skillshot.ability.SkillshotAbility;

public class SkillshotActivationManager implements EveryFrameScript {

    protected SkillshotInputListener currentListener = null;
    protected SkillshotAbility pendingClick = null;

    public static SkillshotActivationManager getInstanceOrRegister() {
        for (EveryFrameScript s : Global.getSector().getTransientScripts()) {
            if (s instanceof SkillshotActivationManager manager) return manager;
        }

        SkillshotActivationManager manager = new SkillshotActivationManager();
        Global.getSector().addTransientScript(manager);
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
        if (currentListener != null && !currentListener.isActive()) {
            currentListener = null;
            SkillshotLock.release();
        }

        if (pendingClick == null) return;

        SkillshotAbility ability = pendingClick;
        pendingClick = null;

        if (!hasActiveSession()) new OnClickSkillshotListener(ability).activate();
    }

    // started from advance, a listener added inside pressButton eats the bar click that opened it
    public void queueClickSession(SkillshotAbility ability) {
        pendingClick = ability;
    }

    public boolean hasActiveListener() {
        return currentListener != null && currentListener.isActive();
    }

    public boolean hasActiveSession() {
        return hasActiveListener() || SkillshotLock.isHeldByOtherMod();
    }

    public SkillshotInputListener getCurrentListener() {
        return currentListener;
    }

    public void setCurrentListener(SkillshotInputListener listener) {
        SkillshotFramework.log("Setting skillshot listener: " + listener.getClass().getName()
                + ", current: " + (currentListener != null ? currentListener.getClass().getName() : "null"));

        currentListener = listener;
        SkillshotLock.acquire();
    }

    public void reset() {
        pendingClick = null;

        if (currentListener != null) currentListener.reset();
        currentListener = null;

        SkillshotLock.release();
    }
}
