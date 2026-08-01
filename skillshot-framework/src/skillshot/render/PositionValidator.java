package skillshot.render;

import org.lwjgl.util.vector.Vector2f;

/**
 * Decides whether an aim point may be fired at. Hand one to
 * {@link ValidatedAreaReticuleRenderer} to paint the reticule red and block the shot over
 * off-limits positions.
 */
public interface PositionValidator {

    boolean isValid(Vector2f worldPos);
}
