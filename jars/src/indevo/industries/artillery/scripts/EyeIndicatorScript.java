package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.ArtilleryStationEntityPlugin;
import indevo.industries.artillery.entities.LighthouseEntityPlugin;
import indevo.industries.artillery.entities.SearchlightEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEyeIndicator;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;
import java.util.Set;

public class EyeIndicatorScript extends BaseCampaignEventListener implements EveryFrameScript {

    public static final String EYE_SCRIPT = "$IndEvo_EyeScript";
    public static final String WAS_SEEN_BY_HOSTILE_ENTITY = "$IndEvo_WasSeenByOtherEntity";
    public static final float BASE_NPC_KNOWN_DURATION = 5f;
    public static final float MAX_TIME_TO_TARGET_LOCK = 20f; //10s per day
    public static final float DISTANCE_MOD = 2; //max mult for distance
    public static final float RAMP_MIN_SECONDS = 0.5f;
    public static final float RAMP_MAX_SECONDS = 3f;
    public static final float SEARCHLIGHT_FILL_MULT = 4f;
    public static final float DRAIN_RATE = 1f / 1.2f;
    public static final float SEEN_DAYS = 0.6f;

    private SectorEntityToken indicator;
    public IntervalUtil checkInterval = new IntervalUtil(0.5f, 0.5f);
    private float elapsed = 0f;
    private boolean isLocked = false;

    public EyeIndicatorScript() {
        super(true);
        this.indicator = WatchtowerEyeIndicator.create();
        Global.getSector().getMemoryWithoutUpdate().set(EYE_SCRIPT, this);
    }

    public static void register() {
        if (!Global.getSector().getMemoryWithoutUpdate().contains(EYE_SCRIPT))
            Global.getSector().addScript(new EyeIndicatorScript());
    }

    public static EyeIndicatorScript getInstance() {
        return (EyeIndicatorScript) Global.getSector().getMemoryWithoutUpdate().get(EYE_SCRIPT);
    }

    @Override
    public void advance(float amount) {
        checkInterval.advance(amount);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!indicator.isInCurrentLocation()) {
            indicator.getContainingLocation().removeEntity(indicator);
            player.getContainingLocation().addEntity(indicator);
            indicator.setLocation(1000000, 1000000);
            ModPlugin.log("moving eye to current location");
        }

        LocationAPI loc = indicator.getContainingLocation();

