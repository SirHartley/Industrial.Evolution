package indevo.industries.derelicts.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

// transient, the jump script restarts it after a load
public class RiftWarpRenderer implements LunaCampaignRenderingPlugin {

    private static final float GROW = 3f;
    private static final float HOLD = 1f;
    private static final float SHRINK = 0.5f;
    private static final float FLARE = 1f;
    private static final float FLARE_FADE_START = 0.7f;
    private static final float SHRINK_START = GROW + HOLD;
    private static final float FLARE_START = SHRINK_START + SHRINK;
    private static final float COLLAPSE_AT = FLARE_START + 0.1f;
    private static final float END = FLARE_START + FLARE;

    private static final float WHIRL_SIZE_MULT = 10f;
    private static final float[] WHIRL_SPEEDS = {-40f, -80f, -160f};
    private static final Color WHIRL_ONE = new Color(180, 80, 255);
    private static final Color WHIRL_TWO = new Color(60, 80, 240);
    private static final Color WHIRL_THREE = new Color(180, 10, 180, 100);

    private final PlanetAPI planet;
    private final float radius;
    private final Vector2f location = new Vector2f();
    private final float[] angles = new float[WHIRL_SPEEDS.length];
    private final FlickerUtilV2 flicker = new FlickerUtilV2(0.1f);
    private final SpriteAPI whirl = Global.getSettings().getSprite("IndEvo", "fx_succy_storm_desat");
    private final SpriteAPI glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

    private LocationAPI containingLocation;
    private float time = 0f;
    private boolean endSoundPlayed = false;

    public RiftWarpRenderer(PlanetAPI planet, float radius) {
        this.planet = planet;
        this.radius = radius;
        followPlanet();
        if (isVisible()) Global.getSoundPlayer().playSound("IndEvo_warp_start", 0.7f, 2f, location, Misc.ZERO);
    }

    public float getPlanetScale() {
        if (time < SHRINK_START) return 1f;
        if (time >= FLARE_START) return 0f;
        float x = (time - SHRINK_START) / SHRINK;
        return 1f - x * x;
    }

    public boolean isCollapsed() {
        return time >= COLLAPSE_AT;
    }

    @Override
    public boolean isExpired() {
        return time >= END;
    }

    @Override
    public void advance(float amount) {
        if (!isCollapsed()) followPlanet();

        time += amount;
        flicker.advance(amount);
        for (int i = 0; i < angles.length; i++) angles[i] = Misc.normalizeAngle(angles[i] + WHIRL_SPEEDS[i] * amount);

        if (!isVisible()) return;
        if (time < FLARE_START) Global.getSoundPlayer().playLoop("IndEvo_warp_loop", planet, 1f, 1.5f, location, Misc.ZERO);
        if (time >= FLARE_START && !endSoundPlayed) {
            endSoundPlayed = true;
            Global.getSoundPlayer().playSound("IndEvo_warp_end", 1f, 1f, location, Misc.ZERO);
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2, CampaignEngineLayers.TERRAIN_7A);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (!isVisible()) return;

        if (layer == CampaignEngineLayers.TERRAIN_2) renderWhirl();
        else renderFlare(viewport);
    }

    private void followPlanet() {
        location.set(planet.getLocation());
        containingLocation = planet.getContainingLocation();
    }

    private boolean isVisible() {
        return containingLocation != null && containingLocation == Global.getSector().getCurrentLocation();
    }

    private float getWhirlSize() {
        float max = radius * WHIRL_SIZE_MULT;
        if (time < GROW) {
            float x = time / GROW;
            return max * (2f * x - x * x);
        }

        if (time < SHRINK_START) return max;
        if (time >= FLARE_START) return 0f;

        float x = (time - SHRINK_START) / SHRINK;
        return max * (1f - x * x);
    }

    private void renderWhirl() {
        float size = getWhirlSize();
        if (size <= 0f) return;

        whirl.setNormalBlend();
        drawWhirl(angles[0], size, 1f, WHIRL_ONE);
        whirl.setAdditiveBlend();
        drawWhirl(angles[1], size * 0.9f, 1f, WHIRL_TWO);
        drawWhirl(angles[2], size * 0.7f, 0.5f, WHIRL_THREE);
    }

    private void drawWhirl(float angle, float size, float alpha, Color color) {
        whirl.setAngle(angle);
        whirl.setSize(size, size);
        whirl.setAlphaMult(alpha);
        whirl.setColor(color);
        whirl.renderAtCenter(location.x, location.y);
    }

    private void renderFlare(ViewportAPI viewport) {
        if (time < FLARE_START) return;

        float flareTime = (time - FLARE_START) / FLARE;
        float fade = 1f;
        if (flareTime > FLARE_FADE_START) {
            float x = (flareTime - FLARE_FADE_START) / (1f - FLARE_FADE_START);
            fade = 1f - x * x;
        }

        float alpha = flicker.getBrightness() * viewport.getAlphaMult() * fade;

        glow.setAdditiveBlend();
        drawGlow(WHIRL_THREE, radius * 8f, 0.4f * fade, alpha, 2);
        drawGlow(WHIRL_TWO, radius * 2f, 0.3f * fade, alpha, 3);
        drawGlow(WHIRL_ONE, radius * WHIRL_SIZE_MULT * 4f, 0.5f * fade, alpha, 6);
    }

    // one wide halo, then a stack of brighter cores at 30% of the previous size each
    private void drawGlow(Color color, float size, float haloAlpha, float coreAlpha, int cores) {
        glow.setColor(color);
        glow.setSize(size, size);
        glow.setAlphaMult(haloAlpha);
        glow.renderAtCenter(location.x, location.y);

        for (int i = 0; i < cores; i++) {
            size *= 0.3f;
            glow.setSize(size, size);
            glow.setAlphaMult(coreAlpha);
            glow.renderAtCenter(location.x, location.y);
        }
    }
}
