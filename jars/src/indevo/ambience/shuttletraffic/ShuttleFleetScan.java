package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Arrays;

public class ShuttleFleetScan {

    public static final float SLOW_SPEED = 40f;
    public static final int MIN_SHIPS = 5;
    public static final float RANGE = 3000f;
    public static final float THREAT_FP = 30f;

    public float[] x = new float[16];
    public float[] y = new float[16];
    public float[] radius = new float[16];
    public boolean[] obstacle = new boolean[16];
    public boolean[] threat = new boolean[16];
    public FactionAPI[] faction = new FactionAPI[16];
    public int count = 0;

    public void scan(LocationAPI location, CampaignFleetAPI player) {
        count = 0;

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (fleet.isStationMode() || !fleet.isAlive()) continue;

            boolean isPlayer = fleet.isPlayerFleet();
            boolean isObstacle = (isPlayer || fleet.getNumShips() >= MIN_SHIPS) && fleet.getVelocity().length() < SLOW_SPEED
                    && Misc.getDistance(player.getLocation(), fleet.getLocation()) <= RANGE;
            boolean isThreat = isPlayer || fleet.getFleetData().getFleetPointsUsed() >= THREAT_FP;
            if (!isObstacle && !isThreat) continue;

            add(fleet, isObstacle, isThreat);
        }
    }

    public boolean isHostileThreat(int i, FactionAPI to) {
        return threat[i] && to != null && faction[i] != null && faction[i].isHostileTo(to);
    }

    public int findThreat(float px, float py, float range, FactionAPI to) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            if (!isHostileThreat(i, to)) continue;

            float dx = x[i] - px;
            float dy = y[i] - py;
            float dist = dx * dx + dy * dy;
            float reach = range + radius[i];
            if (dist <= reach * reach && dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }

        return best;
    }

    public void clear() {
        count = 0;
        Arrays.fill(faction, null);
    }

    private void add(CampaignFleetAPI fleet, boolean isObstacle, boolean isThreat) {
        if (count == x.length) {
            int size = count * 2;
            x = Arrays.copyOf(x, size);
            y = Arrays.copyOf(y, size);
            radius = Arrays.copyOf(radius, size);
            obstacle = Arrays.copyOf(obstacle, size);
            threat = Arrays.copyOf(threat, size);
            faction = Arrays.copyOf(faction, size);
        }

        x[count] = fleet.getLocation().x;
        y[count] = fleet.getLocation().y;
        radius[count] = fleet.getRadius();
        obstacle[count] = isObstacle;
        threat[count] = isThreat;
        faction[count] = fleet.getFaction();
        count++;
    }
}
