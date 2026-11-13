package indevo.industries.artillery.terrain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.artillery.renderers.LightConeRenderer;
import indevo.industries.artillery.utils.VisionBlockers;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.List;

public class LighthouseBeamTerrain extends BaseRingTerrain {

    public static final String TERRAIN_ID = "IndEvo_lighthouse_beam";
    public static final float INNER_RADIUS = 40f;
    public static final float FADE_SECONDS = 1f;
    public static final int MAX_BEAMS = 2;
    public static final float BEAM_ALPHA = 0.35f;
    public static final float LAMP_SIZE = 70f;
    public static final float LAMP_ALPHA = 0.6f;
    public static final float SCROLL_SPEED = 0.02f;
    public static final float BLOCKER_SECONDS = 0.1f;
    public static final float MAP_ALPHA = 0.3f;
    public static final Color LIGHT_COLOR = new Color(255, 236, 190);
    public static final float FACTION_TINT = 0.2f;
    private static final int MAP_ARC_SEGMENTS = 8;

    protected float angle = (float) Math.random() * 360f;
    protected float direction = Math.random() < 0.5f ? 1f : -1f;
    protected boolean lit = false;
    protected int beamCount = 1;

    protected transient float brightness;
    protected transient SpriteAPI texture;
    protected transient SpriteAPI glow;
    protected transient List<VisionBlockers.Blocker> blockers;
    protected transient float sinceBlockers;
    protected transient float scroll;

    @Override
    protected Object readResolve() {
        super.readResolve();
        layers = EnumSet.of(CampaignEngineLayers.TERRAIN_7);
        texture = Global.getSettings().getSprite("terrain", "pulsar");
        glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        return this;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }

    public boolean isLit() {
        return lit;
    }

    public static float getLength() {
        return Setting.ARTILLERY_LIGHTHOUSE_BEAM_LENGTH.value();
    }

    public static float getArc() {
        return Setting.ARTILLERY_LIGHTHOUSE_BEAM_WIDTH.value();
    }

    public int getBeamCount() {
        return beamCount;
    }

    public void setBeamCount(int beamCount) {
        this.beamCount = Math.max(1, Math.min(MAX_BEAMS, beamCount));
    }

    @Override
    public void advance(float amount) {
        if (amount <= 0) return;

        SectorEntityToken house = params.relatedEntity;
        if (house == null || !house.isAlive()) {
            Misc.fadeAndExpire(entity, 0f);
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        float turnDays = Global.getSector().getClock().convertToDays(Setting.ARTILLERY_LIGHTHOUSE_TURN_TIME.value());
        angle = Misc.normalizeAngle(angle + direction * 360f * days / turnDays);

        brightness += (lit ? amount : -amount) / FADE_SECONDS;
        brightness = Math.max(0f, Math.min(1f, brightness));

        scroll = (scroll + amount * SCROLL_SPEED) % 1f;

        sinceBlockers += amount;
        if (sinceBlockers >= BLOCKER_SECONDS) blockers = null;
    }

    // planets and chaff clouds, the same list for the drawn light and for sees
    private List<VisionBlockers.Blocker> getBlockers() {
        if (blockers == null) {
            blockers = VisionBlockers.collect(entity.getContainingLocation(), getBlockerExclude());
            sinceBlockers = 0f;
        }

        return blockers;
    }

    // the body the lighthouse orbits would shadow half its sweep
    private SectorEntityToken getBlockerExclude() {
        SectorEntityToken focus = params.relatedEntity.getOrbitFocus();
        return focus instanceof PlanetAPI ? focus : null;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        return sees(point, radius, 0f);
    }

    // a check that runs every checkSeconds widens the arc by the sweep in between, so a fast beam skips nothing
    public boolean sees(Vector2f point, float radius, float checkSeconds) {
        if (!lit) return false;

        Vector2f loc = entity.getLocation();
        float dist = Misc.getDistance(loc, point);
        if (dist < INNER_RADIUS || dist > getLength() + radius) return false;

        float arc = getArc() + 360f * checkSeconds / Setting.ARTILLERY_LIGHTHOUSE_TURN_TIME.value();
        for (int i = 0; i < beamCount; i++) {
            if (Misc.isInArc(angle + 180f * i, arc, loc, point)) return !VisionBlockers.isBlocked(getBlockers(), loc, point);
        }

        return false;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (brightness <= 0f) return;

        float alpha = viewport.getAlphaMult() * brightness;
        Vector2f loc = entity.getLocation();
        Color color = getLightColor();

        for (int i = 0; i < beamCount; i++)
            LightConeRenderer.draw(loc, angle + 180f * i, getArc(), getLength(), getBlockers(), color, alpha * BEAM_ALPHA, texture, scroll);

        glow.setAdditiveBlend();
        glow.setSize(LAMP_SIZE, LAMP_SIZE);
        glow.setColor(color);
        glow.setAlphaMult(alpha * LAMP_ALPHA);
        glow.renderAtCenter(loc.x, loc.y);
    }

    private Color getLightColor() {
        return Misc.interpolateColor(LIGHT_COLOR, params.relatedEntity.getFaction().getBaseUIColor(), FACTION_TINT);
    }

    // map space is world space times factor, same as the vanilla terrain map icons
    @Override
    public void renderOnMap(float factor, float alphaMult) {
        if (!lit || alphaMult <= 0f) return;

        Vector2f loc = entity.getLocation();
        Color color = getLightColor();
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float length = getLength();
        float arc = getArc();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (int i = 0; i < beamCount; i++) {
            float start = angle + 180f * i - arc / 2f;

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glColor4f(r, g, b, MAP_ALPHA * alphaMult);
            GL11.glVertex2f(loc.x * factor, loc.y * factor);
            GL11.glColor4f(r, g, b, 0f);
            for (int j = 0; j <= MAP_ARC_SEGMENTS; j++) {
                double rad = Math.toRadians(start + arc * j / MAP_ARC_SEGMENTS);
                GL11.glVertex2f((loc.x + (float) Math.cos(rad) * length) * factor, (loc.y + (float) Math.sin(rad) * length) * factor);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    @Override
    public float getRenderRange() {
        return getLength() + 1000f;
    }

    @Override
    protected boolean shouldCheckFleetsToApplyEffect() {
        return false;
    }

    @Override
    public boolean canPlayerHoldStationIn() {
        return false;
    }

    @Override
    public String getTerrainName() {
        return "Lighthouse Beam";
    }

    @Override
    public String getNameForTooltip() {
        return "Lighthouse Beam";
    }

    @Override
    public Color getNameColor() {
        return Misc.interpolateColor(Misc.getHighlightColor(), Misc.getNegativeHighlightColor(),
                Global.getSector().getCampaignUI().getSharedFader().getBrightness());
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle("Lighthouse Beam");
        tooltip.addPara("A fleet caught in the beam is %s and its position sent to the artillery in this system.",
                10f, Misc.getNegativeHighlightColor(), "spotted at once");
        tooltip.addPara("Planets and chaff clouds block the beam.", 10f);
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public float getTooltipWidth() {
        return 350f;
    }

    @Override
    public String getEffectCategory() {
        return TERRAIN_ID;
    }

    @Override
    public float getMaxEffectRadius(Vector2f locFrom) {
        return getLength();
    }

    @Override
    public float getMinEffectRadius(Vector2f locFrom) {
        return 0f;
    }
}
