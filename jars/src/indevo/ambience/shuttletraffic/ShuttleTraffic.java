package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import indevo.ambience.shuttletraffic.flights.ShuttleFlights;
import indevo.ambience.shuttletraffic.flights.ShuttlePaths;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ShuttleTraffic {

    private static final float SCAN_INTERVAL = 0.25f;

    private final ShuttleNetwork network = new ShuttleNetwork();
    private final Random random = new Random();
    private final ShuttleFleetScan fleetScan = new ShuttleFleetScan();
    private final ShuttleFlights flights = new ShuttleFlights(this);
    private final ShuttleSpawner spawner = new ShuttleSpawner(this);
    private final ShuttleReactions reactions = new ShuttleReactions(this);
    private final List<Shuttle> shuttles = new ArrayList<>();
    private final List<Shuttle> pods = new ArrayList<>();

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
            return;
        }

        time += amount;
        float elapsedDays = Global.getSector().getClock().convertToDays(amount);
        days += elapsedDays;

        if (current != location) {
            clear();
            location = current;
            network.rebuild(location, random);
            fleetScan.scan(location, player);
            reactions.updateThreats();
            spawner.warmStart(cap);
        } else {
            daysSinceRebuild += elapsedDays;
            if (daysSinceRebuild >= 1f) {
                daysSinceRebuild = 0f;
                ShuttleHullPool.dropStaleFactionPools();
                network.rebuild(location, random);
                spawner.rollRareFlights();
            }
        }

        scanTimer -= amount;
        if (scanTimer <= 0f) {
            scanTimer = SCAN_INTERVAL;
            fleetScan.scan(location, player);
            reactions.updateThreats();
            reactions.react();
        }

        spawner.spawn(amount, cap);
        flights.getPods().launchQueued();
        advance(shuttles, amount);
        advance(pods, amount);
    }

    public void queuePods(CampaignFleetAPI fleet) {
        flights.getPods().queue(fleet);
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

    public ShuttleNetwork getNetwork() {
        return network;
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

    private void clear() {
        shuttles.clear();
        pods.clear();
        network.clear();
        fleetScan.clear();
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
