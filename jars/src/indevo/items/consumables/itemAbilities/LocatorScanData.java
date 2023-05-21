package indevo.items.consumables.itemAbilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.abilities.GraviticScanData;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import indevo.items.consumables.listeners.LocatorSystemRatingUpdater;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LocatorScanData {

    public LocatorScanData(LocatorAbilityPlugin ability) {
        this.ability = ability;
    }

    private LocatorAbilityPlugin ability;
    private int resolution = 360;
    transient private float[] data;
    private List<GraviticScanData.GSPing> pings = new ArrayList<>();
    private IntervalUtil pingInterval = new IntervalUtil(0.01f, 0.01f);

    public void advance(float days) {
        if (ability.getFleet() == null || ability.getFleet().getContainingLocation() == null) return;


        Iterator<GraviticScanData.GSPing> iter = pings.iterator();
        while (iter.hasNext()) {
            GraviticScanData.GSPing ping = iter.next();
            ping.advance(days);
            if (ping.isDone()) {
                iter.remove();
            }
        }

        pingInterval.advance(days);
        if (pingInterval.intervalElapsed()) {
            maintainSystemPings();
        }

        updateData();
    }

    public void maintainSystemPings() {
        CampaignFleetAPI fleet = ability.getFleet();
        Vector2f loc = fleet.getLocation();

        if (!fleet.isInHyperspace()) {
            return;
        }

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            SectorEntityToken entity = system.getHyperspaceAnchor();
            if (entity == null || entity.getLocation() == null) continue;

            float dist = Misc.getDistance(loc, entity.getLocation());

            float arc = Misc.computeAngleSpan(entity.getRadius(), dist);
            arc *= 2f;
            if (arc > 150f) arc = 150f;
            if (arc < 20) arc = 20;

            float angle = Misc.getAngleInDegrees(loc, entity.getLocation());

            float g = LocatorSystemRatingUpdater.getRating(system);
            g *= getRangeGMult(dist);

            float in = pingInterval.getIntervalDuration() * 5f;
            GraviticScanData.GSPing ping = new GraviticScanData.GSPing(angle, arc, g, in, in);
            pings.add(ping);
        }
    }

    public void updateData() {
        data = new float[resolution];

        float max = 0f;
        float incr = 360f / (float) resolution;
        for (GraviticScanData.GSPing ping : pings) {

            float b = ping.fader.getBrightness();
            if (b <= 0) continue;

            float arc = ping.arc;
            float mid = ping.angle;
            float half = (float) Math.ceil(0.5f * arc / incr);
            for (float i = -half; i <= half; i++) {
                float curr = mid + incr * i;
                int index = getIndex(curr);

                float intensity = 1f - Math.abs(i / half);
                intensity *= intensity;
                float value = ping.grav * intensity * b;
                data[index] += value;
            }
        }
    }

    public float getDataAt(float angle) {
        if (data == null) return 0f;
        int index = getIndex(angle);
        return data[index];
    }

    public int getIndex(float angle) {
        angle = Misc.normalizeAngle(angle);
        int index = (int) Math.floor(resolution * angle / 360f);
        return index;
    }

    private int initialCount = 0;
    private List<SectorEntityToken> special = new ArrayList<SectorEntityToken>();

    private static final float MAX_RANGE = 10000f;

    public float getRangeGMult(float distance) {
        float mult = 0;
        if (distance < MAX_RANGE) mult = MAX_RANGE / distance;

        return mult;
    }
}
