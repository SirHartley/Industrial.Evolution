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
import com.fs.starfarer.api.util.*;
import indevo.ids.Ids;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
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
    protected IntervalUtil rotationAngleFactor = new IntervalUtil(2,2);
    protected IntervalUtil rotationAngleFactor2 = new IntervalUtil(1,1);
    protected IntervalUtil moteInterval = new IntervalUtil(2,10);

    protected FlickerUtilV2 flicker2 = new FlickerUtilV2(1);
    protected FlickerUtilV2 flicker1 = new FlickerUtilV2(6);

    transient private SpriteAPI glow;
    transient protected SpriteAPI whirl;
    transient protected SpriteAPI mass;
    transient protected WarpingSpriteRendererUtil warp;

    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        //this.entity = entity;
        entity.setDetectionRangeDetailsOverrideMult(0.75f);
        readResolve();
    }

    public void advance(float amount) {
        if (entity.isInCurrentLocation()){
            float volumeDistance = 1000f;
            float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), entity);
            float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0,1);

            Global.getSoundPlayer().playLoop("IndEvo_crucible_drone", entity, 1f, fract, entity.getLocation(), new Vector2f(0f, 0f));
        }

        rotationAngleFactor.advance(amount);
        moteInterval.advance(amount);
        if (warp != null) warp.advance(amount);

        if (moteInterval.intervalElapsed()) spawnMote();
        phase1 += amount * GLOW_FREQUENCY;
        while (phase1 > 1) phase1--;

        phase2 += amount * GLOW_FREQUENCY;
        while (phase2 > 1) phase2 --;

        flicker1.advance(amount * 1f);
        flicker2.advance(amount * 1f);

        entity.setFacing(entity.getFacing() - 0.13f);
    }

    Object readResolve() {
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        whirl = Global.getSettings().getSprite("IndEvo", "whirl_round");
        mass = Global.getSettings().getSprite("IndEvo", "crucible_mass");

        //warp = new WarpingSpriteRendererUtil(10, 10, 10f, 20f, 2f);

        return this;
    }

    public void spawnMote(){
        WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>();
        picker.addAll(entity.getContainingLocation().getEntitiesWithTag("IndEvo_orbits_crucible"));

        SectorEntityToken entity = picker.pick();
        YeetopultEntityPlugin p = (YeetopultEntityPlugin) entity.getCustomPlugin();

        Color c = p.color;
        SectorEntityToken target2 = this.entity.getContainingLocation().getEntityById(p.pairedCatapult);

        CrucibleMoteEntityPlugin.CrucibleMotePluginParams params = new CrucibleMoteEntityPlugin.CrucibleMotePluginParams(this.entity.getLocation(), entity, target2, c);
        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(Misc.genUID(), "Crucible Mote", "IndEvo_CrucibleMote", null, params);
        mote.setLocation(this.entity.getLocation().x, this.entity.getLocation().y);
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        //for whirls and funny warp scaling
        float size = entity.getCustomEntitySpec().getSpriteHeight() * 0.22f;

        //Starfield render with warp
        if (layer == CampaignEngineLayers.TERRAIN_6B) {
            float alphaMult = viewport.getAlphaMult();
            alphaMult *= entity.getSensorFaderBrightness();
            alphaMult *= entity.getSensorContactFaderBrightness();
            if (alphaMult <= 0f) return;

            if (warp == null) {
                int cells = 6;
                float cs = mass.getWidth() / 10f;
                warp = new WarpingSpriteRendererUtil(cells, cells, cs * 0.2f, cs * 0.2f, 3f);
            }

            Vector2f loc = entity.getLocation();

            float glowAlpha = 1f;

            mass.setSize(size, size);
            mass.setAlphaMult(alphaMult * glowAlpha);
            mass.setAdditiveBlend();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            warp.renderNoBlendOrRotate(mass, loc.x + 1.5f - mass.getWidth() / 2f,
                    loc.y - mass.getHeight() / 2f, false);

            return;
        }

        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec == null) return;

        Vector2f loc = entity.getLocation();
        float glowAlpha = getGlowAlpha(phase2, flicker2);

        //swirly, two layers
        if (layer == CampaignEngineLayers.BELOW_STATIONS) {
            float angle1 = 360f * (rotationAngleFactor.getElapsed() / rotationAngleFactor.getIntervalDuration());
            angle1 = Misc.normalizeAngle(angle1);
            whirl.setSize(size * 0.9f, size * 0.9f);
            whirl.setAngle(angle1);
            whirl.setAlphaMult(alphaMult * glowAlpha * 0.4f);
            whirl.setAdditiveBlend();
            whirl.renderAtCenter(loc.x, loc.y);

            float angle2 = 360f * (rotationAngleFactor2.getElapsed() / rotationAngleFactor2.getIntervalDuration());
            angle2 = Misc.normalizeAngle(angle2);
            whirl.setSize(size * 0.7f, size * 0.7f);
            whirl.setAngle(angle2);
            whirl.setAlphaMult(alphaMult * glowAlpha * 0.2f);
            whirl.setAdditiveBlend();
            whirl.renderAtCenter(loc.x, loc.y);

            return;
        }

        if (layer == CampaignEngineLayers.ABOVE_STATIONS) {
            //first
            glowAlpha = getGlowAlpha(phase1, flicker1);
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
