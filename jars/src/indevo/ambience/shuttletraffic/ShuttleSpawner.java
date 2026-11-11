package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Random;

public class ShuttleSpawner {

    private final ShuttleTraffic traffic;

    public ShuttleSpawner(ShuttleTraffic traffic) {
        this.traffic = traffic;
    }

    public void warmStart(int cap) {
        Random random = traffic.getRandom();
        for (ShuttleRoute route : traffic.getNetwork().routes.values()) {
            if (route.isRare()) continue;

            for (int i = 0; i < route.max; i++) {
                List<Shuttle> launched = traffic.getFlights().launch(route, getRoom(route, cap));
                if (launched.isEmpty()) break;

                float fraction = Math.min(0.95f, (i + random.nextFloat()) / route.max);
                for (Shuttle shuttle : launched) shuttle.warmStart(fraction);
                route.active++;
                traffic.getShuttles().addAll(launched);
            }
        }
    }

    public void rollRareFlights() {
        Random random = traffic.getRandom();
        for (ShuttleRoute route : traffic.getNetwork().routes.values()) {
            if (route.isRare() && !route.pending && route.active < route.max && random.nextFloat() < route.chancePerDay) route.pending = true;
        }
    }

    public void spawn(float amount, int cap) {
        for (ShuttleRoute route : traffic.getNetwork().routes.values()) {
            if (route.active >= route.max) continue;

            if (route.isRare()) {
                if (!route.pending) continue;
            } else {
                route.timer -= amount;
                if (route.timer > 0f) continue;
                route.timer = getInterval(route);
            }

            List<Shuttle> launched = traffic.getFlights().launch(route, getRoom(route, cap));
            if (launched.isEmpty()) continue;

            route.pending = false;
            route.active++;
            traffic.getShuttles().addAll(launched);
        }
    }

    private int getRoom(ShuttleRoute route, int cap) {
        return (int) (cap * route.kind.capShare) - traffic.getShuttles().size();
    }

    private float getInterval(ShuttleRoute route) {
        float jitter = 0.6f + 0.8f * traffic.getRandom().nextFloat();
        if (route.b == null) return route.kind.pause * jitter;

        float flightTime = Misc.getDistance(route.a.getLocation(), route.b.getLocation()) / route.kind.speed.getMeanSpeed() + 6f;
        return flightTime / Math.max(1, route.max) * jitter;
    }
}
