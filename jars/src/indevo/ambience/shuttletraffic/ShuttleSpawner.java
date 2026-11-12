package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.util.Misc;
import indevo.ambience.shuttletraffic.flights.ShuttlePaths;

import java.util.Collection;
import java.util.List;
import java.util.Random;

public class ShuttleSpawner {

    private final ShuttleTraffic traffic;

    public ShuttleSpawner(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    // fills the routes in rounds, one flight per route each round, so the first routes in the map do not take the whole cap
    public void warmStart() {
        Random random = traffic.getRandom();
        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());

        boolean launchedAny = true;
        for (int round = 0; launchedAny; round++) {
            launchedAny = false;
            for (ShuttleRoute route : traffic.getNetwork().routes.values()) {
                if (route.isRare() || round >= route.max || route.active > round) continue;

                List<Shuttle> launched = launch(route);
                if (launched.isEmpty()) continue;

                float fraction = Math.min(0.95f, (round + random.nextFloat()) / route.max);
                for (Shuttle shuttle : launched) shuttle.warmStart(fraction);
                launchedAny = true;
            }
        }
    }

    public void rollRareFlights() {
        Random random = traffic.getRandom();
        for (ShuttleRoute route : traffic.getNetwork().routes.values()) {
            if (route.isRare() && !route.pending && route.active < route.max && random.nextFloat() < route.chancePerDay) route.pending = true;
        }
    }

    public void spawn(float amount) {
        traffic.getBudget().count(traffic.getShuttles());
        spawn(traffic.getNetwork().routes.values(), amount);
        spawn(traffic.getFleetRoutes().values(), amount);
    }

    private void spawn(Collection<ShuttleRoute> routes, float amount) {
        for (ShuttleRoute route : routes) {
            if (route.active >= route.max) continue;

            if (route.isRare()) {
                if (!route.pending) continue;
            } else {
                route.timer -= amount;
                if (route.timer > 0f) continue;
                route.timer = getInterval(route);
            }

            List<Shuttle> launched = launch(route);
            if (launched.isEmpty()) continue;

            route.pending = false;
        }
    }

    private List<Shuttle> launch(ShuttleRoute route) {
        ShuttleBudget budget = traffic.getBudget();
        List<Shuttle> launched = traffic.getFlights().launch(route, budget.getRoom(route.kind.group));
        if (launched.isEmpty()) return launched;

        for (Shuttle shuttle : launched) shuttle.setGroup(route.kind.group);
        budget.add(route.kind.group, launched.size());
        route.active++;
        traffic.getShuttles().addAll(launched);
        return launched;
    }

    private float getInterval(ShuttleRoute route) {
        float jitter = 0.6f + 0.8f * traffic.getRandom().nextFloat();
        if (route.b == null) return route.kind.pause * jitter;

        float distance = Misc.getDistance(route.a.getLocation(), route.b.getLocation());
        if (route.kind == ShuttleRoute.Kind.TRANSIT) distance = Math.min(distance, route.a.getRadius() + ShuttlePaths.TRANSIT_REACH);
        float flightTime = distance / route.kind.speed.getMeanSpeed() + 6f;
        return flightTime / Math.max(1, route.max) * jitter;
    }
}
