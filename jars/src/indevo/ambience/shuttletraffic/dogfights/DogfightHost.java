package indevo.ambience.shuttletraffic.dogfights;

import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// what a dogfight needs from the campaign: hit particles and a port to flee to; the checks fake both
public interface DogfightHost {

    record Refuge(Vector2f loc, float radius) {
    }

    void particle(Vector2f loc, Vector2f vel, float size, float brightness, float duration, Color color);

    Refuge findRefuge(Dogfighter fighter);
}
