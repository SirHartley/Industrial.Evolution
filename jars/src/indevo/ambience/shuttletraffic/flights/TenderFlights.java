package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleBudget;
import indevo.ambience.shuttletraffic.ShuttleFleetScan;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;
import indevo.ambience.shuttletraffic.movement.SteerMover;
import org.lwjgl.util.vector.Vector2f;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// small craft between a market and a huge fleet that passes without stopping: half meet it at dash speed, half drop out of it and land
public class TenderFlights extends BaseFlights {

    private static final float TENDER_FP = 150f;
    private static final float TENDER_MORE_FP = 300f;
    private static final float PASS_RANGE = 1200f;
    private static final float INTERCEPT_RANGE = 1500f;
    private static final float MIN_PASS_SPEED = 60f;
    private static final float BASE_SHARE = 0.7f;
    private static final float DROP_TIME = 2f;
    private static final float DROP_SIDE = 60f;
    private static final float LEAD_TIME = 4f;
    private static final float FOLD_LENGTH = ShuttleAnchor.FLEET_END_LENGTH;
    private static final float PASS_GAP_DAYS = 1f;
    private static final Set<Shuttle.Trait> TENDER_TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.FLEES));

    // keyed by market and fleet: the last day the fleet was in range, a new pass needs a day out of range
    private final Map<String, Float> lastInRange = new HashMap<>();

    // outbound: lead the fleet at dash speed, fade into it; turns back and lands when it can no longer catch up
    private final class Meet implements Shuttle.Steering {
        final ShuttleAnchor pad;
        final CampaignFleetAPI fleet;

        Meet(ShuttleAnchor pad, CampaignFleetAPI fleet) {
            this.pad = pad;
            this.fleet = fleet;
        }

        @Override
        public float aim(Shuttle shuttle, ShuttleTraffic traffic, Vector2f point, Vector2f velocity) {
            if (!fleet.isAlive() || fleet.getContainingLocation() != traffic.getLocation()) {
                land(shuttle, pad, traffic);
                return 0f;
            }

            Vector2f loc = fleet.getLocation();
            Vector2f vel = fleet.getVelocity();
            float top = SteerMover.getDashSpeed(vel.length());
            float dist = Misc.getDistance(shuttle.pos, loc);
            if (dist < 3f) {
                shuttle.finish();
                return 0f;
            }

            if (dist > FOLD_LENGTH + fleet.getRadius()) {
                float t = SteerMover.interceptTime(shuttle.pos, loc, vel, top);
                if (Float.isNaN(t) || Misc.getDistance(pad.getLocation(), new Vector2f(loc.x + vel.x * t, loc.y + vel.y * t)) > INTERCEPT_RANGE + pad.getRadius() * 2f) {
                    land(shuttle, pad, traffic);
                    return 0f;
                }

                float lead = Math.min(LEAD_TIME, t);
                point.set(loc.x + vel.x * lead, loc.y + vel.y * lead);
            } else {
                float t = Math.min(1f, dist / FOLD_LENGTH);
                shuttle.setSteerLook(1f - 0.3f * (1f - t), t);
                point.set(loc);
            }

            velocity.set(vel);
            return top;
        }
    }

    // inbound: hidden in the fleet until its turn, out toward the market side keeping the fleet's speed for a moment, then a normal path to the pad
    private final class Drop implements Shuttle.Steering {
        final ShuttleAnchor pad;
        final CampaignFleetAPI fleet;
        final float start;

        Drop(ShuttleAnchor pad, CampaignFleetAPI fleet, float start) {
            this.pad = pad;
            this.fleet = fleet;
            this.start = start;
        }

        @Override
        public float aim(Shuttle shuttle, ShuttleTraffic traffic, Vector2f point, Vector2f velocity) {
            Vector2f vel = fleet.getVelocity();
            float since = traffic.getTime() - start;
            if (since < 0f) {
                shuttle.setSteerLook(0.5f, 0f);
                point.set(fleet.getLocation());
                velocity.set(vel);
                return SteerMover.getDashSpeed(vel.length());
            }

            if (since >= DROP_TIME || !pad.isUsable(traffic.getLocation()) || !fleet.isAlive()) {
                land(shuttle, pad, traffic);
                return 0f;
            }

            float appear = Math.min(1f, since);
            shuttle.setSteerLook(0.5f + 0.5f * appear, appear);
            float keep = 1f - since / DROP_TIME;
            Vector2f toPad = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(shuttle.pos, pad.getLocation()));
            point.set(shuttle.pos.x + toPad.x * DROP_SIDE, shuttle.pos.y + toPad.y * DROP_SIDE);
            velocity.set(vel.x * keep, vel.y * keep);
            return SteerMover.getDashSpeed(vel.length());
        }
    }

    public TenderFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public void clear() {
        lastInRange.clear();
    }

    // after every fleet scan
    public void update() {
        ShuttleFleetScan scan = traffic.getFleetScan();
        float days = traffic.getDays();
        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            CampaignFleetAPI fleet = entry.fleet;
            if (fleet.isPlayerFleet() || !entry.visible || fleet.getBattle() != null || fleet.getFleetData().getFleetPointsUsed() < TENDER_FP) continue;
            if (traffic.getFleetRoutes().containsKey(ShuttleRoute.Kind.FLEET_SERVICE.name() + "|" + fleet.getId())) continue;

            for (ShuttleAnchor pad : network.marketAnchors) {
                if (pad.faction == null || pad.faction.isHostileTo(fleet.getFaction())) continue;
                if (Misc.getDistance(fleet.getLocation(), pad.getLocation()) - pad.getRadius() > INTERCEPT_RANGE) continue;

                String key = pad.entity.getId() + "|" + fleet.getId();
                Float last = lastInRange.put(key, days);
                if (last != null && days - last < PASS_GAP_DAYS) continue;
                if (isPassing(fleet, pad)) launch(pad, fleet);
            }
        }

        lastInRange.values().removeIf(day -> days - day > PASS_GAP_DAYS * 2f);
    }

    // moving, and the closest approach from its velocity is still ahead and under 1200 su
    private static boolean isPassing(CampaignFleetAPI fleet, ShuttleAnchor pad) {
        Vector2f vel = fleet.getVelocity();
        float v2 = vel.lengthSquared();
        if (v2 < MIN_PASS_SPEED * MIN_PASS_SPEED) return false;

        float rx = fleet.getLocation().x - pad.getLocation().x;
        float ry = fleet.getLocation().y - pad.getLocation().y;
        float t = -(rx * vel.x + ry * vel.y) / v2;
        if (t <= 0f) return false;

        float mx = rx + vel.x * t;
        float my = ry + vel.y * t;
        return (float) Math.sqrt(mx * mx + my * my) - pad.getRadius() < PASS_RANGE;
    }

    private void launch(ShuttleAnchor pad, CampaignFleetAPI fleet) {
        if (!canFly(pad, pad)) return;

        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());
        boolean large = fleet.getFleetData().getFleetPointsUsed() >= TENDER_MORE_FP;
        int count = Math.min(budget.getRoom(ShuttleBudget.Group.FLEET), large ? 4 + random.nextInt(3) : 2 + random.nextInt(3));
        ShuttleAnchor fleetAnchor = ShuttleAnchor.forFleet(fleet);

        for (int i = 0; i < count; i++) {
            boolean out = i % 2 == 0;
            float delay = i * 0.5f + random.nextFloat() * 0.5f;
            Shuttle shuttle = out ? launchMeet(pad, fleet, fleetAnchor) : launchDrop(pad, fleet, fleetAnchor, delay);
            if (shuttle == null) continue;

            shuttle.tieTo(fleet);
            if (out) shuttle.holdFor(delay);
            shuttle.setGroup(ShuttleBudget.Group.FLEET);
            budget.add(ShuttleBudget.Group.FLEET, 1);
            traffic.getShuttles().add(shuttle);
        }
    }

    private Shuttle launchMeet(ShuttleAnchor pad, CampaignFleetAPI fleet, ShuttleAnchor fleetAnchor) {
        Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(pad.getLocation(), fleet.getLocation()));
        Vector2f start = new Vector2f(pad.getLocation().x + dir.x * pad.getRadius() * pad.getPadDepth(), pad.getLocation().y + dir.y * pad.getRadius() * pad.getPadDepth());
        float speed = SteerMover.getDashSpeed(fleet.getVelocity().length());
        float t = SteerMover.interceptTime(start, fleet.getLocation(), fleet.getVelocity(), speed);
        if (Float.isNaN(t)) return null;

        Vector2f meet = new Vector2f(fleet.getLocation().x + fleet.getVelocity().x * t, fleet.getLocation().y + fleet.getVelocity().y * t);
        if (Misc.getDistance(meet, pad.getLocation()) - pad.getRadius() > INTERCEPT_RANGE) return null;

        ShuttleHull hull = ShuttleHullPool.rollCargo(pad.faction, random, BASE_SHARE, true);
        if (hull == null) return null;

        Shuttle shuttle = new Shuttle(hull, null, pad.faction, pad, fleetAnchor, null, pad.getEnd(), Shuttle.End.FLEET, SpeedClass.SERVICE, 1f, 0f, random.nextFloat(),
                ShuttleLook.SERVICE, TENDER_TRAITS);
        shuttle.pos.set(start);
        shuttle.beginSteer(new Meet(pad, fleet), new Vector2f(dir.x * 30f, dir.y * 30f), true);
        return shuttle;
    }

    private Shuttle launchDrop(ShuttleAnchor pad, CampaignFleetAPI fleet, ShuttleAnchor fleetAnchor, float delay) {
        ShuttleHull hull = ShuttleHullPool.rollSmall(fleet.getFaction(), random);
        if (hull == null) return null;

        Shuttle shuttle = new Shuttle(hull, null, fleet.getFaction(), fleetAnchor, pad, null, Shuttle.End.FLEET, pad.getEnd(), SpeedClass.SERVICE, 1f, 0f, random.nextFloat(),
                ShuttleLook.SERVICE, TENDER_TRAITS);
        shuttle.pos.set(fleet.getLocation());
        shuttle.beginSteer(new Drop(pad, fleet, traffic.getTime() + delay), new Vector2f(fleet.getVelocity()), false);
        return shuttle;
    }

    private static void land(Shuttle shuttle, ShuttleAnchor pad, ShuttleTraffic traffic) {
        if (pad.isUsable(traffic.getLocation())) shuttle.landAt(pad, traffic);
        else shuttle.finish();
    }
}
