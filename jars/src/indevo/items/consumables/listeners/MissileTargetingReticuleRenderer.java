package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.ModPlugin;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

public class MissileTargetingReticuleRenderer implements MissileCampaignRenderer {
    public static final float DEFAULT_ALPHA = 0.9f;
    transient private SpriteAPI reticule;
    private boolean done = false;

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

        if (reticule == null) reticule = Global.getSettings().getSprite("fx", "IndEvo_missile_targetting_reticule");
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        float fleetSize = fleet.getRadius();
        float reticuleSize = fleetSize + 250f;

        float angleToMouse = Misc.getAngleInDegrees(
                fleet.getLocation(),
                new Vector2f(
                        Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                        Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY())));

        //vignette.setNormalBlend();
        reticule.setAlphaMult(DEFAULT_ALPHA);
        reticule.setWidth(reticuleSize);
        reticule.setHeight(reticuleSize);
        reticule.setAngle(angleToMouse - 90f);
        reticule.setColor(fleet.getIndicatorColor());
        reticule.renderAtCenter(fleet.getLocation().x, fleet.getLocation().y);
    }
}
