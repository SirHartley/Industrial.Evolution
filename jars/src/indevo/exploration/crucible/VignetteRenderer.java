package indevo.exploration.crucible;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import indevo.utils.ModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;

import java.util.EnumSet;

public class VignetteRenderer implements LunaCampaignRenderingPlugin {

    public static final float DEFAULT_ALPHA = 0.7f;
    transient private SpriteAPI vignette;
    private float alphaMult = 0f;
    private boolean done = false;

    public void setAlphaMult(float alpha) {
        this.alphaMult = alpha;
    }

    public void setDone() {
        this.done = true;
    }

    @Override
    public boolean isExpired() {
        return done;
    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;
        if (vignette == null) vignette = Global.getSettings().getSprite("IndEvo", "vignette_square");

        float offset = 0f;

        //vignette.setNormalBlend();
        vignette.setAlphaMult(DEFAULT_ALPHA * alphaMult);
        vignette.setSize(viewport.getVisibleWidth(), viewport.getVisibleHeight());
        vignette.render(viewport.getLLX() - (offset / 2), viewport.getLLY() - (offset / 2));
    }
}
