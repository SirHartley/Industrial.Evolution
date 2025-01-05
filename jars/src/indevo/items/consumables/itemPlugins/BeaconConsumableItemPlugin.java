package indevo.items.consumables.itemPlugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.items.consumables.scripts.FlickerInstance;

import java.awt.*;

public class BeaconConsumableItemPlugin extends BaseConsumableItemPlugin{

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult, SpecialItemRendererAPI renderer) {
        super.render(x, y, w, h, alphaMult, glowMult, renderer);

        SpriteAPI glow = (Global.getSettings().getSprite("fx", "IndEvo_beacon_glow"));
        Color glowColor = Global.getSector().getPlayerFaction().getDarkUIColor();

        glow.setColor(glowColor);
        glow.setAdditiveBlend();
        glow.setAlphaMult(0.3f + 0.5f * getQuadFunctAlpha(FlickerInstance.getOrCreateInstance(1.5f).getIntervalFraction()));
        glow.renderAtCenter(0f, 0f);
    }

    public float getQuadFunctAlpha(float fract){
        //this is a quadratic function going 0-1-0 between 0 and 1, returing 1 at fract = 0.5
        //return (float) (-4f * Math.pow(fract, 2f) + 4f * fract);
        return (float) (0.5f * Math.sin(1.5f + Math.PI * fract) + 0.5);
    }
}
