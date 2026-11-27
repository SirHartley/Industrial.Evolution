package indevo.industries.artillery.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import indevo.industries.artillery.scripts.DroneNetScript;
import org.lazywizard.lazylib.MathUtils;

public class ProfileScale {

    public static final float MIN_PROFILE = 100f;
    public static final float FULL_PROFILE = 1000f;

    private float share = 1f;
    private float target = 1f;
    private float rate;
    private float sinceCheck;
    private boolean started;

    // the profile the player sees on the sensor readout, getDetectedRangeMod alone misses the ships
    public static float getEffectiveProfile(CampaignFleetAPI fleet) {
        return Math.max(0f, fleet.getDetectedRangeMod().computeEffective(fleet.getSensorProfile()));
    }

    public static float getShare(CampaignFleetAPI fleet) {
        return MathUtils.clamp((getEffectiveProfile(fleet) - MIN_PROFILE) / (FULL_PROFILE - MIN_PROFILE), 0f, 1f);
    }

    // same sampling and resize as the drone cones, snaps on the first frame and while the player is elsewhere
    public void advance(float amount, LocationAPI location) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        float now = getShare(player);
        if (!started || player.getContainingLocation() != location) {
            started = true;
            share = target = now;
            return;
        }

        sinceCheck += amount;
        if (sinceCheck >= DroneNetScript.CHECK_SECONDS) {
            sinceCheck = 0f;
            if (now != target) {
                target = now;
                rate = Math.abs(target - share) / DroneNetScript.CONE_RESIZE_SECONDS;
            }
        }

        float step = rate * amount;
        share = Math.abs(target - share) <= step ? target : share + Math.signum(target - share) * step;
    }

    // min at or below MIN_PROFILE, 1 at FULL_PROFILE and above
    public float getMult(float min) {
        return min + (1f - min) * share;
    }
}
