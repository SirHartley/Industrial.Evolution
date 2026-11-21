package indevo.industries.derelicts;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RiftTargetArea {

    public final RiftTargetMode mode;
    public final Vector2f origin;
    public final Vector2f aim;
    public final float reachLY;
    public final StarSystemAPI anchor;
    public final List<StarSystemAPI> systems;

    public RiftTargetArea(RiftTargetMode mode, Vector2f origin, Vector2f aim, float reachLY, StarSystemAPI anchor, List<StarSystemAPI> systems) {
        this.mode = mode;
        this.origin = origin;
        this.aim = aim;
        this.reachLY = reachLY;
        this.anchor = anchor;
        this.systems = systems;
    }

    public static RiftTargetArea empty(RiftTargetMode mode, Vector2f origin, float reachLY) {
        return new RiftTargetArea(mode, origin, origin, reachLY, null, new ArrayList<>());
    }

    public boolean isEmpty() {
        return systems.isEmpty();
    }

    public StarSystemAPI pick(Random random) {
        return systems.get(random.nextInt(systems.size()));
    }

    public String getDescription() {
        if (mode == RiftTargetMode.RANDOM) return "anywhere within " + Math.round(reachLY) + " light years";
        return "around the " + anchor.getNameWithLowercaseType();
    }

    public String getHighlight() {
        return mode == RiftTargetMode.RANDOM ? Math.round(reachLY) + "" : anchor.getNameWithLowercaseType();
    }
}
