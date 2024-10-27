package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class MissileAOETargetingReticuleRenderer extends BaseReticuleRenderer {

    transient private SpriteAPI AOE;

    @Override
    public void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (AOE == null) AOE = Global.getSettings().getSprite("fx", "IndEvo_target_reticule"); //IndEvo_missile_targetting_arrow

        AOE.setAlphaMult(DEFAULT_ALPHA*0.6f);
        AOE.setWidth(400f);
        AOE.setHeight(400f);
        AOE.setAngle(angleToCursor - 90f);
        AOE.setColor(colour);
        AOE.renderAtCenter(cursorPos.x, cursorPos.y);
    }
}
