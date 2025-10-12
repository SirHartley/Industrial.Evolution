package indevo.exploration.meteor.spawners;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import indevo.exploration.meteor.renderers.MeteorSwarmWarningPathRenderer;
import indevo.exploration.meteor.renderers.WarningSignNotificationRenderer;
import indevo.utils.helper.CircularArc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

import java.util.Random;

public abstract class BaseArcingSwarmSpawner implements EveryFrameScript, MeteorSwarmSpawnerAPI {

    public final StarSystemAPI system;
    public final CircularArc arc;
    public final float runtime;
    public final Random random;
    private boolean wasInitialized = false;

    public float timePassed = 0;

    public BaseArcingSwarmSpawner(StarSystemAPI system, CircularArc arc, float runtime, long seed) {
        this.system = system;
        this.runtime = runtime;
        this.random = new Random(seed);
        this.arc = arc;
    }

    @Override
    public void advance(float amount) {
        if (isDone()) return;

        if (!wasInitialized) {
            init();
            wasInitialized = true;
        }

        timePassed += amount;
        advanceSpawner(amount);
    }

    public void init(){
        MeteorSwarmWarningPathRenderer warningRenderer = new MeteorSwarmWarningPathRenderer(system, arc);
        system.addScript(warningRenderer);
        LunaCampaignRenderer.addRenderer(new WarningSignNotificationRenderer(arc, system));
        Global.getSoundPlayer().playUISound("cr_allied_critical", 1, 1);
    };

    @Override
    public boolean isDone() {
        return timePassed > runtime;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
