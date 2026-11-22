package indevo.industries.derelicts.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.achievements.Achievements;
import indevo.ids.Ids;
import indevo.industries.derelicts.RiftRelocator;
import indevo.industries.derelicts.industry.RiftGenerator;
import indevo.industries.derelicts.listeners.PlanetJumpListener;
import indevo.industries.derelicts.renderers.RiftWarpRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.apache.log4j.Logger;

public class RiftJumpScript implements EveryFrameScript {

    public static final Logger log = Global.getLogger(RiftJumpScript.class);

    public static final float DELAY = 5f;
    private static final float TIMEOUT = 10f;

    private final MarketAPI market;
    private final StarSystemAPI target;
    private final int tearDays;
    private final float radius;
    private float elapsed = 0f;
    private boolean done = false;
    private transient RiftWarpRenderer renderer;

    public RiftJumpScript(MarketAPI market, StarSystemAPI target, int tearDays) {
        this.market = market;
        this.target = target;
        this.tearDays = tearDays;
        this.radius = market.getPlanetEntity().getRadius();
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        elapsed += amount;
        if (elapsed < DELAY) return;

        PlanetAPI planet = market.getPlanetEntity();
        boolean timedOut = elapsed > DELAY + TIMEOUT;

        if (renderer == null) {
            if (timedOut || !planet.isInCurrentLocation()) {
                jump();
                return;
            }

            renderer = new RiftWarpRenderer(planet, radius);
            LunaCampaignRenderer.addTransientRenderer(renderer);
        }

        planet.setRadius(radius * renderer.getPlanetScale());
        if (renderer.isCollapsed() || timedOut) jump();
    }

    private void jump() {
        done = true;

        PlanetAPI planet = market.getPlanetEntity();
        planet.setRadius(radius);

        if (Global.getSector().getEconomy().getMarket(market.getId()) == null) {
            log.warn("rift jump of " + market.getId() + " cancelled, the market left the economy");
            return;
        }

        StarSystemAPI from = market.getStarSystem();
        RiftRelocator.relocate(market, target, tearDays);

        String name = target.getNameWithLowercaseType();
        Global.getSector().getCampaignUI().addMessage(market.getName() + " has jumped to the " + name + ".",
                Misc.getTextColor(), market.getName(), name, Misc.getHighlightColor(), Misc.getHighlightColor());

        for (PlanetJumpListener l : Global.getSector().getListenerManager().getListeners(PlanetJumpListener.class)) {
            l.reportPlanetJump(planet, from, target);
        }

        if (market.getIndustry(Ids.RIFTGEN) instanceof RiftGenerator gen) gen.onJumpDone();
        if (market.isPlayerOwned()) Achievements.complete(Achievements.FENG_SHUI);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
