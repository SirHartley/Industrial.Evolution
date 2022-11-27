package indevo.industries.artillery.projectiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class IndEvo_RenderTemporaryIndicator extends BaseCustomEntityPlugin {

    public static float DANGER_SIGN_SIZE = 150f;
    public static float DURATION = 10f;

    //dangerSign
    transient private SpriteAPI dangerSign;
    public Vector2f dangerSignPos = new Vector2f();
    public Color color;

    public static class IndicatorParams {
        public Vector2f dangerSignPos;
        public Color color;

        public IndicatorParams(Vector2f pos, Color color) {
            this.dangerSignPos = pos;
            this.color = color;
        }
    }

    public static void spawn(LocationAPI loc, Vector2f pos, Color color) {
        SectorEntityToken t = loc.addCustomEntity(null, null, "IndEvo_tempIndicator", null, new IndicatorParams(pos, color));
        t.setLocation(pos.x, pos.y);
    }

    public float elapsed = 0;

    @Override
    public void advance(float amount) {
        super.advance(amount);

        elapsed += amount;

        if (elapsed > DURATION) Misc.fadeAndExpire(entity);
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        entity.setDetectionRangeDetailsOverrideMult(0.75f);

        if (pluginParams instanceof IndicatorParams) {
            this.dangerSignPos = ((IndicatorParams) pluginParams).dangerSignPos;
            this.color = ((IndicatorParams) pluginParams).color;
        }

        readResolve();
    }

    Object readResolve() {
        dangerSign = Global.getSettings().getSprite("fx", "IndEvo_danger_notif");
        return this;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        renderTargetReticule();
    }

    public void renderTargetReticule() {
        dangerSign.setAdditiveBlend();
        dangerSign.setSize(DANGER_SIGN_SIZE, DANGER_SIGN_SIZE);
        dangerSign.setAlphaMult(1f);
        dangerSign.setColor(color);
        dangerSign.renderAtCenter(dangerSignPos.x, dangerSignPos.y);
    }

    public float getRenderRange() {
        return 9999999999999f;
    }
}
