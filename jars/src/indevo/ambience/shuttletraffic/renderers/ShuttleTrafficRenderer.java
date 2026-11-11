package indevo.ambience.shuttletraffic.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.ShuttleTrail;
import indevo.ambience.shuttletraffic.listeners.EscapePodListener;
import indevo.config.Setting;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.EnumSet;
import java.util.List;

public class ShuttleTrafficRenderer implements LunaCampaignRenderingPlugin {

    // one layer above planets, rings and stations, below fleets
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_7);
    private static final float FADE_ZOOM_START = 2f;
    private static final float FADE_ZOOM_END = 3f;
    private static final float VIEWPORT_MARGIN = 300f;

    private final ShuttleTraffic traffic = new ShuttleTraffic();
    private final SpriteAPI glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

    public static void register() {
        if (LunaCampaignRenderer.hasRendererOfClass(ShuttleTrafficRenderer.class)) return;

        ShuttleHullPool.reset();
        ShuttleTrafficRenderer renderer = new ShuttleTrafficRenderer();
        LunaCampaignRenderer.addTransientRenderer(renderer);
        Global.getSector().addTransientListener(new EscapePodListener(renderer.traffic));
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
        if (traffic.getShuttles().isEmpty() && traffic.getPods().isEmpty()) return;

        float zoom = viewport.getViewMult();
        float fade = Math.max(0f, Math.min(1f, (zoom - FADE_ZOOM_START) / (FADE_ZOOM_END - FADE_ZOOM_START)));
        float alphaMult = viewport.getAlphaMult() * (1f - fade);
        if (alphaMult <= 0.01f) return;

        render(traffic.getShuttles(), viewport, alphaMult);
        render(traffic.getPods(), viewport, alphaMult);
    }

    public ShuttleTraffic getTraffic() {
        return traffic;
    }

    private void render(List<Shuttle> list, ViewportAPI viewport, float alphaMult) {
        ShuttleTrail.beginRender();
        for (Shuttle shuttle : list) {
            if (viewport.isNearViewport(shuttle.pos, VIEWPORT_MARGIN)) shuttle.renderTrail(alphaMult);
        }
        ShuttleTrail.endRender();

        for (Shuttle shuttle : list) {
            if (viewport.isNearViewport(shuttle.pos, VIEWPORT_MARGIN)) shuttle.renderSprite(alphaMult, glow);
        }
    }
}
