package indevo.exploration.meteor.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.exploration.meteor.scripts.RadiationReductionScript;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Handles radiation decay for player fleet as well as vignette, sound and VFX
 * Does not handle NPC radiation decay -> RadiationReductionScript
 */

public class RadiationEffectHandler implements LunaCampaignRenderingPlugin {

    //activity 0 to 1, 10 seconds to 0 at full strength
    public static final float ACTIVITY_REDUCTION_PER_SECOND_AFTER_DELAY = 0.1f;
    public static final float ACTIVITY_INCREASE_PER_SECOND = 0.1f;
    public static final float DELAY_BEFORE_REDUCTION = 1f;
    public static final float MAX_MUSIC_SUPPRESSION = 1f;
    public static final String MEM_EFFECT_LEVEL = "$IndEvo_RadEffectLevel";

    private float lastIncreaseTimer = 999f;

    public static final float DEFAULT_ALPHA = 1f;
    transient private SpriteAPI vignette;

    public static RadiationEffectHandler get(){
        RadiationEffectHandler renderer = null;
        if (LunaCampaignRenderer.hasRendererOfClass(RadiationEffectHandler.class)) renderer = (RadiationEffectHandler) LunaCampaignRenderer.getRendererOfClass(RadiationEffectHandler.class);
        else {
            renderer = new RadiationEffectHandler();
            LunaCampaignRenderer.addTransientRenderer(renderer);
        }

        return renderer;
    }

    public float getActivityLevel(SectorEntityToken entity){
        MemoryAPI mem = entity.getMemoryWithoutUpdate();

        if (mem.contains(MEM_EFFECT_LEVEL)) return mem.getFloat(MEM_EFFECT_LEVEL);
        else return 0f;
    }

    public void setActivityLevel(SectorEntityToken entity, float amt){
        MemoryAPI mem = entity.getMemoryWithoutUpdate();
        mem.set(MEM_EFFECT_LEVEL, amt);
    }

    public void increaseActivity(float secondsExposed, SectorEntityToken entity){
        float activityLevel = getActivityLevel(entity);

        if (activityLevel < 1f) activityLevel += ACTIVITY_INCREASE_PER_SECOND * secondsExposed;
        else activityLevel = 1f;

        setActivityLevel(entity, activityLevel);

        if (!entity.isPlayerFleet()) entity.addScript(new RadiationReductionScript(entity));
        if (entity.isPlayerFleet()) lastIncreaseTimer = 0f;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    private List<SectorEntityToken> hackyTokenList = new ArrayList<>();

    @Override
    public void advance(float amount) {
        lastIncreaseTimer += amount;

        //player decay only
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f loc = player.getLocation();
        float activityLevel = getActivityLevel(player);

        int trackAmt = Math.round(activityLevel / 0.1f);
        for (int i = 0; i < trackAmt; i++){
            if (i >= hackyTokenList.size()) hackyTokenList.add(i, Global.getSector().getCurrentLocation().addCustomEntity(Misc.genUID(), null, "IndEvo_token", Factions.NEUTRAL));
            Global.getSoundPlayer().playLoop("IndEvo_radiation" + i, hackyTokenList.get(i), 1, 1, loc, new Vector2f(0,0));
        }

        if (activityLevel > 0) {
            Global.getSector().getCampaignUI().suppressMusic(MAX_MUSIC_SUPPRESSION * activityLevel);
            RadiationVFXHandler.setLevel(activityLevel);
        }

        if (lastIncreaseTimer > DELAY_BEFORE_REDUCTION && activityLevel > 0) {
            activityLevel -= ACTIVITY_REDUCTION_PER_SECOND_AFTER_DELAY * amount;

            if (activityLevel < 0) {
                activityLevel = 0;
                RadiationVFXHandler.setLevel(0);
            }

            setActivityLevel(player, activityLevel);
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
        vignette.setAlphaMult(DEFAULT_ALPHA * getActivityLevel(Global.getSector().getPlayerFleet()));
        vignette.setSize(viewport.getVisibleWidth(), viewport.getVisibleHeight());
        vignette.render(viewport.getLLX() - (offset / 2), viewport.getLLY() - (offset / 2));
    }
}
