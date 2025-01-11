package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.artillery.scripts.CampaignAttackScript;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class ArtilleryAOEReticuleRenderer extends BaseReticuleRenderer {
    transient private SpriteAPI AOE;
    private Vector2f cursorPos = new Vector2f(
            Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
            Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));

    @Override
    public void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (AOE == null) AOE = Global.getSettings().getSprite("fx", "IndEvo_target_reticule"); //IndEvo_missile_targetting_arrow
        this.cursorPos = cursorPos;

        AOE.setAlphaMult(DEFAULT_ALPHA*0.6f);
        AOE.setWidth(400f);
        AOE.setHeight(400f);
        AOE.setAngle(angleToCursor - 90f);
        AOE.setColor(colour);
        AOE.renderAtCenter(cursorPos.x, cursorPos.y);
    }

    @Override
    public boolean isValidPosition() {
        for (MarketAPI m : Misc.getMarketsInLocation(Global.getSector().getCurrentLocation())) {
            float safeRadius = m.getPrimaryEntity().getRadius() + CampaignAttackScript.INHABITED_AREA_SAFE_RADIUS;
            if (Misc.getDistance(m.getPrimaryEntity().getLocation(), cursorPos) < safeRadius) return false;
        }

        return true;
    }
}
