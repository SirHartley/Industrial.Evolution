package skillshot.util;

import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

public class SkillshotUtils {

    /**
     * The cursor position in campaign world coordinates. Both the reticules and the fire hook read
     * the aim point through here, so they can never disagree about where the player is pointing.
     */
    public static Vector2f getCursorWorldPosition() {
        return new Vector2f(
                Global.getSector().getViewport().convertScreenXToWorldX(Global.getSettings().getMouseX()),
                Global.getSector().getViewport().convertScreenYToWorldY(Global.getSettings().getMouseY()));
    }
}
