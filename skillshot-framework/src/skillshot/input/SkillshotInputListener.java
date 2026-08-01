package skillshot.input;

/**
 * A targeting session. Only one can be live at a time; {@link SkillshotActivationManager} owns it.
 */
public interface SkillshotInputListener {

    boolean isActive();

    /** Cancels the session and retires its reticule. Safe to call when already inactive. */
    void reset();
}
