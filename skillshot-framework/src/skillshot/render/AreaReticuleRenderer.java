package skillshot.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import skillshot.SkillshotSettings;

import java.awt.*;

/**
 * Reticule for abilities that land on a spot rather than fly in a direction: a circle at the cursor
 * sized to the effect radius.
 */
public class AreaReticuleRenderer extends BaseReticuleRenderer {

    transient private SpriteAPI area;

    protected float size;

    public AreaReticuleRenderer() {
        this(SkillshotSettings.DEFAULT_AREA_SIZE);
    }

    /**
     * @param size diameter of the circle in world units - pass the ability's actual effect diameter
     *             so the player is aiming at what they will get
     */
    public AreaReticuleRenderer(float size) {
        this.size = size;
    }

    @Override
    public void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (area == null) area = Global.getSettings().getSprite(SkillshotSettings.SPRITE_CATEGORY, SkillshotSettings.SPRITE_AREA_TARGET);

        area.setAlphaMult(SkillshotSettings.RETICULE_ALPHA * SkillshotSettings.CURSOR_SPRITE_ALPHA_MULT);
        area.setWidth(size);
        area.setHeight(size);
        area.setAngle(angleToCursor - 90f);
        area.setColor(colour);
        area.renderAtCenter(cursorPos.x, cursorPos.y);
    }
}
