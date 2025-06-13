package indevo.exploration.meteor.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WarpingSpriteRendererUtil;
import indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin;
import indevo.exploration.meteor.helper.MeteorFactory;
import indevo.exploration.meteor.renderers.SpicyTrailRenderer;
import indevo.exploration.meteor.renderers.SpicyVignetteRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SpicyRockEntity extends MeteorEntity {

    public static Color GLOW_COLOR_1 = new Color(60,255,20,255);
    public static Color GLOW_COLOR_2 = new Color(155,180,50,255);
    public static final float BASE_ALPHA = 0.3f;
    public static final float MAX_RUNTIME = 5f;
    public static final float GLOW_SIZE_MULT = 20f;

    protected FlickerUtilV2 flicker1 = new FlickerUtilV2(10f);
    protected IntervalUtil trailInterval = new IntervalUtil(0.5f, 3f);
    public float glowRadius = 0f;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        glowRadius = entity.getRadius() * GLOW_SIZE_MULT;
    }

    transient protected SpriteAPI glow;

    @Override
    public void advance(float amount) {
        super.advance(amount);
        flicker1.advance(amount);
        trailInterval.advance(amount);

        if (false && trailInterval.intervalElapsed()){
            LunaCampaignRenderer.addRenderer(new SpicyTrailRenderer(MAX_RUNTIME, size, 0.5f, entity.getContainingLocation(), entity.getLocation()));
        }

        glowRadius = entity.getRadius() * 10;

        if (Misc.getDistance(Global.getSector().getPlayerFleet().getLocation(), entity.getLocation()) < glowRadius)
            SpicyVignetteRenderer.get().increaseActivity(amount);

        //flicker the glow
        //spawn trailing glow patches for the trail lunarendered?
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (!viewport.isNearViewport(entity.getLocation(), 4000f)) return;
        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        if (layer != CampaignEngineLayers.TERRAIN_5) return;

        float alphaMult = viewport.getAlphaMult() * (BASE_ALPHA * 0.7f + 0.3f * flicker1.getBrightness()) * BASE_ALPHA;
        if (alphaMult <= 0) return;

        Vector2f loc = entity.getLocation();

        glow.setColor(GLOW_COLOR_2);
        glow.setAlphaMult(alphaMult);

        //glow.setNormalBlend();
        glow.renderAtCenter(loc.x, loc.y);

        glow.setColor(GLOW_COLOR_1);
        glow.setAdditiveBlend();

        for (int i = 0; i < 3; i++) {
            float sizeMult = 0.3f * (i + 1);
            float glowMult = 1 - sizeMult;

            float w = glowRadius * sizeMult;
            float h = glowRadius * sizeMult;

            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowMult);
            glow.renderAtCenter(loc.x, loc.y);
        }

    }
}
