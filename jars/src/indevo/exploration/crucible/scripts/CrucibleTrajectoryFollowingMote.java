package indevo.exploration.crucible.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import indevo.utils.animation.particles.BaseCircleTrajectoryFollowingParticle;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class CrucibleTrajectoryFollowingMote extends BaseCircleTrajectoryFollowingParticle {

    public static final float GLOW_SIZE = 15f;

    protected FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);
    protected Color color;

    transient SpriteAPI moteSprite;

    public CrucibleTrajectoryFollowingMote(Vector2f start, Vector2f end, float radius, float travelDuration, Color color) {
        super(start, end, radius, travelDuration);
        this.color = color;
    }


    public void init(){
        moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        LunaCampaignRenderer.addRenderer(this);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (moteSprite == null) moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float alphaMult = viewport.getAlphaMult();
        if (alphaMult <= 0) return;

        float w = GLOW_SIZE;
        float h = GLOW_SIZE;

        float moteSpriteAlpha = 1f - 0.5f * flicker.getBrightness();

        moteSprite.setColor(color);
        moteSprite.setSize(w, h);
        moteSprite.setAlphaMult(alphaMult * moteSpriteAlpha);
        moteSprite.setAdditiveBlend();

        moteSprite.renderAtCenter(position.x, position.y);

        for (int i = 0; i < 5; i++) {
            w *= 0.3f;
            h *= 0.3f;
            moteSprite.setSize(w, h);
            moteSprite.setAlphaMult(alphaMult * moteSpriteAlpha * 0.67f);
            moteSprite.renderAtCenter(position.x, position.y);
        }
    }
}
