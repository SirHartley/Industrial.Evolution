package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleAnchor;
import indevo.ambience.shuttletraffic.ShuttleFleetScan;
import indevo.ambience.shuttletraffic.ShuttleHull;
import indevo.ambience.shuttletraffic.ShuttleHullPool;
import indevo.ambience.shuttletraffic.ShuttleLook;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;
import indevo.ambience.shuttletraffic.SpeedClass;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

// craft between a market pad and a fleet that loads, unloads or prepares in orbit there
public class FleetFlights extends BaseFlights {

    private static final float ANCHOR_RANGE = 400f;
    private static final float ARRIVED_RANGE = 1500f;
    private static final float BASE_SHARE = 0.7f;
    private static final Set<Shuttle.Trait> SERVICE_TRAITS = Collections.unmodifiableSet(EnumSet.of(Shuttle.Trait.AVOIDS, Shuttle.Trait.FLEES));

    public FleetFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    // after every fleet scan: routes for serviced fleets, the rest end
    public void update() {
        Map<String, ShuttleRoute> routes = traffic.getFleetRoutes();
        for (ShuttleRoute route : routes.values()) if (route.kind == ShuttleRoute.Kind.FLEET_SERVICE) route.seen = false;

        ShuttleFleetScan scan = traffic.getFleetScan();
        for (int i = 0; i < scan.getCount(); i++) {
            ShuttleFleetScan.Entry entry = scan.get(i);
            ShuttleAnchor pad = findServicePad(entry);
            if (pad == null) continue;

            String key = ShuttleRoute.Kind.FLEET_SERVICE.name() + "|" + entry.fleet.getId();
            ShuttleRoute route = routes.get(key);
            if (route == null || route.a != pad) {
                if (route != null) end(route);
                route = new ShuttleRoute(key, ShuttleRoute.Kind.FLEET_SERVICE, pad, ShuttleAnchor.forFleet(entry.fleet), 0f);
                route.fleet = entry.fleet;
                route.timer = random.nextFloat() * 2f;
                route.demand = getServiceCount(entry.fleet, entry.type, pad);
                route.max = route.demand;
                routes.put(key, route);
            }

            route.seen = true;
        }

        Iterator<ShuttleRoute> iterator = routes.values().iterator();
        while (iterator.hasNext()) {
            ShuttleRoute route = iterator.next();
            if (route.kind != ShuttleRoute.Kind.FLEET_SERVICE || route.seen) continue;

            end(route);
            iterator.remove();
        }
    }

    public Shuttle launchService(ShuttleRoute route) {
        CampaignFleetAPI fleet = route.fleet;
        ShuttleAnchor pad = route.a;
        if (fleet == null || !fleet.isAlive() || !canFly(pad, pad)) return null;

        String type = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
        boolean smuggler = FleetTypes.TRADE_SMUGGLER.equals(type);
        boolean patrol = isPatrol(type);
        boolean toFleet = random.nextBoolean();

        // a fleet sends its own boats down, the port sends base or local craft up
        FactionAPI faction = toFleet ? pad.faction : fleet.getFaction();
        ShuttleHull hull = toFleet ? ShuttleHullPool.rollCargo(pad.faction, random, BASE_SHARE, true) : ShuttleHullPool.rollSmall(fleet.getFaction(), random);
        if (hull == null) return null;

        ShuttleAnchor from = toFleet ? pad : route.b;
        ShuttleAnchor to = toFleet ? route.b : pad;
        Shuttle shuttle = create(hull, route, faction, from, to, paths.servicePath(pad, fleet, toFleet, smuggler), patrol ? SpeedClass.CIVIL : SpeedClass.SERVICE, 1f,
                0f, smuggler ? ShuttleLook.SMUGGLER : ShuttleLook.SERVICE, SERVICE_TRAITS);
        shuttle.tieTo(fleet);
        return shuttle;
    }

    // no more launches; craft flying to the fleet turn back to the pad, craft from it carry on, all go when the fleet is out of sight
    private void end(ShuttleRoute route) {
        boolean seen = route.fleet != null && route.fleet.isAlive() && route.fleet.getSensorFaderBrightness() > 0.01f
                && route.fleet.getContainingLocation() == traffic.getLocation();

        for (Shuttle shuttle : traffic.getShuttles()) {
            if (shuttle.getRoute() != route || shuttle.isDone()) continue;

            if (!seen) {
                shuttle.finish();
                continue;
            }

            if (shuttle.getTo() == route.b && shuttle.canRedirect() && route.a.isUsable(traffic.getLocation())) shuttle.redirect(route.a, traffic);
        }
    }

    private ShuttleAnchor findServicePad(ShuttleFleetScan.Entry entry) {
        CampaignFleetAPI fleet = entry.fleet;
        if (fleet == null || fleet.isPlayerFleet() || fleet.isStationMode() || !fleet.isAlive()) return null;
        if (entry.assignment != FleetAssignment.ORBIT_PASSIVE) return null;
        if (fleet.getVisibilityLevelToPlayerFleet() == SectorEntityToken.VisibilityLevel.NONE) return null;

        ShuttleAnchor pad = entry.target != null ? network.anchors.get(entry.target) : null;
        if (pad == null || pad.role != ShuttleAnchor.Role.MARKET) pad = findNearestMarket(fleet);
        if (pad == null || !pad.isUsable(traffic.getLocation()) || pad.faction == null || pad.faction.isHostileTo(fleet.getFaction())) return null;

        float gap = Misc.getDistance(fleet.getLocation(), pad.getLocation()) - pad.getRadius();
        return gap <= ARRIVED_RANGE ? pad : null;
    }

    private ShuttleAnchor findNearestMarket(CampaignFleetAPI fleet) {
        ShuttleAnchor best = null;
        float bestGap = ANCHOR_RANGE;
        for (ShuttleAnchor anchor : network.anchors.values()) {
            if (anchor.role != ShuttleAnchor.Role.MARKET) continue;

            float gap = Misc.getDistance(fleet.getLocation(), anchor.getLocation()) - anchor.getRadius() - fleet.getRadius();
            if (gap < bestGap) {
                bestGap = gap;
                best = anchor;
            }
        }
        return best;
    }

    // smugglers and patrols get 2 or 3 craft, everyone else a cargo stream by fleet size and port density
    private int getServiceCount(CampaignFleetAPI fleet, String type, ShuttleAnchor pad) {
        if (FleetTypes.TRADE_SMUGGLER.equals(type) || isPatrol(type)) return 2 + random.nextInt(2);

        int stream = Math.max(3, Math.min(8, 2 + fleet.getNumShips() / 3));
        return Math.max(1, Math.round(stream * pad.density));
    }

    private static boolean isPatrol(String type) {
        return FleetTypes.PATROL_SMALL.equals(type) || FleetTypes.PATROL_MEDIUM.equals(type) || FleetTypes.PATROL_LARGE.equals(type);
    }
}
