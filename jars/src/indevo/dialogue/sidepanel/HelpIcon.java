package indevo.dialogue.sidepanel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.util.Misc;

public class HelpIcon extends BaseCustomUIPanelPlugin {

    public static final float SIZE = 20f;
    public static final float TOOLTIP_WIDTH = 360f;

    private final SpriteAPI sprite = Global.getSettings().getSprite("fleetScreen", "icon_more_info");
    private PositionAPI pos;

    public interface Content {
        void create(TooltipMakerAPI tooltip);
    }

    public static TooltipMakerAPI create(CustomPanelAPI parent, Content content) {
        TooltipMakerAPI element = parent.createUIElement(SIZE, SIZE, false);
        element.addCustom(parent.createCustomPanel(SIZE, SIZE, new HelpIcon()), 0f);
        element.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return TOOLTIP_WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                content.create(tooltip);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
        return element;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null) return;

        sprite.setSize(pos.getWidth(), pos.getHeight());
        sprite.setColor(Misc.getBasePlayerColor());
        sprite.setAlphaMult(alphaMult);
        sprite.render(pos.getX(), pos.getY());
    }
}
