package skillshot.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import skillshot.SkillshotSettings;
import skillshot.util.SkillshotUtils;

import java.awt.*;
import java.util.EnumSet;

/**
 * Draws the ring around the player fleet, rotated to face the cursor and tinted by
 * {@link #isValidPosition()}, then hands off to {@link #renderCursorBoundObject} for whatever the
 * concrete reticule wants to draw at the cursor.
 * <p>
 * Subclass this to make a new reticule; return it from
 * {@link skillshot.ability.SkillshotAbility#createReticule()}.
 */
public abstract class BaseReticuleRenderer implements SkillshotRenderer {

    transient private SpriteAPI fleetReticule;

    private boolean done = false;

    /** Aim point of the frame currently being rendered. Safe to read from isValidPosition(). */
    protected Vector2f cursorPos = new Vector2f();

    @Override
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
    public Vector2f getCursorPosition() {
        return cursorPos;
    }

    @Override
    public boolean isValidPosition() {
        return true;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        if (fleetReticule == null) {
            fleetReticule = Global.getSettings().getSprite(SkillshotSettings.SPRITE_CATEGORY, SkillshotSettings.SPRITE_FLEET_RETICULE);
        }

        //update the aim point before anything reads it - isValidPosition() below depends on it
        cursorPos = SkillshotUtils.getCursorWorldPosition();

        float angleToCursor = Misc.getAngleInDegrees(fleet.getLocation(), cursorPos);
        float reticuleSize = fleet.getRadius() + SkillshotSettings.FLEET_RETICULE_PADDING;
        Color color = isValidPosition() ? fleet.getIndicatorColor() : SkillshotSettings.INVALID_COLOR;

        fleetReticule.setAlphaMult(SkillshotSettings.RETICULE_ALPHA);
        fleetReticule.setWidth(reticuleSize);
        fleetReticule.setHeight(reticuleSize);
        fleetReticule.setAngle(angleToCursor - 90f);
        fleetReticule.setColor(color);
        fleetReticule.renderAtCenter(fleet.getLocation().x, fleet.getLocation().y);

        renderCursorBoundObject(layer, viewport, angleToCursor, cursorPos, color);
    }

    /**
     * @param angleToCursor degrees from the player fleet to the cursor
     * @param cursorPos     aim point in world coordinates
     * @param colour        already resolved from {@link #isValidPosition()}
     */
    public abstract void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour);
}
