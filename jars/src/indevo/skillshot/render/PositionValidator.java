package indevo.skillshot.render;

import org.lwjgl.util.vector.Vector2f;

public interface PositionValidator {

    boolean isValid(Vector2f worldPos);
}
