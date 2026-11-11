package livecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.Objects;

public final class Ctx {
    private static final Logger log = Global.getLogger(Ctx.class);
    // statics survive c.reload() (quickLoad in the same process), a new Ctx is made after it
    public static int passed = 0;
    public static int failed = 0;
    private static int keys = 0;

    float waitDays = 0f;
    long waitStart = 0L;
    int waitFrames = 0;
    boolean keepDialog = false;
    boolean reload = false;
    boolean held = false;
    String ack = null;
    long ackUntil = 0L;
    String group = null;
    java.util.function.BooleanSupplier until = null;
    Runnable onFrame = null;
    String untilId = null;
    float untilMax = 0f;
    long untilStart = 0L;

    public void expect(boolean condition, String id) {
        String line = group == null ? id : group + " / " + id;
        if (condition) {
            passed++;
            log.info("LIVE_CHECK ok: " + line);
        } else {
            failed++;
            log.info("LIVE_CHECK FAIL: " + line);
        }
    }

    public void near(float actual, float expected, float tolerance, String id) {
        expect(Math.abs(actual - expected) <= tolerance, id + " (got " + actual + ", want " + expected + ")");
    }

    // a failed line shows both values, which expect(a.equals(b), id) does not
    public void eq(Object actual, Object expected, String id) {
        expect(Objects.equals(actual, expected), id + " (got " + actual + ", want " + expected + ")");
    }

    public void note(String text) {
        log.info("LIVE_CHECK note: " + text);
    }

    public void days(float days) {
        waitDays = days;
        waitStart = Global.getSector().getClock().getTimestamp();
    }

    // the runner script saves check-NAME.png. the next step waits until the script has answered
    public void shot(String name) {
        if (!name.matches("[a-z0-9-]+")) throw new IllegalArgumentException("shot name '" + name + "': lowercase letters, digits and '-' only");
        ask("shot", name);
    }

    // a real key press through the runner script (gamectl names: ESC, ENTER, SPACE, F10, A, 1 ...), for input that
    // only goes through the game's own input loop
    public void key(String key) {
        if (!key.matches("[A-Za-z0-9]+")) throw new IllegalArgumentException("key name '" + key + "': letters and digits only");
        ask("key", key + "_" + ++keys);
    }

    private void ask(String kind, String name) {
        ack = "livecheck_ack_" + kind + "_" + name;
        ackUntil = System.currentTimeMillis() + 15000L;
        log.info("LIVE_CHECK " + kind + " " + name);
    }

    // the runner closes any open dialog before a step, unless a check is working in one
    public void keepDialog(boolean keep) {
        keepDialog = keep;
    }

    // ends the steps here and leaves the game open for clicks and captures by hand; the runner script reports HELD
    public void hold() {
        held = true;
        log.info("LIVE_CHECK hold");
    }

    public void frames(int frames) {
        waitFrames = frames;
    }

    public void until(java.util.function.BooleanSupplier condition, float maxDays, String id, Runnable frame) {
        until = condition;
        untilMax = maxDays;
        untilId = id;
        onFrame = frame;
        untilStart = Global.getSector().getClock().getTimestamp();
    }

    public void until(java.util.function.BooleanSupplier condition, float maxDays, String id) {
        until(condition, maxDays, id, null);
    }

    public void reload() {
        reload = true;
    }

    public Map<String, Object> data() {
        return Global.getSector().getPersistentData();
    }

    public void put(String key, Object value) {
        data().put("live_check_" + key, value);
    }

    public Object get(String key) {
        return data().get("live_check_" + key);
    }

    public MarketAPI market(String key) {
        return Global.getSector().getEconomy().getMarket((String) get(key));
    }
}
