package skillshot.render;

import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

/**
 * A reticule. Rendered through LunaLib's campaign renderer, with two additions the framework needs:
 * the ability to retire the renderer when a targeting session ends, and a validity check that gates
 * firing.
 */
public interface SkillshotRenderer extends LunaCampaignRenderingPlugin {

    /** Retires this renderer at the end of the current frame. */
    void setDone();

    /**
     * Whether the current aim point is a legal target. Returning false paints the reticule with
     * {@link skillshot.SkillshotSettings#INVALID_COLOR} and makes the input listeners refuse to
     * fire.
     */
    boolean isValidPosition();

    /** The aim point in world coordinates, as of the last rendered frame. */
    Vector2f getCursorPosition();
}
