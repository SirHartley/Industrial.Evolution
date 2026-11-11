package livecheck;

import java.util.Map;
import java.util.TreeMap;

// State shared by BattleDriver and the PatchLib patches in Timers.
public final class Perf {
    private Perf() {
    }

    // timers add only while this is true
    public static volatile boolean timing = false;
    // false: Timers skip the code under test, for the OFF windows
    public static volatile boolean codeOn = true;
    // time inside Display.update/sync since the last frame: buffer swap, driver frame cap, vsync
    public static long displayNanos = 0;
    public static long displayCalls = 0;
    static int depth = 0;
    static long displayStart = 0;
    public static final Map<String, long[]> totals = new TreeMap<>();

    public static void add(String key, long nanos) {
        if (!timing) return;
        long[] v = totals.computeIfAbsent(key, k -> new long[2]);
        v[0] += nanos;
        v[1]++;
    }
}
