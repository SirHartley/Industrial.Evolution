package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;

// the visible rect around the player, local flights and warm start placement follow it
public record ShuttleView(float x, float y, float halfWidth, float halfHeight) {

    // the viewport is set in render, so its corner lags a jump by a frame and is empty before the first render
    public static ShuttleView of(Vector2f player, ViewportAPI viewport) {
        float width = viewport != null ? viewport.getVisibleWidth() : 0f;
        float height = viewport != null ? viewport.getVisibleHeight() : 0f;
        if (width <= 0f || height <= 0f) {
            float zoom = Global.getSettings().getFloat("maxCampaignZoom");
            width = Global.getSettings().getScreenWidth() * zoom;
            height = Global.getSettings().getScreenHeight() * zoom;
        }
        return new ShuttleView(player.x, player.y, width * 0.5f, height * 0.5f);
    }

    // distance from the rect, 0 inside
    public float gap(Vector2f loc) {
        float dx = Math.max(0f, Math.abs(loc.x - x) - halfWidth);
        float dy = Math.max(0f, Math.abs(loc.y - y) - halfHeight);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
