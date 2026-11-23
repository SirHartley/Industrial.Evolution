package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

// test only: the real cursor for the clicks a player makes on vanilla screens. java.awt.Robot, so the game window must be
// in front (SMOKE_HIDE=0). two cursor moves read back in game coordinates give screen = a * ui + b per axis,
// which also covers window borders and desktop scaling
public final class ReelMouse {
    private static final Logger log = Global.getLogger(ReelMouse.class);
    private static final float SETTLE = 0.15f;

    private static Robot robot = null;
    private static int stage = -1;
    private static float wait = 0f;
    private static final float[] screen = new float[4];
    private static final float[] game = new float[4];
    private static float ax, bx, ay, by;
    private static boolean ready = false;

    private static Point from = null;
    private static float tx, ty, moveFor, moveAt;
    private static boolean clickAfter = false;
    private static int pressFrames = -1;

    private ReelMouse() {
    }

    public static boolean ready() {
        return ready;
    }

    public static boolean busy() {
        return stage >= 0 || from != null || pressFrames >= 0;
    }

    public static void reset() {
        stage = -1;
        from = null;
        pressFrames = -1;
        if (robot != null) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void calibrate() {
        try {
            if (robot == null) robot = new Robot();
        } catch (Exception e) {
            log.info("LIVE_CHECK note: REEL mouse: no robot, " + e);
            return;
        }
        ready = false;
        screen[0] = Display.getX() + Display.getWidth() * 0.35f;
        screen[1] = Display.getY() + Display.getHeight() * 0.45f;
        screen[2] = Display.getX() + Display.getWidth() * 0.65f;
        screen[3] = Display.getY() + Display.getHeight() * 0.7f;
        robot.mouseMove(Math.round(screen[0]), Math.round(screen[1]));
        stage = 0;
        wait = SETTLE;
    }

    // moves over the given seconds to the centre of the component, then clicks
    public static boolean click(PositionAPI pos, float seconds) {
        if (pos == null) return false;
        return clickAt(pos.getX() + pos.getWidth() / 2f, pos.getY() + pos.getHeight() / 2f, seconds);
    }

    public static boolean clickAt(float uiX, float uiY, float seconds) {
        if (!ready || robot == null) {
            log.info("LIVE_CHECK note: REEL mouse: click at " + uiX + ", " + uiY + " skipped, not calibrated");
            return false;
        }
        from = MouseInfo.getPointerInfo().getLocation();
        tx = ax * uiX + bx;
        ty = ay * uiY + by;
        moveFor = Math.max(0.01f, seconds);
        moveAt = 0f;
        clickAfter = true;
        return true;
    }

    public static void advance(float amount) {
        if (robot == null) return;
        if (stage >= 0) {
            wait -= amount;
            if (wait > 0f) return;
            int i = stage * 2;
            game[i] = Global.getSettings().getMouseX();
            game[i + 1] = Global.getSettings().getMouseY();
            if (stage == 0) {
                robot.mouseMove(Math.round(screen[2]), Math.round(screen[3]));
                stage = 1;
                wait = SETTLE;
                return;
            }
            stage = -1;
            if (game[2] == game[0] || game[3] == game[1]) {
                log.info("LIVE_CHECK note: REEL mouse: calibration failed, the game saw " + game[0] + "," + game[1] + " and " + game[2] + "," + game[3]);
                return;
            }
            ax = (screen[2] - screen[0]) / (game[2] - game[0]);
            bx = screen[0] - ax * game[0];
            ay = (screen[3] - screen[1]) / (game[3] - game[1]);
            by = screen[1] - ay * game[1];
            ready = true;
            log.info("LIVE_CHECK note: REEL mouse: screen x = " + ax + " ui + " + bx + ", y = " + ay + " ui + " + by);
            return;
        }
        if (from != null) {
            moveAt += amount;
            float k = Math.min(1f, moveAt / moveFor);
            k = k * k * (3f - 2f * k);
            robot.mouseMove(Math.round(from.x + (tx - from.x) * k), Math.round(from.y + (ty - from.y) * k));
            if (k < 1f) return;
            from = null;
            if (clickAfter) {
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                pressFrames = 3;
            }
            return;
        }
        if (pressFrames >= 0 && --pressFrames < 0) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
}
