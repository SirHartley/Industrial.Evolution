package indevo.exploration.meteor.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.exploration.meteor.entities.SpicyRockEntity;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

public class RadiationTrailRenderer implements LunaCampaignRenderingPlugin {

    public final float runtime;
    public final float startSize;
    public final float startAlpha;

    public float size;
    public float alpha;
    public Vector2f pos;
    public LocationAPI location;

    public float elapsed = 0f;
    transient protected SpriteAPI glow;

    public RadiationTrailRenderer(float runtime, float size, float alpha, LocationAPI location, Vector2f pos) {
        this.runtime = runtime;
        this.startSize = size;
        this.startAlpha = alpha;
        this.pos = pos;
        this.location = location;

        this.size = size;
        this.alpha = alpha;
    }

    @Override
    public boolean isExpired() {
        return elapsed > runtime;
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;

        float mult = elapsed / runtime;
        alpha = startAlpha * (1 - mult);
        size = startSize * (1 - mult);
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_5);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (!location.isCurrentLocation() || !viewport.isNearViewport(pos, 1000f)) return;
        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float alphaMult = viewport.getAlphaMult() * (alpha);
        if (alphaMult <= 0) return;

        glow.setColor(SpicyRockEntity.GLOW_COLOR_2);
        glow.setAlphaMult(alphaMult);
        glow.setAdditiveBlend();
        glow.renderAtCenter(pos.x, pos.y);
    }
}
