package indevo.industries.artillery.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import indevo.ids.Ids;
import indevo.industries.artillery.entities.IndEvo_WatchtowerEntityPlugin;
import indevo.industries.artillery.entities.IndEvo_WatchtowerEyeIndicator;
import com.fs.starfarer.api.campaign.*;
import indevo.utils.ModPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;

public class IndEvo_EyeIndicatorScript extends BaseCampaignEventListener implements EveryFrameScript {

    public static final String EYE_SCRIPT = "$IndEvo_EyeScript";
    public static final String WAS_SEEN_BY_HOSTILE_ENTITY = "$IndEvo_WasSeenByOtherEntity";
    public static final float BASE_NPC_KNOWN_DURATION = 5f;
    public static final float MAX_TIME_TO_TARGET_LOCK = 20f; //10s per day
    public static final float DISTANCE_MOD = 2; //max mult for distance

    private SectorEntityToken indicator;
    public IntervalUtil checkInterval = new IntervalUtil(0.5f, 0.5f);
    private float elapsed = 0f;
    private boolean isLocked = false;

    public IndEvo_EyeIndicatorScript() {
        super(true);
        this.indicator = IndEvo_WatchtowerEyeIndicator.create();
        Global.getSector().getMemoryWithoutUpdate().set(EYE_SCRIPT, this);
    }

    public static void register(){
        if (!Global.getSector().getMemoryWithoutUpdate().contains(EYE_SCRIPT)) Global.getSector().addScript(new IndEvo_EyeIndicatorScript());
    }

    public static IndEvo_EyeIndicatorScript getInstance(){
        return (IndEvo_EyeIndicatorScript) Global.getSector().getMemoryWithoutUpdate().get(EYE_SCRIPT);
    }

    @Override
    public void advance(float amount) {
        checkInterval.advance(amount);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (!indicator.isInCurrentLocation()) {
            indicator.getContainingLocation().removeEntity(indicator);
            player.getContainingLocation().addEntity(indicator);
            indicator.setLocation(1000000, 1000000);

        }

        if (!checkInterval.intervalElapsed()) return;
        if (player.isInHyperspace() || player.getContainingLocation().getMemoryWithoutUpdate().getBoolean(Ids.MEM_SYSTEM_DISABLE_WATCHTOWERS)) return;

        amount += checkInterval.getIntervalDuration(); //increment by interval since the amount is missing from last advance
        LocationAPI loc = indicator.getContainingLocation();

        if (!loc.hasTag(Ids.TAG_SYSTEM_HAS_ARTILLERY)) return;

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
            IndEvo_WatchtowerEntityPlugin p = (IndEvo_WatchtowerEntityPlugin) t.getCustomPlugin();

            float dist = Misc.getDistance(t, player);
            if (!p.isHacked() && p.isHostileTo(player) && dist < IndEvo_WatchtowerEntityPlugin.RANGE) {
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
            float distanceFraction = 1 - closestDist / IndEvo_WatchtowerEntityPlugin.RANGE;
            float mult = 1 + (DISTANCE_MOD - 1) * distanceFraction;
            addition = amount * mult;
        }

        elapsed = MathUtils.clamp(elapsed += addition, 0, MAX_TIME_TO_TARGET_LOCK);
    }

    private void cycleActions() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        IndEvo_WatchtowerEyeIndicator.State state = IndEvo_WatchtowerEyeIndicator.State.NONE;

        if (elapsed < 0.33f) {
            state = IndEvo_WatchtowerEyeIndicator.State.CLOSED;
        } else if (elapsed < 0.66f) {
            state = IndEvo_WatchtowerEyeIndicator.State.HALF;
        } else if (elapsed > 0.66f) state = IndEvo_WatchtowerEyeIndicator.State.FULL;

        if (elapsed == 0f) {
            isLocked = false; //if it's locked we stay locked until the level is 0
            player.getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);

        } else if (elapsed == MAX_TIME_TO_TARGET_LOCK) {
            isLocked = true;
            player.getMemoryWithoutUpdate().set(WAS_SEEN_BY_HOSTILE_ENTITY, true, 0.5f);
        }

        transferStateToEntity(state, isLocked);

        ModPlugin.log("Eye reporting " + elapsed + " locked " + isLocked);
    }

    public void reset(){
        elapsed = 0f;
        isLocked = false;
        transferStateToEntity(IndEvo_WatchtowerEyeIndicator.State.NONE, false);
    }

    private void transferStateToEntity(IndEvo_WatchtowerEyeIndicator.State state, boolean isLocked){
        IndEvo_WatchtowerEyeIndicator plugin = (IndEvo_WatchtowerEyeIndicator) indicator.getCustomPlugin();
        plugin.setState(state);
        plugin.setLocked(isLocked);
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        fleet.getMemoryWithoutUpdate().unset(WAS_SEEN_BY_HOSTILE_ENTITY);

        if (fleet.isPlayerFleet()) {
            transferStateToEntity(IndEvo_WatchtowerEyeIndicator.State.NONE, false);
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
