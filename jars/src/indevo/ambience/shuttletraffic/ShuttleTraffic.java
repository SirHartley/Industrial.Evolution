package indevo.ambience.shuttletraffic;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ShuttleTraffic {

    private static final float SCAN_INTERVAL = 0.25f;
    private static final float POD_PLANET_RANGE = 2000f;
    private static final int POD_CAP = 32;
    private static final int MAX_PODS_PER_FLEET = 16;
    private static final int EMPTY_SNAPSHOT_PODS = 3;

    private final ShuttleNetwork network = new ShuttleNetwork();
    private final Random random = new Random();
    private final ShuttleFleetScan fleetScan = new ShuttleFleetScan();
    private final ShuttleFlights flights = new ShuttleFlights(this, network, random);
    private final ShuttleSpawner spawner = new ShuttleSpawner(this);
    private final ShuttleReactions reactions = new ShuttleReactions(this);
    private final List<Shuttle> shuttles = new ArrayList<>();
    private final List<Shuttle> pods = new ArrayList<>();
    private final List<PodEvent> podQueue = new ArrayList<>();

    private LocationAPI location;
    private float time = 0f;
    private float days = 0f;
    private float daysSinceRebuild = 0f;
    private float scanTimer = 0f;

    private record PodEvent(LocationAPI location, Vector2f point, PlanetAPI planet, int count) {
    }

    public void advance(float amount, CampaignFleetAPI player, int cap) {
        LocationAPI current = player != null ? player.getContainingLocation() : null;

        if (cap <= 0 || current == null || current.isHyperspace()) {
            if (location != null) clear();
            podQueue.clear();
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
        launchQueuedPods();
        advance(shuttles, amount);
        advance(pods, amount);
    }

    public void queuePods(CampaignFleetAPI fleet) {
        if (Setting.SHUTTLE_TRAFFIC.intValue() <= 0) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI fleetLocation = fleet.getContainingLocation();
        if (player == null || fleetLocation == null || fleetLocation != player.getContainingLocation() || fleetLocation.isHyperspace()) return;

        int count = getPodCount(fleet);
        if (count <= 0) return;

        PlanetAPI planet = findPodPlanet(fleetLocation, fleet.getLocation(), fleet.getFaction());
        if (planet != null) podQueue.add(new PodEvent(fleetLocation, new Vector2f(fleet.getLocation()), planet, count));
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

    private void launchQueuedPods() {
        if (podQueue.isEmpty() || Global.getSector().getCampaignUI().isShowingDialog()) return;

        for (PodEvent event : podQueue) {
            int room = POD_CAP - pods.size();
            if (room <= 0) break;
            if (event.location() != location || !event.planet().isAlive()) continue;

            pods.addAll(flights.launchPods(event.point(), event.planet(), Math.min(room, event.count())));
        }

        podQueue.clear();
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

    private static int getPodCount(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> snapshot = fleet.getFleetData().getSnapshot();
        if (snapshot == null || snapshot.isEmpty()) return EMPTY_SNAPSHOT_PODS;

        int count = 0;
        for (FleetMemberAPI member : snapshot) {
            if (member.isFighterWing() || Misc.isAutomated(member) || member.getHullSpec().getMinCrew() <= 0f) continue;

            count += switch (member.getHullSpec().getHullSize()) {
                case FRIGATE, DESTROYER -> 1;
                case CRUISER -> 2;
                case CAPITAL_SHIP -> 3;
                default -> 0;
            };
        }

        return Math.min(MAX_PODS_PER_FLEET, count);
    }

    private static PlanetAPI findPodPlanet(LocationAPI location, Vector2f point, FactionAPI faction) {
        PlanetAPI nearest = null;
        PlanetAPI friendly = null;
        float nearestDist = POD_PLANET_RANGE;
        float friendlyDist = POD_PLANET_RANGE;

        for (PlanetAPI planet : location.getPlanets()) {
            if (planet.isStar()) continue;

            float dist = Misc.getDistance(point, planet.getLocation()) - planet.getRadius();
            if (dist > POD_PLANET_RANGE) continue;

            MarketAPI market = planet.getMarket();
            if (market != null && market.isInEconomy() && faction != null && !market.getFaction().isHostileTo(faction) && dist < friendlyDist) {
                friendlyDist = dist;
                friendly = planet;
            }

            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = planet;
            }
        }

        return friendly != null ? friendly : nearest;
    }
}
