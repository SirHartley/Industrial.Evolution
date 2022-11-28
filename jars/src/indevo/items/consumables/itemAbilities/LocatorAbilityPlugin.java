package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

public class LocatorAbilityPlugin extends BaseConsumableAbilityPlugin {

    public static float DETECTABILITY_PERCENT = 50f;

    @Override
    protected void activateImpl() {

    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        frameLevel += amount / FADE_IN_SECONDS;

        if (getFleet() != null && !getFleet().isInHyperspace()) deactivate();

        if (data != null && !isActive() && getProgressFraction() <= 0f) {
            data = null;
        }
    }

    @Override
    public boolean isUsable() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        return super.isUsable() && fleet.isInHyperspace();  //only in hyperspace
    }

    protected float phaseAngle;
    protected LocatorScanData data = null;

    @Override
    protected void applyEffect(float amount, float level) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        fleet.getStats().getDetectedRangeMod().modifyPercent(getModId(), DETECTABILITY_PERCENT * level, "Gravimetric scan");

        float days = Global.getSector().getClock().convertToDays(amount);
        phaseAngle += days * 360f * 10f;
        phaseAngle = Misc.normalizeAngle(phaseAngle);

        if (data == null) {
            data = new LocatorScanData(this);
        }

        data.advance(days);
    }

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return;

        fleet.getStats().getDetectedRangeMod().unmodify(getModId());
    }

    public float getRingRadius() {
        return getFleet().getRadius() + 75f;
    }

    transient protected SpriteAPI texture;
    private float frameLevel = 0f;
    private static final float FADE_IN_SECONDS = 3f;

    private float getFrameLevel() {
        return Math.min(frameLevel, 1f);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {

        if (data == null) return;

        float level = getFrameLevel();
        if (level <= 0) return;
        if (getFleet() == null) return;
        if (!getFleet().isPlayerFleet()) return;

        float alphaMult = viewport.getAlphaMult() * level;

        float bandWidthInTexture = 256;
        float bandIndex;

        float radStart = getRingRadius();
        float radEnd = radStart + 75f;

        float circ = (float) (Math.PI * 2f * (radStart + radEnd) / 2f);
        float pixelsPerSegment = circ / 360f;
        float segments = Math.round(circ / pixelsPerSegment);

        float startRad = (float) Math.toRadians(0);
        float endRad = (float) Math.toRadians(360f);
        float spanRad = Math.abs(endRad - startRad);
        float anglePerSegment = spanRad / segments;

        Vector2f loc = getFleet().getLocation();
        float x = loc.x;
        float y = loc.y;

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        if (texture == null) texture = Global.getSettings().getSprite("abilities", "neutrino_detector");
        texture.bindTexture();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float thickness = (radEnd - radStart);

        float texProgress = 0f;
        float texHeight = texture.getTextureHeight();
        float imageHeight = texture.getHeight();
        float texPerSegment = pixelsPerSegment * texHeight / imageHeight * bandWidthInTexture / thickness;

        texPerSegment *= 1f;

        float totalTex = Math.max(1f, Math.round(texPerSegment * segments));
        texPerSegment = totalTex / segments;

        float texWidth = texture.getTextureWidth();
        float imageWidth = texture.getWidth();

        Color color = new Color(230, 30, 120, 255);

        for (int iter = 0; iter < 2; iter++) {
            if (iter == 0) {
                bandIndex = 1;
            } else {
                bandIndex = 0;
                texProgress = segments / 2f * texPerSegment;
            }
            if (iter == 1) {
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            }

            float leftTX = (float) bandIndex * texWidth * bandWidthInTexture / imageWidth;
            float rightTX = (float) (bandIndex + 1f) * texWidth * bandWidthInTexture / imageWidth - 0.001f;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (float i = 0; i < segments + 1; i++) {

                float segIndex = i % (int) segments;
                float phaseAngleRad;

                if (iter == 0) {
                    phaseAngleRad = (float) Math.toRadians(phaseAngle) + (segIndex * anglePerSegment * 29f);
                } else {
                    phaseAngleRad = (float) Math.toRadians(-phaseAngle) + (segIndex * anglePerSegment * 17f);
                }

                float angle = (float) Math.toDegrees(segIndex * anglePerSegment);
                float pulseSin = (float) Math.sin(phaseAngleRad);
                float pulseMax = 10f;

                float pulseAmount = pulseSin * pulseMax;
                float pulseInner = pulseAmount * 0.1f;

                float theta = anglePerSegment * segIndex;
                float cos = (float) Math.cos(theta);
                float sin = (float) Math.sin(theta);

                float rInner = radStart - pulseInner;
                float rOuter = radStart + thickness - pulseAmount;

                float grav = data.getDataAt(angle);

                if (grav > 750) grav = 750;
                grav *= 250f / 750f;
                grav *= level;
                rOuter += grav;

                float alpha = alphaMult;
                alpha *= 0.25f + Math.min(grav / 100, 0.75f);

                float x1 = cos * rInner;
                float y1 = sin * rInner;
                float x2 = cos * rOuter;
                float y2 = sin * rOuter;

                x2 += (float) (Math.cos(phaseAngleRad) * pixelsPerSegment * 0.33f);
                y2 += (float) (Math.sin(phaseAngleRad) * pixelsPerSegment * 0.33f);

                GL11.glColor4ub((byte) color.getRed(),
                        (byte) color.getGreen(),
                        (byte) color.getBlue(),
                        (byte) ((float) color.getAlpha() * alphaMult * alpha));

                GL11.glTexCoord2f(leftTX, texProgress);
                GL11.glVertex2f(x1, y1);
                GL11.glTexCoord2f(rightTX, texProgress);
                GL11.glVertex2f(x2, y2);

                texProgress += texPerSegment;
            }
            GL11.glEnd();

        }

        GL11.glPopMatrix();
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, boolean forItem) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();
        float opad = 10f;

        if(!forItem) {
            tooltip.addTitle(spec.getName());
            int amt = getCargoItemAmt();
            tooltip.addPara("Remaining in inventory: %s", opad, amt > 0 ? highlight : Misc.getNegativeHighlightColor(), amt + "");
            tooltip.addPara(Global.getSettings().getSpecialItemSpec(getItemID()).getDesc(), gray, opad);
        }

        tooltip.addPara("Measures the total radiation emitted by %s in nearby star systems while in hyperspace, " +
                        "the signal scaling with strength and distance. It does not provide false readings." +
                        ".", opad, highlight,
                "most technology");

        tooltip.addPara("The locator will stay active for %s or until the fleet %s.", opad, highlight,
                "10 days", "exits hyperspace");

        tooltip.addPara("Increases the range at which the fleet can be detected by %s while active.",
                opad, highlight,
                "" + (int) DETECTABILITY_PERCENT + "%");

        if (!getFleet().isInHyperspace()) tooltip.addPara("%s",
                opad, Misc.getNegativeHighlightColor(),
                "Only usable in Hyperspace!");
    }
}
