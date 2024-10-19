package indevo.exploration.crucible.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.*;
import indevo.exploration.crucible.scripts.CrucibleStartupAnimationScript;
import indevo.exploration.crucible.terrain.CrucibleFieldTerrainPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static indevo.exploration.crucible.plugin.CrucibleSpawner.MAGNETIC_FIELD_WIDTH;

public class BaseCrucibleEntityPlugin extends BaseCustomEntityPlugin {

    public static class CrucibleData{
        public float rotationAnglePerTick;
        public float spriteHeightFactor;
        public float warpMass;
        public float warpRadius;
        public float glowRadius;
        public float glowAlpha;
        public float innerGlowAlpha;
        public IntervalUtil moteInterval;

        public CrucibleData(float rotationAnglePerTick, float spriteHeightFactor, float warpMass, float warpRadius, float glowRadius, float glowAlpha, float innerGlowAlpha, IntervalUtil moteInterval) {
            this.rotationAnglePerTick = rotationAnglePerTick;
            this.spriteHeightFactor = spriteHeightFactor;
            this.warpMass = warpMass;
            this.warpRadius = warpRadius;
            this.glowRadius = glowRadius;
            this.glowAlpha = glowAlpha;
            this.innerGlowAlpha = innerGlowAlpha;
            this.moteInterval = moteInterval;
        }
    }

    public static final String TAG_ENABLED = "crucible_enabled";
    public static final String MEM_ACTIVITY_LEVEL = "$crucible_activity_Level";
    public static final String MEM_CATAPULT_NUM = "$crucible_catapult_num";

    public static Color GLOW_COLOR_1 = new Color(255,30,20,255);
    public static Color GLOW_COLOR_2 = new Color(255,180,20,255);
    public static float GLOW_FREQUENCY = 0.2f; // on/off cycles per second

    protected float phase1 = 0f;
    protected float phase2 = 0f;
    protected IntervalUtil rotationAngleFactor = new IntervalUtil(2,2);
    protected IntervalUtil rotationAngleFactor2 = new IntervalUtil(1,1);
    protected FlickerUtilV2 flicker2 = new FlickerUtilV2(1);
    protected FlickerUtilV2 flicker1 = new FlickerUtilV2(6);
    protected CampaignTerrainAPI magField = null;

    public CrucibleData data;

    transient protected SpriteAPI glow;
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
        if (!entity.hasTag(BaseCrucibleEntityPlugin.TAG_ENABLED)) return;

        if (entity.isInCurrentLocation()){
            float volumeDistance = 1000f;
            float distance = Misc.getDistance(Global.getSector().getPlayerFleet(), entity);
            float fract = 1 - MathUtils.clamp(distance / volumeDistance, 0,1);

            Global.getSoundPlayer().playLoop("IndEvo_crucible_drone", entity, 1f, fract, entity.getLocation(), new Vector2f(0f, 0f));
        }

        rotationAngleFactor.advance(amount);
        data.moteInterval.advance(amount);

        if (warp != null) warp.advance(amount);

        if (data.moteInterval.intervalElapsed() && getActivityLevel() > 0.9f) spawnMote();
        phase1 += amount * GLOW_FREQUENCY;
        while (phase1 > 1) phase1--;

        phase2 += amount * GLOW_FREQUENCY;
        while (phase2 > 1) phase2 --;

        flicker1.advance(amount * 1f);
        flicker2.advance(amount * 1f);

        entity.setFacing(entity.getFacing() + data.rotationAnglePerTick * getActivityLevel());
    }

    Object readResolve() {
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        whirl = Global.getSettings().getSprite("IndEvo", "whirl_round");
        mass = Global.getSettings().getSprite("IndEvo", "crucible_mass");

        return this;
    }

    public void enable(){
        entity.addScript(new CrucibleStartupAnimationScript(entity));

        /*for (SectorEntityToken t : entity.getContainingLocation().getEntitiesWithTag("IndEvo_crucible_part")){
            t.addTag(TAG_ENABLED);
        }*/

        /*runcode for (com.fs.starfarer.api.campaign.SectorEntityToken t : com.fs.starfarer.api.Global.getSector().getPlayerFleet().getContainingLocation().getEntitiesWithTag("IndEvo_crucible")){
            indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin plugin = (indevo.exploration.crucible.entities.BaseCrucibleEntityPlugin) t.getCustomPlugin();
            plugin.enable();
        }*/
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
        if (!entity.hasTag(BaseCrucibleEntityPlugin.TAG_ENABLED)) return;

        //for whirls and funny warp scaling
        float size = entity.getCustomEntitySpec().getSpriteHeight() * data.spriteHeightFactor; //specific
        float alphaMult = viewport.getAlphaMult() * getActivityLevel();

        //render with warp
        if (layer == CampaignEngineLayers.TERRAIN_6B) {
            alphaMult *= entity.getSensorFaderBrightness();
            alphaMult *= entity.getSensorContactFaderBrightness();
            if (alphaMult <= 0f) return;

            if (warp == null) {
                int cells = 6;
                float cs = mass.getWidth() / data.warpMass; //spec
                warp = new WarpingSpriteRendererUtil(cells, cells, cs * 0.2f, cs * 0.2f, data.warpRadius); //spec
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
            whirl.setSize(size*0.9f, size*0.9f);
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

            float w = data.glowRadius; //spec
            float h = data.glowRadius; //spec

            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * data.glowAlpha); //spec
            glow.setAdditiveBlend();

            glow.renderAtCenter(loc.x, loc.y);

            for (int i = 0; i < 2; i++) {
                w *= 0.3f;
                h *= 0.3f;
                //glow.setSize(w * 0.1f, h * 0.1f);
                glow.setSize(w, h);
                glow.setAlphaMult(alphaMult * glowAlpha * data.innerGlowAlpha); //spec
                glow.renderAtCenter(loc.x, loc.y);
            }

            glowAlpha = getGlowAlpha(phase2, flicker2);
            glow.setColor(GLOW_COLOR_2);

            w = 100;
            h = 100;

            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * data.glowAlpha);
            glow.setAdditiveBlend();

            glow.renderAtCenter(loc.x, loc.y);

            for (int i = 0; i < 3; i++) {
                w *= 0.3f;
                h *= 0.3f;
                //glow.setSize(w * 0.1f, h * 0.1f);
                glow.setSize(w, h);
                glow.setAlphaMult(alphaMult * glowAlpha * data.innerGlowAlpha);
                glow.renderAtCenter(loc.x, loc.y);
            }
        }
    }

    public CampaignTerrainAPI getMagField() {
        return magField;
    }

    public void setMagField(CampaignTerrainAPI magField) {
        this.magField = magField;
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

    public float getActivityLevel() {
        return entity.getMemoryWithoutUpdate().getFloat(MEM_ACTIVITY_LEVEL);
    }
}