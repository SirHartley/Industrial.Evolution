package indevo.exploration.meteor.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.utils.ModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.EnumSet;

public class SpicyVignetteRenderer implements LunaCampaignRenderingPlugin {

    //activity 0 to 1, 10 seconds to 0 at full strength
    public static final float ACTIVITY_REDUCTION_PER_SECOND_AFTER_DELAY = 0.1f;
    public static final float ACTIVITY_INCREASE_PER_SECOND = 0.1f;
    public static final float DELAY_BEFORE_REDUCTION = 1f;

    private float lastIncreaseTimer = 999f;
    private float activityLevel = 0f;

    public static final float DEFAULT_ALPHA = 1f;
    transient private SpriteAPI vignette;

    public static SpicyVignetteRenderer get(){
        SpicyVignetteRenderer renderer = null;
        if (LunaCampaignRenderer.hasRendererOfClass(SpicyVignetteRenderer.class)) renderer = (SpicyVignetteRenderer) LunaCampaignRenderer.getRendererOfClass(SpicyVignetteRenderer.class);
        else {
            renderer = new SpicyVignetteRenderer();
            LunaCampaignRenderer.addTransientRenderer(renderer);
        }

        return renderer;
    }

    public void increaseActivity(float secondsExposed){
        if (activityLevel < 1f) activityLevel += ACTIVITY_INCREASE_PER_SECOND * secondsExposed;
        else activityLevel = 1f;

        lastIncreaseTimer = 0f;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        lastIncreaseTimer += amount;

        if (activityLevel > 0) SpicyVFXHandler.setLevel(activityLevel);

        if (lastIncreaseTimer > DELAY_BEFORE_REDUCTION && activityLevel > 0) {
            activityLevel -= ACTIVITY_REDUCTION_PER_SECOND_AFTER_DELAY * amount;

            if (activityLevel < 0) {
                activityLevel = 0;
                SpicyVFXHandler.setLevel(0);
            }
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (vignette == null) vignette = Global.getSettings().getSprite("IndEvo", "vignette_shallow");

        float offset = 0f;

        //vignette.setNormalBlend();
        vignette.setAlphaMult(DEFAULT_ALPHA * activityLevel);
        vignette.setSize(viewport.getVisibleWidth(), viewport.getVisibleHeight());
        vignette.render(viewport.getLLX() - (offset / 2), viewport.getLLY() - (offset / 2));
    }
}
