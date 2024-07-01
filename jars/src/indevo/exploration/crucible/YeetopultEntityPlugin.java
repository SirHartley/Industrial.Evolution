package indevo.exploration.crucible;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.crucible.ability.YeetScript;
import indevo.utils.ModPlugin;
import indevo.utils.helper.MiscIE;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class YeetopultEntityPlugin extends BaseCustomEntityPlugin {

    public static class YeetopultParams{
        Color color;
        String target;

        public YeetopultParams(Color color, String target) {
            this.color = color;
            this.target = target;
        }
    }

    public static final float ANIM_TIME = 0.1f;
    public Color color;
    public static float GLOW_FREQUENCY = 0.2f; // on/off cycles per second
    protected float phase1 = 0f;
    protected FlickerUtilV2 flicker1 = new FlickerUtilV2(6);
    protected boolean doAnimation = false;
    protected float animProgress = 0f;
    transient private SpriteAPI glow;
    public String targetEntity;


    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        //this.entity = entity;

        targetEntity = ((YeetopultParams) pluginParams).target;
        color = ((YeetopultParams) pluginParams).color;

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
        flicker1.advance(amount);

        Vector2f targetLoc = getTarget();
        float facing = targetLoc != null ? Misc.getAngleInDegrees(entity.getLocation(), targetLoc) : Misc.getAngleInDegrees(entity.getLocation(), entity.getOrbit().getFocus().getLocation()) + 180f;
        entity.setFacing(facing);

        if (targetLoc == null || color == null) return;

        if (doAnimation) {
            animProgress+= amount;
            ModPlugin.log("anim firing " + (animProgress / ANIM_TIME) + " scale = " + ((animProgress / ANIM_TIME) * 500));
        }

        if (!entity.isInCurrentLocation() || animProgress >= ANIM_TIME) {
            doAnimation = false;
            animProgress = 0f;
            return;
        }

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        SectorEntityToken playerTarget = fleet.getInteractionTarget();
        if (playerTarget != null && playerTarget == entity && Misc.getDistance(fleet, entity) < entity.getRadius() + 15f) {
            fleet.addScript(new YeetScript(fleet, getTarget()));
            doAnimation = true;
        }

    }

    public Vector2f getTarget(){
        SectorEntityToken target = entity.getContainingLocation().getEntityById(targetEntity);
        return target == null ? null : target.getLocation();
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (getTarget() == null || color == null) return;

        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        CustomEntitySpecAPI spec = entity.getCustomEntitySpec();
        if (spec == null) return;

        //first
        float glowAlpha = getGlowAlpha(phase1, flicker1);
        glow.setColor(color);

        float factor = 1;

        if (doAnimation) factor = (float) MiscIE.smootherstep(0, ANIM_TIME, animProgress) * 100f;


        float w = 50 * factor;
        float h = 50 * factor;

        glow.setSize(w, h);
        glow.setAlphaMult(alphaMult * glowAlpha * 0.5f);
        glow.setAdditiveBlend();

        Vector2f renderLoc = MathUtils.getPointOnCircumference(entity.getLocation(), 12f, entity.getFacing()-180f); //make glow spawn a bit to the back

        glow.renderAtCenter(renderLoc.x, renderLoc.y);

        for (int i = 0; i < 3; i++) {
            w *= 0.3f;
            h *= 0.3f;
            //glow.setSize(w * 0.1f, h * 0.1f);
            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * glowAlpha * 0.67f);
            glow.renderAtCenter(renderLoc.x, renderLoc.y);
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

    public static double calculateGaussian(double x) {
        double sigmaSquared = 0.2;
        double sigma = Math.sqrt(sigmaSquared);
        double mu = 0;
        double exponent = -Math.pow(x - mu, 2) / (2 * sigmaSquared);
        double coefficient = 1 / (Math.sqrt(2 * Math.PI * sigmaSquared));
        return coefficient * Math.exp(exponent);
    }

    public boolean hasCustomMapTooltip() {
        return true;
    }

    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float opad = 10f;
        SectorEntityToken target = entity.getContainingLocation().getEntityById(targetEntity);
        if (target == null) tooltip.addPara("Not functional", opad);
        else tooltip.addPara(entity.getName() + "\nLinked to: %s", opad, color, target.getCustomPlugin() instanceof YeetopultEntityPlugin ? target.getOrbitFocus().getName() : target.getName());
    }

    public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {
        float opad = 10f;
        SectorEntityToken target = entity.getContainingLocation().getEntityById(targetEntity);
        if (target == null) tooltip.addPara("Not functional", opad);
        else tooltip.addPara(entity.getName() + "\nLinked to: %s", opad, color, target.getCustomPlugin() instanceof YeetopultEntityPlugin ? target.getOrbitFocus().getName() : target.getName());
    }
}
