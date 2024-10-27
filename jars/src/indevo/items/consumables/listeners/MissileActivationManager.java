package indevo.items.consumables.listeners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import indevo.utils.ModPlugin;

public class MissileActivationManager implements EveryFrameScript {

    protected MissileTargetUIKeypressListener currentListener = null;
    protected MissileTargetUIKeypressListener forceDeregisterNextTick = null;

    public static MissileActivationManager getInstanceOrRegister(){
        MissileActivationManager manager = null;

        for (EveryFrameScript s : Global.getSector().getScripts()) {
            if (s instanceof MissileActivationManager) {
                manager = (MissileActivationManager) s;
                break;
            }
        }

        if (manager == null){
            manager = new MissileActivationManager();
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
        if (currentListener != null){
            if (forceDeregisterNextTick != null) {
                Global.getSector().getListenerManager().removeListener(forceDeregisterNextTick);
                forceDeregisterNextTick = null;
            }

            if (!currentListener.isActive()) {
                currentListener = null;
            }
        }
    }

    public boolean hasActiveListener(){
        return currentListener != null && currentListener.isActive();
    }

    public MissileTargetUIKeypressListener getCurrentListener() {
        return currentListener;
    }

    public void setCurrentListener(MissileTargetUIKeypressListener listener){
        ModPlugin.log("Setting new listener: " + listener.getClass().getName() + " current: " + (currentListener != null ? currentListener.getClass().getName() : "null"));
        currentListener = listener;
    }

    public void deregisterListenerOnNextTick(MissileTargetUIKeypressListener listener){
        forceDeregisterNextTick = listener;
    }
}
