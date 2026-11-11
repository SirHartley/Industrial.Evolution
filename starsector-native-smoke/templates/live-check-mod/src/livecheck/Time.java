package livecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.settings.StarfarerSettings;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

// test only. speedupToggle stops CampaignState resetting fastForward from the held key.
// StarfarerSettings.ö00000(boolean) is the speedupToggle setter on 0.98a-RC8 windows: after a game update,
// find it where launcher code reads optBoolean("speedupToggle")
public final class Time {
    public static float mult = 60f;
    private static VarHandle fastForward = null;

    private Time() {
    }

    public static void fast(boolean on) {
        StarfarerSettings.ö00000(true);
        Global.getSettings().setFloat("campaignSpeedupMult", on ? mult : 2f);

        Object state = Global.getSector().getCampaignUI();
        try {
            if (fastForward == null) {
                fastForward = MethodHandles.privateLookupIn(state.getClass(), MethodHandles.lookup())
                        .findVarHandle(state.getClass(), "fastForward", boolean.class);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No fastForward field on " + state.getClass(), e);
        }
        fastForward.set(state, on);
    }
}
