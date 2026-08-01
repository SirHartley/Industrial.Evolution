package skillshot.util;

import com.fs.starfarer.api.util.DelayedActionScript;

/**
 * A DelayedActionScript that keeps ticking while the game is paused - needed because skillshot
 * aiming happens with the campaign paused just as often as not.
 */
public abstract class DelayedActionScriptRunWhilePaused extends DelayedActionScript {

    public DelayedActionScriptRunWhilePaused(float daysLeft) {
        super(daysLeft);
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
