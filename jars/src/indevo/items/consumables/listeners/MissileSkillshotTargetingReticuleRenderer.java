package indevo.items.consumables.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class MissileSkillshotTargetingReticuleRenderer extends BaseReticuleRenderer {

    transient private SpriteAPI cursorBoundSprite;

    @Override
    public void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (cursorBoundSprite == null) cursorBoundSprite = Global.getSettings().getSprite("fx", "IndEvo_missile_targetting_arrow");

        cursorBoundSprite.setAlphaMult(DEFAULT_ALPHA*0.6f);
        cursorBoundSprite.setWidth(100);
        cursorBoundSprite.setHeight(100);
        cursorBoundSprite.setAngle(angleToCursor - 90f);
        cursorBoundSprite.setColor(colour);
        cursorBoundSprite.renderAtCenter(cursorPos.x, cursorPos.y);
    }
}
