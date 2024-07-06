package indevo.exploration.crucible;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CustomEntitySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.FlickerUtilV2;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin.auroraColors;
import static com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin.baseColors;

public class CrucibleStationEntityPlugin extends BaseCustomEntityPlugin {

    public static Color GLOW_COLOR_1 = new Color(255,30,20,255);
    public static Color GLOW_COLOR_2 = new Color(255,180,20,255);
    public static float GLOW_FREQUENCY = 0.2f; // on/off cycles per second

    protected float phase1 = 0f;
    protected float phase2 = 0f;

    protected FlickerUtilV2 flicker2 = new FlickerUtilV2(1);
    protected FlickerUtilV2 flicker1 = new FlickerUtilV2(6);

    transient private SpriteAPI glow;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        //this.entity = entity;
        entity.setDetectionRangeDetailsOverrideMult(0.75f);
        readResolve();
    }

    Object readResolve() {
        //sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    public void advance(float amount) {
        phase1 += amount * GLOW_FREQUENCY;
        while (phase1 > 1) phase1--;

        phase2 += amount * GLOW_FREQUENCY;
        while (phase2 > 1) phase2 --;

        flicker1.advance(amount * 1f);
        flicker2.advance(amount * 1f);

        entity.setFacing(entity.getFacing() + 0.15f);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec == null) return;

        Vector2f loc = entity.getLocation();

        //first
        float glowAlpha = getGlowAlpha(phase1, flicker1);
        glow.setColor(GLOW_COLOR_1);

        float w = 600;
        float h = 600;

        glow.setSize(w, h);
        glow.setAlphaMult(alphaMult * glowAlpha * 0.5f);
        glow.setAdditiveBlend();

        glow.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 2; i++) {
            w *= 0.3f;
            h *= 0.3f;
            //glow.setSize(w * 0.1f, h * 0.1f);
            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * 0.67f);
            glow.renderAtCenter(loc.x, loc.y);
        }

        glowAlpha = getGlowAlpha(phase2, flicker2);
        glow.setColor(GLOW_COLOR_2);

        w = 100;
        h = 100;

        glow.setSize(w, h);
        glow.setAlphaMult(alphaMult * glowAlpha * 0.5f);
        glow.setAdditiveBlend();

        glow.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 3; i++) {
            w *= 0.3f;
            h *= 0.3f;
            //glow.setSize(w * 0.1f, h * 0.1f);
            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * 0.67f);
            glow.renderAtCenter(loc.x, loc.y);
        }
    }

    public float getFlickerBasedMult(FlickerUtilV2 flicker) {
        float shortage = 0.5f;
        //float f = (1f - shortage) + (shortage * flicker.getBrightness());
        float f = 1f - shortage * flicker.getBrightness();
        return f;
    }

    public float getGlowAlpha(float phase, FlickerUtilV2 flicker) {
        float glowAlpha = 0f;
        if (phase < 0.5f) glowAlpha = phase * 2f;
        if (phase >= 0.5f) glowAlpha = (1f - (phase - 0.5f) * 2f);
        glowAlpha = 0.75f + glowAlpha * 0.25f;
        glowAlpha *= getFlickerBasedMult(flicker);
        if (glowAlpha < 0) glowAlpha = 0;
        if (glowAlpha > 1) glowAlpha = 1;
        return glowAlpha;
    }

    public float getRenderRange() {
        return entity.getRadius() + 1200f;
    }


    public static void generateMagneticField(SectorEntityToken token, float flareProbability, float width) {
        //if (!(context.star instanceof PlanetAPI)) return null;

        StarSystemAPI system = token.getStarSystem();

        int baseIndex = (int) (baseColors.length * StarSystemGenerator.random.nextFloat());
        int auroraIndex = (int) (auroraColors.length * StarSystemGenerator.random.nextFloat());

        float bandWidth = token.getRadius() + width;
        float midRadius = 250f;
        float visStartRadius = token.getRadius();
        float visEndRadius = token.getRadius() + width + 50f;

        SectorEntityToken magField = system.addTerrain(Terrain.MAGNETIC_FIELD,
                new MagneticFieldTerrainPlugin.MagneticFieldParams(bandWidth, // terrain effect band width
                        midRadius, // terrain effect middle radius
                        token, // entity that it's around
                        visStartRadius, // visual band start
                        visEndRadius, // visual band end
                        baseColors[baseIndex], // base color
                        flareProbability, // probability to spawn aurora sequence, checked once/day when no aurora in progress
                        auroraColors[auroraIndex]
                ));

        magField.setCircularOrbit(token, 0, 0, 100);
    }


    public static Color [] baseColors = {
            new Color(255, 50, 50, 100),
            //new Color(50, 30, 100, 30),
            //new Color(75, 105, 165, 75)
    };

    public static Color[][] auroraColors = {
            {new Color(235, 100, 140),
                    new Color(210, 110, 180),
                    new Color(190, 140, 150),
                    new Color(210, 190, 140),
                    new Color(170, 200, 90),
                    new Color(160, 230, 65),
                    new Color(220, 70, 20)},
            {new Color(110, 20, 50, 130),
                    new Color(120, 30, 150, 150),
                    new Color(130, 50, 200, 190),
                    new Color(150, 70, 250, 240),
                    new Color(130, 80, 200, 255),
                    new Color(160, 0, 75),
                    new Color(255, 0, 127)},
            {new Color(140, 180, 90),
                    new Color(190, 145, 130),
                    new Color(225, 110, 165),
                    new Color(240, 55, 95),
                    new Color(250, 0, 45),
                    new Color(240, 0, 20),
                    new Color(150, 0, 10)},
            {new Color(40, 180, 90),
                    new Color(90, 145, 130),
                    new Color(145, 110, 165),
                    new Color(160, 55, 95),
                    new Color(130, 0, 45),
                    new Color(130, 0, 20),
                    new Color(150, 0, 10)},
            {new Color(110, 20, 50, 130),
                    new Color(120, 30, 150, 150),
                    new Color(130, 50, 200, 190),
                    new Color(150, 70, 250, 240),
                    new Color(130, 80, 200, 255),
                    new Color(160, 0, 75),
                    new Color(255, 0, 127)},
            {new Color(140, 60, 55),
                    new Color(155, 85, 65),
                    new Color(165, 105, 175),
                    new Color(180, 130, 90),
                    new Color(190, 150, 105),
                    new Color(205, 175, 120),
                    new Color(220, 200, 135)},
    };
}
