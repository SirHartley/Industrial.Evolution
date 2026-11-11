package indevo.ambience.shuttletraffic;

import java.util.Collection;
import java.util.List;

// splits the traffic cap: local flights keep a reserve, rare flights fit while there is room, lanes share the rest
public class ShuttleBudget {

    public enum Group {
        LANES,
        LOCAL,
        RARE
    }

    private static final float LOCAL_SHARE = 0.25f;

    private final int[] counts = new int[Group.values().length];
    private int cap = 0;
    private int localReserve = 0;
    private int laneReserve = 0;

    // route.demand is set by the network rebuild, route.max is what the budget allows
    public void update(Collection<ShuttleRoute> routes, int cap) {
        this.cap = cap;

        int localDemand = 0;
        int laneDemand = 0;
        for (ShuttleRoute route : routes) {
            if (route.kind.group == Group.LOCAL) localDemand += route.demand * route.kind.craftPerFlight;
            else if (route.kind.group == Group.LANES) laneDemand += route.demand;
        }

        localReserve = Math.min(localDemand, (int) (cap * LOCAL_SHARE));
        int laneBudget = Math.max(0, cap - localReserve);
        float scale = laneDemand > laneBudget ? laneBudget / (float) laneDemand : 1f;

        laneReserve = 0;
        for (ShuttleRoute route : routes) {
            route.max = route.kind.group == Group.LANES ? Math.max(1, (int) (route.demand * scale)) : route.demand;
            if (route.kind.group == Group.LANES) laneReserve += route.max;
        }

        laneReserve = Math.min(laneReserve, laneBudget);
    }

    public void count(List<Shuttle> shuttles) {
        for (int i = 0; i < counts.length; i++) counts[i] = 0;
        for (Shuttle shuttle : shuttles) {
            counts[shuttle.getGroup().ordinal()]++;
        }
    }

    public void add(Group group, int craft) {
        counts[group.ordinal()] += craft;
    }

    // a group may use free room that the other reserve does not hold
    public int getRoom(Group group) {
        int free = cap - counts[0] - counts[1] - counts[2];
        return switch (group) {
            case LANES -> free - Math.max(0, localReserve - counts[Group.LOCAL.ordinal()]);
            case LOCAL -> free - Math.max(0, laneReserve - counts[Group.LANES.ordinal()]);
            case RARE -> free;
        };
    }

    public int getCap() {
        return cap;
    }
}
