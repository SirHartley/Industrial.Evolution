package indevo.skillshot;

import java.awt.*;

public class SkillshotSettings {

    // shared with catch.release, never rename
    public static final String LOCK_KEY = "$sirhartley_skillshotActive";

    public static String LOCK_OWNER = "IndEvo";
    public static String TAG_SKILLSHOT = "IndEvo_skillshot";
    public static String SPRITE_CATEGORY = "fx";
    public static String SPRITE_FLEET_RETICULE = "IndEvo_missile_targetting_reticule";
    public static String SPRITE_DIRECTION_ARROW = "IndEvo_missile_targetting_arrow";
    public static String SPRITE_AREA_TARGET = "IndEvo_skillshot_target";
    public static String SOUND_INVALID_TARGET = "IndEvo_denied_buzzer";

    public static float RETICULE_ALPHA = 0.9f;
    public static float CURSOR_SPRITE_ALPHA_MULT = 0.6f;
    public static float FLEET_RETICULE_PADDING = 250f;
    public static float DIRECTION_ARROW_SIZE = 170f;
    public static float DEFAULT_AREA_SIZE = 400f;

    public static float GUIDE_LINE_WIDTH = 2f;
    public static float GUIDE_LINE_ALPHA_MULT = 0.45f;
    public static GuideLineStyle GUIDE_LINE_STYLE = GuideLineStyle.SOLID;
    public static float GUIDE_LINE_DASH_PX = 24f;
    public static float GUIDE_LINE_DASH_GAP_PX = 24f;
    public static float GUIDE_LINE_DOT_PX = 4f;
    public static float GUIDE_LINE_DOT_GAP_PX = 28f;

    public static Color INVALID_COLOR = Color.RED.darker();
    public static boolean LOG_DEBUG = false;
}
