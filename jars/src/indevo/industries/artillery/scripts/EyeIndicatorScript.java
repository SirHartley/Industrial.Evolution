package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.WatchtowerEntityPlugin;
import indevo.industries.artillery.entities.WatchtowerEyeIndicator;
import indevo.utils.ModPlugin;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;

public class EyeIndicatorScript extends BaseCampaignEventListener implements EveryFrameScript {

    public static final String EYE_SCRIPT = "$IndEvo_EyeScript";
    public static final String WAS_SEEN_BY_HOSTILE_ENTITY = "$IndEvo_WasSeenByOtherEntity";
    public static final float BASE_NPC_KNOWN_DURATION = 5f;
    public static final float MAX_TIME_TO_TARGET_LOCK = 20f; //10s per day
    public static final float DISTANCE_MOD = 2; //max mult for distance

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
        for (SectorEntityToken t : loc.getEntitiesWithTag(Ids.TAG_ARTILLERY_STATION)) {
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

        boolean inFleetRange = false;
        for (CampaignFleetAPI f : loc.getFleets()) {
            //check if visible to other fleet
            for (CampaignFleetAPI otherFLeet : loc.getFleets()) {
                if (otherFLeet == f) continue;

                if (otherFLeet.isHostileTo(f)
                        && otherFLeet.getAI() != null
                        && !otherFLeet.isStationMode()
                        && Misc.getVisibleFleets(otherFLeet, false).contains(f)) {

                    if (f.isPlayerFleet()) {
                        elapsed = MAX_TIME_TO_TARGET_LOCK;
                        isLocked = true;
                        inFleetRange = true;

                        ModPlugin.log("in fleet range, is seen");

                    } else f.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, BASE_NPC_KNOWN_DURATION);
                    break;
                }
            }
        }

        List<SectorEntityToken> watchtowerList = loc.getEntitiesWithTag(Ids.TAG_WATCHTOWER);
        if (watchtowerList.isEmpty()) return;

        float closestDist = Float.MIN_VALUE;
        SectorEntityToken closestTower = null;

        for (SectorEntityToken t : watchtowerList) {
            WatchtowerEntityPlugin p = (WatchtowerEntityPlugin) t.getCustomPlugin();

            float dist = Misc.getDistance(t, player);
            if (!p.isHacked() && p.isHostileTo(player) && dist < WatchtowerEntityPlugin.RANGE) {
                if (dist > closestDist) {
                    closestDist = dist;
                    closestTower = t;
                }
            }
        }

        float addition = 0f;

        if (closestTower == null && !inFleetRange) { //null means we are out of range of any tower
            addition = -amount;

        } else { //at this point we are in range because there is a closest hostile tower
            float distanceFraction = 1 - closestDist / WatchtowerEntityPlugin.RANGE;
            float mult = 1 + distanceFraction;
            addition = amount * mult;
        }

        elapsed = MathUtils.clamp(elapsed += addition, 0, MAX_TIME_TO_TARGET_LOCK);
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
            player.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, 0.5f);
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

        if (fleet.isPlayerFleet()) {
            transferStateToEntity(WatchtowerEyeIndicator.State.NONE, false);
        }
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
