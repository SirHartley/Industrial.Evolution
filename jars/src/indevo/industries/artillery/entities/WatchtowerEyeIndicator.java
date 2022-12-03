package indevo.industries.artillery.entities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class WatchtowerEyeIndicator implements CustomCampaignEntityPlugin {

    protected SectorEntityToken entity;
    public transient SpriteAPI sprite;
    private boolean isLocked = false;
    public State state = State.NONE;

    public enum State {
        NONE,
        CLOSED,
        HALF,
        FULL
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public void setState(State state) {
        this.state = state;
    }

    public static SectorEntityToken create() {
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
        if (loc == null) loc = Global.getSector().getStarSystems().get(0);

        SectorEntityToken t = loc.addCustomEntity(Misc.genUID(), "", "IndEvo_Eye", null, null);
        Global.getSector().addListener((CampaignEventListener) t.getCustomPlugin());

        return t;
    }

    public void init(SectorEntityToken entity, Object pluginParams) {
        this.entity = entity;
        readResolve();
    }

    public void advance(float amount) {
    }

    Object readResolve() {
        sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_3");
        return this;
    }

    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (state == State.NONE) return;

        Color color = Color.RED;
        sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_3");

        if (state == State.NONE) {
            sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_1");
            if (!isLocked) color = new Color(255, 255, 150, 255);
        } else if (state == State.HALF) {
            sprite = Global.getSettings().getSprite("fx", "IndEvo_eye_2");
            if (!isLocked) color = new Color(255, 200, 50, 255);
        } else if (!isLocked) color = new Color(255, 130, 50, 255);

        sprite.setAdditiveBlend();
        sprite.setAlphaMult(0.7f);
        sprite.setColor(color);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Vector2f loc = fleet.getLocation();

        //min zoom 0.5f
        //max zoom 3.0f
        float zoom = Global.getSector().getCampaignUI().getZoomFactor();
        float size = 10 * zoom;
        sprite.setSize(size * 2, size);

        sprite.renderAtCenter(loc.x, loc.y + fleet.getRadius() + size + 10f);
    }

    public float getRenderRange() {
        return 9999999999999f;
    }

    public boolean hasCustomMapTooltip() {
        return false;
    }

    public float getMapTooltipWidth() {
        return 0f;
    }

    public boolean isMapTooltipExpandable() {
        return false;
    }

    public void createMapTooltip(TooltipMakerAPI tooltip, boolean expanded) {

    }

    public void appendToCampaignTooltip(TooltipMakerAPI tooltip, SectorEntityToken.VisibilityLevel level) {

    }
}
