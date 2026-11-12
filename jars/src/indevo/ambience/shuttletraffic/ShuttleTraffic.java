package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import indevo.ambience.shuttletraffic.dogfights.ShuttleDogfights;
import indevo.ambience.shuttletraffic.flights.ShuttleFlights;
import indevo.ambience.shuttletraffic.flights.ShuttlePaths;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ShuttleTraffic {

    public static final float SCAN_INTERVAL = 0.25f;

    private final ShuttleNetwork network = new ShuttleNetwork();
    private final Random random = new Random();
    private final ShuttleFleetScan fleetScan = new ShuttleFleetScan();
    private final ShuttleBudget budget = new ShuttleBudget();
    private final ShuttleFlights flights = new ShuttleFlights(this);
    private final ShuttleSpawner spawner = new ShuttleSpawner(this);
    private final ShuttleReactions reactions = new ShuttleReactions(this);
    private final ShuttleHits hits = new ShuttleHits(this);
    private final ShuttleDogfights dogfights = new ShuttleDogfights(this);
    // fleets on sustained burn, refreshed with the scan, their live positions checked every frame
    private final List<ShuttleFleetScan.Entry> fastFleets = new ArrayList<>();
    private final List<Shuttle> shuttles = new ArrayList<>();
    private final List<Shuttle> pods = new ArrayList<>();
    // keyed by kind and fleet or market id, outside the daily network rebuild that drops every route it did not re-add
    private final Map<String, ShuttleRoute> fleetRoutes = new LinkedHashMap<>();

    private LocationAPI location;
    private float time = 0f;
    private float days = 0f;
    private float daysSinceRebuild = 0f;
    private float scanTimer = 0f;

    public void advance(float amount, CampaignFleetAPI player, int cap) {
        LocationAPI current = player != null ? player.getContainingLocation() : null;

        if (cap <= 0 || current == null || current.isHyperspace()) {
            if (location != null) clear();
            flights.getPods().clearQueue();
            flights.getFleets().clearQueue();
            return;
        }

        time += amount;
        float elapsedDays = Global.getSector().getClock().convertToDays(amount);
        days += elapsedDays;

        if (current != location) {
            clear();
            location = current;
            network.rebuild(location, random, Global.getSector().getClock().getElapsedDaysSince(0L));
            network.updateSwarms(location);
            network.updateNear(player.getLocation());
            budget.update(network.routes.values(), cap);
            fleetScan.scan(location, player);
            reactions.updateThreats();
            spawner.warmStart();
        } else {
            daysSinceRebuild += elapsedDays;
            if (daysSinceRebuild >= 1f) {
                daysSinceRebuild = 0f;
                ShuttleHullPool.dropStaleFactionPools();
                network.rebuild(location, random, Global.getSector().getClock().getElapsedDaysSince(0L));
                network.updateNear(player.getLocation());
                budget.update(network.routes.values(), cap);
                spawner.rollRareFlights();
            }
        }

        if (cap != budget.getCap()) budget.update(network.routes.values(), cap);

        scanTimer -= amount;
        if (scanTimer <= 0f) {
            scanTimer = SCAN_INTERVAL;
            fleetScan.scan(location, player);
            network.updateSwarms(location);
            if (network.updateNear(player.getLocation())) budget.update(network.routes.values(), cap);
            reactions.updateThreats();
            reactions.react();
            flights.getFleets().update();
            flights.getEvents().update();
            hits.scan();
            dogfights.update();
            fastFleets.clear();
            for (int i = 0; i < fleetScan.getCount(); i++) {
                ShuttleFleetScan.Entry entry = fleetScan.get(i);
                if (entry.fleet.getVelocity().length() >= ShuttleHits.RUN_OVER_SPEED * 0.8f) fastFleets.add(entry);
            }
        }

        hits.advance(fastFleets);

        spawner.spawn(amount);
        flights.getPods().launchQueued();
        flights.getFleets().launchQueued();
        advance(shuttles, amount);
        advance(pods, amount);
        dogfights.advance(amount);
    }

    public void queuePods(CampaignFleetAPI fleet, BattleAPI battle) {
        flights.getPods().queue(fleet, battle);
    }

    public float getTime() {
        return time;
    }

    public float getDays() {
        return days;
    }

    public LocationAPI getLocation() {
        return location;
    }

    public ShuttleDogfights getDogfights() {
        return dogfights;
    }

    public ShuttleNetwork getNetwork() {
        return network;
    }

    public ShuttleBudget getBudget() {
        return budget;
    }

    public ShuttleFleetScan getFleetScan() {
        return fleetScan;
    }

    public ShuttleFlights getFlights() {
        return flights;
    }

    public ShuttlePaths getPaths() {
        return flights.getPaths();
    }

    public Random getRandom() {
        return random;
    }

    public List<Shuttle> getShuttles() {
        return shuttles;
    }

    public List<Shuttle> getPods() {
        return pods;
    }

    public Map<String, ShuttleRoute> getFleetRoutes() {
        return fleetRoutes;
    }

    private void clear() {
        shuttles.clear();
        pods.clear();
        network.clear();
        fleetRoutes.clear();
        fleetScan.clear();
        hits.clear();
        dogfights.clear();
        fastFleets.clear();
        flights.getEvents().clear();
        location = null;
        daysSinceRebuild = 0f;
        scanTimer = 0f;
    }

    private void advance(List<Shuttle> list, float amount) {
        Iterator<Shuttle> iterator = list.iterator();
        while (iterator.hasNext()) {
            Shuttle shuttle = iterator.next();
            shuttle.advance(amount, this);
            if (!shuttle.isDone()) continue;

            if (shuttle.getRoute() != null) shuttle.getRoute().active--;
            iterator.remove();
        }
    }
}
