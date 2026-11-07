package indevo.skillshot.render;

import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

public interface SkillshotRenderer extends LunaCampaignRenderingPlugin {

    void setDone();

    boolean isValidPosition();

    Vector2f getCursorPosition();
}
