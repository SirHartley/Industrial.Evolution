package indevo.items.consumables.particles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.utils.helper.MiscIE;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

public class SmoothFadingParticleRenderer implements LunaCampaignRenderingPlugin {
    public final float runtime;
    public float timePassed = 0f;
    public String spriteName;
    public float size;
    public Vector2f position;
    public CampaignEngineLayers layer;
    public float angle;
    public Color color;
    public transient SpriteAPI sprite;

    public SmoothFadingParticleRenderer(float runtime, String spriteName, float size, Vector2f position, float angle, Color color, CampaignEngineLayers layer) {
        this.runtime = runtime;
        this.spriteName = spriteName;
        this.size = size;
        this.position = position;
        this.angle = angle;
        this.layer = layer;
        this.color = color;
    }

    @Override
    public boolean isExpired() {
        return timePassed >= runtime;
    }

    @Override
    public void advance(float amount) {
        if (isExpired()) return;
        timePassed += amount;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;
        if (sprite == null) sprite = Global.getSettings().getSprite(spriteName);

        float factor = timePassed / runtime;
        float alpha = 1 - MiscIE.smootherstep(0,1,factor);

        sprite.setNormalBlend();
        sprite.setAngle(angle);
        sprite.setSize(size, size);
        sprite.setAlphaMult(alpha);
        sprite.setColor(color);
        sprite.renderAtCenter(position.x, position.y);
    }
}
