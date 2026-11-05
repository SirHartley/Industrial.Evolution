package iecheck;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.settings.StarfarerSettings;
import indevo.utils.helper.ReflectionUtils;

// speedupToggle (StarfarerSettings setter, name from 0.98a-RC8 windows) stops CampaignState resetting fastForward from the held key
public final class Time {
    public static float mult = 60f;

    private Time() {
    }

    public static void fast(boolean on) {
        StarfarerSettings.ö00000(true);
        Global.getSettings().setFloat("campaignSpeedupMult", on ? mult : 2f);
        ReflectionUtils.set("fastForward", Global.getSector().getCampaignUI(), on);
    }
}
