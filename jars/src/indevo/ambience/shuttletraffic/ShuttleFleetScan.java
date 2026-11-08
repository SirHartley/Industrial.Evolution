package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Arrays;

public class ShuttleFleetScan {

    public static final float SLOW_SPEED = 40f;
    public static final int MIN_SHIPS = 5;
    public static final float RANGE = 3000f;

    public float[] x = new float[16];
    public float[] y = new float[16];
    public float[] radius = new float[16];
    public int count = 0;

    public void scan(LocationAPI location, CampaignFleetAPI player) {
        count = 0;

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (fleet.isStationMode()) continue;
            if (!fleet.isPlayerFleet() && fleet.getNumShips() < MIN_SHIPS) continue;
            if (fleet.getVelocity().length() >= SLOW_SPEED) continue;
            if (Misc.getDistance(player.getLocation(), fleet.getLocation()) > RANGE) continue;

            add(fleet.getLocation().x, fleet.getLocation().y, fleet.getRadius());
        }
    }

    public void clear() {
        count = 0;
    }

    private void add(float px, float py, float r) {
        if (count == x.length) {
            x = Arrays.copyOf(x, count * 2);
            y = Arrays.copyOf(y, count * 2);
            radius = Arrays.copyOf(radius, count * 2);
        }

        x[count] = px;
        y[count] = py;
        radius[count] = r;
        count++;
    }
}
