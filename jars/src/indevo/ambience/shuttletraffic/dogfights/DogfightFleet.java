package indevo.ambience.shuttletraffic.dogfights;

import org.lwjgl.util.vector.Vector2f;

import java.util.List;

// a fleet in a watched battle as the dogfight sees it, copied from the live fleet every frame
public class DogfightFleet {

    public final int side;
    public final List<WingType> wings;
    public final Vector2f loc = new Vector2f();
    public final Vector2f vel = new Vector2f();
    public float radius;
    public boolean alive = true;

    public DogfightFleet(int side, List<WingType> wings) {
        this.side = side;
        this.wings = wings;
    }
}
