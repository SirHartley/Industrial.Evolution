package indevo.other;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.utils.timers.NewDayListener;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicVariables;

import java.util.LinkedList;
import java.util.List;

public class PlayerHyperspaceTripTracker extends BaseCampaignEventListener implements NewDayListener {
    public static final int MIN_TRIP_DAYS_FOR_RECORD = 7;
    public static final int MIN_TRIP_DISTANCE_PERCENT_FOR_START = 20;

    private Vector2f lastPos = new Vector2f(0f, 0f);
    private TripData currentTrip = new TripData();

    private final float sectorWidthLY = MagicVariables.getSectorWidthLY();
    private final float sectorHeightLY = MagicVariables.getSectorHeightLY();

    public PlayerHyperspaceTripTracker(boolean permaRegister) {
        super(permaRegister);
    }

    public static void register() {
        for(CampaignEventListener listener : Global.getSector().getAllListeners()){
            if (listener instanceof PlayerHyperspaceTripTracker) return;
        }

        //this is a travesty, alex why
        PlayerHyperspaceTripTracker tracker = new PlayerHyperspaceTripTracker(true);
        Global.getSector().getListenerManager().addListener(tracker);
    }

    @Override
    public void onNewDay() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Vector2f currentLoc = fleet.getLocationInHyperspace();

        float fuelPerLY = fleet.getLogistics().getFuelCostPerLightYear();
        float distanceTravelled = Misc.getDistanceLY(currentLoc, lastPos);
        float percentTotalFromCore = calculateDistancePercentage(currentLoc);

        if (percentTotalFromCore < MIN_TRIP_DISTANCE_PERCENT_FOR_START) {
            if (currentTrip.isActive & currentTrip.days > MIN_TRIP_DAYS_FOR_RECORD) commitTripAndRestart();
        } else {
            if (!currentTrip.isActive) currentTrip.setActive(true);

            currentTrip.incrementDays();
            currentTrip.addDistance(distanceTravelled);
            currentTrip.addFuelUsed(fuelPerLY * distanceTravelled);
            currentTrip.updateFurthestOut(percentTotalFromCore);
        }

        lastPos = new Vector2f(currentLoc);
    }

    public void commitTripAndRestart() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String id = "$IndEvo_tripData";

        if (!mem.contains(id)) mem.set(id, new LinkedList<TripData>());
        ((List<TripData>) mem.get(id)).add(currentTrip);

        currentTrip = new TripData();
        lastPos = Global.getSector().getPlayerFleet().getLocationInHyperspace();
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        //this is so if the player uses a gate the distance does not count against the moved distance & fuel use
        if (fleet.isPlayerFleet() && to != null && to.getDestination() != null && to.getDestination().isInHyperspace())
            lastPos = Global.getSector().getPlayerFleet().getLocationInHyperspace();
    }

    public float calculateDistancePercentage(Vector2f currentLoc) {
        float x = currentLoc.x;
        float y = currentLoc.y;

        float distanceToRight = sectorWidthLY - x;
        float distanceToBottom = sectorHeightLY - y;

        float minDistanceToEdge = Math.min(Math.min(x, distanceToRight), Math.min(y, distanceToBottom));
        float maxPossibleDistance = Math.min(sectorWidthLY / 2, sectorHeightLY / 2);

        return (minDistanceToEdge / maxPossibleDistance) * 100f;
    }

    public static class TripData {
        public boolean isActive = false;
        public float distanceLY = 0f;
        public float fuelUse = 0f;
        public float days = 0f;
        public float furthestOutPercent = 0f;

        public void addDistance(float ly) {
            distanceLY += ly;
        }

        public void addFuelUsed(float amt) {
            fuelUse += amt;
        }

        public void incrementDays() {
            days += 1;
        }

        public void updateFurthestOut(float percent) {
            if (percent > furthestOutPercent) furthestOutPercent = percent;
        }

        public boolean isActive() {
            return isActive;
        }

        public void setActive(boolean active) {
            isActive = active;
        }
    }
}
