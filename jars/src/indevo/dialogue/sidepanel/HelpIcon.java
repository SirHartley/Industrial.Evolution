package indevo.dialogue.sidepanel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.achievements.Achievements;
import indevo.utils.ui.UiDraw;

import java.awt.Color;
import java.util.List;

public class HelpIcon extends BaseCustomUIPanelPlugin {

    public static final float SIZE = 20f;
    public static final float TOOLTIP_WIDTH = 360f;
    static final float GLOW_REACH = 8f;
    static final float GLOW_ALPHA = 0.75f;
    static final float FILL_ALPHA = 0.18f;

    private final SpriteAPI sprite = Global.getSettings().getSprite("fleetScreen", "icon_more_info");
    private final Pulse pulse;
    private PositionAPI pos;

    public interface Content {
        void create(TooltipMakerAPI tooltip);
    }

    // brightens the icon with a soft glow while level is above 0; reached runs when the mouse gets to the icon
    public interface Pulse {
        float level();

        void reached();
    }

    private HelpIcon(Pulse pulse) {
        this.pulse = pulse;
    }

    public static TooltipMakerAPI create(CustomPanelAPI parent, Content content) {
        return create(parent, content, null);
    }

    public static TooltipMakerAPI create(CustomPanelAPI parent, Content content, Pulse pulse) {
        TooltipMakerAPI element = parent.createUIElement(SIZE, SIZE, false);
        element.addCustom(parent.createCustomPanel(SIZE, SIZE, new HelpIcon(pulse)), 0f);
        element.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return TOOLTIP_WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                content.create(tooltip);
                Achievements.addKey(Achievements.LUDDS_SAKE, panelKey(content));
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
        return element;
    }

    // the content is a lambda or method reference, its class name starts with the panel class that made it
    private static String panelKey(Content content) {
        String name = content.getClass().getName();
        int cut = name.indexOf('$');
        if (cut > 0) name = name.substring(0, cut);
        return name.substring(name.lastIndexOf('.') + 1);
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

        float level = pulse != null ? pulse.level() : 0f;
        if (level <= 0f) return;

        float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
        UiDraw.glow(x, y, w, h, GLOW_REACH, Misc.getBasePlayerColor(), GLOW_ALPHA * level * alphaMult);
        UiDraw.quad(x, y, w, h, Misc.getBasePlayerColor(), FILL_ALPHA * level * alphaMult);
        sprite.setColor(Color.WHITE);
        sprite.setAlphaMult(level * alphaMult);
        sprite.render(x, y);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pulse == null || pos == null) return;

        for (InputEventAPI event : events) {
            if (!event.isConsumed() && event.isMouseMoveEvent() && pos.containsEvent(event)) pulse.reached();
        }
    }
}
