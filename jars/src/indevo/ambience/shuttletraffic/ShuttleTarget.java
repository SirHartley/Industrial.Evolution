package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

public class ShuttleTarget {

    public final SectorEntityToken center;
    public final float offsetX;
    public final float offsetY;
    // distance of the target point from center when it lies on a ring band, 0 for a point target
    public final float bandRadius;
    public final float wander;

    public ShuttleTarget(SectorEntityToken center, float offsetX, float offsetY, float bandRadius, float wander) {
        this.center = center;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.bandRadius = bandRadius;
        this.wander = wander;
    }

    public void getPoint(Vector2f out) {
        Vector2f loc = center.getLocation();
        out.set(loc.x + offsetX, loc.y + offsetY);
    }
}
