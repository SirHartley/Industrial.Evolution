package indevo.ambience.shuttletraffic.flights;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
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
import indevo.ambience.shuttletraffic.movement.ShuttlePath;
import indevo.config.Setting;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class PodFlights extends BaseFlights {

    private static final float POD_SPEED = 35f;
    private static final float POD_PLANET_RANGE = 2000f;
    private static final int POD_CAP = 32;
    private static final int MAX_PODS_PER_FLEET = 16;
    private static final int EMPTY_SNAPSHOT_BURST = 3;
    private static final float BURST_GAP = 0.3f;
    private static final float BURST_SPREAD = 1.5f;
    private static final float FAN = 80f;
    private static final float BOOST_TIME = 2f;
    private static final float THREAT_RANGE = 3000f;

    private static final float EVACUATION_GAP = 0.25f;

    private final List<PodEvent> queue = new ArrayList<>();
    private final List<Evacuation> evacuations = new ArrayList<>();

    private record Evacuation(MarketAPI market, int count) {
    }

    // heading is NaN when no enemy is known
    private record PodEvent(LocationAPI location, Vector2f point, PlanetAPI planet, List<Integer> bursts, float heading) {
    }

    public PodFlights(ShuttleTraffic traffic, ShuttlePaths paths) {
        super(traffic, paths);
    }

    public void queue(CampaignFleetAPI fleet, BattleAPI battle) {
        if (Setting.SHUTTLE_TRAFFIC.intValue() <= 0) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI fleetLocation = fleet.getContainingLocation();
        if (player == null || fleetLocation == null || fleetLocation != player.getContainingLocation() || fleetLocation.isHyperspace()) return;

        List<Integer> bursts = getBursts(fleet);
        if (bursts.isEmpty()) return;

        PlanetAPI planet = findPodPlanet(fleetLocation, fleet.getLocation(), fleet.getFaction());
        if (planet == null) return;

        Vector2f point = new Vector2f(fleet.getLocation());
        queue.add(new PodEvent(fleetLocation, point, planet, bursts, getAwayHeading(fleet, battle, point)));
    }

    // a crewed craft that blew up: one pod toward the nearest planet in pod range, at once
    public void launchFromWreck(Vector2f point, float heading, FactionAPI faction) {
        List<Shuttle> pods = traffic.getPods();
        LocationAPI location = traffic.getLocation();
        if (pods.size() >= POD_CAP || location == null) return;

        PlanetAPI planet = findPodPlanet(location, point, faction);
        if (planet == null) return;

        pods.addAll(launch(new PodEvent(location, new Vector2f(point), planet, List.of(1), heading), 1));
    }

    public void queueEvacuation(MarketAPI market, int count) {
        if (Setting.SHUTTLE_TRAFFIC.intValue() > 0 && market != null && market.getPrimaryEntity() instanceof PlanetAPI) evacuations.add(new Evacuation(market, count));
    }

    public void clearQueue() {
        queue.clear();
        evacuations.clear();
    }

    // listener callbacks arrive during dialogs, pods wait until the player sees the system again
    public void launchQueued() {
        if ((queue.isEmpty() && evacuations.isEmpty()) || Global.getSector().getCampaignUI().isShowingDialog()) return;

        List<Shuttle> pods = traffic.getPods();
        for (Evacuation evacuation : evacuations) pods.addAll(launchEvacuation(evacuation, POD_CAP - pods.size()));
        evacuations.clear();

        for (PodEvent event : queue) {
            int room = POD_CAP - pods.size();
            if (room <= 0) break;
            if (event.location() != traffic.getLocation() || !event.planet().isAlive()) continue;

            pods.addAll(launch(event, room));
        }

        queue.clear();
    }

    // one burst per lost ship: a string of pods on one shared curve, fanned away from the enemy
    private List<Shuttle> launch(PodEvent event, int room) {
        List<Shuttle> pods = new ArrayList<>();
        PlanetAPI planet = event.planet();
        Vector2f point = event.point();
        ShuttleAnchor target = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, planet.getMarket(), null);
        float distance = Misc.getDistance(point, planet.getLocation());

        for (int size : event.bursts()) {
            ShuttleHull hull = ShuttleHullPool.getPod();
            if (hull == null || pods.size() >= room) break;

            float heading = Float.isNaN(event.heading()) ? random.nextFloat() * 360f : event.heading() + (random.nextFloat() - 0.5f) * FAN;
            float padOffset = paths.spread(60f);
            float startHandle = 120f + random.nextFloat() * 80f;
            float delay = random.nextFloat() * BURST_SPREAD;
            float boost = 45f + random.nextFloat() * 40f;
            float tumble = (20f + random.nextFloat() * 40f) * paths.randomSign();

            for (int i = 0; i < size && pods.size() < room; i++) {
                ShuttlePath.Waypoint start = new ShuttlePath.FixedWaypoint(point, Misc.getUnitVectorAtDegreeAngle(heading));
                ShuttlePath path = new ShuttlePath().add(new ShuttlePath.BezierSegment(start, new ShuttlePath.PadWaypoint(target, start, padOffset, 0f, true),
                        startHandle, paths.getHandle(target, distance), 0f));

                Shuttle pod = new Shuttle(hull, null, null, target, target, path, Shuttle.End.NONE, Shuttle.End.PLANET,
                        SpeedClass.CIVIL, POD_SPEED / hull.getCruiseSpeed(), paths.spread(6f), random.nextFloat(), ShuttleLook.pod(tumble * (0.7f + 0.6f * random.nextFloat())),
                        EnumSet.noneOf(Shuttle.Trait.class));
                pod.eject(delay + i * BURST_GAP, boost, BOOST_TIME);
                pods.add(pod);
            }
        }

        return pods;
    }

    // pods rise from a bombarded planet toward the other ports of the system, nose first, no tumble
    private List<Shuttle> launchEvacuation(Evacuation evacuation, int room) {
        List<Shuttle> pods = new ArrayList<>();
        ShuttleHull hull = ShuttleHullPool.getPod();
        PlanetAPI planet = (PlanetAPI) evacuation.market().getPrimaryEntity();
        if (hull == null || room <= 0 || planet.getContainingLocation() != traffic.getLocation()) return pods;

        ShuttleAnchor from = network.anchors.get(planet);
        if (from == null) from = new ShuttleAnchor(planet, ShuttleAnchor.Type.PLANET, ShuttleAnchor.Role.TARGET, evacuation.market(), evacuation.market().getFaction());

        List<ShuttleAnchor> ports = new ArrayList<>();
        for (ShuttleAnchor anchor : network.marketAnchors) if (anchor.entity != planet && anchor.isUsable(traffic.getLocation())) ports.add(anchor);
        if (ports.isEmpty()) return pods;

        for (int i = 0; i < evacuation.count() && pods.size() < room; i++) {
            ShuttleAnchor to = ports.get(random.nextInt(ports.size()));
            Shuttle pod = new Shuttle(hull, null, evacuation.market().getFaction(), from, to, paths.lanePath(from, to, ShuttleRoute.rollBend(random), false),
                    from.getEnd(), to.getEnd(), SpeedClass.CIVIL, POD_SPEED / hull.getCruiseSpeed(), paths.spread(6f), random.nextFloat(), ShuttleLook.pod(0f),
                    EnumSet.noneOf(Shuttle.Trait.class));
            pod.holdFor(i * EVACUATION_GAP + random.nextFloat() * EVACUATION_GAP);
            pods.add(pod);
        }

        return pods;
    }

    private float getAwayHeading(CampaignFleetAPI fleet, BattleAPI battle, Vector2f point) {
        float sumX = 0f;
        float sumY = 0f;
        int count = 0;

        List<CampaignFleetAPI> enemies = battle != null ? battle.getOtherSideFor(fleet) : null;
        if (enemies != null) {
            for (CampaignFleetAPI enemy : enemies) {
                if (enemy == null || enemy.getContainingLocation() != fleet.getContainingLocation()) continue;
                sumX += enemy.getLocation().x;
                sumY += enemy.getLocation().y;
                count++;
            }
        }

        if (count == 0) {
            ShuttleFleetScan.Entry threat = traffic.getFleetScan().findThreat(point.x, point.y, THREAT_RANGE, fleet.getFaction());
            if (threat == null) return Float.NaN;
            sumX = threat.x;
            sumY = threat.y;
            count = 1;
        }

        float dx = point.x - sumX / count;
        float dy = point.y - sumY / count;
        if (dx * dx + dy * dy < 1f) return Float.NaN;
        return Misc.getAngleInDegrees(new Vector2f(dx, dy));
    }

    // 2 pods per lost frigate or destroyer, 3 per cruiser or capital, crewed ships only
    private static List<Integer> getBursts(CampaignFleetAPI fleet) {
        List<Integer> bursts = new ArrayList<>();
        List<FleetMemberAPI> snapshot = fleet.getFleetData().getSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            bursts.add(EMPTY_SNAPSHOT_BURST);
            return bursts;
        }

        int total = 0;
        for (FleetMemberAPI member : snapshot) {
            if (member.isFighterWing() || Misc.isAutomated(member) || member.getHullSpec().getMinCrew() <= 0f) continue;

            int size = switch (member.getHullSpec().getHullSize()) {
                case FRIGATE, DESTROYER -> 2;
                case CRUISER, CAPITAL_SHIP -> 3;
                default -> 0;
            };
            size = Math.min(size, MAX_PODS_PER_FLEET - total);
            if (size <= 0) continue;

            bursts.add(size);
            total += size;
        }

        return bursts;
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