        if (!checkInterval.intervalElapsed()
                || player.isInHyperspace()
                || loc.getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)
                || !loc.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)) return;

        //check if there's a hostile arty, if not, we reset to 0 and cycle
        boolean hostileArtilleryPresent = false;
        if (Setting.ENABLE_ARTILLERY.enabled()) for (SectorEntityToken t : loc.getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION)) {
            if (t.getFaction().isHostileTo(player.getFaction())) {
                hostileArtilleryPresent = true;
                break;
            }
        }

        if (!hostileArtilleryPresent) {
            elapsed = 0f;
            cycleActions();
            return;
        }

        //otherwise, we do all the other stuff
        amount += checkInterval.getIntervalDuration(); //increment by interval since the amount is missing from last advance
        cycleActions();

        // watchtowers fill the eye at 1 + the share of their range the player is inside, searchlights and reporting fleets four times as fast, beams and drones by getRampRate; the fastest source counts
        float bestRate = -1f;

        Set<String> artilleryFactions = ArtilleryStationEntityPlugin.getArtilleryFactionIds(loc);
        for (CampaignFleetAPI f : loc.getFleets()) {
            //check if visible to other fleet
            for (CampaignFleetAPI otherFLeet : loc.getFleets()) {
                if (canReport(otherFLeet, f, artilleryFactions)
                        && Misc.getVisibleFleets(otherFLeet, false).contains(f)) {

                    if (f.isPlayerFleet()) {
                        bestRate = Math.max(bestRate, getFleetRate(otherFLeet, f));
                        continue;
                    }

                    f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, BASE_NPC_KNOWN_DURATION);
                    break;
                }
            }
        }

        List<SectorEntityToken> watchtowerList = loc.getEntitiesWithTag(Ids.TAG_WATCHTOWER);

        DroneNetScript droneNet = DroneNetScript.get(loc);
        if (droneNet != null && droneNet.getPlayerFraction() >= 0f) bestRate = Math.max(bestRate, getRampRate(droneNet.getPlayerFraction()));

        for (SectorEntityToken t : watchtowerList) {
            WatchtowerEntityPlugin p = (WatchtowerEntityPlugin) t.getCustomPlugin();

            if (p instanceof LighthouseEntityPlugin lighthouse && lighthouse.isLit()) {
                float fraction = lighthouse.getBeamFraction(player);
                if (fraction >= 0f) bestRate = Math.max(bestRate, getRampRate(fraction));
                continue;
            }

            if (p instanceof SearchlightEntityPlugin searchlight && searchlight.isLit()) {
                bestRate = Math.max(bestRate, SEARCHLIGHT_FILL_MULT * searchlight.getPlayerRate());
                continue;
            }

            float dist = Misc.getDistance(t, player);
            if (!p.isHacked() && p.isHostileTo(player) && dist < Setting.ARTILLERY_WATCHTOWER_RANGE.value())
                bestRate = Math.max(bestRate, 2f - dist / Setting.ARTILLERY_WATCHTOWER_RANGE.value());
        }

        float addition;

        if (bestRate < 0f) { //out of range of every source
            addition = -amount * DRAIN_RATE;

        } else {
            addition = amount * bestRate;
        }

        elapsed = MathUtils.clamp(elapsed += addition, 0, MAX_TIME_TO_TARGET_LOCK);
    }

    // only fleets of an artillery station's own faction report a target to it, other hostile fleets don't
    public static boolean canReport(CampaignFleetAPI fleet, CampaignFleetAPI target, Set<String> artilleryFactions) {
        return fleet != target
                && fleet.getAI() != null
                && !fleet.isStationMode()
                && artilleryFactions.contains(fleet.getFaction().getId())
                && fleet.getFaction().isHostileTo(target.getFaction())
                && fleet.isHostileTo(target);
    }

    // a reporting fleet fills the eye like a searchlight spot: 2.5 s at its centre to 5 s at the edge of its sight
    public static float getFleetRate(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        float fraction = Misc.getDistance(fleet, player) / getDetectionRadius(fleet, player);
        return SEARCHLIGHT_FILL_MULT * (2f - Math.min(1f, fraction));
    }

    // centre distance at which a fleet sees the player: vanilla shows composition at the full range with the transponder on, else at detectionRangeDetailsMult of it
    public static float getDetectionRadius(CampaignFleetAPI fleet, CampaignFleetAPI player) {
        float range = fleet.getMaxSensorRangeToDetect(player);

        if (!player.isTransponderOn()) {
            float always = Math.max(range * Global.getSettings().getFloat("detectionRangeDetailsAlwaysMult"),
                    Global.getSettings().getFloat("detectionRangeDetailsAlwaysMin"));
            range = Math.max(range * Global.getSettings().getFloat("detectionRangeDetailsMult"), always);
        }

        return range + fleet.getRadius() + player.getRadius();
    }

    // fraction 1 at the source, 0 at the end of its reach: a full eye in RAMP_MIN_SECONDS up to RAMP_MAX_SECONDS
    public static float getRampRate(float fraction) {
        float seconds = RAMP_MAX_SECONDS - (RAMP_MAX_SECONDS - RAMP_MIN_SECONDS) * MathUtils.clamp(fraction, 0f, 1f);
        return MAX_TIME_TO_TARGET_LOCK / seconds;
    }

    public static boolean isPlayerSpotted() {
        return Global.getSector().getPlayerFleet().getMemoryWithoutUpdate().getBoolean(WAS_SEEN_BY_HOSTILE_ENTITY);
    }

    private void cycleActions() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        float fraction = elapsed / MAX_TIME_TO_TARGET_LOCK;

        WatchtowerEyeIndicator.State state = WatchtowerEyeIndicator.State.NONE;

        if (fraction < 0.33f) {
            state = WatchtowerEyeIndicator.State.CLOSED;
        } else if (fraction < 0.66f) {
            state = WatchtowerEyeIndicator.State.HALF;
        } else if (fraction > 0.66f) state = WatchtowerEyeIndicator.State.FULL;

        if (fraction == 0f) {
            isLocked = false; //if it's locked we stay locked until the level is 0
            player.getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);
            state = WatchtowerEyeIndicator.State.NONE;

        } else if (elapsed == MAX_TIME_TO_TARGET_LOCK) {
            isLocked = true;
            player.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, SEEN_DAYS);
        }

        transferStateToEntity(state, isLocked);
    }

    public void reset() {
        elapsed = 0f;
        isLocked = false;
        transferStateToEntity(WatchtowerEyeIndicator.State.NONE, false);
    }

    private void transferStateToEntity(WatchtowerEyeIndicator.State state, boolean isLocked) {
        WatchtowerEyeIndicator plugin = (WatchtowerEyeIndicator) indicator.getCustomPlugin();
        plugin.setState(state);
        plugin.setLocked(isLocked);

        //ModPlugin.log("Eye reporting status " + plugin.state + " at " + elapsed + ", locked: " + isLocked);
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        fleet.getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);

        if (fleet.isPlayerFleet()) reset();
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
