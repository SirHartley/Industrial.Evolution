package indevo.items.consumables.scripts;

import com.fs.starfarer.api.util.DelayedActionScript;

public abstract class DelayedActionScriptRunWhilePaused extends DelayedActionScript {

    public DelayedActionScriptRunWhilePaused(float daysLeft) {
        super(daysLeft);
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
