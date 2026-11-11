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
            if (fleetScan.findThreat(loc.x, loc.y, anchor.getRadius() + ANCHOR_THREAT_RANGE, anchor.faction) == null) continue;

            anchor.threatened = true;
            anchor.lastThreatDay = traffic.getDays();
            anchor.overrun = fleetScan.findThreat(loc.x, loc.y, anchor.getRadius(), anchor.faction) != null;
        }
    }

    public void react() {
        ShuttleFleetScan fleetScan = traffic.getFleetScan();
        for (Shuttle shuttle : traffic.getShuttles()) {
            if (shuttle.isDone() || shuttle.isDown() || shuttle.isFollower() || shuttle.isWaiting() || shuttle.isHit()) continue;

            if (shuttle.isStranded()) {
                if (fleetScan.findThreat(shuttle.pos.x, shuttle.pos.y, STRANDED_CLEAR_RANGE, shuttle.faction) != null) shuttle.markThreatNear(traffic.getDays());
                continue;
            }

            if (shuttle.isPicket()) {
                ShuttleAnchor home = shuttle.getHome();
                ShuttleFleetScan.Entry threat = home != null && home.threatened ? findAnchorThreat(home) : null;
                if (home != null && home.overrun) flee(shuttle, threat);
                else if (threat != null) shuttle.screen(threat.x, threat.y, traffic);
                else divertIfClosed(shuttle);
                continue;
            }

            if (!shuttle.flees()) {
                divertIfClosed(shuttle);
                continue;
            }

            boolean destinationThreatened = shuttle.getTo() != null && shuttle.getTo().threatened;
            ShuttleFleetScan.Entry near = fleetScan.findThreat(shuttle.pos.x, shuttle.pos.y, CRAFT_THREAT_RANGE, shuttle.faction);

            if (destinationThreatened || (near != null && !shuttle.isFleeing())) {
                flee(shuttle, near != null ? near : findAnchorThreat(shuttle.getTo()));
                continue;
            }

            divertIfClosed(shuttle);
        }
    }

    private void flee(Shuttle shuttle, ShuttleFleetScan.Entry threat) {
        ShuttleNetwork network = traffic.getNetwork();
        float threatX = threat != null ? threat.x : shuttle.pos.x;
        float threatY = threat != null ? threat.y : shuttle.pos.y;

        List<Shuttle> wing = new ArrayList<>(shuttle.getFollowers());
        shuttle.flee(network.findRefuge(null, shuttle.faction, shuttle.pos, traffic.getLocation()), threatX, threatY, traffic);
        for (Shuttle follower : wing) follower.flee(network.findRefuge(null, follower.faction, follower.pos, traffic.getLocation()), threatX, threatY, traffic);
    }

    private ShuttleFleetScan.Entry findAnchorThreat(ShuttleAnchor anchor) {
        if (anchor == null) return null;

        Vector2f loc = anchor.getLocation();
        return traffic.getFleetScan().findThreat(loc.x, loc.y, anchor.getRadius() + ANCHOR_THREAT_RANGE, anchor.faction);
    }

    private void divertIfClosed(Shuttle shuttle) {
        ShuttleAnchor to = shuttle.getTo();
        if (to == null || shuttle.isScreening() || to.isUsable(traffic.getLocation())) return;

        ShuttleAnchor refuge = traffic.getNetwork().findRefuge(to, shuttle.faction, shuttle.pos, traffic.getLocation());
        if (refuge != null) shuttle.redirect(refuge, traffic);
        else if (!to.entity.isAlive() || to.entity.getContainingLocation() != traffic.getLocation()) shuttle.finish();
    }
}
