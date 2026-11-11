package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public class ShuttleFleetScan {

    public static final float SLOW_SPEED = 40f;
    public static final int MIN_SHIPS = 5;
    public static final float RANGE = 3000f;
    public static final float THREAT_FP = 30f;

    private final List<Entry> entries = new ArrayList<>();
    private int count = 0;

    // numbers are copied at scan time, the fleet reference gives the live state
    public static final class Entry {
        public CampaignFleetAPI fleet;
        public FactionAPI faction;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float radius;
        public boolean obstacle;
        public boolean threat;
        public boolean visible;
        // ORBIT_PASSIVE fleets are loading, unloading or preparing; fleet tied flights read assignment, target and type
        public FleetAssignment assignment;
        public SectorEntityToken target;
        public String type;

        public boolean isHostileThreat(FactionAPI to) {
            return threat && to != null && faction != null && faction.isHostileTo(to);
        }

        private void set(CampaignFleetAPI fleet, boolean obstacle, boolean threat, FleetAssignmentDataAPI current) {
            this.fleet = fleet;
            this.faction = fleet.getFaction();
            this.x = fleet.getLocation().x;
            this.y = fleet.getLocation().y;
            this.vx = fleet.getVelocity().x;
            this.vy = fleet.getVelocity().y;
            this.radius = fleet.getRadius();
            this.obstacle = obstacle;
            this.threat = threat;
            this.visible = fleet.isVisibleToPlayerFleet();
            this.assignment = current != null ? current.getAssignment() : null;
            this.target = current != null ? current.getTarget() : null;
            this.type = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
        }

        private void clear() {
            fleet = null;
            faction = null;
            target = null;
        }
    }

    public void scan(LocationAPI location, CampaignFleetAPI player) {
        clear();

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (fleet.isStationMode() || !fleet.isAlive()) continue;

            boolean isPlayer = fleet.isPlayerFleet();
            boolean isObstacle = (isPlayer || fleet.getNumShips() >= MIN_SHIPS) && fleet.getVelocity().length() < SLOW_SPEED
                    && Misc.getDistance(player.getLocation(), fleet.getLocation()) <= RANGE;
            boolean isThreat = isPlayer || fleet.getFleetData().getFleetPointsUsed() >= THREAT_FP;
            FleetAssignmentDataAPI current = fleet.getAI() != null ? fleet.getAI().getCurrentAssignment() : null;
            if (!isObstacle && !isThreat && !isTied(current)) continue;

            if (count == entries.size()) entries.add(new Entry());
            entries.get(count++).set(fleet, isObstacle, isThreat, current);
        }
    }

    // fleets that may get fleet tied craft
    private static boolean isTied(FleetAssignmentDataAPI current) {
        return current != null && current.getAssignment() == FleetAssignment.ORBIT_PASSIVE;
    }

    public int getCount() {
        return count;
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public Entry findThreat(float px, float py, float range, FactionAPI to) {
        Entry best = null;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            if (!entry.isHostileThreat(to)) continue;

            float dx = entry.x - px;
            float dy = entry.y - py;
            float dist = dx * dx + dy * dy;
            float reach = range + entry.radius;
            if (dist <= reach * reach && dist < bestDist) {
                bestDist = dist;
                best = entry;
            }
        }

        return best;
    }

    // drops the fleet references so a scan never keeps a despawned fleet alive
    public void clear() {
        for (int i = 0; i < count; i++) entries.get(i).clear();
        count = 0;
    }
}
