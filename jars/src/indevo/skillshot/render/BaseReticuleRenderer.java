package indevo.skillshot.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;
import indevo.skillshot.GuideLineStyle;
import indevo.skillshot.SkillshotSettings;
import indevo.skillshot.util.SkillshotUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public abstract class BaseReticuleRenderer implements SkillshotRenderer {

    transient private SpriteAPI fleetReticule;
    private boolean done = false;
    protected Vector2f cursorPos = new Vector2f();

    protected boolean showTrajectory = false;
    protected boolean showBounds = false;

    protected float boundsSpread = 0f;
    protected float length = 0f;
    protected GuideLineStyle lineStyle = null;

    public BaseReticuleRenderer withTrajectory() {
        showTrajectory = true;
        return this;
    }

    public BaseReticuleRenderer withBounds(float spreadDegrees) {
        showBounds = true;
        boundsSpread = spreadDegrees;
        return this;
    }

    public BaseReticuleRenderer withLength(float worldUnits) {
        length = worldUnits;
        return this;
    }

    public BaseReticuleRenderer withLineStyle(GuideLineStyle style) {
        lineStyle = style;
        return this;
    }

    protected float getGuideLineEndPadding() {
        return 0f;
    }

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

        // set first, isValidPosition() reads it
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

        renderGuideLines(fleet, reticuleSize, angleToCursor, cursorPos, color);

        renderCursorBoundObject(layer, viewport, angleToCursor, cursorPos, color);
    }

    protected void renderGuideLines(CampaignFleetAPI fleet, float reticuleSize, float angleToCursor, Vector2f cursorPos, Color colour) {
        if (!showTrajectory && !showBounds) return;

        Vector2f origin = fleet.getLocation();

        // start outside the fleet ring so lines do not cross the ring sprite
        float from = reticuleSize * 0.5f;

        float to = Misc.getDistance(origin, cursorPos) - getGuideLineEndPadding();
        if (length > 0f) to = Math.min(to, length);

        if (to <= from) return;

        List<Vector2f> vertices = new ArrayList<>();

        if (showTrajectory) addLine(vertices, origin, angleToCursor, from, to);

        if (showBounds) {
            addLine(vertices, origin, angleToCursor - boundsSpread * 0.5f, from, to);
            addLine(vertices, origin, angleToCursor + boundsSpread * 0.5f, from, to);
        }

        SkillshotUtils.drawLines(vertices, colour,
                SkillshotSettings.RETICULE_ALPHA * SkillshotSettings.GUIDE_LINE_ALPHA_MULT,
                SkillshotSettings.GUIDE_LINE_WIDTH,
                lineStyle != null ? lineStyle : SkillshotSettings.GUIDE_LINE_STYLE);
    }

    protected void addLine(List<Vector2f> vertices, Vector2f origin, float angle, float from, float to) {
        Vector2f direction = Misc.getUnitVectorAtDegreeAngle(angle);

        vertices.add(new Vector2f(origin.x + direction.x * from, origin.y + direction.y * from));
        vertices.add(new Vector2f(origin.x + direction.x * to, origin.y + direction.y * to));
    }

    public abstract void renderCursorBoundObject(CampaignEngineLayers layer, ViewportAPI viewport, float angleToCursor, Vector2f cursorPos, Color colour);
}
