package indevo.ambience.shuttletraffic.flights;

import indevo.ambience.shuttletraffic.Shuttle;
import indevo.ambience.shuttletraffic.ShuttleRoute;
import indevo.ambience.shuttletraffic.ShuttleTraffic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ShuttleFlights {

    @FunctionalInterface
    public interface Flight {
        List<Shuttle> launch(ShuttleRoute route, int room);
    }

    private final ShuttlePaths paths;
    private final PodFlights pods;
    private final FleetFlights fleets;
    private final EventFlights events;
    private final Map<ShuttleRoute.Kind, Flight> flights = new EnumMap<>(ShuttleRoute.Kind.class);

    public ShuttleFlights(ShuttleTraffic traffic) {
        paths = new ShuttlePaths(traffic.getNetwork(), traffic.getRandom());
        pods = new PodFlights(traffic, paths);
        fleets = new FleetFlights(traffic, paths);
        LaneFlights lanes = new LaneFlights(traffic, paths);
        LocalFlights local = new LocalFlights(traffic, paths);
        events = new EventFlights(traffic, paths, local);

        Flight lane = (route, room) -> BaseFlights.single(lanes.launchLane(route));
        flights.put(ShuttleRoute.Kind.SUPPLY, lane);
        flights.put(ShuttleRoute.Kind.HOP, lane);
        flights.put(ShuttleRoute.Kind.SURFACE, (route, room) -> BaseFlights.single(local.launchSurface(route)));
        flights.put(ShuttleRoute.Kind.MARKET, lanes::launchMarket);
        flights.put(ShuttleRoute.Kind.PIRATE_CARGO, lane);
        flights.put(ShuttleRoute.Kind.SMUGGLER, lane);
        flights.put(ShuttleRoute.Kind.COURIER, (route, room) -> BaseFlights.single(lanes.launchCourier(route)));
        flights.put(ShuttleRoute.Kind.PILGRIM, (route, room) -> BaseFlights.single(lanes.launchPilgrim(route)));
        flights.put(ShuttleRoute.Kind.DARK, (route, room) -> BaseFlights.single(lanes.launchDark(route)));
        flights.put(ShuttleRoute.Kind.DIPLOMAT, (route, room) -> BaseFlights.single(lanes.launchDiplomat(route)));
        flights.put(ShuttleRoute.Kind.HUNT, (route, room) -> BaseFlights.single(lanes.launchHunt(route)));
        flights.put(ShuttleRoute.Kind.CRUISE, local::launchCruise);
        flights.put(ShuttleRoute.Kind.PICKET, local::launchPicket);
        flights.put(ShuttleRoute.Kind.TRAINING, local::launchTraining);
        flights.put(ShuttleRoute.Kind.PARADE, local::launchParade);
        flights.put(ShuttleRoute.Kind.TOW, local::launchTow);
        flights.put(ShuttleRoute.Kind.MINING, (route, room) -> BaseFlights.single(local.launchLinger(route)));
        flights.put(ShuttleRoute.Kind.SALVAGE, (route, room) -> BaseFlights.single(local.launchLinger(route)));
        flights.put(ShuttleRoute.Kind.MAINTENANCE, (route, room) -> BaseFlights.single(local.launchMaintenance(route)));
        flights.put(ShuttleRoute.Kind.FLEET_SERVICE, (route, room) -> BaseFlights.single(fleets.launchService(route)));
        flights.put(ShuttleRoute.Kind.TRADE, (route, room) -> Collections.emptyList());
        flights.put(ShuttleRoute.Kind.SURVEY, (route, room) -> BaseFlights.single(fleets.launchSurvey(route)));

        for (ShuttleRoute.Kind kind : ShuttleRoute.Kind.values()) {
            if (!flights.containsKey(kind)) throw new IllegalStateException("Shuttle traffic: no flight for route kind " + kind);
        }
    }

    public List<Shuttle> launch(ShuttleRoute route, int room) {
        if (room <= 0) return Collections.emptyList();
        return flights.get(route.kind).launch(route, room);
    }

    public ShuttlePaths getPaths() {
        return paths;
    }

    public PodFlights getPods() {
        return pods;
    }

    public FleetFlights getFleets() {
        return fleets;
    }

    public EventFlights getEvents() {
        return events;
    }
}
