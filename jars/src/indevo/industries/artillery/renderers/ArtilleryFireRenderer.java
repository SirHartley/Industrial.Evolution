package indevo.industries.artillery.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArtilleryFireRenderer implements LunaCampaignRenderingPlugin {

    // above the station sprite, below fleets
    private static final EnumSet<CampaignEngineLayers> LAYERS = EnumSet.of(CampaignEngineLayers.TERRAIN_7);
    private static final float VIEWPORT_MARGIN = 400f;

    private final Map<SectorEntityToken, StationFireEffect> effects = new LinkedHashMap<>();
    private final SpriteAPI smoke = Global.getSettings().getSprite("misc", "nebula_particles");
    private final SpriteAPI glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
    private final SpriteAPI line = Global.getSettings().getSprite("fx", "IndEvo_line");

    public static void reportIncoming(SectorEntityToken station, String type, float angle, float secondsToShot) {
        if (station == null || !station.isInCurrentLocation()) return;
        getInstanceOrRegister().getEffect(station, type).incoming(angle, secondsToShot);
    }

    public static void reportShot(SectorEntityToken station, String type, float angle) {
        if (station == null || !station.isInCurrentLocation()) return;
        getInstanceOrRegister().getEffect(station, type).shot(angle);
    }

    // transient, so a loaded game gets a new one with the first shot
    private static ArtilleryFireRenderer getInstanceOrRegister() {
        LunaCampaignRenderingPlugin existing = LunaCampaignRenderer.getRendererOfClass(ArtilleryFireRenderer.class);
        if (existing instanceof ArtilleryFireRenderer renderer) return renderer;

        ArtilleryFireRenderer renderer = new ArtilleryFireRenderer();
        LunaCampaignRenderer.addTransientRenderer(renderer);
        return renderer;
    }

    private StationFireEffect getEffect(SectorEntityToken station, String type) {
        StationFireEffect effect = effects.get(station);
        if (effect == null) {
            effect = new StationFireEffect(station, type);
            effects.put(station, effect);
        }

        return effect;
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
        if (effects.isEmpty() || Global.getSector().isPaused()) return;

        Iterator<StationFireEffect> iterator = effects.values().iterator();
        while (iterator.hasNext()) {
            StationFireEffect effect = iterator.next();
            SectorEntityToken station = effect.getStation();

            if (!station.isAlive() || !station.isInCurrentLocation()) {
                iterator.remove();
                continue;
            }

            effect.advance(amount);
            if (effect.isIdle()) iterator.remove();
        }
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        for (StationFireEffect effect : effects.values()) {
            SectorEntityToken station = effect.getStation();
            if (!station.isInCurrentLocation() || !viewport.isNearViewport(station.getLocation(), VIEWPORT_MARGIN)) continue;

            float alphaMult = viewport.getAlphaMult() * station.getSensorFaderBrightness() * station.getSensorContactFaderBrightness();
            if (alphaMult <= 0f) continue;

            effect.render(alphaMult, smoke, glow, line);
        }
    }
}
