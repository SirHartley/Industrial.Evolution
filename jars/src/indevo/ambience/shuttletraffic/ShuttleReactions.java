package indevo.ambience.shuttletraffic;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class ShuttleReactions {

    private static final float ANCHOR_THREAT_RANGE = 600f;
    private static final float CRAFT_THREAT_RANGE = 400f;
    private static final float STRANDED_CLEAR_RANGE = 800f;

    private final ShuttleTraffic traffic;

    public ShuttleReactions(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    public void updateThreats() {
        ShuttleFleetScan fleetScan = traffic.getFleetScan();
        for (ShuttleAnchor anchor : traffic.getNetwork().anchors.values()) {
            anchor.threatened = false;
            anchor.overrun = false;
            if (anchor.role == ShuttleAnchor.Role.REMNANT || anchor.faction == null) continue;

            Vector2f loc = anchor.getLocation();
            if (fleetScan.findThreat(loc.x, loc.y, anchor.getRadius() + ANCHOR_THREAT_RANGE, anchor.faction) < 0) continue;

            anchor.threatened = true;
            anchor.lastThreatDay = traffic.getDays();
            anchor.overrun = fleetScan.findThreat(loc.x, loc.y, anchor.getRadius(), anchor.faction) >= 0;
        }
    }

    public void react() {
        ShuttleFleetScan fleetScan = traffic.getFleetScan();
        for (Shuttle shuttle : traffic.getShuttles()) {
            if (shuttle.done || shuttle.isDown() || shuttle.leader != null || shuttle.isWaiting()) continue;

            if (shuttle.isStranded()) {
                if (fleetScan.findThreat(shuttle.pos.x, shuttle.pos.y, STRANDED_CLEAR_RANGE, shuttle.faction) >= 0) shuttle.lastThreatNearDay = traffic.getDays();
                continue;
            }

            if (shuttle.picket) {
                int threat = shuttle.home != null && shuttle.home.threatened ? findAnchorThreat(shuttle.home) : -1;
                if (shuttle.home != null && shuttle.home.overrun) flee(shuttle, threat);
                else if (threat >= 0) shuttle.screen(fleetScan.x[threat], fleetScan.y[threat], traffic);
                else divertIfClosed(shuttle);
                continue;
            }

            if (!shuttle.flees) {
                divertIfClosed(shuttle);
                continue;
            }

            boolean destinationThreatened = shuttle.to != null && shuttle.to.threatened;
            int near = fleetScan.findThreat(shuttle.pos.x, shuttle.pos.y, CRAFT_THREAT_RANGE, shuttle.faction);

            if (destinationThreatened || (near >= 0 && !shuttle.isFleeing())) {
                flee(shuttle, near >= 0 ? near : findAnchorThreat(shuttle.to));
                continue;
            }

            divertIfClosed(shuttle);
        }
    }

    private void flee(Shuttle shuttle, int threat) {
        ShuttleFleetScan fleetScan = traffic.getFleetScan();
        ShuttleNetwork network = traffic.getNetwork();
        float threatX = threat >= 0 ? fleetScan.x[threat] : shuttle.pos.x;
        float threatY = threat >= 0 ? fleetScan.y[threat] : shuttle.pos.y;

        List<Shuttle> wing = new ArrayList<>(shuttle.followers);
        shuttle.flee(network.findRefuge(null, shuttle.faction, shuttle.pos, traffic.getLocation()), threatX, threatY, traffic);
        for (Shuttle follower : wing) follower.flee(network.findRefuge(null, follower.faction, follower.pos, traffic.getLocation()), threatX, threatY, traffic);
    }

    private int findAnchorThreat(ShuttleAnchor anchor) {
        if (anchor == null) return -1;

        Vector2f loc = anchor.getLocation();
        return traffic.getFleetScan().findThreat(loc.x, loc.y, anchor.getRadius() + ANCHOR_THREAT_RANGE, anchor.faction);
    }

    private void divertIfClosed(Shuttle shuttle) {
        if (shuttle.to == null || shuttle.isScreening() || shuttle.to.isUsable(traffic.getLocation())) return;

        ShuttleAnchor refuge = traffic.getNetwork().findRefuge(shuttle.to, shuttle.faction, shuttle.pos, traffic.getLocation());
        if (refuge != null) shuttle.redirect(refuge, traffic);
        else if (!shuttle.to.entity.isAlive() || shuttle.to.entity.getContainingLocation() != traffic.getLocation()) shuttle.done = true;
    }
}
