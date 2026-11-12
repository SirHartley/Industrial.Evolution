package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.util.Misc;

import org.lwjgl.util.vector.Vector2f;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public class ShuttleSpawner {

    // a craft placed mid flight closer to the player than this could pop up on screen, it launches from its pad later
    private static final float WARM_CLEARANCE = 3000f;

    private final ShuttleTraffic traffic;

    public ShuttleSpawner(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    public void warmStart() {
        warmStart(route -> true, null);
    }

    // a market that came into range is busy by the time the player gets there
    public void warmStart(List<ShuttleAnchor> arrived, Vector2f player) {
        warmStart(route -> route.kind.group == ShuttleBudget.Group.LOCAL && arrived.contains(route.a), player);
    }

    // fills the routes in rounds, one flight per route each round, so the first routes in the map do not take the whole cap
    private void warmStart(Predicate<ShuttleRoute> filter, Vector2f player) {
        Random random = traffic.getRandom();
        ShuttleBudget budget = traffic.getBudget();
        budget.count(traffic.getShuttles());

        boolean launchedAny = true;
        for (int round = 0; launchedAny; round++) {
            launchedAny = false;
            for (ShuttleRoute route : traffic.getNetwork().routes.values()) {
                if (route.isRare() || round >= route.max || route.active > round || !filter.test(route)) continue;

                List<Shuttle> launched = traffic.getFlights().launch(route, budget.getRoom(route.kind.group));
                if (launched.isEmpty()) continue;

                launchedAny = true;
                float fraction = Math.min(0.95f, (round + random.nextFloat()) / route.max);
                for (Shuttle shuttle : launched) shuttle.warmStart(fraction);
                if (player == null || isClear(launched, player)) add(route, launched);
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
        List<Shuttle> launched = traffic.getFlights().launch(route, traffic.getBudget().getRoom(route.kind.group));
        if (!launched.isEmpty()) add(route, launched);
        return launched;
    }

    private void add(ShuttleRoute route, List<Shuttle> launched) {
        for (Shuttle shuttle : launched) shuttle.setGroup(route.kind.group);
        traffic.getBudget().add(route.kind.group, launched.size());
        route.active++;
        traffic.getShuttles().addAll(launched);
    }

    private static boolean isClear(List<Shuttle> launched, Vector2f player) {
        for (Shuttle shuttle : launched) {
            if (Misc.getDistance(shuttle.pos, player) < WARM_CLEARANCE) return false;
        }
        return true;
    }

    private float getInterval(ShuttleRoute route) {
        float jitter = 0.6f + 0.8f * traffic.getRandom().nextFloat();
        if (route.b == null) return route.kind.pause * jitter;

        float flightTime = Misc.getDistance(route.a.getLocation(), route.b.getLocation()) / route.kind.speed.getMeanSpeed() + 6f;
        return flightTime / Math.max(1, route.max) * jitter;
    }
}
