package indevo.ambience.shuttletraffic.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.ShuttleTrail;
import indevo.ambience.shuttletraffic.dogfights.Dogfighter;
import indevo.ambience.shuttletraffic.dogfights.ShuttleDogfight;
import indevo.ambience.shuttletraffic.listeners.ShuttleCampaignListener;
import indevo.ambience.shuttletraffic.listeners.ShuttleColonyListener;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.EnumSet;
import java.util.List;

public class ShuttleTrafficRenderer implements LunaCampaignRenderingPlugin {

    // one layer above planets, rings and stations, below fleets
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_7);
    // past this view mult craft keep their screen size
    private static final float HOLD_SIZE_ZOOM = 2f;
    private static final float MIN_TRAIL_PX = 1.5f;
    private static final float VIEWPORT_MARGIN = 300f;

    private final ShuttleTraffic traffic = new ShuttleTraffic();
    private final SpriteAPI glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(ShuttleTrafficRenderer.class)) return;

        ShuttleHullPool.reset();
        ShuttleTrafficRenderer renderer = new ShuttleTrafficRenderer();
        LunaCampaignRenderer.addTransientRenderer(renderer);
        Global.getSector().addTransientListener(new ShuttleCampaignListener(renderer.traffic));
        Global.getSector().getListenerManager().addListener(new ShuttleColonyListener(renderer.traffic), true);
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) return;

        traffic.advance(amount, Global.getSector().getPlayerFleet(), Setting.SHUTTLE_TRAFFIC.intValue());
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (traffic.getLocation() == null || traffic.getLocation() != Global.getSector().getCurrentLocation()) return;
        if (traffic.getShuttles().isEmpty() && traffic.getPods().isEmpty() && traffic.getDogfights().isEmpty()) return;

        float alphaMult = viewport.getAlphaMult();
        if (alphaMult <= 0.01f) return;

        // view mult is su per screen pixel
        float zoom = viewport.getViewMult();
        float sizeMult = Math.max(1f, zoom / HOLD_SIZE_ZOOM);
        float minTrail = MIN_TRAIL_PX * zoom;
        render(traffic.getShuttles(), viewport, alphaMult, minTrail, sizeMult);
        render(traffic.getPods(), viewport, alphaMult, minTrail, sizeMult);
        for (ShuttleDogfight fight : traffic.getDogfights().getFights()) render(fight, viewport, alphaMult, minTrail, sizeMult);
    }

    public ShuttleTraffic getTraffic() {
        return traffic;
    }

    private void render(ShuttleDogfight fight, ViewportAPI viewport, float alphaMult, float minTrail, float sizeMult) {
        List<Dogfighter> fighters = fight.getFighters();
        ShuttleTrail.beginRender();
        for (Dogfighter fighter : fighters) {
            if (viewport.isNearViewport(fighter.pos, VIEWPORT_MARGIN)) fighter.renderTrail(alphaMult, minTrail, sizeMult);
        }
        ShuttleTrail.endRender();

        for (Dogfighter fighter : fighters) {
            if (viewport.isNearViewport(fighter.pos, VIEWPORT_MARGIN)) fighter.render(alphaMult, glow, sizeMult);
        }

        fight.getProjectiles().render(alphaMult);
    }

    private void render(List<Shuttle> list, ViewportAPI viewport, float alphaMult, float minTrail, float sizeMult) {
        ShuttleTrail.beginRender();
        for (Shuttle shuttle : list) {
            if (viewport.isNearViewport(shuttle.pos, VIEWPORT_MARGIN)) shuttle.renderTrail(alphaMult, minTrail, sizeMult);
        }
        ShuttleTrail.endRender();

        for (Shuttle shuttle : list) {
            if (viewport.isNearViewport(shuttle.pos, VIEWPORT_MARGIN)) shuttle.renderSprite(alphaMult, glow, sizeMult);
        }
    }
}
