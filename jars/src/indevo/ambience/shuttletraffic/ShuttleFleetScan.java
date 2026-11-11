package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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

        public boolean isHostileThreat(FactionAPI to) {
            return threat && to != null && faction != null && faction.isHostileTo(to);
        }

        private void set(CampaignFleetAPI fleet, boolean obstacle, boolean threat) {
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
        }

        private void clear() {
            fleet = null;
            faction = null;
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
            if (!isObstacle && !isThreat) continue;

            if (count == entries.size()) entries.add(new Entry());
            entries.get(count++).set(fleet, isObstacle, isThreat);
        }
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
