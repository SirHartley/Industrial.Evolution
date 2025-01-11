package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

public abstract class BaseReticuleRenderer implements MissileCampaignRenderer {
    public static final float DEFAULT_ALPHA = 0.9f;
    transient private SpriteAPI fleetDirectionalReticule;
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

        if (fleetDirectionalReticule == null) fleetDirectionalReticule = Global.getSettings().getSprite("fx", "IndEvo_missile_targetting_reticule");
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        float fleetSize = fleet.getRadius();
        float reticuleSize = fleetSize + 250f;
        Color color = isValidPosition() ? fleet.getIndicatorColor() : Color.RED.darker();

        Vector2f mousePos = new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

        float angleToCursor = Misc.getAngleInDegrees(fleet.getLocation(), mousePos);

        fleetDirectionalReticule.setAlphaMult(DEFAULT_ALPHA);
        fleetDirectionalReticule.setWidth(reticuleSize);
        fleetDirectionalReticule.setHeight(reticuleSize);
        fleetDirectionalReticule.setAngle(angleToCursor - 90f);
        fleetDirectionalReticule.setColor(color);
        fleetDirectionalReticule.renderAtCenter(fleet.getLocation().x, fleet.getLocation().y);

        renderCursorBoundObject(layer, viewport, angleToCursor, mousePos, color);
    }

    public abstract void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour);

    @Override
    public boolean isValidPosition() {
        return true;
    }
}